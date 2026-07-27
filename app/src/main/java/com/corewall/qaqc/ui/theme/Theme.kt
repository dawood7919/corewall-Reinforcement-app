package com.corewall.qaqc.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corewall.qaqc.R
import com.corewall.qaqc.data.AppTheme
import com.corewall.qaqc.data.model.ElementCategory
import com.corewall.qaqc.data.model.InspectionStatus

// ---------------------------------------------------------------- الخطوط
// هوية "Reimagined 2026": IBM Plex Sans Arabic للنصوص،
// IBM Plex Mono للكولاوتات والأكواد، Barlow Condensed لأرقام البرج والمقاييس.

val PlexArabic = FontFamily(
    Font(R.font.ibm_plex_sans_arabic_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_arabic_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_arabic_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_sans_arabic_semibold, FontWeight.Bold)
)

val PlexMono = FontFamily(Font(R.font.ibm_plex_mono_medium, FontWeight.Medium))

val BarlowCondensed = FontFamily(Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold))

private fun typography(base: Typography): Typography = base.copy(
    displayLarge = base.displayLarge.copy(fontFamily = PlexArabic),
    displayMedium = base.displayMedium.copy(fontFamily = PlexArabic),
    displaySmall = base.displaySmall.copy(fontFamily = PlexArabic),
    headlineLarge = base.headlineLarge.copy(fontFamily = PlexArabic, fontWeight = FontWeight.SemiBold),
    headlineMedium = base.headlineMedium.copy(fontFamily = PlexArabic, fontWeight = FontWeight.SemiBold),
    headlineSmall = base.headlineSmall.copy(fontFamily = PlexArabic, fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontFamily = PlexArabic, fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontFamily = PlexArabic, fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontFamily = PlexArabic, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontFamily = PlexArabic),
    bodyMedium = base.bodyMedium.copy(fontFamily = PlexArabic),
    bodySmall = base.bodySmall.copy(fontFamily = PlexArabic),
    labelLarge = base.labelLarge.copy(fontFamily = PlexArabic, fontWeight = FontWeight.Medium),
    labelMedium = base.labelMedium.copy(fontFamily = PlexArabic, fontWeight = FontWeight.Medium),
    labelSmall = base.labelSmall.copy(fontFamily = PlexArabic)
)

/** ستايل الكولاوتات (T25-100, 22Ø12) — مونوسبيس. */
val CodeTextStyle = TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.Medium, fontSize = 13.sp)

/** ستايل أرقام البرج/المقاييس الكبيرة. */
val TowerNumberStyle = TextStyle(fontFamily = BarlowCondensed, fontWeight = FontWeight.SemiBold)

// ---------------------------------------------------------------- ألوان دلالية

data class CategoryColors(
    val wall: Color,
    val couplingBeam: Color,
    val internalBeam: Color,
    val other: Color
) {
    fun of(cat: ElementCategory): Color = when (cat) {
        ElementCategory.WALL -> wall
        ElementCategory.COUPLING_BEAM -> couplingBeam
        ElementCategory.INTERNAL_BEAM -> internalBeam
        // TODO: فئة "other" لسه نوعها غير مؤكد — مؤقتاً بلون البيمات الداخلية.
        ElementCategory.OTHER -> other
    }
}

private val LightCategoryColors = CategoryColors(
    wall = Color(0xFF324A70),
    couplingBeam = Color(0xFFC0392B),
    internalBeam = Color(0xFFA35D34),
    other = Color(0xFFA35D34)
)

// حوائط أفتح على الخلفيات الغامقة عشان تفضل واضحة (زي توكنات التصميم)
private val DarkCategoryColors = CategoryColors(
    wall = Color(0xFF5A6E96),
    couplingBeam = Color(0xFFC0392B),
    internalBeam = Color(0xFFA15C38),
    other = Color(0xFFA15C38)
)

private val BlueprintCategoryColors = CategoryColors(
    wall = Color(0xFF6E8CBE),
    couplingBeam = Color(0xFFC0392B),
    internalBeam = Color(0xFFA15C38),
    other = Color(0xFFA15C38)
)

val LocalCategoryColors = staticCompositionLocalOf { LightCategoryColors }

