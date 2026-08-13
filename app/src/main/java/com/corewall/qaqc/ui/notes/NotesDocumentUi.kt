package com.corewall.qaqc.ui.notes

import android.media.MediaPlayer
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.corewall.qaqc.notes.NotesBlock
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Radius
import com.corewall.qaqc.ui.design.Space
import java.io.File

/** بطاقة وسائط موحّدة؛ لا يظهر للمستخدم أي مسار تخزين. */
@Composable
fun DocumentImageBlock(block: NotesBlock, compact: Boolean = false, onOpen: (() -> Unit)? = null) {
    val c = LocalCwColors.current
    val title = if (block.type == NotesBlock.DRAWING) "رسم ميداني" else "صورة"
    Surface(onClick = { onOpen?.invoke() }, shape = Radius.shapeLg, color = c.surfaceAlt, modifier = Modifier.fillMaxWidth()) {
        Column {
            coil3.compose.AsyncImage(
                model = File(block.mediaPath),
                contentDescription = block.caption.ifBlank { title },
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(if (compact) 120.dp else 220.dp)
            )
            if (!compact || block.caption.isNotBlank()) {
                Row(Modifier.padding(horizontal = Space.md, vertical = Space.sm), verticalAlignment = Alignment.CenterVertically) {
                    Text(block.caption.ifBlank { title }, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text(if (block.type == NotesBlock.DRAWING) "رسم" else "صورة", style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                }
            }
        }
    }
}

@Composable
fun DocumentAudioBlock(block: NotesBlock, compact: Boolean = false) {
    val c = LocalCwColors.current
    val file = remember(block.mediaPath) { File(block.mediaPath) }
    var player by remember(block.id) { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember(block.id) { mutableStateOf(false) }
    fun release() {
        runCatching { player?.stop() }
        player?.release()
        player = null
        playing = false
    }
    fun toggle() {
        if (!file.exists()) return
        if (playing) release()
        else runCatching {
            MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener { release() }
                prepare(); start()
            }
        }.onSuccess { player = it; playing = true }
    }
    DisposableEffect(block.id) { onDispose { release() } }
    Surface(onClick = ::toggle, color = c.surfaceAlt, shape = Radius.shapeLg, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(Space.md), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = Radius.pill, color = c.accent, modifier = Modifier.size(if (compact) 40.dp else 52.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "إيقاف" else "تشغيل", tint = c.onAccent)
                }
            }
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text("ملاحظة صوتية", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(if (file.exists()) if (playing) "يعمل الآن" else "اضغط للتشغيل" else "ملف الصوت غير متاح", style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
            }
            Icon(Icons.Filled.Mic, null, tint = c.accent)
        }
    }
}

@Composable
fun DocumentDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
}
