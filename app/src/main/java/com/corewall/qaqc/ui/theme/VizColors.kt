package com.corewall.qaqc.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * ألوان الرسوم البيانية.
 *
 * الترتيب مش تجميلي — هو آلية الأمان لعمى الألوان: كل زوج متجاور اتفحص
 * بمحاكاة protan/deutan/tritan على أسطح التطبيق الثلاثة (أبيض، #16181F،
 * #0E2A48) وعدّى الحدود (CVD ΔE ≥ 8، الرؤية العادية ≥ 15).
 * **ممنوع** إعادة ترتيب الخانات أو توليد لون تاسع — الحل للسلاسل الزيادة
 * هو التجميع في "أخرى" أو جدول.
 *
 * ثلاث خانات في الوضع الفاتح تحت 3:1 مقابل الأبيض (aqua/yellow/magenta)،
 * وعشان كده كل رسمة هنا بتكتب قيمها صراحة أو بتتحوّل لجدول — اللون
 * لوحده عمره ما بيحمل المعنى.
 */
data class VizPalette(
    /** ألوان السلاسل بالترتيب المعتمد — استخدمها بالفهرس، متقلبهاش. */
    val series: List<Color>,
    /** مسار العدّاد: درجة أفتح من نفس تدرّج الأزرق. */
    val track: Color,
    /** خطوط الشبكة والمحاور — شعرة واحدة، متراجعة عن البيانات. */
    val grid: Color,
    val axis: Color,
    // ألوان الحالة ثابتة ومش بتتبدّل مع الثيم، وعمرها ما بتُستخدم كسلسلة.
    val good: Color = Color(0xFF0CA30C),
    val warning: Color = Color(0xFFFAB219),
    val serious: Color = Color(0xFFEC835A),
    val critical: Color = Color(0xFFD03B3B)
) {
    /** لون السلسلة رقم [i] — بيلتفّ بأمان بدل ما يولّد لون جديد. */
    fun series(i: Int): Color = series[i.coerceAtLeast(0) % series.size]
}

/** الوضع الفاتح — سطح أبيض. */
internal val VizLight = VizPalette(
    series = listOf(
        Color(0xFF3A6EF0), // أزرق الهوية
        Color(0xFFEB6834), // برتقالي
        Color(0xFF1BAF7A), // أخضر مزرق
        Color(0xFFEDA100), // أصفر
        Color(0xFFE87BA4), // ماجنتا
        Color(0xFF008300), // أخضر
        Color(0xFF4A3AA7), // بنفسجي
        Color(0xFFE34948)  // أحمر
    ),
    track = Color(0xFFDCE7FD),
    grid = Color(0xFFEDEEF1),
    axis = Color(0xFFD8DBE1)
)

/** الوضع الغامق — نفس الدرجات مضبوطة على سطح #16181F. */
internal val VizDark = VizPalette(
    series = listOf(
        Color(0xFF4E7DF5),
        Color(0xFFD95926),
        Color(0xFF199E70),
        Color(0xFFC98500),
        Color(0xFFD55181),
        Color(0xFF008300),
        Color(0xFF9085E9),
        Color(0xFFE66767)
    ),
    track = Color(0xFF1E2B4D),
    grid = Color(0xFF23262E),
    axis = Color(0xFF2E323C)
)

/** Blueprint — نفس سلاسل الغامق على سطح #0E2A48. */
internal val VizBlueprint = VizDark.copy(
    track = Color(0xFF14486E),
    grid = Color(0xFF15355A),
    axis = Color(0xFF2E5680)
)

val LocalVizColors = staticCompositionLocalOf { VizLight }
