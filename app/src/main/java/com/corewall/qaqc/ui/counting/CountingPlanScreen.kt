package com.corewall.qaqc.ui.counting

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.data.db.BarCountEntity
import com.corewall.qaqc.export.PlanExporter
import com.corewall.qaqc.ui.ColorDot
import com.corewall.qaqc.ui.plan.InteractivePlanCanvas
import com.corewall.qaqc.ui.plan.PlanLabel
import com.corewall.qaqc.ui.theme.LocalCategoryColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * أداة Corewall Counting: نفس البلان — دوس على أي جدار عشان تسجّل
 * أعداد الأسياخ الرأسية (الموقع والدروينج). الأعداد بتظهر في منتصف
 * كل جدار موازية له، وحجمها نسبة من البلان (بتكبر مع الزوم).
 */
@Composable
fun CountingPlanScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val barCounts by vm.barCounts.collectAsStateWithLifecycle()
    val selectedId by vm.selectedElementId.collectAsStateWithLifecycle()
    val catColors = LocalCategoryColors.current
    var showExport by remember { mutableStateOf(false) }

    val matchColor = Color(0xFF34C759)
    val mismatchColor = Color(0xFFFF453A)
    val defaultColor = MaterialTheme.colorScheme.onBackground
    val dimColor = MaterialTheme.colorScheme.onSurfaceVariant

    val byElement = remember(barCounts) { barCounts.groupBy { it.elementId } }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "دوس على جدار لتسجيل عدد الأسياخ",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { showExport = true }) {
                Icon(Icons.Filled.IosShare, contentDescription = "تصدير الدروينج بالأعداد")
            }
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            InteractivePlanCanvas(
                planData = vm.planData,
                selectedId = selectedId,
                backgroundColor = MaterialTheme.colorScheme.background,
                selectionColor = MaterialTheme.colorScheme.primary,
                fillFor = { el ->
                    val entries = byElement[el.id]
                    catColors.of(el.cat).copy(alpha = if (entries.isNullOrEmpty()) 0.45f else 1f)
                },
                strokeFor = { null },
                labelFor = { el ->
                    val entries = byElement[el.id] ?: return@InteractivePlanCanvas null
                    val site = siteOf(entries)
                    val drawing = drawingOf(entries)
                    val text = formatEntries(site.ifEmpty { drawing })
                    if (text.isEmpty()) return@InteractivePlanCanvas null
                    val color = when {
                        site.isEmpty() -> dimColor
                        drawing.isEmpty() -> defaultColor
                        totalsByDiameter(site) == totalsByDiameter(drawing) -> matchColor
                        else -> mismatchColor
                    }
                    PlanLabel(text, color, scaleWithPlan = true)
                },
                onTapElement = { vm.selectElement(it.id) },
                modifier = Modifier.fillMaxSize()
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendDot(matchColor, "مطابق للدروينج")
            LegendDot(mismatchColor, "مختلف عن الدروينج")
            LegendDot(dimColor, "دروينج فقط")
        }
    }

    if (showExport) {
        CountingExportDialog(vm = vm, onDismiss = { showExport = false })
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ColorDot(color)
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

// ---------------------------------------------------------------- التصدير

@Composable
private fun CountingExportDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val barCounts by vm.barCounts.collectAsStateWithLifecycle()
    val names by vm.names.collectAsStateWithLifecycle()

    var asPdf by remember { mutableStateOf(true) }
    var source by remember { mutableStateOf(BarCountEntity.SOURCE_SITE) }

    fun buildConfig(): PlanExporter.CountingConfig {
        val byElement = barCounts.groupBy { it.elementId }
        val labels = byElement.mapNotNull { (elementId, entries) ->
            val text = when (source) {
                BarCountEntity.SOURCE_SITE -> formatEntries(siteOf(entries))
                BarCountEntity.SOURCE_DRAWING -> formatEntries(drawingOf(entries))
                else -> {
                    val s = formatEntries(siteOf(entries))
                    val d = formatEntries(drawingOf(entries))
                    when {
                        s.isEmpty() -> d
                        d.isEmpty() -> s
                        else -> "$s / $d"
                    }
                }
            }
            if (text.isEmpty()) null else elementId to text
        }.toMap()

        val allSelected = when (source) {
            BarCountEntity.SOURCE_SITE -> siteOf(barCounts)
            BarCountEntity.SOURCE_DRAWING -> drawingOf(barCounts)
            else -> barCounts
        }
        val totals = totalsByDiameter(allSelected)
            .entries.joinToString("   ") { (dia, count) -> "Ø${dia}mm: $count" }
        val sourceTitle = when (source) {
            BarCountEntity.SOURCE_SITE -> "Site count"
            BarCountEntity.SOURCE_DRAWING -> "Shop drawing count"
            else -> "Site / Shop drawing"
        }
        return PlanExporter.CountingConfig(
            planData = vm.planData,
            names = names,
            labels = labels,
            title = "Core Wall Counting — $sourceTitle",
            totalsLine = if (totals.isEmpty()) "" else "Totals:   $totals"
        )
    }

    fun runExport(uri: android.net.Uri?, pdf: Boolean) {
        if (uri == null) return
        val cfg = buildConfig()
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        if (pdf) PlanExporter.writeCountingPdf(os, cfg)
                        else PlanExporter.writeCountingPng(os, cfg)
                    } ?: error("مقدرناش نفتح الملف")
                }
            }
            Toast.makeText(
                context,
                if (result.isSuccess) "تم التصدير ✓" else "فشل التصدير: ${result.exceptionOrNull()?.message}",
                Toast.LENGTH_LONG
            ).show()
        }
        onDismiss()
    }

    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { runExport(it, pdf = true) }
    val pngLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png")
    ) { runExport(it, pdf = false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تصدير الدروينج بالأعداد") },
        text = {
            Column {
                Text("الأعداد:", style = MaterialTheme.typography.labelMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = source == BarCountEntity.SOURCE_SITE,
                        onClick = { source = BarCountEntity.SOURCE_SITE }
                    )
                    Text("الموقع")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = source == BarCountEntity.SOURCE_DRAWING,
                        onClick = { source = BarCountEntity.SOURCE_DRAWING }
                    )
                    Text("الشوب دروينج")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = source == "BOTH",
                        onClick = { source = "BOTH" }
                    )
                    Text("الاتنين (موقع / دروينج)")
                }
                Spacer(Modifier.padding(4.dp))
                Text("الصيغة:", style = MaterialTheme.typography.labelMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = asPdf, onClick = { asPdf = true })
                    Text("PDF")
                    Spacer(Modifier.width(16.dp))
                    RadioButton(selected = !asPdf, onClick = { asPdf = false })
                    Text("PNG")
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val base = "corewall-counting-${source.lowercase()}"
                if (asPdf) pdfLauncher.launch("$base.pdf") else pngLauncher.launch("$base.png")
            }) { Text("تصدير") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
