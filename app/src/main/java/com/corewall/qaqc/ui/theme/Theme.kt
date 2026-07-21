package com.corewall.qaqc.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.corewall.qaqc.data.AppTheme
import com.corewall.qaqc.data.model.ElementCategory
import com.corewall.qaqc.data.model.InspectionStatus

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

private val DarkCategoryColors = CategoryColors(
    wall = Color(0xFF3D4E73),
    couplingBeam = Color(0xFFC0392B),
    internalBeam = Color(0xFFA15C38),
    other = Color(0xFFA15C38)
)

val LocalCategoryColors = staticCompositionLocalOf { DarkCategoryColors }

object StatusColors {
    fun of(status: InspectionStatus): Color = when (status) {
        InspectionStatus.NONE -> Color(0xFF8E8E93)
        InspectionStatus.WIR_SUBMITTED -> Color(0xFFFF9F0A)
        InspectionStatus.APPROVED -> Color(0xFF34C759)
        InspectionStatus.CAST -> Color(0xFF0A84FF)
        InspectionStatus.REJECTED -> Color(0xFFFF3B30)
    }
}

private val IosLightScheme = lightColorScheme(
    primary = Color(0xFF007AFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E9FF),
    onPrimaryContainer = Color(0xFF00325C),
    secondary = Color(0xFF5856D6),
    background = Color(0xFFF2F2F7),
    onBackground = Color(0xFF111111),
    surface = Color.White,
    onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFE9E9EE),
    onSurfaceVariant = Color(0xFF5A5A5F),
    outline = Color(0xFFC7C7CC),
    error = Color(0xFFFF3B30)
)

private val OledDarkScheme = darkColorScheme(
    primary = Color(0xFF0A84FF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0A3060),
    onPrimaryContainer = Color(0xFFBDDCFF),
    secondary = Color(0xFF5E5CE6),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF2F2F7),
    surface = Color(0xFF121214),
    onSurface = Color(0xFFF2F2F7),
    surfaceVariant = Color(0xFF1E1E22),
    onSurfaceVariant = Color(0xFFB0B0B8),
    outline = Color(0xFF3A3A3E),
    error = Color(0xFFFF453A)
)

private val BlueprintScheme = darkColorScheme(
    primary = Color(0xFF7FD1F7),
    onPrimary = Color(0xFF06263D),
    primaryContainer = Color(0xFF16456B),
    onPrimaryContainer = Color(0xFFD3EEFF),
    secondary = Color(0xFFF5C664),
    background = Color(0xFF0B2440),
    onBackground = Color(0xFFE3EEF8),
    surface = Color(0xFF12314F),
    onSurface = Color(0xFFE3EEF8),
    surfaceVariant = Color(0xFF1A3A5C),
    onSurfaceVariant = Color(0xFF9FBAD3),
    outline = Color(0xFF3E6285),
    error = Color(0xFFFF6B5E)
)

private val RoundedShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/** ثيم Blueprint هندسي بحواف حادة. */
private val SharpShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp)
)

@Composable
fun CoreWallTheme(theme: AppTheme, content: @Composable () -> Unit) {
    val (scheme, shapes, categories) = when (theme) {
        AppTheme.IOS_LIGHT -> Triple(IosLightScheme, RoundedShapes, LightCategoryColors)
        AppTheme.DARK_OLED -> Triple(OledDarkScheme, RoundedShapes, DarkCategoryColors)
        AppTheme.BLUEPRINT -> Triple(BlueprintScheme, SharpShapes, DarkCategoryColors)
    }
    CompositionLocalProvider(LocalCategoryColors provides categories) {
        MaterialTheme(colorScheme = scheme, shapes = shapes, content = content)
    }
}
