package com.corewall.qaqc.ui.counting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.Lens
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.ui.design.CwButton
import com.corewall.qaqc.ui.design.CwButtonStyle
import com.corewall.qaqc.ui.design.CwCard
import com.corewall.qaqc.ui.design.CwDivider
import com.corewall.qaqc.ui.design.CwEmptyState
import com.corewall.qaqc.ui.design.CwSectionHeader
import com.corewall.qaqc.ui.design.CwStatusBadge
import com.corewall.qaqc.ui.design.CwText
import com.corewall.qaqc.ui.design.CwTone
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Space
import com.corewall.qaqc.ui.design.semantic

/**
 * تقرير العدّ — كان مبنيّ ومحدش يقدر يوصله كمان.
 *
 * بيقارن العدّ المسجّل في الموقع بالمطلوب في الشوب دروينج، لكل قطر ولكل
 * عنصر. التقرير القديم كان بيقول "مطابق ✓" بأخضر و"مختلف!" بأحمر —
 * والدرجتين ساقطين في التباين، والعلامة نفسها معناها متوقّف على اللون.
 * دلوقتي كل نتيجة شارة فيها أيقونة ونص ولون مع بعض.
 */
@Composable
fun CountingReportScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val c = LocalCwColors.current
    val allCounts by vm.barCounts.collectAsStateWithLifecycle()
    val names by vm.names.collectAsStateWithLifecycle()
    val level by vm.currentLevel.collectAsStateWithLifecycle()

    val barCounts = remember(allCounts, level) { allCounts.filter { it.level == level } }
    val siteTotals = remember(barCounts) { totalsByDiameter(siteOf(barCounts)) }
    val drawingTotals = remember(barCounts) { totalsByDiameter(drawingOf(barCounts)) }
    val diameters = remember(barCounts) { (siteTotals.keys + drawingTotals.keys).toSortedSet().toList() }
    val byElement = remember(barCounts) { barCounts.groupBy { it.elementId } }

    if (diameters.isEmpty()) {
        CwEmptyState(
            icon = Icons.Filled.Calculate,
            title = "مفيش أعداد متسجّلة في دور $level",
            detail = "العدّ بيتسجّل من المسقط: افتح عدسة العدّ ودوس على أي حائط عشان تدخّل عدد الأسياخ.",
            modifier = modifier.fillMaxSize(),
            action = {
                CwButton("افتح عدسة العدّ", { vm.goToLens(Lens.COUNT) })
            }
        )
        return
    }

    val siteSum = siteTotals.values.sum()
    val drawingSum = drawingTotals.values.sum()
    val diffSum = siteSum - drawingSum

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Space.screen, end = Space.screen,
            top = Space.md, bottom = Space.bottomInset
        ),
        verticalArrangement = Arrangement.spacedBy(Space.stack)
    ) {
        item(key = "verdict") {
            val tone = if (diffSum == 0) CwTone.Success else CwTone.Danger
            CwCard {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "الفرق الكلي",
                            style = MaterialTheme.typography.labelMedium,
                            color = c.textTertiary
                        )
                        Text(
                            if (diffSum == 0) "مطابق" else formatDiff(diffSum) + " سيخ",
                            style = CwText.metric,
                            color = tone.semantic().fg
                        )
                    }
                    CwStatusBadge(
                        label = if (diffSum == 0) "الموقع = الدروينج" else "فيه فرق",
                        tone = tone
                    )
                }
                Spacer(Modifier.height(Space.sm))
                Text(
                    "الموقع $siteSum · الدروينج $drawingSum · ${byElement.size} عنصر متعدّ في دور $level",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textTertiary
                )
            }
        }

        item(key = "table-header") { CwSectionHeader("التفصيل بالقطر") }

        item(key = "table") {
            CwCard {
                ReportRow("القطر", "الموقع", "الدروينج", "الفرق", header = true)
                CwDivider(inset = false)
                Spacer(Modifier.height(Space.xs))
                diameters.forEach { dia ->
                    val site = siteTotals[dia] ?: 0
                    val drawing = drawingTotals[dia] ?: 0
                    val diff = site - drawing
                    ReportRow(
                        "Ø$dia",
                        "$site",
                        "$drawing",
                        if (diff == 0) "—" else formatDiff(diff),
                        diffColor = if (diff == 0) c.textTertiary else c.danger.fg
                    )
                }
                Spacer(Modifier.height(Space.xs))
                CwDivider(inset = false)
                Spacer(Modifier.height(Space.xs))
                ReportRow(
                    "الإجمالي",
                    "$siteSum",
                    "$drawingSum",
                    if (diffSum == 0) "—" else formatDiff(diffSum),
                    header = true,
                    diffColor = if (diffSum == 0) c.textTertiary else c.danger.fg
                )
            }
        }

        item(key = "elements-header") {
            CwSectionHeader("العناصر", count = byElement.size)
        }

        val sorted = byElement.entries
            .sortedBy { it.key.removePrefix("s").toIntOrNull() ?: 0 }
            .toList()

        items(
            count = sorted.size,
            key = { i -> "el-${sorted[i].key}" }
        ) { i ->
            val (elementId, entries) = sorted[i]
            val site = siteOf(entries)
            val drawing = drawingOf(entries)
            val comparable = site.isNotEmpty() && drawing.isNotEmpty()
            val match = totalsByDiameter(site) == totalsByDiameter(drawing)

            CwCard {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(
                        names[elementId] ?: elementId,
                        style = CwText.code,
                        color = c.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    when {
                        !comparable -> CwStatusBadge("ناقص جهة", CwTone.Warning, compact = true)
                        match -> CwStatusBadge("مطابق", CwTone.Success, compact = true)
                        else -> CwStatusBadge("مختلف", CwTone.Danger, compact = true)
                    }
                }
                Spacer(Modifier.height(Space.sm))
                LabeledLine("الموقع", formatEntries(site).ifEmpty { "مش متسجّل" }, c.textPrimary)
                Spacer(Modifier.height(Space.xxs))
                LabeledLine("الدروينج", formatEntries(drawing).ifEmpty { "مش متسجّل" }, c.textTertiary)
            }
        }
    }
}

private fun formatDiff(d: Int): String = if (d > 0) "+$d" else "$d"

@Composable
private fun LabeledLine(label: String, value: String, valueColor: Color) {
    val c = LocalCwColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = c.textTertiary,
            modifier = Modifier.weight(0.5f)
        )
        Text(
            value,
            style = CwText.codeSmall,
            color = valueColor,
            modifier = Modifier.weight(1.5f)
        )
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
    val c = LocalCwColors.current
    val style = if (header) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium
    val color = if (header) c.textPrimary else c.textSecondary
    Row(Modifier.fillMaxWidth()) {
        Text(c1, Modifier.weight(1.2f), style = style, color = color)
        Text(c2, Modifier.weight(1f), style = style, color = color, textAlign = TextAlign.Center)
        Text(c3, Modifier.weight(1f), style = style, color = color, textAlign = TextAlign.Center)
        Text(
            c4,
            Modifier.weight(1f),
            style = style,
            color = diffColor ?: color,
            textAlign = TextAlign.Center
        )
    }
}
