package com.corewall.qaqc.stylus

import android.view.MotionEvent
import androidx.compose.ui.input.pointer.PointerType

/**
 * تصنيف أداة اللمس.
 *
 * أندرويد بيقول لكل مؤشّر (pointer) إيه اللي عامله: صباع، قلم، أستيكة
 * القلم، ماوس. الكلام ده جاي من الـdigitizer نفسه مش تخمين، وهو أساس
 * الوضع كله.
 */
enum class PointerKind {
    FINGER,

    /** قلم على الشاشة — S Pen أو أي قلم متوافق. */
    STYLUS,

    /** الطرف العكسي للقلم (أستيكة) — بعض الأقلام بتبلّغ عنه لوحده. */
    ERASER,

    /** ماوس أو حاجة مش معروفة. بنعاملها معاملة الصباع. */
    OTHER;

    val isPen: Boolean get() = this == STYLUS || this == ERASER
}

/**
 * تصنيف مؤشّر Compose.
 *
 * **ده المصدر الوحيد للتصنيف دلوقتي.**
 *
 * المحاولة الأولى كانت بتقرا `MotionEvent` الخام من
 * `Modifier.pointerInteropFilter` وبتعتمد على "استهلاك" الحدث عشان توقف
 * طبقة الإيماءات. ده فشل على الجهاز: `detectPdfGestures` بيبدأ بـ
 * `awaitFirstDown(requireUnconsumed = false)`، يعني الإيماءة بتبدأ **حتى
 * لو الحدث اتاستهلك** — فالقلم كان بيحرّك الصفحة قبل ما طبقة الحبر
 * توقفه، والنتيجة إن الشاشة بتهتز بدل ما القلم يكتب.
 *
 * الدرس: التحكيم بين "ده حبر" و"ده تنقّل" لازم يحصل **جوّه** نظام
 * المؤشّرات بتاع Compose، مش على حدود الـinterop. كل طبقة بتبصّ على نوع
 * المؤشّر وبتتجاهل اللي مش بتاعها — مفيش سباق استهلاك أصلاً.
 */
fun PointerType.toKind(): PointerKind = when (this) {
    PointerType.Stylus -> PointerKind.STYLUS
    PointerType.Eraser -> PointerKind.ERASER
    PointerType.Touch -> PointerKind.FINGER
    else -> PointerKind.OTHER
}

/**
 * بيحوّل `TOOL_TYPE_*` الخام لتصنيف.
 *
 * مابقاش مستخدم في مسار اللمس (بقى من `PointerType`)، بس سايبينه لأنه
 * الترجمة المرجعية بين أرقام أندرويد والتصنيف، وعليه اختبارات.
 */
fun pointerKindOf(toolType: Int): PointerKind = when (toolType) {
    MotionEvent.TOOL_TYPE_STYLUS -> PointerKind.STYLUS
    MotionEvent.TOOL_TYPE_ERASER -> PointerKind.ERASER
    MotionEvent.TOOL_TYPE_FINGER -> PointerKind.FINGER
    else -> PointerKind.OTHER
}

/**
 * سُمك الخط من ضغط القلم.
 *
 * الضغط بيتحوّل لمعامل بين [MIN_FACTOR] و[MAX_FACTOR] حوالين السُمك
 * الأساسي للأداة. المدى ضيّق عن قصد: خط بيتغيّر سُمكه تلات أضعاف بيبقى
 * زي الفرشاة مش زي قلم هندسي، والرسمة دي مستند مش لوحة.
 *
 * الأجهزة اللي مابتقيسش ضغط بترجّع ١ ثابت، فالمعامل بيطلع ١ والسُمك
 * بيفضل زي ما هو بالظبط — يعني الميزة اختيارية من غير أي فرع في الكود.
 */
fun pressureWidthFactor(pressure: Float): Float {
    if (pressure <= 0f) return 1f
    val p = pressure.coerceIn(0f, 1f)
    return (MIN_FACTOR + (MAX_FACTOR - MIN_FACTOR) * p).coerceIn(MIN_FACTOR, MAX_FACTOR)
}

private const val MIN_FACTOR = 0.65f
private const val MAX_FACTOR = 1.45f

/**
 * متوسّط ضغط الخط.
 *
 * نموذج التعليق بيخزّن **سُمك واحد للخط كله** مش لكل نقطة، فالخيار الصادق
 * هو إن الضغط يحدّد سُمك الخط ككل: تضغط أكتر → خط أتخن. تغيير السُمك جوّه
 * الخط الواحد كان هيحتاج تغيير في المخطط وترحيل، وده مش مبرّر لمكسب
 * بصري بسيط.
 *
 * كائن عادي مش حالة Compose عن قصد: بيتحدّث مع كل عيّنة (عشرات في
 * الثانية)، ولو كان حالة كان هيعيد التركيب مع كل نقطة.
 */
class PressureAverage {
    private var sum = 0f
    private var count = 0

    fun add(pressure: Float) {
        if (pressure > 0f) { sum += pressure; count++ }
    }

    /** ١ لو مافيش عيّنات — يعني الجهاز مابيقيسش ضغط، والسُمك بيفضل زي ما هو. */
    val value: Float get() = if (count == 0) 1f else sum / count

    fun reset() { sum = 0f; count = 0 }
}
