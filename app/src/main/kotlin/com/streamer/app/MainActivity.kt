package com.streamer.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.streamer.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var vb: ActivityMainBinding

    private val permsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) startServer()
        else Toast.makeText(this, "Camera / mic permission required", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vb = ActivityMainBinding.inflate(layoutInflater)
        setContentView(vb.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val cfg = Config.load(this)
        vb.user.setText(cfg.user)
        vb.pass.setText(cfg.pass)
        vb.httpsBox.isChecked = cfg.useHttps
        vb.motionBox.isChecked = cfg.motionRecord
        vb.stealthBox.isChecked = cfg.stealth

        vb.startBtn.setOnClickListener {
            saveCfg()
            requestPermsAndStart()
        }
        vb.stopBtn.setOnClickListener {
            val i = Intent(this, StreamerService::class.java).setAction(StreamerService.ACTION_STOP)
            startService(i)
            vb.status.text = "Server stopped"
            vb.urls.text = ""
        }
    }

    override fun onResume() {
        super.onResume()
        if (StreamerService.instance != null) {
            showRunning()
        }
    }

    private fun saveCfg() {
        val cur = Config.load(this)
        Config.save(this, cur.copy(
            user = vb.user.text.toString().trim(),
            pass = vb.pass.text.toString(),
            useHttps = vb.httpsBox.isChecked,
            motionRecord = vb.motionBox.isChecked,
            stealth = vb.stealthBox.isChecked,
        ))
    }

    private fun requestPermsAndStart() {
        val needed = mutableListOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed += Manifest.permission.POST_NOTIFICATIONS
        }
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) startServer() else permsLauncher.launch(missing.toTypedArray())
    }

    private fun startServer() {
        val svc = Intent(this, StreamerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc)
        else startService(svc)
        showRunning()
        if (Config.load(this).stealth) {
            window.attributes = window.attributes.apply { screenBrightness = 0f }
        }
    }

    private fun showRunning() {
        val cfg = Config.load(this)
        val scheme = if (cfg.useHttps) "https" else "http"
        val ips = StreamerService.localIpAddresses()
        vb.status.text = "Server running on port ${cfg.port}"
        vb.urls.text = ips.joinToString("\n") { "$scheme://$it:${cfg.port}/" }
    }
}
