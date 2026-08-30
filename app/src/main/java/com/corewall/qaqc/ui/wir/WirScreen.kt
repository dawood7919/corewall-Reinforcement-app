package com.corewall.qaqc.ui.wir

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.data.db.WirEntity
import com.corewall.qaqc.ui.design.CwCard
import com.corewall.qaqc.ui.design.CwChip
import com.corewall.qaqc.ui.design.CwEmptyState
import com.corewall.qaqc.ui.design.CwIconButton
import com.corewall.qaqc.ui.design.CwStatusBadge
import com.corewall.qaqc.ui.design.CwText
import com.corewall.qaqc.ui.design.CwTone
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Radius
import com.corewall.qaqc.ui.design.Space
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * طلبات فحص الأعمال (WIR) للدور الشغّال.
 *
 * الطلب نفسه ملف PDF عادي على القرص، فالضغط عليه بيفتح **نفس العارض**
 * بكل أدواته — هايلايت، سحابة، سهم، نص، قياس، وتصدير نسخة بتعليقات PDF
 * حقيقية. الشاشة دي سجل ومدخل، مش محرّر تاني.
 */
@Composable
fun WirScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val wirs by vm.wirs.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf<WirEntity?>(null) }
    var deleting by remember { mutableStateOf<WirEntity?>(null) }

    if (wirs.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            CwEmptyState(
                icon = Icons.Filled.FactCheck,
                title = "مفيش طلبات فحص للدور ده",
                detail = "افتح أي رسمة، روح للصفحة اللي عايز تتفحص، ومن قايمة " +
                    "الخيارات (⋮) اختار «أرسل الصفحة لـWIR» واكتب اسم الطلب. " +
                    "الصفحة بتتضاف في آخر ملف الطلب، وتفتحه من هنا تأشّر عليه " +
                    "زي أي PDF."
            )
        }
        return
    }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Space.screen, end = Space.screen,
            top = Space.sm, bottom = Space.bottomInset
        ),
        verticalArrangement = Arrangement.spacedBy(Space.sm)
    ) {
        items(wirs, key = { it.id }) { wir ->
            WirCard(
                wir = wir,
                onOpen = { vm.openPdf(wir.filePath) },
                onStatus = { vm.setWirStatus(wir, it) },
                onRename = { renaming = wir },
                onShare = {
                    vm.shareWir(wir) { message ->
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                },
                onDelete = { deleting = wir },
                sources = { WirSources(vm, wir.id) }
            )
        }
    }

    renaming?.let { target ->
        RenameWirDialog(
            current = target.name,
            onConfirm = { vm.renameWir(target, it); renaming = null },
            onDismiss = { renaming = null }
        )
    }

    deleting?.let { target ->
        ConfirmDeleteWirDialog(
            name = target.name,
            pageCount = target.pageCount,
            onConfirm = { vm.deleteWir(target); deleting = null },
            onDismiss = { deleting = null }
        )
    }
}

private val wirDateFmt = SimpleDateFormat("dd/MM/yyyy  ·  hh:mm a", Locale.ENGLISH)

private val STATUSES = listOf(
    "OPEN" to "مفتوح",
    "SUBMITTED" to "متقدّم",
    "APPROVED" to "مقبول",
    "REJECTED" to "مرفوض"
)

private fun toneFor(status: String): CwTone = when (status) {
    "APPROVED" -> CwTone.Success
    "REJECTED" -> CwTone.Danger
    "SUBMITTED" -> CwTone.Info
    else -> CwTone.Pending
}

