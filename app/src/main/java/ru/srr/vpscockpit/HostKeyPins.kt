package ru.srr.vpscockpit

import android.content.Context
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.UserInfo
import java.security.MessageDigest
import java.util.Base64

interface PinStore {
    fun get(host: String): String?
    fun put(host: String, fingerprint: String)
    fun remove(host: String)
}

class HostKeyPins(private val store: PinStore) : HostKeyRepository {
    data class Change(val host: String, val pinned: String, val presented: String)

    @Volatile var lastChange: Change? = null
        private set

    override fun check(host: String, key: ByteArray): Int {
        val presented = fingerprint(key)
        return when (val pinned = store.get(host)) {
            null -> {
                store.put(host, presented)
                HostKeyRepository.OK
            }
            presented -> HostKeyRepository.OK
            else -> {
                lastChange = Change(host, pinned, presented)
                HostKeyRepository.CHANGED
            }
        }
    }

    override fun add(hostkey: HostKey?, ui: UserInfo?) {}

    override fun remove(host: String?, type: String?) {
        host?.let(store::remove)
    }

    override fun remove(host: String?, type: String?, key: ByteArray?) {
        host?.let(store::remove)
    }

    override fun getKnownHostsRepositoryID(): String = "vps-cockpit-pins"

    override fun getHostKey(): Array<HostKey> = emptyArray()

    override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()

    companion object {
        fun fingerprint(key: ByteArray): String =
            "SHA256:" + Base64.getEncoder().withoutPadding()
                .encodeToString(MessageDigest.getInstance("SHA-256").digest(key))

        fun keyFor(host: String, port: Int): String = if (port == 22) host else "[$host]:$port"

        fun forApp(context: Context) = HostKeyPins(PrefsPinStore(context))

        fun forget(context: Context, host: String, port: Int) {
            PrefsPinStore(context).remove(keyFor(host, port))
        }
    }
}

class PrefsPinStore(context: Context) : PinStore {
    private val prefs = context.applicationContext.getSharedPreferences("ssh_host_keys", Context.MODE_PRIVATE)

    override fun get(host: String): String? = prefs.getString(host, null)

    override fun put(host: String, fingerprint: String) {
        prefs.edit().putString(host, fingerprint).commit()
    }

    override fun remove(host: String) {
        prefs.edit().remove(host).commit()
    }
}

object TunnelTls {
    private val localHosts = setOf("127.0.0.1", "localhost")

    fun mayProceed(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return false
        return uri.scheme.equals("https", ignoreCase = true) && uri.host?.lowercase() in localHosts
    }
}
