package com.corewall.qaqc.ui.ai

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.data.db.PromptEntity
import com.corewall.qaqc.ui.design.CwBanner
import com.corewall.qaqc.ui.design.CwButton
import com.corewall.qaqc.ui.design.CwButtonStyle
import com.corewall.qaqc.ui.design.CwCard
import com.corewall.qaqc.ui.design.CwEmptyState
import com.corewall.qaqc.ui.design.CwField
import com.corewall.qaqc.ui.design.CwIconButton
import com.corewall.qaqc.ui.design.CwSectionHeader
import com.corewall.qaqc.ui.design.CwStatusBadge
import com.corewall.qaqc.ui.design.CwTone
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Radius
import com.corewall.qaqc.ui.design.Space

/**
 * مكتبة البرومبت — تعليمات قراية المستندات، باسم.
 *
 * المشكلة اللي بتحلّها: التطبيق كان بيقرا كل مستند بنفس التعليمات العامة،
 * فجدول حديد (BBS) وطلب فحص وكشف تسليح كلهم بيتعاملوا بنفس الطريقة —
 * والنتيجة تحليل عام وغالباً غلط. المهندس هو اللي عارف الملف ده بيتقري
 * إزاي، فالمعرفة دي مكانها عنده مش متحطوطة جوّه الكود.
 *
 * البرومبت هنا **بيتضاف** على عقد الاستخراج مش بيستبدله: شكل الرد بيفضل
 * بتاع التطبيق (عشان الحقائق تتخزّن وتتبحث)، واللي بيتغيّر هو طريقة قراية
 * المستند نفسه.
 */
@Composable
fun PromptsScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val c = LocalCwColors.current
    val context = LocalContext.current
    val prompts by vm.prompts.collectAsStateWithLifecycle()

    var editingId by rememberSaveable { mutableLongStateOf(-1L) }
    var draftName by rememberSaveable { mutableStateOf("") }
    var draftBody by rememberSaveable { mutableStateOf("") }
    var confirmDelete by rememberSaveable { mutableStateOf<Long?>(null) }

    fun startNew() { editingId = 0L; draftName = ""; draftBody = "" }
    fun startEdit(p: PromptEntity) { editingId = p.id; draftName = p.name; draftBody = p.body }
    fun close() { editingId = -1L; draftName = ""; draftBody = "" }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Space.screen, end = Space.screen,
            top = Space.md, bottom = Space.bottomInset
        ),
        verticalArrangement = Arrangement.spacedBy(Space.stack)
    ) {
        item(key = "why") {
            CwBanner(
                title = "ليه البرومبت بيفرق",
                detail = "البرومبت بيقول للموديل المستند ده يتقري إزاي — أعمدة جدول " +
                    "الحديد، شكل كود البار مارك، إيه اللي يتجاهله. لما تحلّل ملف، " +
                    "هتختار من القايمة دي، والاختيار بيتحفظ على الملف فإعادة التحليل " +
                    "بترجع بنفسه.",
                tone = CwTone.Info
            )
        }

        if (editingId >= 0L) {
            item(key = "editor") {
                CwCard {
                    Text(
                        if (editingId == 0L) "برومبت جديد" else "تعديل البرومبت",
                        style = MaterialTheme.typography.titleSmall,
                        color = c.textPrimary
                    )
                    Spacer(Modifier.height(Space.md))
                    CwField(
                        value = draftName,
                        onValueChange = { draftName = it },
                        label = "الاسم",
                        placeholder = "BBS",
                        helper = "الاسم ده اللي هيظهر في قايمة الاختيار وقت التحليل."
                    )
                    Spacer(Modifier.height(Space.md))
                    CwField(
                        value = draftBody,
                        onValueChange = { draftBody = it },
                        label = "التعليمات",
                        placeholder = SAMPLE,
                        helper = "اكتب المستند ده بيتقري إزاي. شكل الرد التطبيق بيتكفّل بيه.",
                        singleLine = false,
                        minLines = 6
                    )
                    Spacer(Modifier.height(Space.md))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        CwButton(
                            "حفظ",
                            {
                                vm.savePrompt(editingId, draftName, draftBody) { msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    if (msg.startsWith("اتحفظ") || msg.startsWith("اتعدّل")) close()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        CwButton(
                            "إلغاء",
                            { close() },
                            style = CwButtonStyle.Ghost,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        item(key = "list-header") {
            CwSectionHeader(
                "البرومبتات المحفوظة",
                count = prompts.size,
                action = {
                    if (editingId < 0L) {
                        CwIconButton(Icons.Filled.Add, "برومبت جديد", { startNew() })
                    }
                }
            )
        }

        if (prompts.isEmpty()) {
            item(key = "empty") {
                CwEmptyState(
                    icon = Icons.Filled.AutoAwesome,
                    title = "مفيش برومبت لسه",
                    detail = "اعمل واحد باسم نوع المستند — BBS، رسمة تسليح، طلب فحص — " +
                        "واكتب فيه الملف ده بيتقري إزاي.",
                    action = { CwButton("ابدأ بواحد", { startNew() }, icon = Icons.Filled.Add) }
                )
            }
        } else {
            items(prompts, key = { it.id }) { p ->
                PromptCard(
                    prompt = p,
                    onEdit = { startEdit(p) },
                    onDelete = { confirmDelete = p.id }
                )
            }
        }
    }

    val pendingDelete = confirmDelete
    if (pendingDelete != null) {
        val name = prompts.firstOrNull { it.id == pendingDelete }?.name.orEmpty()
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            shape = Radius.shapeLg,
            containerColor = c.surface,
            title = { Text("تمسح \"$name\"؟", color = c.textPrimary) },
            text = {
                Text(
                    "الملفات اللي اتحلّلت بيه هتفضل زي ما هي، بس إعادة تحليلها " +
                        "هترجع للتحليل العام.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textSecondary
                )
            },
            confirmButton = {
                CwButton("امسح", {
                    vm.deletePrompt(pendingDelete)
                    if (editingId == pendingDelete) close()
                    confirmDelete = null
                }, style = CwButtonStyle.Danger)
            },
            dismissButton = { CwButton("رجوع", { confirmDelete = null }, style = CwButtonStyle.Ghost) }
        )
    }
}

@Composable
private fun PromptCard(prompt: PromptEntity, onEdit: () -> Unit, onDelete: () -> Unit) {
    val c = LocalCwColors.current
    CwCard(onClick = onEdit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(prompt.name, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                Spacer(Modifier.height(Space.xxs))
                Text(
                    prompt.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textTertiary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            CwIconButton(Icons.Filled.Edit, "تعديل", onEdit)
            CwIconButton(Icons.Filled.Delete, "مسح", onDelete, tint = c.danger.fg)
        }
        if (prompt.usageCount > 0) {
            Spacer(Modifier.height(Space.sm))
            Box {
                CwStatusBadge("اتستخدم ${prompt.usageCount} مرة", CwTone.Neutral, compact = true)
            }
        }
    }
}

private const val SAMPLE =
    "ده جدول حديد (BBS). الأعمدة: Bar Mark، القطر، العدد، الطول، الشكل، الوزن.\n" +
    "استخرج كل صف كـBAR_MARK ومعاه العدد والقطر والطول.\n" +
    "تجاهل صفوف المجاميع وعناوين الصفحات."
