package com.corewall.qaqc.ui.notes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.corewall.qaqc.data.db.NoteEntity
import com.corewall.qaqc.data.db.NoteLabelEntity
import com.corewall.qaqc.domain.NoteColors
import com.corewall.qaqc.domain.NotesLogic
import com.corewall.qaqc.ui.design.CwText
import com.corewall.qaqc.ui.design.Elevation
import com.corewall.qaqc.ui.design.IconSize
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Radius
import com.corewall.qaqc.ui.design.Space
import com.corewall.qaqc.ui.design.Stroke as CwStroke
import com.corewall.qaqc.ui.design.animatedElevation
import com.corewall.qaqc.ui.design.rememberPressScale

/**
 * كارت ملاحظة.
 *
 * الكارت بيوري **المحتوى** الأول: العنوان، وبعده أول سطور من النص أو أول
 * بنود القايمة. الميتاداتا (تصنيفات، تذكير، دور) بتيجي تحت وبحجم أصغر —
 * لأن المستخدم بيدوّر على ملاحظته بالكلام اللي كتبه، مش بالوسوم عليها.
 *
 * وارتفاع الكارت **مش ثابت**: بيقصر مع المحتوى القصير ويطول مع الطويل.
 * ده اللي بيدّي شبكة الملاحظات شكلها المميّز، وبيخلّي الصفحة تقول قد إيه
 * جوّه كل ملاحظة من غير ما تفتحها.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(
    note: NoteEntity,
    labels: List<NoteLabelEntity>,
    compact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null
) {
    val c = LocalCwColors.current
    val interaction = remember { MutableInteractionSource() }
    val press by rememberPressScale(interaction)
    val elevation by animatedElevation(interaction, Elevation.flat, Elevation.raised)

    val tinted = note.colorArgb != NoteColors.DEFAULT
    val container = if (tinted) Color(note.colorArgb) else c.surface
    val onContainer = NoteColors.contentOn(note.colorArgb)?.let { Color(it) } ?: c.textPrimary
    val secondary = if (tinted) onContainer.copy(alpha = 0.66f) else c.textSecondary

    val items = remember(note.body, note.kind) {
        if (note.kind == NoteEntity.KIND_CHECKLIST) NotesLogic.checklist(note.body)
        else emptyList()
    }
    val preview = remember(note.body, note.kind) {
        if (note.kind == NoteEntity.KIND_CHECKLIST) ""
        else note.body.lineSequence().filter { it.isNotBlank() }.take(PREVIEW_LINES)
            .joinToString("\n")
    }

    // الضغطة الطويلة بتفتح الخيارات — ده اللي المستخدم بيتوقّعه من كارت
    // في شبكة، و`Surface(onClick = ...)` مابيعرفش يعملها، فبنركّب اللمس
    // بنفسنا على المُعدِّل ونسيب الـ`Surface` للشكل بس.
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .scale(press)
            .combinedClickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onLongClick = onLongClick,
                onClick = onClick
            ),
        shape = Radius.shapeLg,
        color = container,
        shadowElevation = elevation,
        // الملاحظة الملوّنة مالهاش حدود — اللون نفسه هو اللي بيفصلها.
        border = if (tinted) null else BorderStroke(CwStroke.hair, c.outline)
    ) {
        Column(Modifier.padding(Space.md)) {

            if (note.title.isNotBlank()) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        note.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = onContainer,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (note.pinned) {
                        Icon(
                            Icons.Filled.PushPin,
                            contentDescription = "مثبّتة",
                            tint = secondary,
                            modifier = Modifier.size(IconSize.sm)
                        )
                    }
                }
                Spacer(Modifier.height(Space.xs))
            } else if (note.pinned) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Icon(
                        Icons.Filled.PushPin,
                        contentDescription = "مثبّتة",
                        tint = secondary,
                        modifier = Modifier.size(IconSize.sm)
                    )
                }
            }

            when {
                items.isNotEmpty() -> ChecklistPreview(items, onContainer, secondary, compact)
                preview.isNotBlank() -> Text(
                    preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer.copy(alpha = 0.9f),
                    maxLines = if (compact) COMPACT_LINES else PREVIEW_LINES,
                    overflow = TextOverflow.Ellipsis
                )
                note.title.isBlank() -> Text(
                    "ملاحظة فاضية",
                    style = MaterialTheme.typography.bodySmall,
                    color = secondary
                )
            }

            val imageCount = remember(note.body) { countImages(note.body) }
            val audioCount = remember(note.body) { countAudio(note.body) }
            val hasFooter = labels.isNotEmpty() || note.reminderAt != null ||
                note.noteType.isNotBlank() || imageCount > 0 || audioCount > 0
            if (hasFooter) {
                Spacer(Modifier.height(Space.sm))
                Footer(note, labels, secondary, tinted, imageCount, audioCount)
            }
        }
    }
}

@Composable
private fun ChecklistPreview(
    items: List<com.corewall.qaqc.domain.ChecklistItem>,
    onContainer: Color,
    secondary: Color,
    compact: Boolean
) {
    val limit = if (compact) COMPACT_LINES else PREVIEW_LINES
    // المنجز بيتأخّر في المعاينة: المستخدم بيبصّ على اللي **لسه** عليه.
    val ordered = remember(items) { items.sortedBy { it.done } }

    Column(verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
        ordered.take(limit).forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (item.done) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                    contentDescription = null,
                    tint = if (item.done) secondary else onContainer.copy(alpha = 0.7f),
                    modifier = Modifier.size(IconSize.sm)
                )
                Spacer(Modifier.width(Space.xs))
                Text(
                    item.text.ifBlank { "…" },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.done) secondary else onContainer.copy(alpha = 0.9f),
                    textDecoration = if (item.done)
                        androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        val remaining = ordered.size - limit
        if (remaining > 0) {
            Text(
                "+$remaining بند",
                style = CwText.codeSmall,
                color = secondary
            )
        }
    }
}

@Composable
private fun Footer(
    note: NoteEntity,
    labels: List<NoteLabelEntity>,
    secondary: Color,
    tinted: Boolean,
    imageCount: Int,
    audioCount: Int
) {
    val c = LocalCwColors.current
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        if (note.reminderAt != null || note.noteType.isNotBlank()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.xs)
            ) {
                if (note.reminderAt != null) {
                    Icon(
                        Icons.Filled.NotificationsActive,
                        contentDescription = "فيها تذكير",
                        tint = secondary,
                        modifier = Modifier.size(IconSize.sm)
                    )
                    Text(
                        formatReminder(note.reminderAt),
                        style = CwText.codeSmall,
                        color = secondary
                    )
                }
                if (note.noteType.isNotBlank()) {
                    Text(
                        noteTypeLabel(note.noteType),
                        style = CwText.codeSmall,
                        color = secondary
                    )
                }
            }
        }
        if (labels.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                labels.take(MAX_LABEL_CHIPS).forEach { label ->
                    Box(
                        Modifier
                            .background(
                                if (tinted) secondary.copy(alpha = 0.14f) else c.surfaceAlt,
                                Radius.pill
                            )
                            .padding(horizontal = Space.sm, vertical = 2.dp)
                    ) {
                        Text(
                            label.name,
                            style = CwText.codeSmall,
                            color = secondary,
                            maxLines = 1
                        )
                    }
                }
                if (labels.size > MAX_LABEL_CHIPS) {
                    Text(
                        "+${labels.size - MAX_LABEL_CHIPS}",
                        style = CwText.codeSmall,
                        color = secondary
                    )
                }
            }
        }
        if (imageCount > 0 || audioCount > 0) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                if (imageCount > 0) {
                    Icon(Icons.Filled.Image, "$imageCount صورة أو رسم", tint = secondary, modifier = Modifier.size(IconSize.sm))
                    Text("$imageCount", style = CwText.codeSmall, color = secondary)
                }
                if (audioCount > 0) {
                    Icon(Icons.Filled.Mic, "$audioCount تسجيل صوتي", tint = secondary, modifier = Modifier.size(IconSize.sm))
                    Text("$audioCount", style = CwText.codeSmall, color = secondary)
                }
            }
        }
    }
}

/** "النهاردة ٣:٠٠ م" أقرب للفهم من تاريخ كامل على كارت صغير. */
fun formatReminder(at: Long): String {
    val now = java.util.Calendar.getInstance()
    val then = java.util.Calendar.getInstance().apply { timeInMillis = at }
    val time = java.text.SimpleDateFormat("h:mm a", java.util.Locale("ar")).format(then.time)
    val sameDay = now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR) &&
        now.get(java.util.Calendar.DAY_OF_YEAR) == then.get(java.util.Calendar.DAY_OF_YEAR)
    if (sameDay) return "النهاردة $time"

    now.add(java.util.Calendar.DAY_OF_YEAR, 1)
    val tomorrow = now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR) &&
        now.get(java.util.Calendar.DAY_OF_YEAR) == then.get(java.util.Calendar.DAY_OF_YEAR)
    if (tomorrow) return "بكرة $time"

    return java.text.SimpleDateFormat("d MMM · h:mm a", java.util.Locale("ar")).format(then.time)
}

fun noteTypeLabel(type: String): String = when (type) {
    NoteEntity.TYPE_QA -> "جودة"
    NoteEntity.TYPE_RFI -> "RFI"
    NoteEntity.TYPE_INSPECTION -> "تفتيش"
    NoteEntity.TYPE_SAFETY -> "سلامة"
    else -> type
}

private const val PREVIEW_LINES = 8
private const val COMPACT_LINES = 3
private const val MAX_LABEL_CHIPS = 2
