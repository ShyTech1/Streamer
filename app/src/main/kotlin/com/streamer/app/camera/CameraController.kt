package com.streamer.app.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.camera2.CameraCharacteristics
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraFilter
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.streamer.app.Config
import com.streamer.app.Frame
import com.streamer.app.FrameBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCamera2Interop::class)
class CameraController(
    private val ctx: Context,
    private val scope: CoroutineScope,
) {
    private val analyzerExec = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var lastOwner: LifecycleOwner? = null
    private var lastCfg: Config? = null

    val allCameras: List<CameraInfoRow> by lazy { CameraEnumerator.list(ctx) }
    private val backMain    get() = allCameras.firstOrNull { it.facing == CameraCharacteristics.LENS_FACING_BACK && it.role == "main" }
    private val backWide    get() = allCameras.firstOrNull { it.facing == CameraCharacteristics.LENS_FACING_BACK && it.role == "ultrawide" }
    private val frontCam    get() = allCameras.firstOrNull { it.facing == CameraCharacteristics.LENS_FACING_FRONT }

    @Volatile private var currentId: String? = null
    private val skipCounter = AtomicInteger(0)

    @Volatile var currentZoom: Float = 1f
    @Volatile var torchOn: Boolean = false
    val frontFacing: Boolean get() = currentId == frontCam?.id
    val wideActive: Boolean  get() = currentId != null && currentId == backWide?.id

    val minZoom: Float get() = camera?.cameraInfo?.zoomState?.value?.minZoomRatio ?: 1f
    val maxZoom: Float get() = camera?.cameraInfo?.zoomState?.value?.maxZoomRatio ?: 1f

    @SuppressLint("RestrictedApi")
    fun start(owner: LifecycleOwner, cfg: Config, onReady: () -> Unit = {}) {
        lastOwner = owner
        lastCfg = cfg
        val future = ProcessCameraProvider.getInstance(ctx)
        future.addListener({
            provider = future.get()
            currentId = backMain?.id ?: allCameras.firstOrNull { it.facing == CameraCharacteristics.LENS_FACING_BACK }?.id
            bind(owner, cfg)
            onReady()
        }, ContextCompat.getMainExecutor(ctx))
    }

    fun stop() {
        provider?.unbindAll()
        activeRecording?.stop()
        activeRecording = null
    }

    private fun selectorFor(cameraId: String?): CameraSelector {
        val id = cameraId
        val builder = CameraSelector.Builder()
        if (id != null) {
            builder.addCameraFilter(object : CameraFilter {
                override fun filter(cameraInfos: MutableList<CameraInfo>): MutableList<CameraInfo> {
                    val match = cameraInfos.firstOrNull {
                        try { Camera2CameraInfo.from(it).cameraId == id } catch (_: Exception) { false }
                    }
                    return if (match != null) mutableListOf(match) else cameraInfos
                }
            })
        } else {
            builder.requireLensFacing(CameraSelector.LENS_FACING_BACK)
        }
        return builder.build()
    }

    private fun bind(owner: LifecycleOwner, cfg: Config) {
        val p = provider ?: return
        try {
            p.unbindAll()

            val resolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(
                        android.util.Size(cfg.width, cfg.height),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                    )
                )
                .build()

            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(resolutionSelector)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()

            val skipEvery = maxOf(1, 30 / maxOf(1, cfg.fps))
            analysis.setAnalyzer(analyzerExec) { image ->
                try {
                    if (skipCounter.getAndIncrement() % skipEvery != 0) return@setAnalyzer
                    encodeAndPublish(image, cfg.jpegQuality)
                } finally {
                    image.close()
                }
            }

            val recorder = Recorder.Builder().build()
            val vc = VideoCapture.withOutput(recorder)
            videoCapture = vc

            val selector = selectorFor(currentId)

            camera = try {
                p.bindToLifecycle(owner, selector, analysis, vc)
            } catch (e: Exception) {
                Log.w(TAG, "bind with VideoCapture failed on $currentId, retrying stream-only: $e")
                videoCapture = null
                p.bindToLifecycle(owner, selector, analysis)
            }

            camera?.cameraControl?.enableTorch(torchOn)
            currentZoom = 1f
            Log.i(TAG, "bound camera id=$currentId")
        } catch (e: Exception) {
            Log.e(TAG, "bind failed for id=$currentId", e)
        }
    }

    fun switchCamera() {
        val owner = lastOwner ?: return
        val cfg = lastCfg ?: return
        mainHandler.post {
            val order = listOfNotNull(backMain?.id, frontCam?.id, backWide?.id).distinct()
            if (order.isEmpty()) return@post
            val idx = order.indexOf(currentId).let { if (it < 0) 0 else (it + 1) % order.size }
            currentId = order[idx]
            bind(owner, cfg)
        }
    }

    fun selectCamera(id: String) {
        val owner = lastOwner ?: return
        val cfg = lastCfg ?: return
        if (allCameras.none { it.id == id }) {
            Log.w(TAG, "selectCamera: unknown id=$id"); return
        }
        mainHandler.post { currentId = id; bind(owner, cfg) }
    }

    fun setWide(on: Boolean) {
        val owner = lastOwner ?: return
        val cfg = lastCfg ?: return
        mainHandler.post {
            val target = if (on) (backWide?.id ?: backMain?.id) else backMain?.id
            if (target != null && target != currentId) {
                currentId = target
                bind(owner, cfg)
            } else {
                Log.i(TAG, "setWide($on): no separate ultrawide available; trying zoom-out fallback")
                val z = if (on) (minZoom.takeIf { it < 1f } ?: 1f) else 1f
                setZoom(z)
            }
        }
    }

    fun setTorch(on: Boolean) {
        torchOn = on
        camera?.cameraControl?.enableTorch(on)
    }

    fun setZoom(ratio: Float) {
        val info = camera?.cameraInfo ?: return
        val max = info.zoomState.value?.maxZoomRatio ?: 1f
        val min = info.zoomState.value?.minZoomRatio ?: 1f
        val clamped = ratio.coerceIn(min, max)
        currentZoom = clamped
        camera?.cameraControl?.setZoomRatio(clamped)
    }

    fun triggerAutofocus() {
        val factory = androidx.camera.core.SurfaceOrientedMeteringPointFactory(1f, 1f)
        val point = factory.createPoint(0.5f, 0.5f)
        val action = androidx.camera.core.FocusMeteringAction.Builder(point).build()
        camera?.cameraControl?.startFocusAndMetering(action)
    }

    fun videoCapture(): VideoCapture<Recorder>? = videoCapture
    fun setActiveRecording(r: Recording?) { activeRecording = r }
    fun activeRecording(): Recording? = activeRecording

    private fun encodeAndPublish(image: ImageProxy, quality: Int) {
        val jpeg = yuvToJpeg(image, quality) ?: return
        scope.launch(Dispatchers.Default) {
            FrameBus.publish(Frame(jpeg, image.width, image.height, System.currentTimeMillis()))
        }
    }

    private fun yuvToJpeg(image: ImageProxy, quality: Int): ByteArray? {
        val yBuf = image.planes[0].buffer
        val uBuf = image.planes[1].buffer
        val vBuf = image.planes[2].buffer

        val ySize = yBuf.remaining()
        val uSize = uBuf.remaining()
        val vSize = vBuf.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuf.get(nv21, 0, ySize)
        vBuf.get(nv21, ySize, vSize)
        uBuf.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream(64 * 1024)
        val ok = yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), quality, out)
        return if (ok) out.toByteArray() else null
    }

    companion object { private const val TAG = "CameraController" }
}
