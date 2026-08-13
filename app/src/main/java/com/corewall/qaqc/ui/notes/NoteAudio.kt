package com.corewall.qaqc.ui.notes

import android.media.MediaPlayer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Radius
import com.corewall.qaqc.ui.design.Space
import java.io.File

/** بطاقة تشغيل محلية لملاحظة صوتية مضمّنة داخل الملاحظة. */
@Composable
fun AudioAttachmentCard(path: String) {
    val c = LocalCwColors.current
    val file = remember(path) { File(path) }
    var player by remember(path) { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember(path) { mutableStateOf(false) }

    fun stop() {
        runCatching { player?.stop() }
        player?.release()
        player = null
        playing = false
    }
    fun toggle() {
        if (!file.exists()) return
        if (playing) {
            stop()
        } else {
            runCatching {
                MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    setOnCompletionListener { finished ->
                        playing = false
                        finished.release()
                        player = null
                    }
                    prepare()
                    start()
                }
            }.onSuccess { created ->
                player = created
                playing = true
            }
        }
    }
    DisposableEffect(path) { onDispose { stop() } }

    Surface(
        onClick = { toggle() },
        shape = Radius.shapeLg,
        color = c.info.container,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(Space.md), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = Radius.pill, color = c.info.solid, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playing) "إيقاف الصوت" else "تشغيل الصوت",
                        tint = c.info.onSolid
                    )
                }
            }
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text("ملاحظة صوتية", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = c.info.onContainer)
                Text(
                    if (file.exists()) "${file.name} · ${if (playing) "يعمل الآن" else "اضغط للتشغيل"}" else "الملف غير متاح على هذا الجهاز",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.info.onContainer
                )
            }
            Icon(Icons.Filled.Mic, null, tint = c.info.onContainer)
        }
    }
}
