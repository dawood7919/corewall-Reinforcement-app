package com.corewall.qaqc.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.corewall.qaqc.data.AppTheme
import com.corewall.qaqc.data.model.ElementCategory
import com.corewall.qaqc.data.model.InspectionStatus
import com.corewall.qaqc.ui.design.CwBlueprint
import com.corewall.qaqc.ui.design.CwColors
import com.corewall.qaqc.ui.design.CwDark
import com.corewall.qaqc.ui.design.CwLight
import com.corewall.qaqc.ui.design.CwText
import com.corewall.qaqc.ui.design.CwTypography
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Radius

/**
 * الثيم — طبقة توصيل بس.
 *
 * كل قيمة هنا **مشتقّة** من [CwColors]، مفيش ولا لون متعرّف في الملف ده.
 * التوكنات القديمة (Srt / Viz / Category / Gradients / Status) لسه موجودة
 * كأسماء عشان الشاشات اللي لسه ما اتحوّلتش تفضل تشتغل، بس بقت **مناظر** على
 * المصدر الواحد مش أنظمة مستقلة. يعني تصليح تباين واحد بيوصل لكل شاشة فوراً.
 */

// ─────────────────────────────────────────── خطوط (أسماء متوافقة مع القديم)

val PlexArabic = com.corewall.qaqc.ui.design.PlexArabic
val PlexMono = com.corewall.qaqc.ui.design.PlexMono
val BarlowCondensed = com.corewall.qaqc.ui.design.BarlowCondensed

val CodeTextStyle: TextStyle = CwText.code
val TowerNumberStyle: TextStyle = CwText.metricSmall

// ─────────────────────────────────────────── مناظر متوافقة على المصدر الواحد

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
        ElementCategory.OTHER -> other
    }
}

private fun CwColors.categories() = CategoryColors(
    wall = catWall,
    couplingBeam = catCouplingBeam,
    internalBeam = catInternalBeam,
    other = catOther
)

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

private fun CwColors.srt() = SrtColors(
    blue = accent,
    bluePress = accentPressed,
    blueTint = accentContainer,
    surface2 = surfaceAlt,
    divider = divider,
    // كان #8C92A0 وبيدي 2.81:1 على الأسطح الفاتحة — دلوقتي بيعدّي 4.5:1.
    text3 = textTertiary,
    green = success.fg,
    orange = warning.fg,
    red = danger.fg,
    grayDot = neutral.fg,
    purple = pending.fg
)

data class VizPalette(
    val series: List<Color>,
    val track: Color,
    val grid: Color,
    val axis: Color,
    val good: Color,
    val warning: Color,
    val serious: Color,
    val critical: Color
) {
    fun series(i: Int): Color = series[i.coerceAtLeast(0) % series.size]
}

private fun CwColors.viz() = VizPalette(
    series = series,
    track = chartTrack,
    grid = chartGrid,
    axis = chartAxis,
    good = success.fg,
    warning = this.warning.fg,
    serious = this.warning.fg,
    critical = danger.fg
)

data class AppGradients(val header: List<Color>, val fab: List<Color>)

private fun CwColors.gradients() = AppGradients(
    header = listOf(accent, accentPressed),
    fab = listOf(accent, accentPressed)
)

/**
 * ألوان الحالة. بتاخد اللوحة صراحةً عشان تشتغل جوّه كود الرسم (Canvas) اللي
 * مش @Composable — وعشان يفضل واضح إن اللون بييجي من مصدر واحد.
 */
object StatusColors {
    fun of(status: InspectionStatus, c: CwColors): Color = when (status) {
        InspectionStatus.NONE -> c.neutral.fg
        InspectionStatus.WIR_SUBMITTED -> c.pending.fg
        InspectionStatus.APPROVED -> c.success.fg
        InspectionStatus.CAST -> c.info.fg
        InspectionStatus.REJECTED -> c.danger.fg
    }

    @Composable
    fun of(status: InspectionStatus): Color = of(status, LocalCwColors.current)
}

val LocalCategoryColors = staticCompositionLocalOf { CwLight.categories() }
val LocalSrtColors = staticCompositionLocalOf { CwLight.srt() }
val LocalVizColors = staticCompositionLocalOf { CwLight.viz() }
val LocalAppGradients = staticCompositionLocalOf { CwLight.gradients() }

// ─────────────────────────────────────────── سكيم Material مشتق من اللوحة

private fun schemeOf(c: CwColors) = if (c.isLight) {
    lightColorScheme(
        primary = c.accent, onPrimary = c.onAccent,
        primaryContainer = c.accentContainer, onPrimaryContainer = c.onAccentContainer,
        secondary = c.accent, onSecondary = c.onAccent,
        secondaryContainer = c.accentContainer, onSecondaryContainer = c.onAccentContainer,
        tertiary = c.success.solid, onTertiary = c.success.onSolid,
        tertiaryContainer = c.success.container, onTertiaryContainer = c.success.onContainer,
        background = c.background, onBackground = c.textPrimary,
        surface = c.surface, onSurface = c.textPrimary,
        surfaceVariant = c.surfaceAlt, onSurfaceVariant = c.textSecondary,
        surfaceContainer = c.surfaceAlt, surfaceContainerHigh = c.surfaceAlt,
        outline = c.outline, outlineVariant = c.divider,
        error = c.danger.solid, onError = c.danger.onSolid,
        errorContainer = c.danger.container, onErrorContainer = c.danger.onContainer,
        scrim = Color(0x99000000)
    )
} else {
    darkColorScheme(
        primary = c.accent, onPrimary = c.onAccent,
        primaryContainer = c.accentContainer, onPrimaryContainer = c.onAccentContainer,
        secondary = c.accent, onSecondary = c.onAccent,
        secondaryContainer = c.accentContainer, onSecondaryContainer = c.onAccentContainer,
        tertiary = c.success.solid, onTertiary = c.success.onSolid,
        tertiaryContainer = c.success.container, onTertiaryContainer = c.success.onContainer,
        background = c.background, onBackground = c.textPrimary,
        surface = c.surface, onSurface = c.textPrimary,
        surfaceVariant = c.surfaceAlt, onSurfaceVariant = c.textSecondary,
        surfaceContainer = c.surfaceAlt, surfaceContainerHigh = c.surfaceAlt,
        outline = c.outline, outlineVariant = c.divider,
        error = c.danger.solid, onError = c.danger.onSolid,
        errorContainer = c.danger.container, onErrorContainer = c.danger.onContainer,
        scrim = Color(0x99000000)
    )
}

// أنصاف الأقطار كلها من [Radius] — أربع خطوات بدل ١٩ قيمة متفرّقة.
private val CwShapes = Shapes(
    extraSmall = Radius.shapeSm,
    small = Radius.shapeSm,
    medium = Radius.shapeMd,
    large = Radius.shapeLg,
    extraLarge = Radius.shapeXl
)

@Composable
fun CoreWallTheme(theme: AppTheme, content: @Composable () -> Unit) {
    val colors = when (theme) {
        AppTheme.IOS_LIGHT -> CwLight
        AppTheme.DARK_OLED -> CwDark
        AppTheme.BLUEPRINT -> CwBlueprint
    }
    CompositionLocalProvider(
        LocalCwColors provides colors,
        LocalCategoryColors provides colors.categories(),
        LocalSrtColors provides colors.srt(),
        LocalVizColors provides colors.viz(),
        LocalAppGradients provides colors.gradients()
    ) {
        MaterialTheme(
            colorScheme = schemeOf(colors),
            shapes = CwShapes,
            typography = CwTypography,
            content = content
        )
    }
}
