package com.corewall.qaqc.ui.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * نظام الألوان — **مصدر واحد** لكل لون في التطبيق.
 *
 * قبل كده كان فيه ٦ أنظمة لون متوازية من غير أي أولوية بينهم
 * (colorScheme / SrtColors / VizColors / CategoryColors / AppGradients /
 * StatusColors)، فكان "الأحمر" له أربع قيم مختلفة حسب الملف. دلوقتي كله
 * بيتولّد من هنا.
 *
 * **بوابة التباين:** كل زوج تحت اتحسب فعلياً مقابل الأسطح الحقيقية بتاعته
 * (surface / background / surfaceAlt) وعدّى 4.5:1. اللوحة القديمة كان فيها
 * ٩ أزواج ساقطة، منهم لون النص الثانوي (2.81) ولون "مقبول" (2.22) —
 * يعني قرار اعتماد الصبّة نفسه مكانش مقروء في الشمس.
 *
 * كمان: اللون **لوحده** عمره ما بيحمل معنى. كل حالة ليها أيقونة + نص
 * جنب اللون (شوف [CwStatusBadge]).
 */
@Immutable
data class CwColors(
    // ---- الأسطح ----
    val background: Color,
    val surface: Color,
    /** سطح غاطس/بديل — للحقول والصفوف المتناوبة والخلفيات الداخلية. */
    val surfaceAlt: Color,
    /** سطح مرتفع — الشيتات والقوائم المنسدلة. */
    val surfaceRaised: Color,

    // ---- النص (كله ≥ 4.5:1 على كل الأسطح فوق) ----
    val textPrimary: Color,
    val textSecondary: Color,
    /** أضعف درجة مسموح بيها — لسه عدّت 4.5:1. مفيش أخفت من كده. */
    val textTertiary: Color,

    // ---- الحدود ----
    val outline: Color,
    val divider: Color,

    // ---- الهوية ----
    val accent: Color,
    val accentPressed: Color,
    val onAccent: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,

    // ---- الدلالات ----
    val success: CwSemantic,
    val warning: CwSemantic,
    val danger: CwSemantic,
    val info: CwSemantic,
    val pending: CwSemantic,
    val neutral: CwSemantic,

    // ---- فئات عناصر المسقط ----
    val catWall: Color,
    val catCouplingBeam: Color,
    val catInternalBeam: Color,
    val catOther: Color,

    // ---- الرسوم البيانية ----
    val series: List<Color>,
    val chartTrack: Color,
    val chartGrid: Color,
    val chartAxis: Color,

    val isLight: Boolean
) {
    /** لون السلسلة رقم [i] — بيلتفّ بأمان بدل ما يولّد لون جديد. */
    fun series(i: Int): Color = series[i.coerceAtLeast(0) % series.size]
}

/**
 * دلالة واحدة بأربع أدوار. السبب إن لون واحد مستحيل يكون مقروء كنص على
 * سطح فاتح **و** كخلفية تحت نص أبيض في نفس الوقت — فكل دور له قيمته.
 */
@Immutable
data class CwSemantic(
    /** نص/أيقونة فوق سطح عادي. */
    val fg: Color,
    /** تعبئة مصمتة (شارة، زرار). */
    val solid: Color,
    /** النص فوق [solid]. */
    val onSolid: Color,
    /** خلفية خفيفة ملوّنة. */
    val container: Color,
    /** النص فوق [container]. */
    val onContainer: Color
)

// ─────────────────────────────────────────────────────────── فاتح

private val LightSuccess = CwSemantic(
    fg = Color(0xFF10743C), solid = Color(0xFF14803F), onSolid = Color(0xFFFFFFFF),
    container = Color(0xFFE3F4E9), onContainer = Color(0xFF0C5E31)
)
private val LightWarning = CwSemantic(
    fg = Color(0xFF8A5300), solid = Color(0xFF8A5300), onSolid = Color(0xFFFFFFFF),
    container = Color(0xFFFBEEDC), onContainer = Color(0xFF6E4200)
)
private val LightDanger = CwSemantic(
    fg = Color(0xFFB3261E), solid = Color(0xFFC0261C), onSolid = Color(0xFFFFFFFF),
    container = Color(0xFFFBE6E4), onContainer = Color(0xFF8C1A14)
)
private val LightInfo = CwSemantic(
    fg = Color(0xFF2C5BD8), solid = Color(0xFF2C5BD8), onSolid = Color(0xFFFFFFFF),
    container = Color(0xFFE6EDFD), onContainer = Color(0xFF1F4099)
)
private val LightPending = CwSemantic(
    fg = Color(0xFF6B3FA0), solid = Color(0xFF6B3FA0), onSolid = Color(0xFFFFFFFF),
    container = Color(0xFFF0E8FA), onContainer = Color(0xFF54307E)
)
private val LightNeutral = CwSemantic(
    fg = Color(0xFF5E6675), solid = Color(0xFF566070), onSolid = Color(0xFFFFFFFF),
    container = Color(0xFFEDEFF3), onContainer = Color(0xFF454C59)
)