/**
 * لوحة SRT الموسّعة — التوكنات اللي مالهاش مقابل في Material colorScheme
 * (text-3, الألوان الدلالية الثابتة، وخلفية الأزرق الخفيفة blue-tint).
 * ثابتة المعنى في كل التطبيق زي ما الـ spec بيقول.
 */
data class SrtColors(
    val blue: Color,
    val bluePress: Color,
    val blueTint: Color,
    val surface2: Color,
    val divider: Color,
    val text3: Color,
    val green: Color,
    val orange: Color,
    val red: Color,
    val grayDot: Color,
    val purple: Color
)

private val SrtLight = SrtColors(
    blue = Color(0xFF3A6EF0),
    bluePress = Color(0xFF2F5AD0),
    blueTint = Color(0xFFEBF1FE),
    surface2 = Color(0xFFF2F3F6),
    divider = Color(0xFFEEEFF2),
    text3 = Color(0xFF8C92A0),
    green = Color(0xFF34C759),
    orange = Color(0xFFFF9500),
    red = Color(0xFFFF3B30),
    grayDot = Color(0xFFAEB2BC),
    purple = Color(0xFFAF52DE)
)

private val SrtDark = SrtColors(
    blue = Color(0xFF4E7DF5),
    bluePress = Color(0xFF3A6EF0),
    blueTint = Color(0xFF1B2540),
    surface2 = Color(0xFF1F2229),
    divider = Color(0xFF23262E),
    text3 = Color(0xFF7E8592),
    green = Color(0xFF34C759),
    orange = Color(0xFFFF9500),
    red = Color(0xFFFF453A),
    grayDot = Color(0xFF5A5F6C),
    purple = Color(0xFFBF5AF2)
)

private val SrtBlueprint = SrtLight.copy(
    blue = Color(0xFF7FD1F7),
    bluePress = Color(0xFF5FB8E8),
    blueTint = Color(0xFF10395B),
    surface2 = Color(0xFF15355A),
    divider = Color(0xFF15355A),
    text3 = Color(0xFF8FB4D4)
)

val LocalSrtColors = staticCompositionLocalOf { SrtLight }

/** تدرّجات لونية — للهيدر وكروت الأبطال (hero) الزرقا. */
data class AppGradients(val header: List<Color>, val fab: List<Color>)

private val BlueLightGradients = AppGradients(
    header = listOf(Color(0xFF4E7DF5), Color(0xFF3A6EF0)),
    fab = listOf(Color(0xFF4E7DF5), Color(0xFF3A6EF0))
)
private val BlueDarkGradients = AppGradients(
    header = listOf(Color(0xFF3A6EF0), Color(0xFF2F5AD0)),
    fab = listOf(Color(0xFF4E7DF5), Color(0xFF3A6EF0))
)
private val BlueprintGradients = AppGradients(
    header = listOf(Color(0xFF14486E), Color(0xFF0E2A48)),
    fab = listOf(Color(0xFF2E86C1), Color(0xFF14486E))
)

val LocalAppGradients = staticCompositionLocalOf { BlueLightGradients }

object StatusColors {
    fun of(status: InspectionStatus): Color = when (status) {
        InspectionStatus.NONE -> Color(0xFFAEB2BC)
        InspectionStatus.WIR_SUBMITTED -> Color(0xFFAF52DE)
        InspectionStatus.APPROVED -> Color(0xFF34C759)
        InspectionStatus.CAST -> Color(0xFF3A6EF0)
        InspectionStatus.REJECTED -> Color(0xFFFF3B30)
    }
}

// ---------------------------------------------------------------- الثيمات

// SRT فاتح: خلفية رمادي فاتح #F7F8FA + أزرق iOS #3A6EF0 + كروت بيضا
private val ReimaginedLight = lightColorScheme(
    primary = Color(0xFF3A6EF0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEBF1FE),
    onPrimaryContainer = Color(0xFF2F5AD0),
    secondary = Color(0xFF3A6EF0),
    secondaryContainer = Color(0xFFEBF1FE),
    onSecondaryContainer = Color(0xFF2F5AD0),
    tertiary = Color(0xFF34C759),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDDF6E3),
    onTertiaryContainer = Color(0xFF0B5127),
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF1C1E26),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1E26),
    surfaceVariant = Color(0xFFF2F3F6),
    onSurfaceVariant = Color(0xFF6B7280),
    outline = Color(0xFFE8EAEE),
    error = Color(0xFFFF3B30),
    errorContainer = Color(0xFFFFE3E1),
    onErrorContainer = Color(0xFF7A1610)
)

