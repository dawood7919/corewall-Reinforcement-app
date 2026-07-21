package com.corewall.qaqc.ui.attention

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.domain.AttentionItem
import com.corewall.qaqc.ui.ColorDot
import com.corewall.qaqc.ui.LevelSelector
import com.corewall.qaqc.ui.theme.LocalCategoryColors

/**
 * تبويب Attention: كل حائط وكمرة اتغيّر تسليحها عن الدور اللي قبله
 * أو بعده مباشرة — Diff تلقائي بدون اختيار يدوي.
 */
@Composable
fun AttentionScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val level by vm.currentLevel.collectAsStateWithLifecycle()
    val schedule by vm.schedule.collectAsStateWithLifecycle()

    val items = remember(schedule, level) { vm.attentionFor(level) }

    Column(modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            LevelSelector(
                levels = vm.levels,
                current = level,
                onPick = vm::setLevel,
                onStep = vm::stepLevel
            )
        }
        Text(
            "${items.size} عنصر محتاج انتباه في دور $level",
            Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (items.isEmpty()) {
            Text(
                "مفيش أي تغيير في التسليح حوالين الدور ده ✓",
                Modifier.padding(24.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
            items(items) { item -> AttentionCard(item) }
        }
    }
}

@Composable
private fun AttentionCard(item: AttentionItem) {
    val colors = LocalCategoryColors.current
    Card(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ColorDot(if (item.isWall) colors.wall else colors.couplingBeam)
                Spacer(Modifier.width(8.dp))
                Text(item.mark, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (item.isWall) "حائط" else "كمرة",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (item.gapHere) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "فجوة بيانات في الدور ده — مفيش صف في الجدول بيغطيه!",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (item.vsPrev.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("عن الدور السابق:", style = MaterialTheme.typography.labelMedium)
                item.vsPrev.forEach { c ->
                    Text(
                        "• ${c.field}: ${c.before} ← ${c.after}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (item.vsNext.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("عن الدور التالي:", style = MaterialTheme.typography.labelMedium)
                item.vsNext.forEach { c ->
                    Text(
                        "• ${c.field}: ${c.before} ← ${c.after}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            item.note?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    "⚠️ $it",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
