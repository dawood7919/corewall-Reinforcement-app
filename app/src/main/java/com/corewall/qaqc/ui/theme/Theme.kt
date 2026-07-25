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

/** تدرّجات لونية (ستايل Aurora) — للهيدر والأسطح المميزة. */
data class AppGradients(val header: List<Color>, val fab: List<Color>)

private val AuroraLightGradients = AppGradients(
    header = listOf(Color(0xFF7C86EC), Color(0xFF5B66D6)),
    fab = listOf(Color(0xFF7C86EC), Color(0xFF5B66D6))
)
private val AuroraDarkGradients = AppGradients(
    header = listOf(Color(0xFF3A3F7A), Color(0xFF23264A)),
    fab = listOf(Color(0xFF6E79E0), Color(0xFF4B57C4))
)
private val BlueprintGradients = AppGradients(
    header = listOf(Color(0xFF14486E), Color(0xFF0E2A48)),
    fab = listOf(Color(0xFF2E86C1), Color(0xFF14486E))
)

val LocalAppGradients = staticCompositionLocalOf { AuroraLightGradients }

object StatusColors {
    fun of(status: InspectionStatus): Color = when (status) {
        InspectionStatus.NONE -> Color(0xFF8A8A8E)
        InspectionStatus.WIR_SUBMITTED -> Color(0xFFFF9F0A)
        InspectionStatus.APPROVED -> Color(0xFF34C759)
        InspectionStatus.CAST -> Color(0xFF0A84FF)
        InspectionStatus.REJECTED -> Color(0xFFFF3B30)
    }
}

// ---------------------------------------------------------------- الثيمات

// Aurora فاتح: خلفية لافندر + نيلي متدرّج + كروت بيضا ناعمة (ستايل Finance app)
private val ReimaginedLight = lightColorScheme(
    primary = Color(0xFF5B66D6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE6E8FB),
    onPrimaryContainer = Color(0xFF2C336E),
    secondary = Color(0xFF7C86EC),
    secondaryContainer = Color(0xFFEDEFFC),
    onSecondaryContainer = Color(0xFF2C336E),
    tertiary = Color(0xFF37B98A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD6F5E8),
    onTertiaryContainer = Color(0xFF0E4632),
    background = Color(0xFFEFF1FB),
    onBackground = Color(0xFF1E2038),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1E2038),
    surfaceVariant = Color(0xFFEEF0FA),
    onSurfaceVariant = Color(0xFF7E82A0),
    outline = Color(0xFFE0E3F1),
    error = Color(0xFFF25C6E),
    errorContainer = Color(0xFFFFE1E5),
    onErrorContainer = Color(0xFF6B121F)
)

// Aurora دارك: نيلي غامق مخملي + نفس الأكسنت
private val ReimaginedDark = darkColorScheme(
    primary = Color(0xFF8B95F0),
    onPrimary = Color(0xFF1A1E3A),
    primaryContainer = Color(0xFF2E3466),
    onPrimaryContainer = Color(0xFFD9DCFB),
    secondary = Color(0xFF9AA2F2),
    secondaryContainer = Color(0xFF262B52),
    onSecondaryContainer = Color(0xFFD9DCFB),
    tertiary = Color(0xFF4FD3A0),
    onTertiary = Color(0xFF06281C),
    tertiaryContainer = Color(0xFF10452F),
    onTertiaryContainer = Color(0xFFCFF6E5),
    background = Color(0xFF0F1024),
    onBackground = Color(0xFFE7E8F6),
    surface = Color(0xFF191B36),
    onSurface = Color(0xFFE7E8F6),
    surfaceVariant = Color(0xFF232645),
    onSurfaceVariant = Color(0xFF9DA1C4),
    outline = Color(0xFF33375C),
    error = Color(0xFFFF7A88),
    errorContainer = Color(0xFF4A1620),
    onErrorContainer = Color(0xFFFFD9DD)
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

// أنصاف أقطار كبيرة (24/16) — وBlueprint حاد (6/4)
private val SoftShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
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
        AppTheme.IOS_LIGHT -> AuroraLightGradients
        AppTheme.DARK_OLED -> AuroraDarkGradients
        AppTheme.BLUEPRINT -> BlueprintGradients
    }
    CompositionLocalProvider(
        LocalCategoryColors provides categories,
        LocalAppGradients provides gradients
    ) {
        MaterialTheme(
            colorScheme = scheme,
            shapes = shapes,
            typography = typography(Typography()),
            content = content
        )
    }
}
