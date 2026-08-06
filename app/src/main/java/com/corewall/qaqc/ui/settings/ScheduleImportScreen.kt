package com.corewall.qaqc.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.TableRows
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.data.ScheduleImport
import com.corewall.qaqc.data.db.ImportedMarkEntity
import com.corewall.qaqc.ui.design.CwBanner
import com.corewall.qaqc.ui.design.CwButton
import com.corewall.qaqc.ui.design.CwButtonStyle
import com.corewall.qaqc.ui.design.CwCard
import com.corewall.qaqc.ui.design.CwCardStyle
import com.corewall.qaqc.ui.design.CwEmptyState
import com.corewall.qaqc.ui.design.CwSectionHeader
import com.corewall.qaqc.ui.design.CwStatusBadge
import com.corewall.qaqc.ui.design.CwText
import com.corewall.qaqc.ui.design.CwTone
import com.corewall.qaqc.ui.design.CwIconButton
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Radius
import com.corewall.qaqc.ui.design.Sizes
import com.corewall.qaqc.ui.design.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * استيراد أكواد الجدول — الكمرات الداخلية وغيرها.
 *
 * جدول المكتب جوّه التطبيق للقراية بس، وده مقصود: هو المرجع، وما ينفعش
 * يتكتب فوقه من الموقع. الشاشة دي بتضيف طبقة **فوقه**: أكواد المهندس
 * بتتحفظ لوحدها، بتتدمج مع الجدول وقت العرض، وبتتمسح في أي وقت من غير ما
 * الأصل يتأثر.
 *
 * أهم حاجة هنا هي **رسالة الرفض**. لو كود دور غلط عدّى بصمت، الكمرة ما
 * هتظهرش في أي دور والمهندس هيفتكر إن الاستيراد نجح — وده أسوأ من فشل
 * صريح. فكل سطر بيترفض بيتعرض برقمه وسببه.
 */
