package ru.srr.vpscockpit

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import java.util.Properties
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class SshTunnelService : Service() {
    private val running = AtomicBoolean(false)
    private var worker: Thread? = null
    @Volatile private var session: Session? = null
    @Volatile private var activeConfig: TunnelConfig? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopTunnel()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_STATUS_PING -> {
                replayStatus()
            }
            else -> {
                val cfg = TunnelConfigStore.fromIntent(this, intent)
                if (!cfg.isComplete) {
                    broadcast(TunnelEvents.STATE_FAILED, "Нет данных подключения")
                    stopSelf()
                    return START_NOT_STICKY
                }
                activeConfig = cfg
                if (running.compareAndSet(false, true)) {
                    startForeground(NOTIF_ID, buildNotification(getString(R.string.tunnel_connecting)))
                    broadcast(TunnelEvents.STATE_CONNECTING, getString(R.string.tunnel_connecting))
                    worker = thread(name = "ssh-tunnel", isDaemon = true) { runTunnelLoop(cfg) }
                } else {
                    // already running: if host changed, restart
                    val current = activeConfig
                    if (current != null &&
                        (current.host != cfg.host || current.localPort != cfg.localPort ||
                            current.remotePort != cfg.remotePort || current.user != cfg.user)
                    ) {
                        stopTunnel()
                        running.set(true)
                        activeConfig = cfg
                        startForeground(NOTIF_ID, buildNotification(getString(R.string.tunnel_connecting)))
                        broadcast(TunnelEvents.STATE_CONNECTING, getString(R.string.tunnel_connecting))
                        worker = thread(name = "ssh-tunnel", isDaemon = true) { runTunnelLoop(cfg) }
                    } else {
                        replayStatus()
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopTunnel()
        super.onDestroy()
    }

    private fun runTunnelLoop(cfg: TunnelConfig) {
        var attempt = 0
        var hostKeyChanged = false
        while (running.get()) {
            attempt++
            val pins = HostKeyPins.forApp(this)
            try {
                val msg = "SSH ${cfg.host}:${cfg.sshPort} (попытка $attempt)…"
                Log.i(TAG, msg)
                broadcast(TunnelEvents.STATE_CONNECTING, msg)
                updateNotification(msg)

                val jsch = JSch()
                jsch.hostKeyRepository = pins
                if (!BuildConfig.PUBLIC_BUILD) {
                    try {
                        val keyId = resources.getIdentifier("vps_ssh_key", "raw", packageName)
                        if (keyId != 0) {
                            val keyBytes = resources.openRawResource(keyId).use { it.readBytes() }
                            jsch.addIdentity("vps-key", keyBytes, null, null)
                        }
                    } catch (t: Throwable) {
                        Log.w(TAG, "no bundled key", t)
                    }
                }

                val s = jsch.getSession(cfg.user, cfg.host, cfg.sshPort)
                if (cfg.password.isNotBlank()) {
                    s.setPassword(cfg.password)
                }
                val cfgProps = Properties().apply {
                    put("StrictHostKeyChecking", "yes")
                    put(
                        "PreferredAuthentications",
                        if (BuildConfig.PUBLIC_BUILD) "password,keyboard-interactive,publickey"
                        else "publickey,password"
                    )
                    put("ServerAliveInterval", "30")
                    put("ServerAliveCountMax", "4")
                }
                s.setConfig(cfgProps)
                s.connect(25_000)
                session = s

                val localPort = cfg.localPort
                val remotePort = cfg.remotePort
                try {
                    s.delPortForwardingL(localPort)
                } catch (_: Throwable) {
                }
                s.setPortForwardingL("127.0.0.1", localPort, "127.0.0.1", remotePort)

                val ready = "Туннель :$localPort → ${cfg.host}:$remotePort"
                Log.i(TAG, ready)
                broadcast(TunnelEvents.STATE_READY, ready)
                updateNotification(getString(R.string.tunnel_ready))

                while (running.get() && s.isConnected) {
                    Thread.sleep(2_000L)
                }
                if (running.get()) {
                    throw IllegalStateException("SSH session closed")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "tunnel failed", t)
                val change = pins.lastChange
                if (change != null) {
                    hostKeyChanged = true
                    running.set(false)
                    val msg = getString(R.string.hostkey_changed, cfg.host, change.presented)
                    broadcast(TunnelEvents.STATE_HOSTKEY_CHANGED, msg)
                    updateNotification(getString(R.string.hostkey_changed_short))
                } else {
                    val err = t.message ?: t.javaClass.simpleName
                    broadcast(TunnelEvents.STATE_FAILED, "Туннель упал: $err")
                    updateNotification(getString(R.string.tunnel_failed) + ": " + err)
                }
            } finally {
                cleanupSession()
            }

            if (!running.get()) break
            try {
                Thread.sleep(3_000L)
            } catch (_: InterruptedException) {
                break
            }
        }
        if (!hostKeyChanged) broadcast(TunnelEvents.STATE_STOPPED, "Туннель остановлен")
    }

    private fun stopTunnel() {
        running.set(false)
        cleanupSession()
        worker?.interrupt()
        worker = null
    }

    private fun cleanupSession() {
        try {
            session?.disconnect()
        } catch (_: Throwable) {
        }
        session = null
    }

    private fun replayStatus() {
        val s = session
        val alive = s != null && s.isConnected
        if (alive) {
            val msg = lastMessage.ifBlank { getString(R.string.tunnel_ready) }
            startForeground(NOTIF_ID, buildNotification(msg))
            broadcast(TunnelEvents.STATE_READY, msg)
        } else if (running.get()) {
            val msg = getString(R.string.tunnel_connecting)
            startForeground(NOTIF_ID, buildNotification(msg))
            broadcast(TunnelEvents.STATE_CONNECTING, msg)
        } else {
            broadcast(lastState, lastMessage)
        }
    }

    private fun broadcast(state: String, message: String) {
        lastState = state
        lastMessage = message
        sendBroadcast(
            Intent(TunnelEvents.ACTION_STATUS)
                .setPackage(packageName)
                .putExtra(TunnelEvents.EXTRA_STATE, state)
                .putExtra(TunnelEvents.EXTRA_MESSAGE, message)
        )
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        ensureChannel()
        val launch = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, SshTunnelService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(launch)
            .setOngoing(true)
            .addAction(0, "Стоп", stop)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.tunnel_channel),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    companion object {
        const val ACTION_STOP = "ru.srr.vpscockpit.STOP"
        const val ACTION_STATUS_PING = "ru.srr.vpscockpit.STATUS_PING"
        private const val CHANNEL_ID = "vps_tunnel"
        private const val NOTIF_ID = 42
        private const val TAG = "VpsTunnel"

        @Volatile var lastState: String = TunnelEvents.STATE_CONNECTING
            private set
        @Volatile var lastMessage: String = ""
            private set
    }
}
