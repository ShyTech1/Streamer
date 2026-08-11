package com.streamer.app

import android.content.Context

data class Config(
    val port: Int = 8080,
    val rtspPort: Int = 8554,
    val user: String = "",
    val pass: String = "",
    val useHttps: Boolean = false,
    val motionRecord: Boolean = false,
    val stealth: Boolean = true,
    val width: Int = 1280,
    val height: Int = 720,
    val fps: Int = 15,
    val jpegQuality: Int = 70,
    val enableAudio: Boolean = true,
    val startOnBoot: Boolean = false,
    val motionSensitivity: Int = 12,     // 1..40 average pixel diff threshold
    val motionCooldownSec: Int = 5,
    val motionOnlyStream: Boolean = false,
    val motionWindowSec: Int = 10,       // stream keeps flowing this long after motion
) {
    val hasAuth get() = user.isNotBlank() && pass.isNotBlank()

    companion object {
        private const val PREFS = "streamer_prefs"

        fun load(ctx: Context): Config {
            val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return Config(
                port = p.getInt("port", 8080),
                rtspPort = p.getInt("rtspPort", 8554),
                user = p.getString("user", "") ?: "",
                pass = p.getString("pass", "") ?: "",
                useHttps = p.getBoolean("useHttps", false),
                motionRecord = p.getBoolean("motionRecord", false),
                stealth = p.getBoolean("stealth", true),
                width = p.getInt("width", 1280),
                height = p.getInt("height", 720),
                fps = p.getInt("fps", 15),
                jpegQuality = p.getInt("jpegQuality", 70),
                enableAudio = p.getBoolean("enableAudio", true),
                startOnBoot = p.getBoolean("startOnBoot", false),
                motionSensitivity = p.getInt("motionSensitivity", 12),
                motionCooldownSec = p.getInt("motionCooldownSec", 5),
                motionOnlyStream = p.getBoolean("motionOnlyStream", false),
                motionWindowSec = p.getInt("motionWindowSec", 10),
            )
        }

        fun save(ctx: Context, c: Config) {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
                putInt("port", c.port)
                putInt("rtspPort", c.rtspPort)
                putString("user", c.user)
                putString("pass", c.pass)
                putBoolean("useHttps", c.useHttps)
                putBoolean("motionRecord", c.motionRecord)
                putBoolean("stealth", c.stealth)
                putInt("width", c.width)
                putInt("height", c.height)
                putInt("fps", c.fps)
                putInt("jpegQuality", c.jpegQuality)
                putBoolean("enableAudio", c.enableAudio)
                putBoolean("startOnBoot", c.startOnBoot)
                putInt("motionSensitivity", c.motionSensitivity)
                putInt("motionCooldownSec", c.motionCooldownSec)
                putBoolean("motionOnlyStream", c.motionOnlyStream)
                putInt("motionWindowSec", c.motionWindowSec)
                apply()
            }
        }
    }
}
