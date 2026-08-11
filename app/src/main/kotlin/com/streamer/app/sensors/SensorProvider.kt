package com.streamer.app.sensors

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import org.json.JSONObject

class SensorProvider(ctx: Context) : SensorEventListener {
    private val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val appCtx = ctx.applicationContext

    private var accel = floatArrayOf(0f, 0f, 0f)
    private var light = 0f

    init {
        sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        sm.getDefaultSensor(Sensor.TYPE_LIGHT)?.let {
            sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onSensorChanged(e: SensorEvent) {
        when (e.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> accel = e.values.copyOf()
            Sensor.TYPE_LIGHT -> light = e.values[0]
        }
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun json(): JSONObject {
        val bm = appCtx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val chargingStatus = appCtx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = chargingStatus == BatteryManager.BATTERY_STATUS_CHARGING ||
                       chargingStatus == BatteryManager.BATTERY_STATUS_FULL
        return JSONObject().apply {
            put("accel_x", accel[0].toDouble())
            put("accel_y", accel[1].toDouble())
            put("accel_z", accel[2].toDouble())
            put("light_lux", light.toDouble())
            put("battery_pct", level)
            put("charging", charging)
        }
    }
}
