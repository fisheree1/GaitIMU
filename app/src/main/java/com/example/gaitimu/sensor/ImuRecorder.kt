package com.example.gaitimu.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.gaitimu.model.ImuSample

class ImuRecorder(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private var sampleListener: ((ImuSample) -> Unit)? = null

    private var lastAccel = FloatArray(3)
    private var lastGyro = FloatArray(3)

    val hasAccelerometer: Boolean = accelerometer != null
    val hasGyroscope: Boolean = gyroscope != null
    val isAvailable: Boolean = hasAccelerometer && hasGyroscope

    private var isRecording = false

    fun start(samplePeriodUs: Int = SensorManager.SENSOR_DELAY_GAME, onSample: (ImuSample) -> Unit): Boolean {
        if (!isAvailable || isRecording) {
            return false
        }
        sampleListener = onSample
        lastAccel = floatArrayOf(0f, 0f, 0f)
        lastGyro = floatArrayOf(0f, 0f, 0f)

        val accelRegistered = accelerometer?.let {
            sensorManager.registerListener(this, it, samplePeriodUs)
        } ?: false

        val gyroRegistered = gyroscope?.let {
            sensorManager.registerListener(this, it, samplePeriodUs)
        } ?: false

        isRecording = accelRegistered && gyroRegistered
        if (!isRecording) {
            stop()
        }
        return isRecording
    }

    fun stop() {
        if (!isRecording) {
            return
        }
        sensorManager.unregisterListener(this)
        sampleListener = null
        isRecording = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                lastAccel[0] = event.values[0]
                lastAccel[1] = event.values[1]
                lastAccel[2] = event.values[2]
            }

            Sensor.TYPE_GYROSCOPE -> {
                lastGyro[0] = event.values[0]
                lastGyro[1] = event.values[1]
                lastGyro[2] = event.values[2]
            }

            else -> return
        }

        sampleListener?.invoke(
            ImuSample(
                timestampNs = event.timestamp,
                ax = lastAccel[0],
                ay = lastAccel[1],
                az = lastAccel[2],
                gx = lastGyro[0],
                gy = lastGyro[1],
                gz = lastGyro[2]
            )
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }
}
