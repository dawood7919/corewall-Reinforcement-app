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

val LocalCategoryColors = staticCompositionLocalOf { DarkCategoryColors }

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

// فاتح: خلفية عشبية فاتحة + أكسنت ليموني غامق الحبر
private val ReimaginedLight = lightColorScheme(
    primary = Color(0xFF6DA018),          // ليموني معتّم للقراءة على الفاتح
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE4F6BC),
    onPrimaryContainer = Color(0xFF2A3C0C),
    secondary = Color(0xFF3C7A20),
    secondaryContainer = Color(0xFFD9EFC7),
    onSecondaryContainer = Color(0xFF1E3A0F),
    tertiary = Color(0xFF4C8F27),
    background = Color(0xFFF3F5F0),
    onBackground = Color(0xFF12160E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF12160E),
    surfaceVariant = Color(0xFFECEFE6),
    onSurfaceVariant = Color(0xFF5B6555),
    outline = Color(0xFFC9CFC0),
    error = Color(0xFFDC3D2F),
    errorContainer = Color(0xFFFFE1DC),
    onErrorContainer = Color(0xFF5C120A)
)

// دارك OLED: أسود نقي + ليموني C6F24E
private val ReimaginedDark = darkColorScheme(
    primary = Color(0xFFC6F24E),
    onPrimary = Color(0xFF12160E),
    primaryContainer = Color(0xFF33420F),
    onPrimaryContainer = Color(0xFFE3FA9E),
    secondary = Color(0xFFB4E82E),
    secondaryContainer = Color(0xFF2A350D),
    onSecondaryContainer = Color(0xFFDCF79A),
    tertiary = Color(0xFF93DB4E),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF1F3EE),
    surface = Color(0xFF161719),
    onSurface = Color(0xFFF1F3EE),
    surfaceVariant = Color(0xFF202124),
    onSurfaceVariant = Color(0xFF9AA096),
    outline = Color(0xFF3A3C36),
    error = Color(0xFFFF6B5E),
    errorContainer = Color(0xFF4A130C),
    onErrorContainer = Color(0xFFFFD9D3)
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
    val (scheme, shapes, categories) = when (theme) {
        AppTheme.IOS_LIGHT -> Triple(ReimaginedLight, SoftShapes, LightCategoryColors)
        AppTheme.DARK_OLED -> Triple(ReimaginedDark, SoftShapes, DarkCategoryColors)
        AppTheme.BLUEPRINT -> Triple(ReimaginedBlueprint, SharpShapes, BlueprintCategoryColors)
    }
    CompositionLocalProvider(LocalCategoryColors provides categories) {
        MaterialTheme(
            colorScheme = scheme,
            shapes = shapes,
            typography = typography(Typography()),
            content = content
        )
    }
}
