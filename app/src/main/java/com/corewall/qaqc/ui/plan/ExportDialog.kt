package com.corewall.qaqc.ui.plan

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.corewall.qaqc.export.PlanExporter
import com.corewall.qaqc.ui.LevelPickerDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ExportDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val level by vm.currentLevel.collectAsStateWithLifecycle()
    val schedule by vm.schedule.collectAsStateWithLifecycle()
    val names by vm.names.collectAsStateWithLifecycle()
    val inspections by vm.inspections.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()

    var asPdf by remember { mutableStateOf(true) }
    var compareMode by remember { mutableStateOf(false) }
    var compareLevel by remember { mutableStateOf<String?>(null) }
    var showLevelPicker by remember { mutableStateOf(false) }

    fun buildConfig() = PlanExporter.Config(
        planData = vm.planData,
        schedule = schedule,
        logic = vm.logic,
        names = names,
        inspections = inspections,
        level = level,
        compareWith = if (compareMode) compareLevel else null,
        showStatuses = settings.showStatuses
    )

    fun runExport(uri: android.net.Uri?, pdf: Boolean) {
        if (uri == null) return
        val cfg = buildConfig()
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        if (pdf) PlanExporter.writePdf(os, cfg) else PlanExporter.writePng(os, cfg)
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
        title = { Text("تصدير المسقط") },
        text = {
            Column {
                Text("الصيغة:", style = MaterialTheme.typography.labelMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = asPdf, onClick = { asPdf = true })
                    Text("PDF")
                    Spacer(Modifier.padding(horizontal = 8.dp))
                    RadioButton(selected = !asPdf, onClick = { asPdf = false })
                    Text("PNG")
                }
                Spacer(Modifier.height(8.dp))
                Text("المحتوى:", style = MaterialTheme.typography.labelMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = !compareMode, onClick = { compareMode = false })
                    Text("الدور الحالي ($level)")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = compareMode, onClick = { compareMode = true })
                    Text("مقارنة بين دورين")
                }
                if (compareMode) {
                    OutlinedButton(onClick = { showLevelPicker = true }) {
                        Text("قارن $level مع: ${compareLevel ?: "اختار دور"}")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !compareMode || compareLevel != null,
                onClick = {
                    val base = if (compareMode) "corewall-$level-vs-$compareLevel" else "corewall-$level"
                    if (asPdf) pdfLauncher.launch("$base.pdf") else pngLauncher.launch("$base.png")
                }
            ) { Text("تصدير") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )

    if (showLevelPicker) {
        LevelPickerDialog(
            levels = vm.levels,
            current = compareLevel ?: level,
            onPick = { compareLevel = it; showLevelPicker = false },
            onDismiss = { showLevelPicker = false },
            title = "اختار دور المقارنة"
        )
    }
}
