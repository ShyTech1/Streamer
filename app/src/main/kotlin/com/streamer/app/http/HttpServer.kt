package com.streamer.app.http

import android.content.Context
import android.util.Base64
import com.streamer.app.Config
import com.streamer.app.FrameBus
import com.streamer.app.camera.CameraController
import com.streamer.app.motion.MotionDetector
import com.streamer.app.sensors.SensorProvider
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream

class HttpServer(
    private val ctx: Context,
    initialCfg: Config,
    private val camera: CameraController,
    private val sensors: SensorProvider,
    private val getMotion: () -> MotionDetector?,
    private val isRecording: () -> Boolean,
    private val toggleRecording: () -> Unit,
    private val getCfg: () -> Config,
    private val updateCfg: (Config) -> Unit,
) : NanoHTTPD(initialCfg.port) {

    private val useHttps = initialCfg.useHttps
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun startServer() {
        if (useHttps) makeSecure(Tls.serverSocketFactory(ctx), null)
        start(SOCKET_READ_TIMEOUT, true)
    }

    override fun stop() {
        scope.cancel()
        super.stop()
    }

    override fun serve(session: IHTTPSession): Response {
        if (!authOk(session)) return unauthorized()

        return when (session.uri.trimEnd('/')) {
            "", "/index.html" -> Assets.serve(ctx, "index.html", "text/html")
            "/style.css"       -> Assets.serve(ctx, "style.css", "text/css")
            "/control.js"      -> Assets.serve(ctx, "control.js", "application/javascript")
            "/video.mjpeg"     -> mjpegStream()
            "/photo.jpg"       -> latestFrame()
            "/status"          -> statusJson()
            "/sensors"         -> sensors.json().let { json(it) }
            "/motion/events"   -> motionEventsJson()
            "/motion/state"    -> motionStateJson()
            "/cameras"         -> camerasJson()
            "/control/torch"   -> torch(session)
            "/control/switch"  -> switchCam()
            "/control/zoom"    -> zoom(session)
            "/control/focus"   -> focus()
            "/control/record"  -> record()
            "/control/wide"    -> wide(session)
            "/control/camera"  -> pickCamera(session)
            "/control/motion"  -> updateMotion(session)
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "404")
        }
    }

    private fun authOk(session: IHTTPSession): Boolean {
        val cfg = getCfg()
        if (!cfg.hasAuth) return true
        val hdr = session.headers["authorization"] ?: return false
        if (!hdr.startsWith("Basic ")) return false
        val decoded = try {
            String(Base64.decode(hdr.removePrefix("Basic "), Base64.DEFAULT))
        } catch (_: Exception) { return false }
        val idx = decoded.indexOf(':')
        if (idx < 0) return false
        return decoded.substring(0, idx) == cfg.user &&
               decoded.substring(idx + 1) == cfg.pass
    }

    private fun unauthorized(): Response {
        val r = newFixedLengthResponse(Response.Status.UNAUTHORIZED, "text/plain", "auth required")
        r.addHeader("WWW-Authenticate", "Basic realm=\"streamer\"")
        return r
    }

    private fun mjpegStream(): Response {
        val boundary = "streamerboundary"
        val pipeIn = PipedInputStream(1 shl 16)
        val pipeOut = PipedOutputStream(pipeIn)

        val job: Job = scope.launch {
            try {
                FrameBus.frames.collect { f ->
                    val cfg = getCfg()
                    if (cfg.motionOnlyStream) {
                        val last = getMotion()?.lastFireAt ?: 0L
                        if (System.currentTimeMillis() - last > cfg.motionWindowSec * 1000L) return@collect
                    }
                    val header = "--$boundary\r\nContent-Type: image/jpeg\r\nContent-Length: ${f.jpeg.size}\r\n\r\n"
                    pipeOut.write(header.toByteArray())
                    pipeOut.write(f.jpeg)
                    pipeOut.write("\r\n".toByteArray())
                    pipeOut.flush()
                }
            } catch (_: Exception) {
            } finally {
                try { pipeOut.close() } catch (_: Exception) {}
            }
        }

        val r = newChunkedResponse(
            Response.Status.OK,
            "multipart/x-mixed-replace; boundary=$boundary",
            pipeIn
        )
        r.addHeader("Cache-Control", "no-cache, private")
        r.addHeader("Pragma", "no-cache")
        r.addHeader("Connection", "close")
        return r
    }

    private fun latestFrame(): Response {
        val f = FrameBus.latest.value
            ?: return newFixedLengthResponse(Response.Status.NO_CONTENT, "text/plain", "")
        val r = newChunkedResponse(Response.Status.OK, "image/jpeg", ByteArrayInputStream(f.jpeg))
        r.addHeader("Content-Length", f.jpeg.size.toString())
        return r
    }

    private fun statusJson(): Response {
        val cfg = getCfg()
        val m = getMotion()
        val activeMs = m?.let { System.currentTimeMillis() - it.lastFireAt } ?: Long.MAX_VALUE
        val j = JSONObject().apply {
            put("width", cfg.width)
            put("height", cfg.height)
            put("fps", cfg.fps)
            put("recording", isRecording())
            put("torch", camera.torchOn)
            put("zoom", camera.currentZoom.toDouble())
            put("minZoom", camera.minZoom.toDouble())
            put("maxZoom", camera.maxZoom.toDouble())
            put("front", camera.frontFacing)
            put("wide", camera.wideActive)
            put("audio", cfg.enableAudio)
            put("motionRecord", cfg.motionRecord)
            put("motionOnlyStream", cfg.motionOnlyStream)
            put("motionSensitivity", cfg.motionSensitivity)
            put("motionCooldownSec", cfg.motionCooldownSec)
            put("motionWindowSec", cfg.motionWindowSec)
            put("motionMagnitude", m?.lastMagnitude ?: 0.0)
            put("motionActive", activeMs in 0..(cfg.motionWindowSec * 1000L))
            put("motionLastAgoMs", if (activeMs == Long.MAX_VALUE) -1 else activeMs)
        }
        return json(j)
    }

    private fun motionStateJson(): Response = statusJson()

    private fun camerasJson(): Response {
        val arr = JSONArray()
        for (c in camera.allCameras) {
            arr.put(JSONObject().apply {
                put("id", c.id)
                put("facing", c.facing)
                put("focalMm", c.focalMm.toDouble())
                put("equiv35", c.equiv35.toDouble())
                put("role", c.role)
            })
        }
        return json(JSONObject().put("cameras", arr))
    }

    private fun pickCamera(s: IHTTPSession): Response {
        val id = param(s, "id") ?: return json(JSONObject().put("ok", false).put("err", "missing id"))
        camera.selectCamera(id)
        return json(JSONObject().put("ok", true).put("id", id))
    }

    private fun torch(s: IHTTPSession): Response {
        val on = param(s, "on")?.equals("1") ?: !camera.torchOn
        camera.setTorch(on)
        return json(JSONObject().put("torch", on))
    }

    private fun switchCam(): Response {
        camera.switchCamera()
        return json(JSONObject().put("ok", true))
    }

    private fun wide(s: IHTTPSession): Response {
        val on = param(s, "on")?.equals("1") ?: !camera.wideActive
        camera.setWide(on)
        return json(JSONObject().put("wide", on))
    }

    private fun zoom(s: IHTTPSession): Response {
        val r = param(s, "ratio")?.toFloatOrNull() ?: camera.currentZoom
        camera.setZoom(r)
        return json(JSONObject().put("zoom", camera.currentZoom.toDouble()))
    }

    private fun focus(): Response {
        camera.triggerAutofocus()
        return json(JSONObject().put("ok", true))
    }

    private fun record(): Response {
        toggleRecording()
        return json(JSONObject().put("recording", isRecording()))
    }

    private fun updateMotion(s: IHTTPSession): Response {
        val cur = getCfg()
        val next = cur.copy(
            motionSensitivity = param(s, "sensitivity")?.toIntOrNull()?.coerceIn(1, 60) ?: cur.motionSensitivity,
            motionCooldownSec = param(s, "cooldown")?.toIntOrNull()?.coerceIn(1, 300) ?: cur.motionCooldownSec,
            motionWindowSec = param(s, "window")?.toIntOrNull()?.coerceIn(1, 300) ?: cur.motionWindowSec,
            motionOnlyStream = param(s, "onlyStream")?.let { it == "1" } ?: cur.motionOnlyStream,
            motionRecord = param(s, "record")?.let { it == "1" } ?: cur.motionRecord,
        )
        updateCfg(next)
        return json(JSONObject().apply {
            put("sensitivity", next.motionSensitivity)
            put("cooldown", next.motionCooldownSec)
            put("window", next.motionWindowSec)
            put("onlyStream", next.motionOnlyStream)
            put("record", next.motionRecord)
        })
    }

    private fun motionEventsJson(): Response {
        val arr = JSONArray()
        for (e in MotionLog.snapshot()) {
            arr.put(JSONObject().put("ts", e.ts).put("magnitude", e.magnitude))
        }
        return json(JSONObject().put("events", arr))
    }

    private fun json(obj: JSONObject): Response =
        newFixedLengthResponse(Response.Status.OK, "application/json", obj.toString())

    private fun param(s: IHTTPSession, name: String): String? {
        s.parseBody(mutableMapOf())
        return s.parms[name] ?: s.parameters[name]?.firstOrNull()
    }
}

object MotionLog {
    private val events = ArrayDeque<com.streamer.app.MotionEvent>()
    private const val MAX = 200

    @Synchronized fun push(e: com.streamer.app.MotionEvent) {
        events.addLast(e)
        while (events.size > MAX) events.removeFirst()
    }

    @Synchronized fun snapshot(): List<com.streamer.app.MotionEvent> = events.toList()
}
