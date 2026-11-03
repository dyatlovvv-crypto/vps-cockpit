package ru.srr.vpscockpit

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!BuildConfig.PUBLIC_BUILD) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_login)

        val host = findViewById<EditText>(R.id.inputHost)
        val user = findViewById<EditText>(R.id.inputUser)
        val pass = findViewById<EditText>(R.id.inputPassword)
        val sshPort = findViewById<EditText>(R.id.inputSshPort)
        val remotePort = findViewById<EditText>(R.id.inputRemotePort)
        val localPort = findViewById<EditText>(R.id.inputLocalPort)
        val error = findViewById<TextView>(R.id.loginError)
        val btn = findViewById<Button>(R.id.btnConnect)

        val saved = TunnelConfigStore.load(this)
        if (saved.host.isNotBlank()) host.setText(saved.host)
        if (saved.user.isNotBlank()) user.setText(saved.user)
        if (saved.password.isNotBlank()) pass.setText(saved.password)
        sshPort.setText(saved.sshPort.toString())
        remotePort.setText(saved.remotePort.toString())
        localPort.setText(saved.localPort.toString())

        btn.setOnClickListener {
            error.visibility = View.GONE
            val cfg = TunnelConfig(
                host = host.text.toString().trim(),
                user = user.text.toString().trim(),
                password = pass.text.toString(),
                sshPort = sshPort.text.toString().toIntOrNull() ?: 22,
                remotePort = remotePort.text.toString().toIntOrNull() ?: 9090,
                localPort = localPort.text.toString().toIntOrNull() ?: 19090
            )
            when {
                cfg.host.isBlank() -> showError(error, "Укажи host / IP")
                cfg.user.isBlank() -> showError(error, "Укажи SSH user")
                cfg.password.isBlank() -> showError(error, "Укажи SSH password")
                cfg.sshPort !in 1..65535 -> showError(error, "SSH port кривой")
                cfg.remotePort !in 1..65535 -> showError(error, "Cockpit port кривой")
                cfg.localPort !in 1..65535 -> showError(error, "Local port кривой")
                else -> {
                    TunnelConfigStore.save(this, cfg)
                    startActivity(
                        Intent(this, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                    finish()
                }
            }
        }
    }

    private fun showError(view: TextView, msg: String) {
        view.text = msg
        view.visibility = View.VISIBLE
    }
}
