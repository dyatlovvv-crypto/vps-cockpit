package ru.srr.vpscockpit

import android.content.Context
import android.content.Intent

data class TunnelConfig(
    val host: String,
    val user: String,
    val password: String,
    val sshPort: Int = 22,
    val remotePort: Int = 9090,
    val localPort: Int = 19090
) {
    val isComplete: Boolean
        get() = host.isNotBlank() && user.isNotBlank() && (password.isNotBlank() || !BuildConfig.PUBLIC_BUILD)
}

object TunnelConfigStore {
    private const val PREFS = "vps_connection"
    private const val K_HOST = "host"
    private const val K_USER = "user"
    private const val K_PASS = "password"
    private const val K_SSH = "ssh_port"
    private const val K_REMOTE = "remote_port"
    private const val K_LOCAL = "local_port"

    fun hasSaved(context: Context): Boolean {
        if (!BuildConfig.PUBLIC_BUILD) return true
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return !p.getString(K_HOST, null).isNullOrBlank() &&
            !p.getString(K_USER, null).isNullOrBlank() &&
            !p.getString(K_PASS, null).isNullOrBlank()
    }

    fun save(context: Context, cfg: TunnelConfig) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(K_HOST, cfg.host.trim())
            .putString(K_USER, cfg.user.trim())
            .putString(K_PASS, cfg.password)
            .putInt(K_SSH, cfg.sshPort)
            .putInt(K_REMOTE, cfg.remotePort)
            .putInt(K_LOCAL, cfg.localPort)
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun load(context: Context): TunnelConfig {
        if (!BuildConfig.PUBLIC_BUILD) {
            return TunnelConfig(
                host = BuildConfig.VPS_HOST,
                user = BuildConfig.VPS_USER,
                password = BuildConfig.VPS_PASSWORD,
                sshPort = 22,
                remotePort = BuildConfig.VPS_REMOTE_PORT,
                localPort = BuildConfig.VPS_LOCAL_PORT
            )
        }
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return TunnelConfig(
            host = p.getString(K_HOST, "").orEmpty(),
            user = p.getString(K_USER, "").orEmpty(),
            password = p.getString(K_PASS, "").orEmpty(),
            sshPort = p.getInt(K_SSH, 22),
            remotePort = p.getInt(K_REMOTE, 9090),
            localPort = p.getInt(K_LOCAL, 19090)
        )
    }

    fun fromIntent(context: Context, intent: Intent?): TunnelConfig {
        val host = intent?.getStringExtra(EXTRA_HOST)
        if (!host.isNullOrBlank()) {
            return TunnelConfig(
                host = host,
                user = intent.getStringExtra(EXTRA_USER).orEmpty(),
                password = intent.getStringExtra(EXTRA_PASSWORD).orEmpty(),
                sshPort = intent.getIntExtra(EXTRA_SSH_PORT, 22),
                remotePort = intent.getIntExtra(EXTRA_REMOTE_PORT, 9090),
                localPort = intent.getIntExtra(EXTRA_LOCAL_PORT, 19090)
            )
        }
        return load(context)
    }

    fun putExtras(intent: Intent, cfg: TunnelConfig): Intent {
        return intent
            .putExtra(EXTRA_HOST, cfg.host)
            .putExtra(EXTRA_USER, cfg.user)
            .putExtra(EXTRA_PASSWORD, cfg.password)
            .putExtra(EXTRA_SSH_PORT, cfg.sshPort)
            .putExtra(EXTRA_REMOTE_PORT, cfg.remotePort)
            .putExtra(EXTRA_LOCAL_PORT, cfg.localPort)
    }

    const val EXTRA_HOST = "cfg_host"
    const val EXTRA_USER = "cfg_user"
    const val EXTRA_PASSWORD = "cfg_password"
    const val EXTRA_SSH_PORT = "cfg_ssh_port"
    const val EXTRA_REMOTE_PORT = "cfg_remote_port"
    const val EXTRA_LOCAL_PORT = "cfg_local_port"
}
