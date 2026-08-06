package com.corewall.qaqc.pdfengine

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs

/**
 * إيماءة التمرير والتكبير، ومعاها **السرعة عند الرفع**.
 *
 * ليه مش `detectTransformGestures` الجاهزة: هي بتدّي التمرير والتكبير بس
 * ومابتقولش السرعة وقت رفع الصبع. من غير السرعة مفيش اندفاع (fling)، ومن
 * غير الاندفاع المستند بيقف ميت في اللحظة اللي ترفع فيها إيدك — وده الفرق
 * اللي بيخلّي العارض يحسّ إنه "رخيص" مهما كان الرندر سريع.
 *
 * بنجمع السرعة من **إصبع واحد بس**. وقت التكبير بإصبعين، السرعة بتبقى
 * بلا معنى (المركز بيتحرك من التقارب مش من التمرير)، والاندفاع بعد تكبير
 * بيحسّ إنه عطل مش ميزة.
 */
suspend fun PointerInputScope.detectPdfGestures(
    onStart: () -> Unit,
    onGesture: (centroid: Offset, pan: Offset, zoom: Float) -> Unit,
    onEnd: (velocity: Velocity) -> Unit
) {
    awaitEachGesture {
        var accumulatedZoom = 1f
        var accumulatedPan = Offset.Zero
        var pastSlop = false
        var multiTouch = false
        val slop = viewConfiguration.touchSlop
        val tracker = VelocityTracker()

        awaitFirstDown(requireUnconsumed = false)
        onStart()

        var canceled = false
        do {
            val event = awaitPointerEvent()
            canceled = event.changes.any { it.isConsumed }
            if (canceled) break

            if (event.changes.size > 1) multiTouch = true

            val zoomChange = event.calculateZoom()
            val panChange = event.calculatePan()

            if (!pastSlop) {
                accumulatedZoom *= zoomChange
                accumulatedPan += panChange
                val centroidSize = event.calculateCentroidSize(useCurrent = false)
                val zoomMotion = abs(1f - accumulatedZoom) * centroidSize
                if (zoomMotion > slop || accumulatedPan.getDistance() > slop) pastSlop = true
            }

            if (pastSlop) {
                val centroid = event.calculateCentroid(useCurrent = false)
                if (zoomChange != 1f || panChange != Offset.Zero) {
                    onGesture(centroid, panChange, zoomChange)
                }
                // الاستهلاك بيمنع أي أب في الشجرة (زي شرائط التمرير) من إنه
                // يخطف الإيماءة في نص التكبير.
                event.changes.forEach { if (it.positionChanged()) it.consume() }

                if (!multiTouch && event.changes.size == 1) {
                    val c = event.changes.first()
                    tracker.addPosition(c.uptimeMillis, c.position)
                }
            }
        } while (event.changes.any { it.pressed })

        val velocity = if (pastSlop && !multiTouch) {
            runCatching { tracker.calculateVelocity() }.getOrDefault(Velocity.Zero)
        } else Velocity.Zero
        onEnd(velocity)
    }
}