private fun labelFor(status: String): String =
    STATUSES.firstOrNull { it.first == status }?.second ?: "مفتوح"

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WirCard(
    wir: WirEntity,
    onOpen: () -> Unit,
    onStatus: (String) -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    sources: @Composable () -> Unit
) {
    val c = LocalCwColors.current
    var menuOpen by remember { mutableStateOf(false) }
    var sourcesOpen by remember { mutableStateOf(false) }
    val missing = remember(wir.filePath, wir.updatedAt) { !File(wir.filePath).exists() }
    val date = remember(wir.updatedAt) { wirDateFmt.format(Date(wir.updatedAt)) }

    CwCard(onClick = if (missing) null else onOpen, contentPadding = PaddingValues(Space.lg)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    wir.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = c.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(Space.xxs))
                Text(
                    if (missing) "الملف مش موجود على القرص"
                    else "${wir.pageCount} صفحة · $date",
                    style = CwText.codeSmall,
                    color = if (missing) c.danger.fg else c.textTertiary,
                    maxLines = 1
                )
            }
            CwStatusBadge(labelFor(wir.status), toneFor(wir.status), compact = true)
            Box {
                CwIconButton(Icons.Filled.MoreVert, "خيارات الطلب", { menuOpen = true })
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("غيّر الاسم") },
                        leadingIcon = {
                            androidx.compose.material3.Icon(
                                Icons.Filled.Edit, contentDescription = null
                            )
                        },
                        onClick = { menuOpen = false; onRename() }
                    )
                    DropdownMenuItem(
                        text = { Text("شارك الملف") },
                        leadingIcon = {
                            androidx.compose.material3.Icon(
                                Icons.Filled.Share, contentDescription = null
                            )
                        },
                        onClick = { menuOpen = false; onShare() }
                    )
                    DropdownMenuItem(
                        text = { Text("امسح الطلب") },
                        leadingIcon = {
                            androidx.compose.material3.Icon(
                                Icons.Filled.Delete, contentDescription = null,
                                tint = c.danger.fg
                            )
                        },
                        onClick = { menuOpen = false; onDelete() }
                    )
                }
            }
        }

        Spacer(Modifier.height(Space.md))
        // أربع شيبات مابيوصلوش عرض كارت على تليفون — يلتفّوا بدل ما
        // يتقصّوا عند الحافة.
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
            verticalArrangement = Arrangement.spacedBy(Space.xs)
        ) {
            STATUSES.forEach { (value, label) ->
                CwChip(
                    label = label,
                    selected = wir.status == value,
                    onClick = { onStatus(value) }
                )
            }
        }

        if (wir.pageCount > 0) {
            Spacer(Modifier.height(Space.sm))
            Text(
                if (sourcesOpen) "إخفاء مصادر الصفحات" else "الصفحات دي جاية منين؟",
                style = MaterialTheme.typography.labelMedium,
                color = c.accent,
                modifier = Modifier
                    .clip(Radius.shapeSm)
                    .clickable { sourcesOpen = !sourcesOpen }
                    .padding(vertical = Space.xs, horizontal = Space.xs)
            )
            if (sourcesOpen) sources()
        }
    }
}

/**
 * مصادر صفحات الطلب.
 *
 * أول سؤال بيتسأل على صفحة متأشّر عليها هو "دي من أنهي لوحة وأنهي صفحة
 * فيها" — من غير السطر ده الطلب بيبقى ورق مقطوع من غير مرجع.
 */
@Composable
fun WirSources(vm: MainViewModel, wirId: Long) {
    val c = LocalCwColors.current
    val items by vm.wirItems(wirId).collectAsStateWithLifecycle(emptyList())
    Column(Modifier.fillMaxWidth()) {
        if (items.isEmpty()) {
            Text(
                "الصفحات دي اتضافت قبل ما نبدأ نسجّل المصدر.",
                style = CwText.codeSmall,
                color = c.textTertiary
            )
            return@Column
        }
        items.forEach { item ->
            Text(
                "ص ${item.page + 1} ← ${item.sourceName} · ص ${item.sourcePage + 1}",
                style = CwText.codeSmall,
                color = c.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(vertical = Space.xxs)
            )
        }
    }
}

@Composable
private fun RenameWirDialog(
    current: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("اسم الطلب") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("الاسم") }
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name) }
            ) { Text("احفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun ConfirmDeleteWirDialog(
    name: String,
    pageCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تمسح «$name»؟") },
        text = {
            Text(
                "هيتمسح ملف الطلب ($pageCount صفحة) وكل التأشير اللي عليه. " +
                    "الرسومات الأصلية مش هتتأثر."
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("امسح") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