// SRT دارك: خلفية #0B0C12 + أسطح #16181F + نفس الأزرق
private val ReimaginedDark = darkColorScheme(
    primary = Color(0xFF4E7DF5),
    onPrimary = Color(0xFF07122E),
    primaryContainer = Color(0xFF1B2540),
    onPrimaryContainer = Color(0xFFCFDCFB),
    secondary = Color(0xFF4E7DF5),
    secondaryContainer = Color(0xFF1B2540),
    onSecondaryContainer = Color(0xFFCFDCFB),
    tertiary = Color(0xFF34C759),
    onTertiary = Color(0xFF04220F),
    tertiaryContainer = Color(0xFF10361E),
    onTertiaryContainer = Color(0xFFCFF6D9),
    background = Color(0xFF0B0C12),
    onBackground = Color(0xFFF5F6F8),
    surface = Color(0xFF16181F),
    onSurface = Color(0xFFF5F6F8),
    surfaceVariant = Color(0xFF1F2229),
    onSurfaceVariant = Color(0xFF9CA3AF),
    outline = Color(0xFF282B33),
    error = Color(0xFFFF453A),
    errorContainer = Color(0xFF3E1512),
    onErrorContainer = Color(0xFFFFD9D5)
)

// Blueprint: أزرق لوحة هندسية + سماوي 7FD1F7 وحواف حادة
private val ReimaginedBlueprint = darkColorScheme(
    primary = Color(0xFF7FD1F7),
    onPrimary = Color(0xFF06243B),
    primaryContainer = Color(0xFF14486E),
    onPrimaryContainer = Color(0xFFD3EEFF),
    secondary = Color(0xFF9BDBF9),
    secondaryContainer = Color(0xFF10395B),
    onSecondaryContainer = Color(0xFFD3EEFF),
    tertiary = Color(0xFF5FB8E8),
    background = Color(0xFF08213C),
    onBackground = Color(0xFFE4F0FA),
    surface = Color(0xFF0E2A48),
    onSurface = Color(0xFFE4F0FA),
    surfaceVariant = Color(0xFF15355A),
    onSurfaceVariant = Color(0xFF8FB4D4),
    outline = Color(0xFF2E5680),
    error = Color(0xFFFF6B5E),
    errorContainer = Color(0xFF4A130C),
    onErrorContainer = Color(0xFFFFD9D3)
)

// أنصاف أقطار SRT: input 14 / icon 12 / card 18 / sheet 28 — وBlueprint حاد (6/4)
private val SoftShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private val SharpShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(6.dp),
    extraLarge = RoundedCornerShape(8.dp)
)

@Composable
fun CoreWallTheme(theme: AppTheme, content: @Composable () -> Unit) {
    val (scheme, shapes) = when (theme) {
        AppTheme.IOS_LIGHT -> ReimaginedLight to SoftShapes
        AppTheme.DARK_OLED -> ReimaginedDark to SoftShapes
        AppTheme.BLUEPRINT -> ReimaginedBlueprint to SharpShapes
    }
    val categories = when (theme) {
        AppTheme.IOS_LIGHT -> LightCategoryColors
        AppTheme.DARK_OLED -> DarkCategoryColors
        AppTheme.BLUEPRINT -> BlueprintCategoryColors
    }
    val gradients = when (theme) {
        AppTheme.IOS_LIGHT -> BlueLightGradients
        AppTheme.DARK_OLED -> BlueDarkGradients
        AppTheme.BLUEPRINT -> BlueprintGradients
    }
    val srt = when (theme) {
        AppTheme.IOS_LIGHT -> SrtLight
        AppTheme.DARK_OLED -> SrtDark
        AppTheme.BLUEPRINT -> SrtBlueprint
    }
    CompositionLocalProvider(
        LocalCategoryColors provides categories,
        LocalAppGradients provides gradients,
        LocalSrtColors provides srt
    ) {
        MaterialTheme(
            colorScheme = scheme,
            shapes = shapes,
            typography = typography(Typography()),
            content = content
        )
    }
}
