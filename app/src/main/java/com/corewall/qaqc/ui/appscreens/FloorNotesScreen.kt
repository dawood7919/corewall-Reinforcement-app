package com.corewall.qaqc.ui.appscreens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.FLOOR_NOTE_ID
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.data.db.NoteEntity
import com.corewall.qaqc.domain.relativeTime
import com.corewall.qaqc.ui.design.CwButton
import com.corewall.qaqc.ui.design.CwCard
import com.corewall.qaqc.ui.design.CwEmptyState
import com.corewall.qaqc.ui.design.CwStatusBadge
import com.corewall.qaqc.ui.design.CwText
import com.corewall.qaqc.ui.design.CwTone
import com.corewall.qaqc.ui.design.IconSize
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Radius
import com.corewall.qaqc.ui.design.Sizes
import com.corewall.qaqc.ui.design.Space
import com.corewall.qaqc.ui.notes.parseImagePaths
import com.corewall.qaqc.ui.notes.rememberThumb

/**
 * ملاحظات الدور — معزولة بالدور الشغّال زي كل حاجة تانية.
 *
 * اتشال من هنا حاجتين:
 * - **Scaffold متداخل**: الشاشة دي بقت قسم جوّه تبويب الداتا، والـScaffold
 *   بتاعها كان بيركب فوق Scaffold التطبيق فالزرار الطايف بيتحسب بالنسبة
 *   للحاوية الغلط. الزرار بقى في رأس القايمة.
 * - **مؤلّف متلفّق**: كل ملاحظة كانت بتعرض "م. أحمد حسن" مكتوب في الكود.
 *   التطبيق مفيهوش حسابات مستخدمين أصلاً، فكل الملاحظات كانت بتتنسب لنفس
 *   الاسم المخترع. الوقت الحقيقي بيقعد مكانه.
 */
@Composable
fun FloorNotesScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val level by vm.currentLevel.collectAsStateWithLifecycle()
    val allNotes by vm.notes.collectAsStateWithLifecycle()
    val names by vm.names.collectAsStateWithLifecycle()

    val notes = remember(allNotes, level) {
        allNotes.filter { it.level == level }.sortedByDescending { it.updatedAt }
    }

    if (notes.isEmpty()) {
        CwEmptyState(
            icon = Icons.Filled.EditNote,
            title = "مفيش ملاحظات في دور $level",
            detail = "سجّل قرارات وتعليمات ومتابعات الدور ده. الملاحظة بتتحفظ مع " +
                "الدور لوحده — لو بدّلت الدور مش هتشوفها.",
            modifier = modifier.fillMaxSize(),
            action = {
                CwButton(
                    "ملاحظة جديدة",
                    { vm.openNoteEditor(FLOOR_NOTE_ID) },
                    icon = Icons.Filled.Add
                )
            }
        )
        return
    }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Space.screen, end = Space.screen,
            top = Space.md, bottom = Space.bottomInset
        ),
        verticalArrangement = Arrangement.spacedBy(Space.stack)
    ) {
        item(key = "add") {
            CwButton(
                "ملاحظة جديدة للدور",
                { vm.openNoteEditor(FLOOR_NOTE_ID) },
                icon = Icons.Filled.Add,
                fillWidth = true
            )
        }
        items(notes, key = { it.id }) { note ->
            NoteCard(
                note = note,
                markLabel = if (note.elementId == FLOOR_NOTE_ID) "الدور"
                else (names[note.elementId] ?: note.elementId),
                isFloor = note.elementId == FLOOR_NOTE_ID,
                onClick = { vm.openNoteEditor(note.elementId, note) }
            )
        }
    }
}

@Composable
private fun NoteCard(note: NoteEntity, markLabel: String, isFloor: Boolean, onClick: () -> Unit) {
    val c = LocalCwColors.current
    val images = remember(note.imagePathsJson) { parseImagePaths(note.imagePathsJson) }
    val preview = remember(note.body) {
        note.body.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() && !it.startsWith("!") && !it.startsWith("#") }
            ?.take(120)
            ?: ""
    }

    CwCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CwStatusBadge(
                label = if (isFloor) "ملاحظة دور" else markLabel,
                tone = if (isFloor) CwTone.Info else CwTone.Success,
                compact = true
            )
            Spacer(Modifier.weight(1f))
            Text(
                relativeTime(note.updatedAt),
                style = MaterialTheme.typography.labelSmall,
                color = c.textTertiary
            )
        }

        Spacer(Modifier.height(Space.sm))
        Text(
            note.title.ifBlank { "بدون عنوان" },
            style = MaterialTheme.typography.titleMedium,
            color = c.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (preview.isNotBlank()) {
            Spacer(Modifier.height(Space.xs))
            Text(
                preview,
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (images.isNotEmpty()) {
            Spacer(Modifier.height(Space.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                images.take(4).forEach { path -> NoteThumb(path) }
                if (images.size > 4) {
                    Box(
                        Modifier
                            .size(Sizes.avatarLg)
                            .clip(Radius.shapeSm)
                            .background(c.surfaceAlt),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "+${images.size - 4}",
                            style = MaterialTheme.typography.labelMedium,
                            color = c.textSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteThumb(path: String) {
    val c = LocalCwColors.current
    val bmp = rememberThumb(path, 200)
    Box(
        Modifier
            .size(Sizes.avatarLg)
            .clip(Radius.shapeSm)
            .background(c.surfaceAlt)
    ) {
        if (bmp != null) {
            Image(
                bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.Image,
                    contentDescription = null,
                    tint = c.textTertiary,
                    modifier = Modifier.size(IconSize.md)
                )
            }
        }
    }
}
