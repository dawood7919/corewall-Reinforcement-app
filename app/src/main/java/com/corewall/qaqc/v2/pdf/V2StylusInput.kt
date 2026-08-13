package com.corewall.qaqc.v2.pdf

import android.view.MotionEvent

/**
 * تحكيم لمسات S Pen على مستوى MotionEvent. لا يلتقط اللمس بالأصابع، ولذلك
 * تبقى إيماءات الإصبع محجوزة حصراً لتحريك الصفحة وتكبيرها.
 */
internal class V2StylusInput(
    private val enabled: () -> Boolean,
    private val onDown: (x: Float, y: Float, pressure: Float, eraser: Boolean) -> Unit,
    private val onMove: (x: Float, y: Float, pressure: Float) -> Unit,
    private val onUp: (x: Float, y: Float, pressure: Float) -> Unit,
    private val onCancel: () -> Unit
) {
    private var activePointerId = MotionEvent.INVALID_POINTER_ID

    val isActive: Boolean get() = activePointerId != MotionEvent.INVALID_POINTER_ID

    fun handle(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                // أثناء رسم قلم نشط لا نسمح لإصبع ثانٍ أن يبدأ مسار تنقل أو تكبير.
                if (isActive) return true
                if (!enabled()) return false
                val index = event.actionIndex
                val tool = event.getToolType(index)
                if (!tool.isPen()) return false
                activePointerId = event.getPointerId(index)
                onDown(event.getX(index), event.getY(index), event.getPressure(index), tool == MotionEvent.TOOL_TYPE_ERASER)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(activePointerId)
                if (index < 0) return false
                onMove(event.getX(index), event.getY(index), event.getPressure(index))
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (!isActive) return false
                if (event.getPointerId(event.actionIndex) != activePointerId) return true
                val index = event.actionIndex
                onUp(event.getX(index), event.getY(index), event.getPressure(index))
                activePointerId = MotionEvent.INVALID_POINTER_ID
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                if (!isActive) return false
                activePointerId = MotionEvent.INVALID_POINTER_ID
                onCancel()
                return true
            }
        }
        return isActive
    }

    private fun Int.isPen(): Boolean = this == MotionEvent.TOOL_TYPE_STYLUS || this == MotionEvent.TOOL_TYPE_ERASER
}
