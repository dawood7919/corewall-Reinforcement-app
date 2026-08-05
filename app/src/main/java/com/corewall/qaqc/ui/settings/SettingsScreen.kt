package com.corewall.qaqc.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.data.AppTheme
import com.corewall.qaqc.ui.design.CwButton
import com.corewall.qaqc.ui.design.CwButtonStyle
import com.corewall.qaqc.ui.design.CwCard
import com.corewall.qaqc.ui.design.CwChip
import com.corewall.qaqc.ui.design.CwLeadingIcon
import com.corewall.qaqc.ui.design.CwListItem
import com.corewall.qaqc.ui.design.CwSectionHeader
import com.corewall.qaqc.ui.design.CwSwitchRow
import com.corewall.qaqc.ui.design.CwTone
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Space
import com.corewall.qaqc.ui.nav.Dest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * الإعدادات — **شاشة واحدة**.
 *
 * قبل كده كان فيه شاشتين مختلفتين اسمهم "الإعدادات": واحدة تبويب في الشريط
 * السفلي وواحدة في الدرج، وكانوا كودين مختلفين خالص. المستخدم مكانش يقدر
 * يعرف أي واحدة فيها الإعداد اللي بيدوّر عليه.
 *
 * كمان: الشاشة القديمة كانت مليانة صفوف شكلها شغّال وهي مش عاملة حاجة —
 * "الإشعارات الفورية" و"المزامنة التلقائية" و"قفل التطبيق" كانوا مربوطين
 * بحالة محلية بتضيع أول ما تقفل الشاشة، و٧ صفوف `onClick = {}` فاضية.
 * كنترول بيدّعي إنه شغّال أسوأ من كنترول مش موجود، فاتشالوا.
 */
@Composable
fun SettingsScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val c = LocalCwColors.current
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
                if (result.isSuccess) "اتصدّرت النسخة الاحتياطية ✓"
                else "فشل التصدير: ${result.exceptionOrNull()?.message}",
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
                    context.contentResolver.openInputStream(uri)
                        ?.use { it.readBytes().toString(Charsets.UTF_8) }
                        ?: error("مقدرناش نقرا الملف")
                }
                vm.repo.importBackupJson(content).getOrThrow()
            }.fold(onSuccess = { it }, onFailure = { "فشل الاستيراد: ${it.message}" })
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Space.screen, end = Space.screen,
            top = Space.md, bottom = Space.bottomInset
        ),
        verticalArrangement = Arrangement.spacedBy(Space.stack)
    ) {
        item(key = "appearance-header") { CwSectionHeader("المظهر") }
        item(key = "theme") {
            CwCard {
                Text("الثيم", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                Spacer(Modifier.height(Space.md))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    AppTheme.entries.forEach { theme ->
                        CwChip(
                            label = theme.label,
                            selected = settings.theme == theme,
                            onClick = { vm.updateSettings { it.copy(theme = theme) } }
                        )
                    }
                }
            }
        }

        item(key = "plan-header") { CwSectionHeader("عرض المسقط") }
        item(key = "plan") {
            CwCard(contentPadding = PaddingValues(vertical = Space.xs)) {
                CwSwitchRow(
                    title = "إظهار الأكواد على المسقط",
                    subtitle = "كود كل عنصر يبان جوّه الشكل",
                    checked = settings.showNames,
                    onCheckedChange = { v -> vm.updateSettings { it.copy(showNames = v) } },
                    leading = { CwLeadingIcon(Icons.Filled.Label, tone = CwTone.Info) }
                )
                CwSwitchRow(
                    title = "تلوين العناصر بحالة الفحص",
                    subtitle = "لمّا يتقفل، العناصر بتتلوّن بفئتها (حائط/كمرة)",
                    checked = settings.showStatuses,
                    onCheckedChange = { v -> vm.updateSettings { it.copy(showStatuses = v) } },
                    leading = { CwLeadingIcon(Icons.Filled.Palette, tone = CwTone.Info) }
                )
            }
        }

        item(key = "assistant-header") { CwSectionHeader("المساعد") }
        item(key = "assistant") {
            CwCard(contentPadding = PaddingValues(vertical = Space.xs)) {
                CwListItem(
                    title = "إعدادات المساعد الذكي",
                    subtitle = "المزوّد والموديل ومفتاح الـAPI",
                    leading = { CwLeadingIcon(Icons.Filled.AutoAwesome, tone = CwTone.Info) },
                    onClick = { vm.go(Dest.AiSettings) }
                )
            }
        }

        item(key = "data-header") { CwSectionHeader("البيانات") }
        item(key = "backup") {
            CwCard {
                Text("نسخة احتياطية", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                Spacer(Modifier.height(Space.xs))
                Text(
                    "كل البيانات متخزّنة محلياً على الجهاز وبتفضل بعد قفل التطبيق. " +
                        "التصدير هنا لنسخة JSON للنقل أو للأمان.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textTertiary
                )
                Spacer(Modifier.height(Space.md))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    CwButton(
                        "تصدير",
                        {
                            val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.ENGLISH).format(Date())
                            exportLauncher.launch("corewall-backup-$stamp.json")
                        },
                        icon = Icons.Filled.Download,
                        modifier = Modifier.weight(1f)
                    )
                    CwButton(
                        "استيراد",
                        { importLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                        style = CwButtonStyle.Secondary,
                        icon = Icons.Filled.Upload,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        item(key = "system") {
            CwCard(contentPadding = PaddingValues(vertical = Space.xs)) {
                CwListItem(
                    title = "مزامنة البيانات",
                    subtitle = "حالة المزامنة وآخر مرّة اتعملت",
                    leading = { CwLeadingIcon(Icons.Filled.CloudSync, tone = CwTone.Info) },
                    onClick = { vm.go(Dest.Sync) }
                )
                CwListItem(
                    title = "عن التطبيق",
                    subtitle = "الإصدار والترخيص",
                    leading = { CwLeadingIcon(Icons.Filled.Info, tone = CwTone.Neutral) },
                    onClick = { vm.go(Dest.About) }
                )
            }
        }
    }
}
