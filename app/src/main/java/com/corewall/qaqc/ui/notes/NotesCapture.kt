package com.corewall.qaqc.ui.notes

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.media.MediaRecorder
import android.os.SystemClock
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.corewall.qaqc.stylus.pressureWidthFactor
import com.corewall.qaqc.stylus.pointerKindOf
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Radius
import com.corewall.qaqc.ui.design.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class InkTool { PEN, MARKER, HIGHLIGHTER, ERASER }
private data class InkStroke(val points: MutableList<Offset>, val color: Color, val width: Float, val tool: InkTool)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun NotesDrawingSheet(file: File, onDismiss: () -> Unit, onSaved: (File) -> Unit) {
    val c = LocalCwColors.current
    val scope = rememberCoroutineScope()
    val strokes = remember { mutableStateListOf<InkStroke>() }
    val redo = remember { ArrayDeque<InkStroke>() }
    var tool by remember { mutableStateOf(InkTool.PEN) }
    var color by remember { mutableStateOf(c.textPrimary) }
    var thickness by remember { mutableStateOf(5f) }
    var stylusOnly by remember { mutableStateOf(false) }
    var activeStroke by remember { mutableStateOf<InkStroke?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var saving by remember { mutableStateOf(false) }
    val colors = remember { listOf(c.textPrimary, c.accent, c.danger.fg, c.warning.fg, c.success.fg, Color(0xFF4D70FF)) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = c.surface) {
            Column(Modifier.fillMaxSize().padding(horizontal = Space.md, vertical = Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") }
                Column(Modifier.weight(1f)) {
                    Text("رسم ميداني", style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Text("قلم، علامة، تمييز وأستيكة مع حفظ الرسم كصورة", style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                }
                IconButton(onClick = { if (strokes.isNotEmpty()) { redo.addLast(strokes.removeAt(strokes.lastIndex)) } }) { Icon(Icons.Filled.Undo, "تراجع") }
                IconButton(onClick = { redo.removeLastOrNull()?.let { strokes.add(it) } }) { Icon(Icons.Filled.Redo, "إعادة") }
                IconButton(onClick = { strokes.clear(); redo.clear() }) { Icon(Icons.Filled.DeleteOutline, "مسح الكل") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("استخدم S Pen فقط", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(checked = stylusOnly, onCheckedChange = { stylusOnly = it })
            }
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(470.dp)
                    .background(Color.White, Radius.shapeLg)
                    .border(1.dp, c.outline, Radius.shapeLg)
                    .pointerInteropFilter { event ->
                        val kind = pointerKindOf(event.getToolType(event.actionIndex.coerceAtLeast(0)))
                        if (stylusOnly && !kind.isPen) return@pointerInteropFilter true
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                val base = if (tool == InkTool.ERASER) Color.White else color
                                val created = InkStroke(mutableStateListOf(Offset(event.x, event.y)), base, thickness * pressureWidthFactor(event.pressure), tool)
                                strokes.add(created)
                                activeStroke = created
                                redo.clear()
                                true
                            }
                            MotionEvent.ACTION_MOVE -> {
                                activeStroke?.points?.add(Offset(event.x, event.y))
                                activeStroke != null
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                activeStroke = null
                                true
                            }
                            else -> false
                        }
                    }
            ) {
                canvasSize = IntSize(size.width.toInt(), size.height.toInt())
                strokes.forEach { stroke ->
                    val alpha = when (stroke.tool) { InkTool.HIGHLIGHTER -> .28f; InkTool.MARKER -> .62f; else -> 1f }
                    if (stroke.points.size == 1) drawCircle(stroke.color.copy(alpha = alpha), stroke.width / 2, stroke.points.first())
                    else stroke.points.zipWithNext().forEach { (a, b) -> drawLine(stroke.color.copy(alpha = alpha), a, b, strokeWidth = stroke.width, cap = androidx.compose.ui.graphics.StrokeCap.Round) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                InkTool.entries.forEach { choice ->
                    Surface(onClick = { tool = choice }, color = if (tool == choice) c.accentContainer else c.surfaceAlt, shape = Radius.pill) {
                        Row(Modifier.padding(horizontal = Space.sm, vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                            Icon(when (choice) { InkTool.PEN -> Icons.Filled.Edit; InkTool.MARKER -> Icons.Filled.Brush; InkTool.HIGHLIGHTER -> Icons.Filled.AutoFixHigh; InkTool.ERASER -> Icons.Filled.Clear }, null, tint = c.accent, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(3.dp)); Text(when (choice) { InkTool.PEN -> "قلم"; InkTool.MARKER -> "علامة"; InkTool.HIGHLIGHTER -> "تمييز"; InkTool.ERASER -> "أستيكة" }, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
                colors.forEach { swatch -> Surface(onClick = { color = swatch }, color = swatch, shape = Radius.pill, modifier = Modifier.size(26.dp), border = if (color == swatch) androidx.compose.foundation.BorderStroke(3.dp, c.accent) else null) {} }
                Spacer(Modifier.weight(1f))
                listOf(3f, 6f, 12f).forEach { candidate -> Surface(onClick = { thickness = candidate }, color = if (thickness == candidate) c.accentContainer else c.surfaceAlt, shape = Radius.pill, modifier = Modifier.size(30.dp)) { Box(contentAlignment = Alignment.Center) { Text(candidate.toInt().toString(), style = MaterialTheme.typography.labelSmall) } } }
            }
            Surface(
                onClick = {
                    if (strokes.isEmpty() || saving) return@Surface
                    saving = true
                    val frozen = strokes.map { it.copy(points = it.points.toMutableList()) }
                    scope.launch(Dispatchers.IO) {
                        val ok = exportInk(file, canvasSize, frozen)
                        withContext(Dispatchers.Main) { saving = false; if (ok) onSaved(file) }
                    }
                },
                color = c.accent,
                contentColor = c.onAccent,
                shape = Radius.pill,
                modifier = Modifier.fillMaxWidth()
            ) { Row(Modifier.padding(Space.md), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Done, null); Spacer(Modifier.width(Space.xs)); Text(if (saving) "يحفظ الرسم…" else "إضافة الرسم إلى الملاحظة", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) } }
            }
        }
    }
}

private fun exportInk(file: File, size: IntSize, strokes: List<InkStroke>): Boolean = runCatching {
    file.parentFile?.mkdirs()
    val bitmap = Bitmap.createBitmap(size.width.coerceAtLeast(1), size.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)
    strokes.forEach { stroke ->
        val alpha = when (stroke.tool) { InkTool.HIGHLIGHTER -> 72; InkTool.MARKER -> 160; else -> 255 }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; strokeWidth = stroke.width; color = android.graphics.Color.argb(alpha, (stroke.color.red * 255).toInt(), (stroke.color.green * 255).toInt(), (stroke.color.blue * 255).toInt()) }
        stroke.points.zipWithNext().forEach { (a, b) -> canvas.drawLine(a.x, a.y, b.x, b.y, paint) }
    }
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    bitmap.recycle()
    file.exists() && file.length() > 0
}.getOrDefault(false)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesVoiceSheet(file: File, onDismiss: () -> Unit, onSaved: (File, Long) -> Unit) {
    val c = LocalCwColors.current
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recording by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var tick by remember { mutableLongStateOf(0L) }
    fun start() {
        runCatching {
            file.parentFile?.mkdirs()
            @Suppress("DEPRECATION") MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare(); start()
            }
        }.onSuccess { recorder = it; startedAt = SystemClock.elapsedRealtime(); recording = true; paused = false }
    }
    fun stop(delete: Boolean) {
        runCatching { recorder?.stop() }
        recorder?.release(); recorder = null; recording = false; paused = false
        if (delete) file.delete()
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) start() }
    LaunchedEffect(recording, paused) { while (recording && !paused) { tick = SystemClock.elapsedRealtime(); delay(350) } }
    val total = elapsed + if (recording && !paused) tick - startedAt else 0L
    DisposableEffect(Unit) { onDispose { if (recording) stop(true) } }
    ModalBottomSheet(onDismissRequest = { stop(recording); onDismiss() }) {
        Column(Modifier.padding(Space.xl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.md)) {
            Text("تسجيل ملاحظة صوتية", style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Text("${total / 60_000}:${((total / 1000) % 60).toString().padStart(2, '0')}", style = MaterialTheme.typography.displayMedium, color = if (recording) c.danger.fg else c.textPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
                if (recording) {
                    Surface(onClick = { if (paused) { recorder?.resume(); startedAt = SystemClock.elapsedRealtime(); paused = false } else { recorder?.pause(); elapsed += SystemClock.elapsedRealtime() - startedAt; paused = true } }, color = c.surfaceAlt, shape = Radius.pill, modifier = Modifier.size(70.dp)) { Box(contentAlignment = Alignment.Center) { Icon(if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, if (paused) "استئناف" else "إيقاف مؤقت") } }
                    Surface(onClick = { stop(false); if (file.exists() && file.length() > 0) onSaved(file, total) }, color = c.accent, shape = Radius.pill, modifier = Modifier.size(86.dp)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Stop, "إيقاف وحفظ", tint = c.onAccent, modifier = Modifier.size(34.dp)) } }
                } else Surface(onClick = { permission.launch(android.Manifest.permission.RECORD_AUDIO) }, color = c.danger.solid, shape = Radius.pill, modifier = Modifier.size(86.dp)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Mic, "بدء التسجيل", tint = c.danger.onSolid, modifier = Modifier.size(34.dp)) } }
            }
            Text(if (recording) "أوقف التسجيل لإضافته إلى الملاحظة" else "يُطلب إذن الميكروفون عند بدء التسجيل فقط", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
        }
    }
}
