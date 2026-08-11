package com.corewall.qaqc.pdfengine

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.Velocity
import com.corewall.qaqc.stylus.PointerKind
import com.corewall.qaqc.stylus.toKind
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
    /**
     * بيقول هل المؤشّر ده بتاع التنقّل ولا لأ.
     *
     * في وضع القلم بيرجّع `false` للقلم، فالإيماءة **مابتبدأش أصلاً**
     * لمّا القلم ينزل. ده كان لازم يبقى هنا مش برّه: الدالة دي بتبدأ بـ
     * `awaitFirstDown(requireUnconsumed = false)`، يعني بتبدأ حتى لو
     * الحدث اتاستهلك من طبقة تانية — فمحاولة إيقافها بالاستهلاك من برّه
     * كانت بتفشل، والقلم كان بيحرّك الصفحة بدل ما يكتب.
     */
    acceptPointer: (PointerKind) -> Boolean = { true },
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

        val down = awaitFirstDown(requireUnconsumed = false)
        if (!acceptPointer(down.type.toKind())) return@awaitEachGesture
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

/**
 * خطوط القلم.
 *
 * طبقة مستقلة بتشتغل جنب طبقة الإيماءات مش بدالها — وده كل الفكرة: القلم
 * بيكتب **وانت بتمرّر وتكبّر بصوابعك في نفس الوقت**، من غير ما تبدّل
 * أداة.
 *
 * الفرز بالنوع: أي مؤشّر مش قلم بيتساب من غير ما يتلمس، فبيوصل لطبقة
 * الإيماязات عادي. وده كمان **رفض الكف**: الكف صباع، وطبقة الحبر
 * مابتشوفش الصوابع خالص — مش بترسم منه وبعدين تمسح.
 */
@OptIn(ExperimentalComposeUiApi::class)   // `PointerInputChange.historical`
suspend fun PointerInputScope.detectStylusStrokes(
    /** بيرجّع `true` للمؤشّر اللي مسموح له يحبّر (القلم والأستيكة). */
    acceptPointer: (PointerKind) -> Boolean,
    onStart: (position: Offset, pressure: Float) -> Unit,
    onMove: (position: Offset, pressure: Float) -> Unit,
    onEnd: () -> Unit,
    onCancel: () -> Unit
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (!acceptPointer(down.type.toKind())) return@awaitEachGesture

        // الاستهلاك هنا مش للتحكيم (الفرز بالنوع بيكفي) — هو عشان أي أب
        // في الشجرة مايخطفش الخط في نصّه.
        down.consume()
        onStart(down.position, down.pressure)

        var completed = false
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id }
            if (change == null) break          // المؤشّر اختفى — إلغاء
            if (!change.pressed) { completed = true; change.consume(); break }

            // العيّنات التاريخية = اللي الجهاز جمّعها بين إطارين. بتدّي
            // خط أقرب لطرف القلم من غير أي تأخير مضاف — دي عيّنات حصلت
            // فعلاً مش تنعيم.
            change.historical.forEach { onMove(it.position, change.pressure) }
            onMove(change.position, change.pressure)
            change.consume()
        }

        if (completed) onEnd() else onCancel()
    }
}

/**
 * نقر وضغطة مطوّلة ونقرتين — **مع نوع المؤشّر**.
 *
 * `detectTapGestures` الجاهزة بتدّي الموضع بس. محتاجين النوع كمان لأن
 * القياس ومعايرة المقياس بيتحطّوا بالنقر: في وضع القلم، نقرة الصباع
 * لازم تفضل تقلب الواجهة بس، والقلم هو اللي بيحطّ النقط.
 */
suspend fun PointerInputScope.detectPdfTaps(
    onTap: (Offset, PointerKind) -> Unit,
    onLongPress: (Offset, PointerKind) -> Unit,
    onDoubleTap: (Offset, PointerKind) -> Unit
) {
    awaitEachGesture {
        val first = awaitFirstDown(requireUnconsumed = false)
        val kind = first.type.toKind()
        val longPressTimeout = viewConfiguration.longPressTimeoutMillis
        val doubleTapTimeout = viewConfiguration.doubleTapTimeoutMillis

        var longPressed = false
        val up = try {
            withTimeout(longPressTimeout) { waitForUpOrCancellation() }
        } catch (_: PointerEventTimeoutCancellationException) {
            longPressed = true
            null
        }

        if (longPressed) {
            onLongPress(first.position, kind)
            // بنستنى رفع الإصبع عشان الرفع مايتحسبش نقرة جديدة.
            waitForUpOrCancellation()
            return@awaitEachGesture
        }
        if (up == null) return@awaitEachGesture   // اتلغت

        // نقرة تانية جوّه المهلة؟
        val second = try {
            withTimeout(doubleTapTimeout) { awaitFirstDown(requireUnconsumed = false) }
        } catch (_: PointerEventTimeoutCancellationException) {
            null
        }

        if (second == null) {
            onTap(up.position, kind)
        } else {
            onDoubleTap(second.position, second.type.toKind())
            waitForUpOrCancellation()
        }
    }
}
