package com.corewall.qaqc.ui.notes

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.data.db.NoteEntity
import com.corewall.qaqc.domain.NoteColors
import com.corewall.qaqc.ui.design.CwButton
import com.corewall.qaqc.ui.design.CwButtonStyle
import com.corewall.qaqc.ui.design.CwChip
import com.corewall.qaqc.ui.design.CwText
import com.corewall.qaqc.ui.design.IconSize
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Radius
import com.corewall.qaqc.ui.design.Space
import com.corewall.qaqc.ui.design.Stroke as CwStroke

/**
 * خيارات الملاحظة — لون، تصنيفات، تذكير، تثبيت، أرشفة، حذف، وحقول هندسية.
 *
 * كلها في ورقة واحدة عن قصد. الخيارات دي بتتفتح مرة كل كام ملاحظة، فحطّها
 * في شريط دايم معناه إن مساحة الكتابة بتقلّ عشان أزرار مش بتتضغط. والورقة
 * بتتقفل لوحدها بعد أي فعل حاسم (أرشفة/حذف) لأن الملاحظة نفسها مابقتش
 * مكانها.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteOptionsSheet(
    vm: MainViewModel,
    note: NoteEntity,
    onDismiss: () -> Unit,
    onNoteGone: () -> Unit
) {
    val c = LocalCwColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val labels by vm.notesStore.noteLabels.collectAsStateWithLifecycle()
    val labelsByNote by vm.notesStore.labelsByNote.collectAsStateWithLifecycle()
    val attached = remember(labelsByNote, note.id) {
        labelsByNote[note.id].orEmpty().map { it.id }.toSet()
    }
    var reminderOpen by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.surface,
        shape = Radius.sheet
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(bottom = Space.lg)
        ) {
            // الملاحظة المحذوفة مالهاش خيارات — ليها قرارين بس: ترجع أو
            // تتمسح خالص. عرض لوحة الألوان والتصنيفات عليها بيدّي إحساس
            // إنها لسه حيّة وهي مش كده.
            if (note.isTrashed) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.lg),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm)
                ) {
                    ActionTile(
                        icon = Icons.Filled.Restore,
                        label = "رجّعها",
                        active = false,
                        onClick = { vm.notesStore.restore(note); onNoteGone() },
                        modifier = Modifier.weight(1f)
                    )
                    ActionTile(
                        icon = Icons.Filled.DeleteForever,
                        label = "امسحها نهائي",
                        active = false,
                        danger = true,
                        onClick = { vm.notesStore.deleteForever(note); onNoteGone() },
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    "المهملات بتتفضّى لوحدها بعد ${NoteEntity.TRASH_RETENTION_DAYS} يوم.",
                    style = CwText.codeSmall,
                    color = c.textTertiary,
                    modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.md)
                )
                return@Column
            }

            // ── أفعال سريعة
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.lg),
                horizontalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                ActionTile(
                    icon = Icons.Filled.PushPin,
                    label = if (note.pinned) "إلغاء التثبيت" else "تثبيت",
                    active = note.pinned,
                    onClick = { vm.notesStore.togglePin(note) },
                    modifier = Modifier.weight(1f)
                )
                ActionTile(
                    icon = if (note.archived) Icons.Filled.Unarchive else Icons.Filled.Archive,
                    label = if (note.archived) "رجّع من الأرشيف" else "أرشفة",
                    active = false,
                    onClick = { vm.notesStore.setArchived(note, !note.archived); onNoteGone() },
                    modifier = Modifier.weight(1f)
                )
                ActionTile(
                    icon = Icons.Filled.NotificationsActive,
                    label = if (note.reminderAt != null) "التذكير" else "تذكير",
                    active = note.reminderAt != null,
                    onClick = { reminderOpen = true },
                    modifier = Modifier.weight(1f)
                )
                ActionTile(
                    icon = Icons.Filled.DeleteOutline,
                    label = "حذف",
                    active = false,
                    danger = true,
                    onClick = { vm.notesStore.trash(note); onNoteGone() },
                    modifier = Modifier.weight(1f)
                )
            }

            SectionLabel("اللون")
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Space.lg),
                horizontalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                NoteColors.PALETTE.forEach { argb ->
                    val chosen = argb == note.colorArgb
                    Box(
                        Modifier
                            .size(SWATCH)
                            .background(
                                if (argb == NoteColors.DEFAULT) c.surfaceAlt else Color(argb),
                                CircleShape
                            )
                            .border(
                                if (chosen) CwStroke.thick else CwStroke.hair,
                                if (chosen) c.accent else c.outline,
                                CircleShape
                            )
                            .clickable { vm.notesStore.setColor(note, argb) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (chosen) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = c.accent,
                                modifier = Modifier.size(IconSize.sm)
                            )
                        }
                    }
                }
            }

            SectionLabel("التصنيفات")
            if (labels.isEmpty()) {
                Text(
                    "مفيش تصنيفات لسه — اعملها من قايمة الملاحظات.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textTertiary,
                    modifier = Modifier.padding(horizontal = Space.lg)
                )
            } else {
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = Space.lg),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm)
                ) {
                    labels.forEach { label ->
                        val on = label.id in attached
                        CwChip(
                            label = label.name,
                            selected = on,
                            onClick = { vm.notesStore.setLabel(note.id, label.id, !on) }
                        )
                    }
                }
            }

            // ── الحقول الهندسية: اختيارية بالكامل
            SectionLabel("تصنيف هندسي (اختياري)")
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Space.lg),
                horizontalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                val types = listOf(
                    "" to "بدون",
                    NoteEntity.TYPE_QA to "جودة",
                    NoteEntity.TYPE_RFI to "RFI",
                    NoteEntity.TYPE_INSPECTION to "تفتيش",
                    NoteEntity.TYPE_SAFETY to "سلامة"
                )
                types.forEach { (value, label) ->
                    CwChip(
                        label = label,
                        selected = note.noteType == value,
                        onClick = { vm.notesStore.setMeta(note, value, note.priority) }
                    )
                }
            }

            SectionLabel("الأولوية")
            Row(
                Modifier.padding(horizontal = Space.lg),
                horizontalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                listOf(0 to "عادي", 1 to "مهم", 2 to "عاجل").forEach { (value, label) ->
                    CwChip(
                        label = label,
                        selected = note.priority == value,
                        onClick = { vm.notesStore.setMeta(note, note.noteType, value) }
                    )
                }
            }
        }
    }

    if (reminderOpen) {
        NoteReminderSheet(
            current = note.reminderAt,
            onPick = { at -> vm.notesStore.setReminder(note, at); reminderOpen = false },
            onDismiss = { reminderOpen = false }
        )
    }
}

/**
 * تذكير بخيارات جاهزة.
 *
 * منتقي تاريخ/وقت كامل لتذكير سريع مبالغة: التذكير في الموقع بيبقى
 * "بعد ساعة" أو "بكرة الصبح". الخيارات دي بتغطّي أغلب الحالات بلمسة
 * واحدة، والباقي بيتشال لحد ما يبقى مطلوب فعلاً.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteReminderSheet(
    current: Long?,
    onPick: (Long?) -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalCwColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    // صلاحية الإشعارات بتتطلب **لما** المستخدم يحطّ تذكير، مش عند فتح
    // التطبيق. طلبها من غير سبب واضح بيتقفل، وبعدين مايتطلبش تاني.
    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    fun askNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.surface,
        shape = Radius.sheet
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = Space.lg)
                .padding(bottom = Space.lg)
        ) {
            Text("تذكير", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
            if (current != null) {
                Spacer(Modifier.height(Space.xxs))
                Text(
                    "الحالي: ${formatReminder(current)}",
                    style = CwText.codeSmall,
                    color = c.textTertiary
                )
            }
            Spacer(Modifier.height(Space.md))

            presets().forEach { (label, at) ->
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { askNotificationPermission(); onPick(at) }
                        .padding(vertical = Space.md)
                )
            }

            if (current != null) {
                Spacer(Modifier.height(Space.sm))
                CwButton(
                    label = "شيل التذكير",
                    onClick = { onPick(null) },
                    style = CwButtonStyle.Ghost,
                    fillWidth = true
                )
            }
        }
    }
}

private fun presets(): List<Pair<String, Long>> {
    val now = System.currentTimeMillis()
    val hour = 60L * 60L * 1000L

    fun atHour(daysAhead: Int, hourOfDay: Int): Long =
        java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.DAY_OF_YEAR, daysAhead)
            set(java.util.Calendar.HOUR_OF_DAY, hourOfDay)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis

    return listOf(
        "بعد ساعة" to now + hour,
        "بعد ٣ ساعات" to now + 3 * hour,
        "بكرة الصبح (٧:٠٠)" to atHour(1, 7),
        "بكرة بعد الضهر (٢:٠٠)" to atHour(1, 14),
        "بعد أسبوع" to atHour(7, 8)
    )
}

@Composable
private fun ActionTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    danger: Boolean = false
) {
    val c = LocalCwColors.current
    val tint = when {
        danger -> c.danger.fg
        active -> c.accent
        else -> c.textSecondary
    }
    Column(
        modifier
            .background(
                if (active) c.accentContainer else c.surfaceAlt,
                Radius.shapeMd
            )
            .clickable(onClick = onClick)
            .padding(vertical = Space.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.xxs)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(IconSize.lg))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    val c = LocalCwColors.current
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = c.textTertiary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = Space.lg, end = Space.lg, top = Space.lg, bottom = Space.xs)
    )
}

private val SWATCH = 40.dp
