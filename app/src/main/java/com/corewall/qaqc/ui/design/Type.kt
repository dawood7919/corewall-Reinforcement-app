package com.corewall.qaqc.ui.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.corewall.qaqc.R

/**
 * سلّم الخطوط — سلّم واحد، والأوزان **متبنية جوّاه**.
 *
 * القياس القديم لقى ٢٢٣ `fontWeight =` و ١٧ `fontSize =` مكتوبين بالإيد جوّه
 * الشاشات، يعني السلّم كان موجود وبيتخطّى باستمرار. الحل مش تحذير — الحل إن
 * كل خانة هنا تيجي بوزنها الصح من الأول فمحدش يحتاج يعدّله.
 *
 * القاعدة: ممنوع `fontSize` أو `fontWeight` خام جوّه أي شاشة.
 */

val PlexArabic = FontFamily(
    Font(R.font.ibm_plex_sans_arabic_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_arabic_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_arabic_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_sans_arabic_semibold, FontWeight.Bold)
)

/** للأكواد الهندسية (T25-100، 22Ø12) — مونوسبيس عشان الأرقام تتصفّ. */
val PlexMono = FontFamily(Font(R.font.ibm_plex_mono_medium, FontWeight.Medium))

/** للأرقام الكبيرة — مضغوط عشان رقم من ٤ خانات يفضل مقروء في مساحة ضيقة. */
val BarlowCondensed = FontFamily(Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold))

// العربي محتاج ارتفاع سطر أوسع من اللاتيني عشان التشكيل والنقط ما تتقصّش.
private val ArabicLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None
)

private fun style(
    size: Int,
    lineHeight: Int,
    weight: FontWeight = FontWeight.Normal,
    family: FontFamily = PlexArabic,
    tracking: Double = 0.0
) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp,
    lineHeightStyle = ArabicLineHeight
)

/**
 * ستايلات خاصة برّه سلّم Material — لأنها مالهاش خانة مناسبة فيه.
 */
object CwText {
    /** الرقم البطل في كارت المقياس (نسبة، عدّاد كبير). */
    val metric = style(32, 36, FontWeight.SemiBold, BarlowCondensed)
    /** رقم أصغر جوّه شبكة مقاييس مزدحمة. */
    val metricSmall = style(22, 26, FontWeight.SemiBold, BarlowCondensed)
    /** كود هندسي — T25-100 / 22Ø12. */
    val code = style(13, 18, FontWeight.Medium, PlexMono)
    /** كود صغير جوّه شيب. */
    val codeSmall = style(11, 15, FontWeight.Medium, PlexMono)
    /** عنوان قسم فوق مجموعة — حروف صغيرة متباعدة. */
    val sectionLabel = style(11, 16, FontWeight.SemiBold, tracking = 0.6)
}

val CwTypography = Typography(
    displayLarge = style(40, 48, FontWeight.SemiBold, BarlowCondensed),
    displayMedium = style(34, 40, FontWeight.SemiBold, BarlowCondensed),
    displaySmall = style(28, 34, FontWeight.SemiBold, BarlowCondensed),

    // عنوان شاشة
    headlineLarge = style(26, 34, FontWeight.SemiBold),
    headlineMedium = style(22, 30, FontWeight.SemiBold),
    headlineSmall = style(20, 28, FontWeight.SemiBold),

    // عناوين الأقسام والكروت
    titleLarge = style(19, 26, FontWeight.SemiBold),
    titleMedium = style(16, 22, FontWeight.SemiBold),
    titleSmall = style(14, 20, FontWeight.SemiBold),

    // النص الجاري
    bodyLarge = style(16, 24),
    bodyMedium = style(14, 21),
    bodySmall = style(13, 19),

    // التسميات
    labelLarge = style(14, 20, FontWeight.Medium),
    labelMedium = style(12, 16, FontWeight.Medium),
    labelSmall = style(11, 15, FontWeight.Medium)
)
