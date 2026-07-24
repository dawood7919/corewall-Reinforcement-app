package com.corewall.qaqc.ui.counting

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel

/**
 * ريبورت العدّ **للدور الحالي بس** (كل دور معزول): إجمالي أعداد الأسياخ
 * من كل قطر — الموقع والشوب دروينج والفرق — وتفصيلة لكل جدار متسجّل.
 */
@Composable
fun CountingReportScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val allCounts by vm.barCounts.collectAsStateWithLifecycle()
    val names by vm.names.collectAsStateWithLifecycle()
    val level by vm.currentLevel.collectAsStateWithLifecycle()

    val barCounts = remember(allCounts, level) { allCounts.filter { it.level == level } }
    val siteTotals = remember(barCounts) { totalsByDiameter(siteOf(barCounts)) }
    val drawingTotals = remember(barCounts) { totalsByDiameter(drawingOf(barCounts)) }
    val allDiameters = remember(barCounts) { (siteTotals.keys + drawingTotals.keys).toSortedSet().toList() }
    val byElement = remember(barCounts) { barCounts.groupBy { it.elementId } }
    val matchColor = Color(0xFF34C759)
    val mismatchColor = Color(0xFFFF453A)

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
    ) {
        item {
            com.corewall.qaqc.ui.LevelSelector(
                levels = vm.levels,
                current = level,
                onPick = vm::setLevel,
                onStep = vm::stepLevel
            )
            Text("إجمالي الأعداد — دور $level", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (allDiameters.isEmpty()) {
                Text(
                    "لسه مفيش أعداد متسجّلة في دور $level — افتح البلان (عدسة العدّ) ودوس على أي جدار.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        ReportRow("القطر", "الموقع", "الدروينج", "الفرق", header = true)
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        allDiameters.forEach { dia ->
                            val site = siteTotals[dia] ?: 0
                            val drawing = drawingTotals[dia] ?: 0
                            val diff = site - drawing
                            ReportRow(
                                "Ø$dia mm",
                                site.toString(),
                                drawing.toString(),
                                if (diff == 0) "✓" else (if (diff > 0) "+$diff" else "$diff"),
                                diffColor = if (diff == 0) matchColor else mismatchColor
                            )
                        }
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        val siteSum = siteTotals.values.sum()
                        val drawingSum = drawingTotals.values.sum()
                        val diffSum = siteSum - drawingSum
                        ReportRow(
                            "الإجمالي",
                            siteSum.toString(),
                            drawingSum.toString(),
                            if (diffSum == 0) "✓" else (if (diffSum > 0) "+$diffSum" else "$diffSum"),
                            header = true,
                            diffColor = if (diffSum == 0) matchColor else mismatchColor
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            if (byElement.isNotEmpty()) {
                Text("تفصيلة الجدران (${byElement.size})", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
            }
        }

        items(byElement.entries.sortedBy { it.key.removePrefix("s").toIntOrNull() ?: 0 }.toList()) { (elementId, entries) ->
            val site = siteOf(entries)
            val drawing = drawingOf(entries)
            val match = totalsByDiameter(site) == totalsByDiameter(drawing)
            Card(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(10.dp)) {
                    Row {
                        Text(
                            names[elementId] ?: elementId,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        if (site.isNotEmpty() && drawing.isNotEmpty()) {
                            Text(
                                if (match) "مطابق ✓" else "مختلف!",
                                color = if (match) matchColor else mismatchColor,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "الموقع: ${formatEntries(site).ifEmpty { "—" }}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "الدروينج: ${formatEntries(drawing).ifEmpty { "—" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ReportRow(
    c1: String,
    c2: String,
    c3: String,
    c4: String,
    header: Boolean = false,
    diffColor: Color? = null
) {
    val weight = if (header) FontWeight.Bold else FontWeight.Normal
    Row(Modifier.fillMaxWidth()) {
        Text(c1, Modifier.weight(1.2f), fontWeight = weight, style = MaterialTheme.typography.bodyMedium)
        Text(c2, Modifier.weight(1f), fontWeight = weight, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
        Text(c3, Modifier.weight(1f), fontWeight = weight, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
        Text(
            c4,
            Modifier.weight(1f),
            fontWeight = weight,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = diffColor ?: MaterialTheme.colorScheme.onSurface
        )
    }
}
