package com.corewall.qaqc

import com.corewall.qaqc.stylus.PointerKind
import com.corewall.qaqc.stylus.PressureAverage
import com.corewall.qaqc.stylus.pointerKindOf
import com.corewall.qaqc.stylus.pressureWidthFactor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * الأجزاء الخالصة من نظام القلم.
 *
 * الفرز نفسه بقى جوّه كواشف إيماءات Compose، ودي محتاجة جهاز عشان
 * تتّست. اللي هنا هو التصنيف وحساب الضغط — دول اللي أي غلط فيهم بيغيّر
 * السلوك من غير ما يفشل البناء.
 */
class StylusTest {

    @Test
    fun `tool types map to the right kind`() {
        assertEquals(PointerKind.STYLUS, pointerKindOf(android.view.MotionEvent.TOOL_TYPE_STYLUS))
        assertEquals(PointerKind.ERASER, pointerKindOf(android.view.MotionEvent.TOOL_TYPE_ERASER))
        assertEquals(PointerKind.FINGER, pointerKindOf(android.view.MotionEvent.TOOL_TYPE_FINGER))
        assertEquals(PointerKind.OTHER, pointerKindOf(android.view.MotionEvent.TOOL_TYPE_MOUSE))
        assertEquals(PointerKind.OTHER, pointerKindOf(android.view.MotionEvent.TOOL_TYPE_UNKNOWN))
    }

    @Test
    fun `only stylus and eraser count as a pen`() {
        // ده الشرط اللي بيفصل الحبر عن التنقّل. لو الصباع عدّى منه، الوضع
        // كله مالوش لازمة.
        assertTrue(PointerKind.STYLUS.isPen)
        assertTrue(PointerKind.ERASER.isPen)
        assertTrue(!PointerKind.FINGER.isPen)
        assertTrue(!PointerKind.OTHER.isPen)
    }

    @Test
    fun `pressure factor stays inside a sane range`() {
        // خط بيتغيّر سُمكه أضعاف بيبقى فرشاة مش قلم هندسي.
        for (i in 0..100) {
            val f = pressureWidthFactor(i / 100f)
            assertTrue("factor $f out of range at $i", f in 0.65f..1.45f)
        }
    }

    @Test
    fun `pressure factor rises with pressure`() {
        assertTrue(pressureWidthFactor(0.2f) < pressureWidthFactor(0.8f))
    }

    @Test
    fun `no pressure data leaves the width untouched`() {
        // الأجهزة اللي مابتقيسش ضغط بترجّع ١ ثابت، ولازم السُمك يفضل زي
        // ما المستخدم ظابطه — الميزة اختيارية مش شرط.
        val average = PressureAverage()
        assertEquals(1f, average.value, 1e-6f)
        assertEquals(1f, pressureWidthFactor(0f), 1e-6f)
    }

    @Test
    fun `pressure average ignores zero samples and resets clean`() {
        val average = PressureAverage()
        average.add(0f)
        assertEquals(1f, average.value, 1e-6f)
        average.add(0.4f)
        average.add(0.6f)
        assertEquals(0.5f, average.value, 1e-6f)
        average.reset()
        assertEquals(1f, average.value, 1e-6f)
    }
}
