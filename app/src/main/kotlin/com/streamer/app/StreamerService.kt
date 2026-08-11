package com.streamer.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.streamer.app.camera.CameraController
import com.streamer.app.http.HttpServer
import com.streamer.app.motion.MotionDetector
import com.streamer.app.recording.VideoRecorder
import com.streamer.app.rtsp.RtspStreamer
import com.streamer.app.sensors.SensorProvider
import kotlinx.coroutines.launch
import java.net.NetworkInterface

class StreamerService : LifecycleService() {

    private lateinit var cfg: Config
    private lateinit var camera: CameraController
    private var http: HttpServer? = null
    private var rtsp: RtspStreamer? = null
    private var motion: MotionDetector? = null
    private var recorder: VideoRecorder? = null
    private lateinit var sensors: SensorProvider
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        cfg = Config.load(this)
        camera = CameraController(this, lifecycleScope)
        sensors = SensorProvider(this)
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (intent?.action == ACTION_STOP) {
            stopEverything()
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundCompat()
        cfg = Config.load(this)

        camera.start(this, cfg) {
            http = HttpServer(this, cfg, camera, sensors, ::isRecording, ::toggleRecording).also {
                try { it.startServer() } catch (_: Exception) {}
            }
            if (cfg.enableAudio) {
                rtsp = RtspStreamer(this, cfg).also { r ->
                    r.start()
                    lifecycleScope.launch {
                        FrameBus.frames.collect { r.pushFrame(it) }
                    }
                }
            }
            if (cfg.motionRecord) {
                motion = MotionDetector(lifecycleScope) { evt ->
                    lifecycleScope.launch { FrameBus.publishMotion(evt) }
                    if (!isRecording()) toggleRecording()
                }.also { it.start() }
            }
        }

        instance = this
        return START_STICKY
    }

    private fun toggleRecording() {
        val r = recorder
        if (r != null) {
            r.stop()
            recorder = null
        } else {
            val new = VideoRecorder(this, camera).also { it.start() }
            recorder = new
        }
    }

    private fun isRecording(): Boolean = recorder != null

    private fun stopEverything() {
        recorder?.stop(); recorder = null
        motion?.stop(); motion = null
        rtsp?.stop(); rtsp = null
        http?.stop(); http = null
        camera.stop()
        wakeLock?.let { if (it.isHeld) it.release() }
        instance = null
    }

    override fun onDestroy() {
        stopEverything()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Streamer::wl").apply {
            setReferenceCounted(false)
            acquire(24L * 60 * 60 * 1000)
        }
    }

    private fun startForegroundCompat() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                getString(R.string.notif_channel_id),
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(ch)
        }
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, StreamerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notif: Notification = NotificationCompat.Builder(this, getString(R.string.notif_channel_id))
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setContentIntent(openIntent)
            .addAction(0, "Stop", stopIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID, notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    companion object {
        const val ACTION_STOP = "com.streamer.app.STOP"
        private const val NOTIF_ID = 1

        @Volatile var instance: StreamerService? = null

        fun localIpAddresses(): List<String> {
            val out = mutableListOf<String>()
            try {
                val ifs = NetworkInterface.getNetworkInterfaces() ?: return out
                for (nif in ifs) {
                    if (!nif.isUp || nif.isLoopback) continue
                    for (addr in nif.inetAddresses) {
                        if (addr.isLoopbackAddress) continue
                        val h = addr.hostAddress ?: continue
                        if (h.contains(":")) continue
                        out.add(h)
                    }
                }
            } catch (_: Exception) {}
            return out
        }
    }
}