@Composable
fun ScheduleImportScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val c = LocalCwColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val imported by vm.importedMarks.collectAsStateWithLifecycle()
    val officeMarks = remember { vm.officeMarks() }

    var outcome by remember { mutableStateOf<ScheduleImport.Outcome?>(null) }
    var confirmClearAll by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf<String?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) vm.importMarks(uri) { outcome = it } }

    val templateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) scope.launch {
            val ok = runCatching {
                val body = vm.importTemplate()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use {
                        it.write(body.toByteArray(Charsets.UTF_8))
                    } ?: error("مقدرناش نفتح الملف")
                }
            }.isSuccess
            Toast.makeText(
                context,
                if (ok) "اتحفظ القالب — املاه في الإكسل واستورده" else "فشل حفظ القالب",
                Toast.LENGTH_LONG
            ).show()
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
        item(key = "intro") {
            CwBanner(
                title = "أكوادك فوق جدول المكتب",
                detail = "جدول المكتب ما بيتغيّرش. اللي بتستورده هنا بيتحفظ في طبقة " +
                    "لوحدها وبيظهر مع الجدول في كل الشاشات — وتقدر تمسحه في أي وقت " +
                    "وترجع للأصل.",
                tone = CwTone.Info
            )
        }

        item(key = "actions") {
            CwCard {
                Text("استيراد", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                Spacer(Modifier.height(Space.xs))
                Text(
                    "CSV من الإكسل، أو JSON بنفس شكل ملف المكتب. لو أول مرة، " +
                        "نزّل القالب — فيه أكواد أدوار مشروعك جاهزة.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textTertiary
                )
                Spacer(Modifier.height(Space.md))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    CwButton(
                        "استورد ملف",
                        {
                            importLauncher.launch(
                                arrayOf("text/csv", "text/comma-separated-values",
                                    "application/json", "text/plain", "*/*")
                            )
                        },
                        icon = Icons.Filled.Upload,
                        modifier = Modifier.weight(1f)
                    )
                    CwButton(
                        "نزّل القالب",
                        { templateLauncher.launch("corewall-beams-template.csv") },
                        style = CwButtonStyle.Secondary,
                        icon = Icons.Filled.Download,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item(key = "format") { CwSectionHeader("شكل الملف") }
        item(key = "format-card") {
            CwCard {
                Text(
                    "الأعمدة (سطر العناوين مطلوب، والترتيب مش مهم):",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary
                )
                Spacer(Modifier.height(Space.sm))
                Text(
                    "mark, from, to, w, d, B1, B2, B3, T1, T2, T3, side, links, note",
                    style = CwText.codeSmall,
                    color = c.textPrimary
                )
                Spacer(Modifier.height(Space.md))
                FormatNote("mark", "كود الكمرة — CB-01 مثلاً. نفس الكود تاني بيستبدل القديم.")
                FormatNote("from / to", "كود دور من المشروع. مدى الكمرة **شامل** النهاية، " +
                    "وسيب to فاضية لو الكمرة في دور واحد.")
                FormatNote("w / d", "العرض والعمق بالمللي.")
                FormatNote("B1..B3 / T1..T3", "طبقات التسليح السفلي والعلوي — 5T20 مثلاً.")
            }
        }

        item(key = "list-header") {
            CwSectionHeader(
                "المستورد",
                count = imported.size,
                action = {
                    if (imported.isNotEmpty()) {
                        CwIconButton(
                            Icons.Filled.DeleteSweep, "امسح الكل",
                            { confirmClearAll = true }, tint = c.danger.fg
                        )
                    }
                }
            )
        }

        if (imported.isEmpty()) {
            item(key = "empty") {
                CwEmptyState(
                    icon = Icons.Filled.TableRows,
                    title = "مفيش أكواد مستوردة",
                    detail = "الجدول المعروض دلوقتي هو جدول المكتب زي ما هو."
                )
            }
        } else {
            items(imported, key = { it.mark }) { m ->
                ImportedMarkCard(
                    mark = m,
                    overridesOffice = m.mark in officeMarks,
                    onDelete = { confirmDelete = m.mark }
                )
            }
        }
    }

    // ── نتيجة الاستيراد
    val result = outcome
    if (result != null) {
        AlertDialog(
            onDismissRequest = { outcome = null },
            shape = Radius.shapeLg,
            containerColor = c.surface,
            title = {
                Text(
                    if (result.fatal != null || result.marks.isEmpty()) "الاستيراد ما تمّش"
                    else "تم الاستيراد",
                    color = c.textPrimary
                )
            },
            text = {
                Column(Modifier.heightIn(max = Sizes.sheetGridMax).verticalScroll(rememberScrollState())) {
                    Text(
                        result.message(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textPrimary
                    )
                    if (result.overrides.isNotEmpty()) {
                        Spacer(Modifier.height(Space.md))
                        CwBanner(
                            title = "بيغطّي على جدول المكتب",
                            detail = result.overrides.joinToString("، ") +
                                " — الأكواد دي موجودة في جدول المكتب، واللي استوردته " +
                                "هو اللي هيتعرض. امسحه من هنا لو مش ده اللي انت عايزه.",
                            tone = CwTone.Warning
                        )
                    }
                    if (result.rejected.isNotEmpty()) {
                        Spacer(Modifier.height(Space.md))
                        Text(
                            "أسطر اترفضت (${result.rejected.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = c.danger.fg
                        )
                        Spacer(Modifier.height(Space.xs))
                        result.rejected.take(30).forEach {
                            Text(
                                "• $it",
                                style = MaterialTheme.typography.bodySmall,
                                color = c.textSecondary
                            )
                        }
                        if (result.rejected.size > 30) {
                            Text(
                                "… و${result.rejected.size - 30} كمان",
                                style = MaterialTheme.typography.bodySmall,
                                color = c.textTertiary
                            )
                        }
                    }
                }
            },
            confirmButton = { CwButton("تمام", { outcome = null }) }
        )
    }

    if (confirmClearAll) {
        ConfirmDialog(
            title = "تمسح كل الأكواد المستوردة؟",
            body = "الجدول هيرجع لجدول المكتب زي ما هو. تسميات العناصر على المسقط " +
                "هتفضل، بس الأكواد المستوردة مش هيبقى ليها بيانات تسليح.",
            onConfirm = { vm.deleteAllImportedMarks(); confirmClearAll = false },
            onDismiss = { confirmClearAll = false }
        )
    }

    val one = confirmDelete
    if (one != null) {
        ConfirmDialog(
            title = "تمسح \"$one\"؟",
            body = if (one in officeMarks)
                "الكود ده موجود في جدول المكتب كمان — بعد المسح هيرجع لبيانات المكتب."
            else "الكود ده هيختفي من الجدول خالص.",
            onConfirm = { vm.deleteImportedMark(one); confirmDelete = null },
            onDismiss = { confirmDelete = null }
        )
    }
}

@Composable
private fun FormatNote(key: String, detail: String) {
    val c = LocalCwColors.current
    Column(Modifier.fillMaxWidth()) {
        Text(key, style = CwText.codeSmall, color = c.accent)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
        Spacer(Modifier.height(Space.sm))
    }
}

@Composable
private fun ImportedMarkCard(
    mark: ImportedMarkEntity,
    overridesOffice: Boolean,
    onDelete: () -> Unit
) {
    val c = LocalCwColors.current
    CwCard(
        style = if (overridesOffice) CwCardStyle.Accent else CwCardStyle.Plain,
        accent = c.warning.fg
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.sm)
                ) {
                    Text(mark.mark, style = CwText.code, color = c.textPrimary)
                    CwStatusBadge(
                        if (mark.kind == ImportedMarkEntity.BEAM) "كمرة" else "حائط",
                        if (mark.kind == ImportedMarkEntity.BEAM) CwTone.Pending else CwTone.Info,
                        compact = true
                    )
                    if (overridesOffice) {
                        CwStatusBadge("بيغطّي على المكتب", CwTone.Warning, compact = true)
                    }
                }
                Spacer(Modifier.height(Space.xxs))
                Text(
                    "${mark.rowCount} صف · من ${mark.source}",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textTertiary
                )
            }
            CwIconButton(Icons.Filled.Delete, "امسح ${mark.mark}", onDelete, tint = c.danger.fg)
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalCwColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = Radius.shapeLg,
        containerColor = c.surface,
        title = { Text(title, color = c.textPrimary) },
        text = { Text(body, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary) },
        confirmButton = { CwButton("امسح", onConfirm, style = CwButtonStyle.Danger) },
        dismissButton = { CwButton("رجوع", onDismiss, style = CwButtonStyle.Ghost) }
    )
}