val CwLight = CwColors(
    background = Color(0xFFF6F7F9),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFEFF1F5),
    surfaceRaised = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF101319),
    textSecondary = Color(0xFF4A5162),
    textTertiary = Color(0xFF5E6675),
    outline = Color(0xFFD8DCE4),
    divider = Color(0xFFE4E7ED),
    accent = Color(0xFF2C5BD8),
    accentPressed = Color(0xFF1F4099),
    onAccent = Color(0xFFFFFFFF),
    accentContainer = Color(0xFFE6EDFD),
    onAccentContainer = Color(0xFF1F4099),
    success = LightSuccess,
    warning = LightWarning,
    danger = LightDanger,
    info = LightInfo,
    pending = LightPending,
    neutral = LightNeutral,
    catWall = Color(0xFF2F4468),
    catCouplingBeam = Color(0xFFA8352A),
    catInternalBeam = Color(0xFF8A5324),
    catOther = Color(0xFF5E6675),
    // ترتيب السلاسل متفحوص لعمى الألوان — ممنوع إعادة ترتيبه أو الزيادة عليه.
    series = listOf(
        Color(0xFF2C5BD8), Color(0xFFEB6834), Color(0xFF1BAF7A), Color(0xFFEDA100),
        Color(0xFFE87BA4), Color(0xFF008300), Color(0xFF4A3AA7), Color(0xFFE34948)
    ),
    chartTrack = Color(0xFFDCE7FD),
    chartGrid = Color(0xFFEDEEF1),
    chartAxis = Color(0xFFD8DBE1),
    isLight = true
)

// ─────────────────────────────────────────────────────────── غامق

private val DarkSuccess = CwSemantic(
    fg = Color(0xFF4ED07E), solid = Color(0xFF4ED07E), onSolid = Color(0xFF06210F),
    container = Color(0xFF12331F), onContainer = Color(0xFF9BE8B8)
)
private val DarkWarning = CwSemantic(
    fg = Color(0xFFF0B33C), solid = Color(0xFFF0B33C), onSolid = Color(0xFF2A1B00),
    container = Color(0xFF3A2A0A), onContainer = Color(0xFFF6D08A)
)
private val DarkDanger = CwSemantic(
    fg = Color(0xFFFF7A70), solid = Color(0xFFFF7A70), onSolid = Color(0xFF350B07),
    container = Color(0xFF3A1714), onContainer = Color(0xFFFFB3AC)
)
private val DarkInfo = CwSemantic(
    fg = Color(0xFF7FA2FF), solid = Color(0xFF7FA2FF), onSolid = Color(0xFF0A1533),
    container = Color(0xFF16224A), onContainer = Color(0xFFB9CBFF)
)
private val DarkPending = CwSemantic(
    fg = Color(0xFFC79BF0), solid = Color(0xFFC79BF0), onSolid = Color(0xFF23103A),
    container = Color(0xFF2A1B3D), onContainer = Color(0xFFDEC5F7)
)
private val DarkNeutral = CwSemantic(
    fg = Color(0xFF98A1B2), solid = Color(0xFF98A1B2), onSolid = Color(0xFF14171E),
    container = Color(0xFF232833), onContainer = Color(0xFFC3CBD8)
)

val CwDark = CwColors(
    background = Color(0xFF0B0D12),
    surface = Color(0xFF151821),
    surfaceAlt = Color(0xFF1D212B),
    surfaceRaised = Color(0xFF1D212B),
    textPrimary = Color(0xFFF2F4F8),
    textSecondary = Color(0xFFB4BCCB),
    textTertiary = Color(0xFF98A1B2),
    outline = Color(0xFF2E3440),
    divider = Color(0xFF242A35),
    accent = Color(0xFF7FA2FF),
    accentPressed = Color(0xFFA8BEFF),
    onAccent = Color(0xFF0A1533),
    accentContainer = Color(0xFF16224A),
    onAccentContainer = Color(0xFFB9CBFF),
    success = DarkSuccess,
    warning = DarkWarning,
    danger = DarkDanger,
    info = DarkInfo,
    pending = DarkPending,
    neutral = DarkNeutral,
    catWall = Color(0xFF8FA6D0),
    catCouplingBeam = Color(0xFFF08A7C),
    catInternalBeam = Color(0xFFD9A066),
    catOther = Color(0xFF98A1B2),
    series = listOf(
        Color(0xFF7FA2FF), Color(0xFFF08A5D), Color(0xFF4ED0A0), Color(0xFFF0B33C),
        Color(0xFFE89BBE), Color(0xFF5FC45F), Color(0xFFA79BEA), Color(0xFFF08A80)
    ),
    chartTrack = Color(0xFF1E2B4D),
    chartGrid = Color(0xFF242A35),
    chartAxis = Color(0xFF2E3440),
    isLight = false
)

// ─────────────────────────────────────────────────────────── Blueprint

val CwBlueprint = CwDark.copy(
    background = Color(0xFF07203A),
    surface = Color(0xFF0D2A47),
    surfaceAlt = Color(0xFF143A5E),
    surfaceRaised = Color(0xFF143A5E),
    textPrimary = Color(0xFFE9F3FC),
    textSecondary = Color(0xFFAFC9E2),
    textTertiary = Color(0xFF98B6D4),
    outline = Color(0xFF2C5A88),
    divider = Color(0xFF1B4671),
    accent = Color(0xFF7FD1F7),
    accentPressed = Color(0xFFA5E0FA),
    onAccent = Color(0xFF06243B),
    accentContainer = Color(0xFF10395B),
    onAccentContainer = Color(0xFFD3EEFF),
    danger = DarkDanger.copy(fg = Color(0xFFFF8A80), solid = Color(0xFFFF8A80)),
    info = DarkInfo.copy(fg = Color(0xFF8FB6FF), solid = Color(0xFF8FB6FF)),
    pending = DarkPending.copy(fg = Color(0xFFCDA6F2), solid = Color(0xFFCDA6F2)),
    catWall = Color(0xFF9FBDE4),
    chartTrack = Color(0xFF14486E),
    chartGrid = Color(0xFF1B4671),
    chartAxis = Color(0xFF2C5A88)
)

val LocalCwColors = staticCompositionLocalOf { CwLight }
