package com.corewall.qaqc.ui.notes

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.media.MediaRecorder
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.consume
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Radius
import com.corewall.qaqc.ui.design.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private data class SketchStroke(val points: MutableList<Offset>, val color: Color, val width: Float)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteSketchSheet(file: File, onDismiss: () -> Unit, onSaved: (File) -> Unit) {
    val c = LocalCwColors.current
    val scope = rememberCoroutineScope()
    val strokes = remember { mutableStateListOf<SketchStroke>() }
    val swatches = remember { listOf(c.textPrimary, c.accent, c.danger.fg, c.warning.fg, c.success.fg) }
    var color by remember { mutableStateOf(swatches.first()) }
    var width by remember { mutableStateOf(7f) }
    var drawingSize by remember { mutableStateOf(IntSize.Zero) }
    var saving by remember { mutableStateOf(false) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = c.surface, shape = Radius.sheet) {
        Column(
            Modifier.padding(horizontal = Space.lg, vertical = Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.md)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Brush, null, tint = c.accent)
                Spacer(Modifier.width(Space.sm))
                Column(Modifier.weight(1f)) {
                    Text("رسم ميداني", style = MaterialTheme.typography.titleLarge)
                    Text("ارسم بالقلم أو الإصبع ثم أضفه كصورة داخل الملاحظة", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "إغلاق") }
            }

            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .clip(Radius.shapeLg)
                    .background(Color.White)
                    .border(1.dp, c.outline, Radius.shapeLg)
                    .pointerInput(color, width) {
                        detectDragGestures(
                            onDragStart = { point -> strokes.add(SketchStroke(mutableStateListOf(point), color, width)) },
                            onDrag = { change, _ ->
                                change.consume()
                                strokes.lastOrNull()?.points?.add(change.position)
                            }
                        )
                    }
            ) {
                drawingSize = IntSize(size.width.toInt(), size.height.toInt())
                strokes.forEach { stroke ->
                    if (stroke.points.size == 1) drawCircle(stroke.color, stroke.width / 2f, stroke.points.first())
                    else stroke.points.zipWithNext().forEach { (from, to) ->
                        drawLine(stroke.color, from, to, strokeWidth = stroke.width, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Icon(Icons.Filled.Palette, null, tint = c.textSecondary)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    items(swatches) { swatch ->
                        Surface(
                            onClick = { color = swatch },
                            color = swatch,
                            shape = Radius.pill,
                            modifier = Modifier.size(if (color == swatch) 34.dp else 28.dp),
                            border = if (color == swatch) androidx.compose.foundation.BorderStroke(3.dp, c.accent) else null
                        ) {}
                    }
                }
                Spacer(Modifier.weight(1f))
                listOf(4f, 7f, 12f).forEach { candidate ->
                    Surface(
                        onClick = { width = candidate },
                        color = if (width == candidate) c.accentContainer else c.surfaceAlt,
                        shape = Radius.pill,
                        modifier = Modifier.size(32.dp)
                    ) { Box(contentAlignment = Alignment.Center) { Canvas(Modifier.size(20.dp)) { drawCircle(c.textPrimary, candidate / 2f, center) } } }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                OutlinedButton(onClick = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Undo, null); Spacer(Modifier.width(Space.xs)); Text("تراجع")
                }
                OutlinedButton(onClick = { strokes.clear() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.DeleteOutline, null); Spacer(Modifier.width(Space.xs)); Text("مسح")
                }
                Button(
                    onClick = {
                        if (strokes.isEmpty() || drawingSize.width <= 0 || saving) return@Button
                        saving = true
                        val immutableStrokes = strokes.map { it.copy(points = it.points.toMutableList()) }
                        scope.launch(Dispatchers.IO) {
                            val ok = writeSketchPng(file, drawingSize, immutableStrokes)
                            withContext(Dispatchers.Main) {
                                saving = false
                                if (ok) onSaved(file)
                            }
                        }
                    },
                    enabled = strokes.isNotEmpty() && !saving,
                    modifier = Modifier.weight(1.2f)
                ) { Icon(Icons.Filled.Save, null); Spacer(Modifier.width(Space.xs)); Text(if (saving) "يحفظ…" else "إضافة") }
            }
        }
    }
}

private fun writeSketchPng(file: File, size: IntSize, strokes: List<SketchStroke>): Boolean = runCatching {
    file.parentFile?.mkdirs()
    val bitmap = Bitmap.createBitmap(size.width.coerceAtLeast(1), size.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)
    strokes.forEach { stroke ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = stroke.width
            color = android.graphics.Color.argb((stroke.color.alpha * 255).toInt(), (stroke.color.red * 255).toInt(), (stroke.color.green * 255).toInt(), (stroke.color.blue * 255).toInt())
        }
        if (stroke.points.size == 1) canvas.drawCircle(stroke.points.first().x, stroke.points.first().y, stroke.width / 2f, paint)
        else stroke.points.zipWithNext().forEach { (from, to) -> canvas.drawLine(from.x, from.y, to.x, to.y, paint) }
    }
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    bitmap.recycle()
    file.exists() && file.length() > 0
}.getOrDefault(false)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteAudioSheet(file: File, onDismiss: () -> Unit, onSaved: (File) -> Unit) {
    val c = LocalCwColors.current
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var elapsed by remember { mutableIntStateOf(0) }
    var recording by remember { mutableStateOf(false) }

    fun release(deleteIncomplete: Boolean) {
        runCatching { recorder?.stop() }
        recorder?.release()
        recorder = null
        recording = false
        if (deleteIncomplete) file.delete()
    }
    fun start() {
        runCatching {
            file.parentFile?.mkdirs()
            @Suppress("DEPRECATION")
            MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(96_000)
                setAudioSamplingRate(44_100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
        }.onSuccess {
            recorder = it
            startedAt = SystemClock.elapsedRealtime()
            recording = true
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) start() }
    LaunchedEffect(recording) {
        while (recording) {
            elapsed = ((SystemClock.elapsedRealtime() - startedAt) / 1000L).toInt()
            delay(500)
        }
    }
    DisposableEffect(Unit) { onDispose { if (recording) release(deleteIncomplete = true) } }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = { release(deleteIncomplete = recording); onDismiss() }, sheetState = sheet, containerColor = c.surface, shape = Radius.sheet) {
        Column(
            Modifier.padding(horizontal = Space.xl, vertical = Space.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.md)
        ) {
            Text("تسجيل ملاحظة صوتية", style = MaterialTheme.typography.titleLarge)
            Text(if (recording) "يسجّل الآن ${elapsed / 60}:${(elapsed % 60).toString().padStart(2, '0')}" else "اضغط لبدء تسجيل صوتي محلي", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            Surface(
                onClick = {
                    if (recording) {
                        release(deleteIncomplete = false)
                        if (file.exists() && file.length() > 0) onSaved(file)
                    } else permission.launch(android.Manifest.permission.RECORD_AUDIO)
                },
                shape = Radius.pill,
                color = if (recording) c.danger.solid else c.accent,
                modifier = Modifier.size(92.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(if (recording) Icons.Filled.Stop else Icons.Filled.Mic, if (recording) "إنهاء التسجيل" else "بدء التسجيل", tint = if (recording) c.danger.onSolid else c.onAccent, modifier = Modifier.size(38.dp))
                }
            }
            Text(if (recording) "اضغط لإيقاف وحفظ التسجيل" else "سيُضاف التسجيل إلى الملاحظة عند إيقافه", style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
        }
    }
}
