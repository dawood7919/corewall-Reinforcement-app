package com.corewall.qaqc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.ui.theme.LocalAppGradients
import com.corewall.qaqc.ui.theme.TowerNumberStyle

/**
 * هيدر انسيابي بتدرّج نيلي (ستايل Aurora): بيوضّح الدور الشغّال كبير وواضح.
 * سطر واحد رفيع عشان يسيب أكبر مساحة للمحتوى تحته. الدوس عليه يبدّل الدور.
 * showSwitch: يظهر زرار "بدّل" (بنطفّيه في شاشات معيّنة لو حبينا).
 */
@Composable
fun ActiveLevelHeader(vm: MainViewModel, modifier: Modifier = Modifier, onMenu: (() -> Unit)? = null) {
    var showPicker by remember { mutableStateOf(false) }
    val level by vm.currentLevel.collectAsStateWithLifecycle()
    val idx = vm.levels.indexOf(level)
    val gradient = LocalAppGradients.current.header

    Surface(
        color = Color.Transparent,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            Modifier
                .background(Brush.verticalGradient(gradient))
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onMenu != null) {
                androidx.compose.material3.IconButton(onClick = onMenu) {
                    Icon(Icons.Filled.Menu, contentDescription = "الأقسام", tint = Color.White)
                }
                Spacer(Modifier.width(2.dp))
            }
            Column(Modifier.clickable { showPicker = true }) {
                Text(
                    "الدور الشغّال",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.82f)
                )
                Text(
                    level,
                    style = TowerNumberStyle.copy(fontSize = 32.sp),
                    color = Color.White
                )
            }
            Spacer(Modifier.weight(1f))
            if (idx >= 0) {
                Text(
                    "${idx + 1} / ${vm.levels.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.82f)
                )
                Spacer(Modifier.width(10.dp))
            }
            Surface(
                color = Color.White.copy(alpha = 0.20f),
                contentColor = Color.White,
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.SwapVert, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("بدّل", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showPicker) {
        LevelPickerDialog(
            levels = vm.levels,
            current = level,
            onPick = { vm.setLevel(it); showPicker = false },
            onDismiss = { showPicker = false },
            title = "اختار الدور الشغّال"
        )
    }
}
