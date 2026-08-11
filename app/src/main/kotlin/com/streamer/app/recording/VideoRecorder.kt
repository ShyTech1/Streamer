package com.streamer.app.recording

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import com.streamer.app.camera.CameraController
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VideoRecorder(
    private val ctx: Context,
    private val camera: CameraController,
) {
    private var recording: Recording? = null

    @SuppressLint("MissingPermission")
    fun start() {
        val vc = camera.videoCapture() ?: run {
            Log.w(TAG, "no VideoCapture available")
            return
        }
        val dir = File(ctx.getExternalFilesDir(null), "recordings").apply { mkdirs() }
        val name = SimpleDateFormat("yyyyMMdd-HHmmss'.mp4'", Locale.US).format(Date())
        val opts = FileOutputOptions.Builder(File(dir, name)).build()

        val pending = vc.output.prepareRecording(ctx, opts).withAudioEnabled()
        recording = pending.start(ContextCompat.getMainExecutor(ctx)) { evt ->
            when (evt) {
                is VideoRecordEvent.Finalize -> Log.i(TAG, "recording finalized: ${evt.outputResults.outputUri}")
                is VideoRecordEvent.Start    -> Log.i(TAG, "recording started")
                is VideoRecordEvent.Status   -> {}
                else -> {}
            }
        }
        camera.setActiveRecording(recording)
    }

    fun stop() {
        recording?.stop()
        recording = null
        camera.setActiveRecording(null)
    }

    companion object { private const val TAG = "VideoRecorder" }
}
