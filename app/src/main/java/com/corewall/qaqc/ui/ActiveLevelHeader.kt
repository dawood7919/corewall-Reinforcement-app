package com.corewall.qaqc.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UnfoldMore
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corewall.qaqc.ui.theme.TowerNumberStyle

/**
 * الهيدر الكبير الثابت اللي بيوضّح **الدور الشغّال** — بيظهر فوق كل الشاشات.
 * كل حاجة في التطبيق بتخص الدور ده بس؛ الدوس عليه يفتح قائمة تبديل الدور.
 * pipColor: نقطة صغيرة بتلخّص حالة الدور (اختياري).
 */
@Composable
fun ActiveLevelHeader(
    levels: List<String>,
    current: String,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
    pipColor: Color? = null
) {
    var showPicker by remember { mutableStateOf(false) }
    val idx = levels.indexOf(current)

    Surface(
        onClick = { showPicker = true },
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            Modifier
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "الدور الشغّال",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (pipColor != null && pipColor != Color.Transparent) {
                        Box(Modifier.size(9.dp)) {
                            ColorDot(pipColor, 9)
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        current,
                        style = TowerNumberStyle.copy(fontSize = 30.sp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            if (idx >= 0) {
                Text(
                    "${idx + 1} / ${levels.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                )
                Spacer(Modifier.width(8.dp))
            }
            Surface(
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.small
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.UnfoldMore, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("بدّل الدور", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showPicker) {
        LevelPickerDialog(
            levels = levels,
            current = current,
            onPick = { onPick(it); showPicker = false },
            onDismiss = { showPicker = false },
            title = "اختار الدور الشغّال"
        )
    }
}
