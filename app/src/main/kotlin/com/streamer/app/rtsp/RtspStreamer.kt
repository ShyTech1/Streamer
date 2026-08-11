package com.streamer.app.rtsp

import android.content.Context
import android.util.Log
import com.streamer.app.Config
import com.streamer.app.Frame

/**
 * Placeholder for native RTSP/H.264 streaming.
 *
 * MJPEG over HTTP (see HttpServer) is the primary streaming path in v1 — it works
 * in browsers, VLC, ffmpeg, OBS, and Home Assistant natively, and is what IP Webcam
 * users overwhelmingly consume too.
 *
 * A proper RTSP server on this device requires either (a) sharing the CameraX camera
 * session with a second H.264 encoder Surface, or (b) tearing down CameraX and giving
 * RootEncoder full control of the camera while RTSP is active. Both are real work and
 * best done as a follow-up when there's a device to test on.
 *
 * For now: pull the MJPEG stream and consume it with anything, or point Larix Broadcaster
 * / a helper app at your own MediaMTX to push RTMP.
 */
class RtspStreamer(private val ctx: Context, private val cfg: Config) {
    fun start() { Log.i(TAG, "RTSP streaming stub — MJPEG active on port ${cfg.port}") }
    fun stop() {}
    fun pushFrame(f: Frame) {}
    companion object { private const val TAG = "RtspStreamer" }
}
