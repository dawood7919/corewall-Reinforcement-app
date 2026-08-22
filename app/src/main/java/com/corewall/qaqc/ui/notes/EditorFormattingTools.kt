package com.corewall.qaqc.ui.notes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Radius
import com.corewall.qaqc.ui.design.Space

@Composable
fun EditorTextTool(label: String, active: Boolean = false, onClick: () -> Unit) {
    val c = LocalCwColors.current
    Surface(onClick = onClick, color = if (active) c.accentContainer else Color.Transparent, shape = Radius.shapeSm) {
        Text(label, Modifier.padding(horizontal = Space.sm, vertical = Space.sm), color = if (active) c.onAccentContainer else c.textPrimary)
    }
}

@Composable
fun EditorIconTool(icon: ImageVector, label: String, onClick: () -> Unit) {
    val c = LocalCwColors.current
    Surface(onClick = onClick, color = Color.Transparent, shape = Radius.shapeSm, modifier = Modifier.size(46.dp)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, label, tint = c.textPrimary) }
    }
}

@Composable
fun EditorColorTool(color: Color, onClick: () -> Unit) {
    val c = LocalCwColors.current
    Surface(onClick = onClick, color = color, shape = Radius.pill, border = BorderStroke(1.dp, c.outline), modifier = Modifier.size(28.dp)) {}
}
