package com.corewall.qaqc.ui.cad

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corewall.qaqc.CoreWallApp
import com.corewall.qaqc.data.FilesManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import com.corewall.qaqc.ui.design.Radius
import com.corewall.qaqc.ui.design.Space

private val CadBg = Color(0xFF0B1220)
private val CadLine = Color(0xFFE8F1FF)
private val CadAccent = Color(0xFF5B9DFF)
private val CadMeasure = Color(0xFFFFB020)
private val CadMeasure2 = Color(0xFF34C759)
private val CadDim = Color(0xFF8B9BB4)

@Composable
fun CadViewerScreen(path: String, files: FilesManager, onClose: () -> Unit) {
    val context = LocalContext.current
    val file = remember(path) { File(path) }
    val cadStore = remember { (context.applicationContext as CoreWallApp).cadMeasurementStore }
    val scope = rememberCoroutineScope()
    val parseResult by produceState<CadLoadResult?>(null, path) {
        value = null
        value = withContext(Dispatchers.IO) { CadDocumentLoader.load(file) }
    }
    var layers by remember { mutableStateOf<List<CadLayer>>(emptyList()) }
    val measurements by cadStore.measurements(path).collectAsState(initial = emptyList())
    var tool by remember { mutableStateOf(CadMeasureTool.PAN) }
    var unit by remember { mutableStateOf(MeasureUnit.M) }
    var unitsPerMeter by remember { mutableFloatStateOf(1f) }
    var draftPoints by remember { mutableStateOf<List<CadPoint>>(emptyList()) }
    var showLayers by remember { mutableStateOf(false) }
    var calibrateDialog by remember { mutableStateOf<Pair<CadPoint, CadPoint>?>(null) }
    var calibrateInput by remember { mutableStateOf("") }
    val viewport = remember(path) { CadViewport() }
    val labelPaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.argb(200, 180, 200, 230)
            isAntiAlias = true
        }
    }

    LaunchedEffect(parseResult, path) {
        val d = parseResult?.drawing ?: return@LaunchedEffect
        layers = d.layers.map { it.copy() }
        val saved = cadStore.settings(path)
        unitsPerMeter = (saved?.unitsPerMeter ?: DxfParser.unitsPerMeterFromInsUnits(d.insUnits)).toFloat()
        unit = saved?.displayUnit?.let { runCatching { MeasureUnit.valueOf(it) }.getOrNull() }
            ?: when (d.insUnits) { 4 -> MeasureUnit.MM; 5 -> MeasureUnit.CM; else -> MeasureUnit.M }
        viewport.reset()
    }

    fun screenToWorld(o: Offset) = viewport.screenToWorld(o.x, o.y)
    fun worldToScreen(p: CadPoint): Offset { viewport.revision; return viewport.worldToScreen(p).let { Offset(it.first, it.second) } }

    fun handleTap(world: CadPoint, snapIndex: CadSnapIndex) {
        val p = snapIndex.nearest(world, 12.0 / viewport.scale) ?: world
        when (tool) {
            CadMeasureTool.PAN -> Unit
            CadMeasureTool.DISTANCE -> {
                val pts = draftPoints + p
                if (pts.size >= 2) { scope.launch { cadStore.save(path, CadMeasurement.Distance(0, pts[0], pts[1])) }; draftPoints = emptyList() }
                else draftPoints = pts
            }
            CadMeasureTool.CONTINUOUS, CadMeasureTool.AREA -> draftPoints = draftPoints + p
            CadMeasureTool.ANGLE -> {
                val pts = draftPoints + p
                if (pts.size >= 3) { scope.launch { cadStore.save(path, CadMeasurement.Angle(0, pts[1], pts[0], pts[2])) }; draftPoints = emptyList() }
                else draftPoints = pts
            }
            CadMeasureTool.RADIUS -> {
                val pts = draftPoints + p
                if (pts.size >= 2) { scope.launch { cadStore.save(path, CadMeasurement.Radius(0, pts[0], pts[1])) }; draftPoints = emptyList() }
                else draftPoints = pts
            }
            CadMeasureTool.CALIBRATE -> {
                val pts = draftPoints + p
                if (pts.size >= 2) { calibrateDialog = pts[0] to pts[1]; draftPoints = emptyList() }
                else draftPoints = pts
            }
        }
    }

    fun finishPoly() {
        when (tool) {
            CadMeasureTool.CONTINUOUS -> if (draftPoints.size >= 2) scope.launch { cadStore.save(path, CadMeasurement.Continuous(0, draftPoints)) }
            CadMeasureTool.AREA -> if (draftPoints.size >= 3) scope.launch { cadStore.save(path, CadMeasurement.AreaPoly(0, draftPoints)) }
            else -> Unit
        }
        draftPoints = emptyList()
    }

    Surface(Modifier.fillMaxSize(), color = CadBg) {
        when {
            parseResult == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = CadAccent) }
            parseResult!!.drawing == null -> Column(Modifier.fillMaxSize().statusBarsPadding().padding(Space.xl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Filled.Architecture, null, tint = CadAccent, modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(Space.md))
                Text(file.name, color = CadLine, fontWeight = FontWeight.Bold)
                Text(parseResult!!.error ?: "تعذّر الفتح", color = CadDim)
                Spacer(Modifier.height(Space.lg))
                TextButton(onClick = onClose) { Text("إغلاق") }
            }
            else -> {
                val base = parseResult!!.drawing!!
                val drawing = remember(base, layers) { base.copy(layers = layers) }
                val visibleEntities = remember(drawing, layers) { drawing.visibleEntities(layers) }
                val scene by produceState<CadPreparedScene?>(null, visibleEntities, drawing.bounds) {
                    value = withContext(Dispatchers.Default) {
                        CadStaticPath.build(drawing, visibleEntities)
                    }
                }
                var canvasSize by remember(drawing) { mutableStateOf(IntSize.Zero) }
                LaunchedEffect(drawing, canvasSize) {
                    if (!viewport.fitted) viewport.fit(drawing.bounds, canvasSize)
                }
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "إغلاق", tint = CadLine) }
                        Column(Modifier.weight(1f)) {
                            Text(file.name, color = CadLine, fontWeight = FontWeight.Bold, maxLines = 1, style = MaterialTheme.typography.titleSmall)
                            Text("${scene?.visibleEntityCount ?: 0}/${drawing.entities.size} كيان · ${layers.count { it.visible }}/${layers.size} طبقة", color = CadDim, style = MaterialTheme.typography.labelSmall)
                        }
                        IconButton(onClick = { if (measurements.isNotEmpty()) scope.launch { cadStore.delete(measurements.last().id) } else if (draftPoints.isNotEmpty()) draftPoints = draftPoints.dropLast(1) }) { Icon(Icons.AutoMirrored.Filled.Undo, "تراجع", tint = CadLine) }
                        IconButton(onClick = { scope.launch { cadStore.clear(path) }; draftPoints = emptyList() }) { Icon(Icons.Filled.Delete, "مسح", tint = CadLine) }
                        IconButton(onClick = { showLayers = !showLayers }) { Icon(Icons.Filled.Layers, "طبقات", tint = CadLine) }
                    }
                    val hint = when (tool) {
                        CadMeasureTool.PAN -> "إصبعين للزوم · اسحب للتحريك"
                        CadMeasureTool.DISTANCE -> if (draftPoints.isEmpty()) "التقط نقطة البداية" else "التقط نقطة النهاية"
                        CadMeasureTool.CONTINUOUS -> "نقاط متتالية · دبل تاب إنهاء"
                        CadMeasureTool.AREA -> "رؤوس المضلع · دبل تاب إنهاء"
                        CadMeasureTool.ANGLE -> listOf("ضلع 1", "الرأس", "ضلع 2").getOrElse(draftPoints.size) { "" }
                        CadMeasureTool.RADIUS -> if (draftPoints.isEmpty()) "المركز" else "نقطة على المحيط"
                        CadMeasureTool.CALIBRATE -> if (draftPoints.isEmpty()) "طول معروف — نقطة 1" else "نقطة 2"
                    }
                    Text(hint, color = CadDim, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().background(Color(0xFF0E1628)).padding(Space.md, 6.dp))
                    Box(Modifier.weight(1f).fillMaxWidth().onSizeChanged { canvasSize = it }) {
                        Canvas(Modifier.fillMaxSize().pointerInput(Unit) {
                            detectTransformGestures { centroid, pan, zoom, _ ->
                                viewport.transform(centroid.x, centroid.y, pan.x, pan.y, zoom)
                            }
                        }.pointerInput(tool, scene?.snapIndex, draftPoints) {
                            detectTapGestures(
                                onTap = { tap -> scene?.snapIndex?.let { handleTap(screenToWorld(tap), it) } },
                                onDoubleTap = { finishPoly() }
                            )
                        }) {
                            // zoom/pan يعيدان تحويل Picture الجاهزة فقط؛ لا مسح للكيانات ولا إنشاء Path لكل إطار.
                            viewport.revision
                            scene?.let { prepared ->
                                val native = drawContext.canvas.nativeCanvas
                                native.save()
                                native.translate(viewport.offsetX, viewport.offsetY)
                                native.scale(viewport.scale, -viewport.scale)
                                prepared.strokes.forEach { stroke -> native.drawPath(stroke.path, stroke.paint) }
                                native.restore()
                                var labelCount = 0
                                for (preparedLabel in prepared.labels) {
                                    if (labelCount >= 750) break
                                    val label = preparedLabel.label
                                    val point = worldToScreen(label.position)
                                    if (point.x !in -80f..size.width + 80f || point.y !in -40f..size.height + 40f) continue
                                    labelPaint.textSize = max(10f, (label.height * viewport.scale).toFloat().coerceAtMost(28f))
                                    labelPaint.color = preparedLabel.color
                                    native.drawText(label.value.take(80), point.x, point.y, labelPaint)
                                    labelCount++
                                }
                            }
                            for (m in measurements) drawMeas(m, ::worldToScreen, unit, unitsPerMeter.toDouble())
                            if (draftPoints.isNotEmpty()) {
                                for (i in 0 until draftPoints.lastIndex) drawLine(CadMeasure, worldToScreen(draftPoints[i]), worldToScreen(draftPoints[i + 1]), strokeWidth = 2.5f)
                                draftPoints.forEach { drawCircle(CadMeasure, 5f, worldToScreen(it)) }
                            }
                        }
                        if (scene == null) LinearProgressIndicator(Modifier.align(Alignment.TopCenter).fillMaxWidth(), color = CadAccent, trackColor = Color.Transparent)
                        if (showLayers) Surface(Modifier.align(Alignment.TopEnd).padding(Space.sm).width(200.dp), color = Color(0xEE121A2A), shape = Radius.shapeMd) {
                            Column(Modifier.padding(Space.sm)) {
                                Text("الطبقات", color = CadLine, fontWeight = FontWeight.Bold)
                                LazyColumn(Modifier.height(160.dp)) {
                                    items(layers, key = { it.name }) { layer ->
                                        Row(Modifier.fillMaxWidth().clickable { layers = layers.map { if (it.name == layer.name) it.copy(visible = !it.visible) else it } }, verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(layer.visible, { layers = layers.map { if (it.name == layer.name) it.copy(visible = !it.visible) else it } })
                                            Text(layer.name, color = CadLine, fontSize = 12.sp, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                        if (measurements.isNotEmpty()) Surface(Modifier.align(Alignment.BottomStart).padding(Space.sm).fillMaxWidth(0.55f).height(120.dp), color = Color(0xEE121A2A), shape = Radius.shapeMd) {
                            LazyColumn(Modifier.padding(Space.sm)) { items(measurements.asReversed(), key = { it.id }) { m -> Text(m.label(unit, unitsPerMeter.toDouble()), color = CadMeasure2, fontSize = 12.sp, modifier = Modifier.padding(vertical = Space.xxs)) } }
                        }
                    }
                    Surface(color = Color(0xFF121A2A)) {
                        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Space.sm, vertical = Space.xs), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                                @Composable fun chip(label: String, icon: ImageVector, t: CadMeasureTool) {
                                    val sel = tool == t
                                    Row(Modifier.background(if (sel) CadAccent.copy(alpha = 0.25f) else Color.Transparent, Radius.shapeXl).border(1.dp, if (sel) CadAccent else CadDim.copy(alpha = 0.4f), Radius.shapeXl).clickable { tool = t; draftPoints = emptyList() }.padding(horizontal = Space.md, vertical = Space.sm), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(icon, null, tint = if (sel) CadAccent else CadLine, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(Space.xs)); Text(label, color = if (sel) CadAccent else CadLine, fontSize = 12.sp)
                                    }
                                }
                                chip("تحريك", Icons.Filled.PanTool, CadMeasureTool.PAN)
                                chip("بُعد", Icons.Filled.Straighten, CadMeasureTool.DISTANCE)
                                chip("معايرة", Icons.Filled.Straighten, CadMeasureTool.CALIBRATE)
                                if (tool == CadMeasureTool.CONTINUOUS || tool == CadMeasureTool.AREA) TextButton(onClick = { finishPoly() }) { Text("إنهاء", color = CadAccent) }
                            }
                            Row(Modifier.fillMaxWidth().padding(horizontal = Space.sm, vertical = Space.xxs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                                Text("الوحدة:", color = CadDim, fontSize = 12.sp)
                                MeasureUnit.entries.forEach { u -> FilterChip(selected = unit == u, onClick = { unit = u; scope.launch { cadStore.saveSettings(path, unitsPerMeter.toDouble(), u) } }, label = { Text(u.label, fontSize = 11.sp) }) }
                            }
                        }
                    }
                }
            }
        }
    }

    calibrateDialog?.let { (a, b) ->
        AlertDialog(onDismissRequest = { calibrateDialog = null }, title = { Text("معايرة المقياس") }, text = {
            Column { Text("على الرسم: ${"%.4f".format(a.distanceTo(b))} وحدة"); OutlinedTextField(calibrateInput, { calibrateInput = it }, singleLine = true, label = { Text("الطول الحقيقي بالمتر") }) }
        }, confirmButton = {
            TextButton(onClick = {
                val realM = calibrateInput.toDoubleOrNull(); val dist = a.distanceTo(b)
                if (realM != null && realM > 0 && dist > 0) { unitsPerMeter = (dist / realM).toFloat(); scope.launch { cadStore.saveSettings(path, unitsPerMeter.toDouble(), unit) }; Toast.makeText(context, "تمت المعايرة ✓", Toast.LENGTH_SHORT).show(); calibrateDialog = null; calibrateInput = "" }
            }) { Text("تطبيق") }
        }, dismissButton = { TextButton(onClick = { calibrateDialog = null }) { Text("إلغاء") } })
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMeas(m: CadMeasurement, w2s: (CadPoint) -> Offset, unit: MeasureUnit, upm: Double) {
    val paint = android.graphics.Paint().apply { color = android.graphics.Color.rgb(255, 176, 32); textSize = 28f; isFakeBoldText = true; isAntiAlias = true }
    when (m) {
        is CadMeasurement.Distance -> {
            val a = w2s(m.a); val b = w2s(m.b)
            val dx = b.x - a.x; val dy = b.y - a.y
            val length = hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(1f)
            val nx = -dy / length; val ny = dx / length; val offset = 24f
            val da = Offset(a.x + nx * offset, a.y + ny * offset)
            val db = Offset(b.x + nx * offset, b.y + ny * offset)
            // خطوط الامتداد ثم خط البُعد المنفصل عن الكيان المقاس.
            drawLine(CadMeasure.copy(alpha = .75f), a, Offset(a.x + nx * (offset + 10f), a.y + ny * (offset + 10f)), strokeWidth = 1.7f)
            drawLine(CadMeasure.copy(alpha = .75f), b, Offset(b.x + nx * (offset + 10f), b.y + ny * (offset + 10f)), strokeWidth = 1.7f)
            drawLine(CadMeasure, da, db, strokeWidth = 2.6f)
            fun arrow(at: Offset, toward: Offset) {
                val adx = toward.x - at.x; val ady = toward.y - at.y; val al = hypot(adx.toDouble(), ady.toDouble()).toFloat().coerceAtLeast(1f)
                val ux = adx / al; val uy = ady / al; val px = -uy; val py = ux
                val p1 = Offset(at.x + ux * 12f + px * 5f, at.y + uy * 12f + py * 5f)
                val p2 = Offset(at.x + ux * 12f - px * 5f, at.y + uy * 12f - py * 5f)
                val path = Path().apply { moveTo(at.x, at.y); lineTo(p1.x, p1.y); lineTo(p2.x, p2.y); close() }
                drawPath(path, CadMeasure)
            }
            arrow(da, db); arrow(db, da)
            drawCircle(CadMeasure, 4f, a); drawCircle(CadMeasure, 4f, b)
            val label = unit.format(m.length, upm)
            paint.textAlign = android.graphics.Paint.Align.CENTER
            val mid = Offset((da.x + db.x) / 2, (da.y + db.y) / 2 - 8f)
            paint.color = android.graphics.Color.rgb(255, 176, 32)
            drawContext.canvas.nativeCanvas.drawText(label, mid.x, mid.y, paint)
        }
        is CadMeasurement.Continuous -> {
            for (i in 0 until m.points.lastIndex) drawLine(CadMeasure, w2s(m.points[i]), w2s(m.points[i + 1]), strokeWidth = 2.5f)
            m.points.forEach { drawCircle(CadMeasure, 4f, w2s(it)) }
        }
        is CadMeasurement.AreaPoly -> {
            if (m.points.size < 2) return
            val path = Path(); val f = w2s(m.points.first()); path.moveTo(f.x, f.y)
            m.points.drop(1).forEach { path.lineTo(w2s(it).x, w2s(it).y) }; path.close()
            drawPath(path, CadMeasure2.copy(alpha = 0.15f)); drawPath(path, CadMeasure2, style = Stroke(2.5f))
        }
        is CadMeasurement.Angle -> {
            val v = w2s(m.vertex)
            drawLine(CadMeasure, v, w2s(m.armA), strokeWidth = 2f); drawLine(CadMeasure, v, w2s(m.armB), strokeWidth = 2f); drawCircle(CadMeasure, 4f, v)
            drawContext.canvas.nativeCanvas.drawText("%.1f°".format(m.degrees), v.x + 12, v.y - 12, paint)
        }
        is CadMeasurement.Radius -> {
            val c = w2s(m.center); val e = w2s(m.edge)
            val r = hypot((e.x - c.x).toDouble(), (e.y - c.y).toDouble()).toFloat()
            drawLine(CadMeasure, c, e, strokeWidth = 2f); drawCircle(CadMeasure.copy(alpha = 0.6f), r, c, style = Stroke(2f))
            drawCircle(CadMeasure, 4f, c); drawCircle(CadMeasure, 4f, e)
        }
    }
}
