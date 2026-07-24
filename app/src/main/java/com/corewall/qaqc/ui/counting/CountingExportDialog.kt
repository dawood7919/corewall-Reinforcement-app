package com.corewall.qaqc.ui.counting

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.data.db.BarCountEntity
import com.corewall.qaqc.export.PlanExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** تصدير دروينج العدّ — للدور الحالي بس (كل دور معزول). */
@Composable
fun CountingExportDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val barCounts by vm.barCounts.collectAsStateWithLifecycle()
    val names by vm.names.collectAsStateWithLifecycle()
    val level by vm.currentLevel.collectAsStateWithLifecycle()

    var asPdf by remember { mutableStateOf(true) }
    var source by remember { mutableStateOf(BarCountEntity.SOURCE_SITE) }

    fun buildConfig(): PlanExporter.CountingConfig {
        val levelCounts = barCounts.filter { it.level == level }
        val byElement = levelCounts.groupBy { it.elementId }
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
            BarCountEntity.SOURCE_SITE -> siteOf(levelCounts)
            BarCountEntity.SOURCE_DRAWING -> drawingOf(levelCounts)
            else -> levelCounts
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
            title = "Core Wall Counting — Level $level — $sourceTitle",
            totalsLine = if (totals.isEmpty()) "" else "Level $level totals:   $totals"
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
        title = { Text("تصدير عدّ دور $level") },
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
                val base = "corewall-counting-$level-${source.lowercase()}"
                if (asPdf) pdfLauncher.launch("$base.pdf") else pngLauncher.launch("$base.png")
            }) { Text("تصدير") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
