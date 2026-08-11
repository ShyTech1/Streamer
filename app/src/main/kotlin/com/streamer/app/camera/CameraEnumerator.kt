package com.streamer.app.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log

data class CameraInfoRow(
    val id: String,
    val facing: Int,           // CameraCharacteristics.LENS_FACING_*
    val focalMm: Float,        // native focal length (mm)
    val equiv35: Float,        // 35mm-equivalent focal (for display)
    val role: String,          // "ultrawide" | "main" | "tele" | "front"
)

object CameraEnumerator {

    fun list(ctx: Context): List<CameraInfoRow> {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val out = mutableListOf<CameraInfoRow>()

        for (id in cm.cameraIdList) {
            try {
                val ch = cm.getCameraCharacteristics(id)
                val facing = ch.get(CameraCharacteristics.LENS_FACING) ?: continue
                val focals = ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS) ?: continue
                if (focals.isEmpty()) continue

                val sensorSize = ch.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                val focalMm = focals[0]
                // rough 35mm-equiv assuming diagonal ratio to full-frame diagonal (43.3mm)
                val equiv = if (sensorSize != null && sensorSize.width > 0f) {
                    val sensorDiag = kotlin.math.sqrt(
                        (sensorSize.width * sensorSize.width + sensorSize.height * sensorSize.height).toDouble()
                    ).toFloat()
                    focalMm * (43.3f / sensorDiag)
                } else focalMm

                out.add(CameraInfoRow(id = id, facing = facing, focalMm = focalMm, equiv35 = equiv, role = ""))
            } catch (e: Exception) {
                Log.w(TAG, "skipping camera $id: $e")
            }
        }

        // assign roles per facing group by focal length
        val labeled = out.groupBy { it.facing }.flatMap { (facing, cams) ->
            val sorted = cams.sortedBy { it.focalMm }
            sorted.mapIndexed { idx, r ->
                val role = when {
                    facing == CameraCharacteristics.LENS_FACING_FRONT -> "front"
                    sorted.size == 1 -> "main"
                    idx == 0 -> "ultrawide"
                    idx == sorted.lastIndex -> if (r.focalMm > sorted[0].focalMm * 2) "tele" else "main"
                    else -> "main"
                }
                r.copy(role = role)
            }
        }

        labeled.forEach { Log.i(TAG, "camera id=${it.id} facing=${it.facing} focal=${it.focalMm}mm eq35=${it.equiv35}mm role=${it.role}") }
        return labeled
    }

    private const val TAG = "CameraEnumerator"
}
