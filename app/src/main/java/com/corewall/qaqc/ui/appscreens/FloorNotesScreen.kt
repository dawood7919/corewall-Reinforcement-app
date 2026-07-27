package com.corewall.qaqc.ui.appscreens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.FLOOR_NOTE_ID
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.data.db.NoteEntity
import com.corewall.qaqc.ui.EmptyState
import com.corewall.qaqc.ui.notes.parseImagePaths
import com.corewall.qaqc.ui.notes.rememberThumb
import com.corewall.qaqc.ui.theme.LocalSrtColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val noteDate = SimpleDateFormat("dd/MM/yyyy · hh:mm a", Locale.ENGLISH)

/**
 * ملاحظات الدور: كل ملاحظات الدور الشغّال (المربوطة بالدور نفسه + المربوطة بالعناصر).
 * علاقة: مشروع ← مبنى ← دور ← ملاحظات.
 */
@Composable
fun FloorNotesScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val srt = LocalSrtColors.current
    val level by vm.currentLevel.collectAsStateWithLifecycle()
    val allNotes by vm.notes.collectAsStateWithLifecycle()
    val names by vm.names.collectAsStateWithLifecycle()

    val notes = remember(allNotes, level) {
        allNotes.filter { it.level == level }.sortedByDescending { it.updatedAt }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { vm.openNoteEditor(FLOOR_NOTE_ID) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("ملاحظة للدور") }
            )
        }
    ) { padding ->
        if (notes.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.EditNote,
                title = "لا توجد ملاحظات في دور $level",
                subtitle = "سجّل ملاحظات الدور: قرارات، تعليمات، صور موقع، متابعات… كلها معزولة لهذا الدور.",
                modifier = Modifier.fillMaxSize().padding(padding)
            )
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(notes, key = { it.id }) { note ->
                NoteCard(
                    note = note,
                    markLabel = if (note.elementId == FLOOR_NOTE_ID) "الدور" else (names[note.elementId] ?: note.elementId),
                    isFloor = note.elementId == FLOOR_NOTE_ID,
                    onClick = { vm.openNoteEditor(note.elementId, note) }
                )
            }
        }
    }
}

@Composable
private fun NoteCard(note: NoteEntity, markLabel: String, isFloor: Boolean, onClick: () -> Unit) {
    val srt = LocalSrtColors.current
    val images = remember(note.imagePathsJson) { parseImagePaths(note.imagePathsJson) }
    val preview = remember(note.body) {
        note.body.lineSequence().map { it.trim() }.firstOrNull { it.isNotBlank() && !it.startsWith("!") && !it.startsWith("#") }?.take(120) ?: ""
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = (if (isFloor) srt.blue else srt.green).copy(alpha = 0.14f), shape = RoundedCornerShape(8.dp)) {
                    Text(
                        if (isFloor) "ملاحظة دور" else markLabel,
                        Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isFloor) srt.blue else srt.green,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(noteDate.format(Date(note.updatedAt)), style = MaterialTheme.typography.labelSmall, color = srt.text3)
            }
            Spacer(Modifier.height(8.dp))
            Text(note.title.ifBlank { "بدون عنوان" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (preview.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(preview, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (images.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    images.take(4).forEach { path -> NoteThumb(path) }
                    if (images.size > 4) {
                        Box(
                            Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) { Text("+${images.size - 4}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(24.dp).clip(CircleShape).background(srt.blue), contentAlignment = Alignment.Center) {
                    Text("AH", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
                Text("م. أحمد حسن", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun NoteThumb(path: String) {
    val bmp = rememberThumb(path, 200)
    Box(Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (bmp != null) androidx.compose.foundation.Image(bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Image, contentDescription = null, tint = LocalSrtColors.current.text3) }
    }
}
