package com.streamer.app.motion

import android.graphics.BitmapFactory
import com.streamer.app.Frame
import com.streamer.app.FrameBus
import com.streamer.app.MotionEvent
import com.streamer.app.http.MotionLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

class MotionDetector(
    private val scope: CoroutineScope,
    private val threshold: Double = 12.0,
    private val cooldownMs: Long = 5_000L,
    private val onMotion: (MotionEvent) -> Unit,
) {
    private var job: Job? = null
    private var prev: IntArray? = null
    private var lastFire = 0L

    fun start() {
        job = scope.launch(Dispatchers.Default) {
            FrameBus.frames.collect { f -> analyze(f) }
        }
    }

    fun stop() { job?.cancel(); job = null; prev = null }

    private fun analyze(f: Frame) {
        val opts = BitmapFactory.Options().apply { inSampleSize = 16 }
        val bmp = BitmapFactory.decodeByteArray(f.jpeg, 0, f.jpeg.size, opts) ?: return
        val w = bmp.width; val h = bmp.height
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        bmp.recycle()

        val gray = IntArray(pixels.size)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            gray[i] = (r * 30 + g * 59 + b * 11) / 100
        }

        val old = prev
        prev = gray
        if (old == null || old.size != gray.size) return

        var sum = 0L
        for (i in gray.indices) sum += abs(gray[i] - old[i])
        val avg = sum.toDouble() / gray.size

        if (avg > threshold) {
            val now = System.currentTimeMillis()
            if (now - lastFire > cooldownMs) {
                lastFire = now
                val evt = MotionEvent(now, avg)
                MotionLog.push(evt)
                onMotion(evt)
            }
        }
    }
}
