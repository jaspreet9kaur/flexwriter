package io.github.jaspreet9kaur.flexwriter.fold

import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Where the hinge is, in window pixels, and whether the phone is half-folded like a laptop (Flex Mode). */
data class FoldInfo(
    val hasHinge: Boolean = false,
    val tabletop: Boolean = false,
    val hingeTopPx: Int = 0,
    val hingeBottomPx: Int = 0,
)

@Composable
fun rememberFoldInfo(activity: Activity): FoldInfo {
    val info by produceState(FoldInfo(), activity) {
        WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity).collect { layout ->
            val f = layout.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
            value = if (f == null) FoldInfo() else FoldInfo(
                hasHinge = true,
                tabletop = f.state == FoldingFeature.State.HALF_OPENED &&
                    f.orientation == FoldingFeature.Orientation.HORIZONTAL,
                hingeTopPx = f.bounds.top,
                hingeBottomPx = f.bounds.bottom,
            )
        }
    }
    return info
}

/** Live hinge angle in degrees (0 = closed, 180 = flat). Emits nothing on devices without the sensor. */
fun hingeAngleFlow(context: Context, rate: Int = SensorManager.SENSOR_DELAY_UI): Flow<Float> = callbackFlow {
    val sm = context.getSystemService(SensorManager::class.java)
    val sensor = sm?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
    if (sm == null || sensor == null) {
        close()
        return@callbackFlow
    }
    val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            trySend(event.values[0])
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }
    sm.registerListener(listener, sensor, rate)
    awaitClose { sm.unregisterListener(listener) }
}
