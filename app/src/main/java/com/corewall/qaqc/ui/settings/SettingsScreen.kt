package com.corewall.qaqc.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.data.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) scope.launch {
            val result = runCatching {
                val json = vm.repo.exportBackupJson()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(json.toByteArray(Charsets.UTF_8))
                    } ?: error("مقدرناش نفتح الملف")
                }
            }
            Toast.makeText(
                context,
                if (result.isSuccess) "تم تصدير النسخة الاحتياطية ✓" else "فشل التصدير: ${result.exceptionOrNull()?.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) scope.launch {
            val message = runCatching {
                val content = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                        ?: error("مقدرناش نقرا الملف")
                }
                vm.repo.importBackupJson(content).getOrThrow()
            }.fold(onSuccess = { it }, onFailure = { "فشل الاستيراد: ${it.message}" })
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("الثيم", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        AppTheme.entries.forEach { theme ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = settings.theme == theme,
                    onClick = { vm.updateSettings { it.copy(theme = theme) } }
                )
                Text(theme.label)
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("العرض", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = settings.showNames,
                onCheckedChange = { checked -> vm.updateSettings { it.copy(showNames = checked) } }
            )
            Spacer(Modifier.width(8.dp))
            Text("إظهار الأسماء على المسقط")
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = settings.showStatuses,
                onCheckedChange = { checked -> vm.updateSettings { it.copy(showStatuses = checked) } }
            )
            Spacer(Modifier.width(8.dp))
            Text("تلوين العناصر بحالة الفحص")
        }

        Spacer(Modifier.height(16.dp))
        Text("النسخة الاحتياطية", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    "كل البيانات (الأسماء، الحالات، الكومنتات، تعديلات القيم) متخزنة تلقائي في " +
                        "قاعدة بيانات محلية (Room) وبتفضل موجودة بعد قفل التطبيق. " +
                        "التصدير هنا لنسخة JSON احتياطية أو للنقل لموبايل تاني.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Row {
                    Button(onClick = {
                        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.ENGLISH).format(Date())
                        exportLauncher.launch("corewall-backup-$stamp.json")
                    }) { Text("تصدير JSON") }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = {
                        importLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                    }) { Text("استيراد JSON") }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "Core Wall QA/QC — نسخة أندرويد Native (Kotlin + Jetpack Compose + Room)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
