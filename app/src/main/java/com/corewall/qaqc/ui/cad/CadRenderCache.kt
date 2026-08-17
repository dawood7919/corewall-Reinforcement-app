package com.corewall.qaqc.ui.cad

import android.graphics.Paint
import android.graphics.Path
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * منظور ثابت المرجع. لا يدخل scale/offset في مفتاح pointerInput؛ لذلك لا تُلغى
 * إيماءة التكبير بعد أول حركة، وهي كانت سبباً مباشراً لشعور التوقف في ملفات DWG.
 */
@Stable
internal class CadViewport {
    var scale: Float = 1f
        private set
    var offsetX: Float = 0f
        private set
    var offsetY: Float = 0f
        private set
    var fitted: Boolean = false
        private set
    var revision by mutableIntStateOf(0)
        private set

    fun reset() {
        scale = 1f; offsetX = 0f; offsetY = 0f; fitted = false; revision++
    }

    fun fit(bounds: Rect, size: IntSize) {
        if (size.width <= 0 || size.height <= 0) return
        val sx = size.width / max(bounds.width, 1f) * 0.9f
        val sy = size.height / max(bounds.height, 1f) * 0.9f
        scale = min(sx, sy)
        offsetX = size.width / 2f - (bounds.left + bounds.width / 2f) * scale
        offsetY = size.height / 2f + (bounds.top + bounds.height / 2f) * scale
        fitted = true
        revision++
    }

    /** تكبير حول مركز اللمس/القرص مع إزاحة موحدة؛ لا يقفز الرسم إلى الأصل. */
    fun transform(centroidX: Float, centroidY: Float, panX: Float, panY: Float, zoom: Float) {
        val previous = scale
        val next = (previous * zoom).coerceIn(0.01f, 500f)
        val worldX = (centroidX - offsetX) / previous
        val worldY = (offsetY - centroidY) / previous
        scale = next
        offsetX = centroidX - worldX * next + panX
        offsetY = centroidY + worldY * next + panY
        revision++
    }

    fun worldToScreen(point: CadPoint): Pair<Float, Float> =
        (point.x * scale + offsetX).toFloat() to (offsetY - point.y * scale).toFloat()

    fun screenToWorld(x: Float, y: Float): CadPoint =
        CadPoint(((x - offsetX) / scale).toDouble(), ((offsetY - y) / scale).toDouble())
}

internal data class CadPreparedScene(
    val geometryPath: Path,
    val strokePaint: Paint,
    val snapIndex: CadSnapIndex,
    val labels: List<CadEntity.TextEnt>,
    val visibleEntityCount: Int
)

/** فهرس خلايا ثابت للالتقاط، يمنع مسح آلاف/مئات آلاف الكيانات عند كل نقرة قياس. */
internal class CadSnapIndex private constructor(
    private val cellSize: Double,
    private val cells: Map<Long, List<CadPoint>>
) {
    fun nearest(point: CadPoint, tolerance: Double): CadPoint? {
        if (cells.isEmpty()) return null
        val centerX = floorCell(point.x)
        val centerY = floorCell(point.y)
        val range = ceil(tolerance / cellSize).toInt().coerceIn(1, 8)
        var best: CadPoint? = null
        var bestDistance = tolerance
        for (x in centerX - range..centerX + range) for (y in centerY - range..centerY + range) {
            cells[cellKey(x, y)]?.forEach { candidate ->
                val distance = point.distanceTo(candidate)
                if (distance < bestDistance) { bestDistance = distance; best = candidate }
            }
        }
        return best
    }

    private fun floorCell(value: Double): Int = kotlin.math.floor(value / cellSize).toInt()

    companion object {
        private const val MAX_POINTS = 500_000

        fun build(entities: List<CadEntity>, bounds: Rect): CadSnapIndex {
            val span = max(bounds.width, bounds.height).toDouble().coerceAtLeast(1e-4)
            val size = (span / 128.0).coerceAtLeast(1e-4)
            val buckets = HashMap<Long, MutableList<CadPoint>>()
            var count = 0
            fun add(point: CadPoint) {
                if (count >= MAX_POINTS || !point.x.isFinite() || !point.y.isFinite()) return
                val x = kotlin.math.floor(point.x / size).toInt()
                val y = kotlin.math.floor(point.y / size).toInt()
                buckets.getOrPut(cellKey(x, y)) { ArrayList(4) }.add(point)
                count++
            }
            fun arcPoint(center: CadPoint, radius: Double, degrees: Double) = CadPoint(
                center.x + radius * cos(Math.toRadians(degrees)),
                center.y + radius * sin(Math.toRadians(degrees))
            )
            entities.forEach { entity -> when (entity) {
                is CadEntity.Line -> { add(entity.a); add(entity.b); add(CadPoint((entity.a.x + entity.b.x) / 2, (entity.a.y + entity.b.y) / 2)) }
                is CadEntity.Polyline -> {
                    entity.points.forEach(::add)
                    entity.points.zipWithNext().forEach { (a, b) -> add(CadPoint((a.x + b.x) / 2, (a.y + b.y) / 2)) }
                    if (entity.closed && entity.points.size > 2) add(CadPoint((entity.points.first().x + entity.points.last().x) / 2, (entity.points.first().y + entity.points.last().y) / 2))
                }
                is CadEntity.Circle -> {
                    add(entity.center); add(CadPoint(entity.center.x + entity.radius, entity.center.y)); add(CadPoint(entity.center.x - entity.radius, entity.center.y)); add(CadPoint(entity.center.x, entity.center.y + entity.radius)); add(CadPoint(entity.center.x, entity.center.y - entity.radius))
                }
                is CadEntity.Arc -> { add(entity.center); add(arcPoint(entity.center, entity.radius, entity.startDeg)); add(arcPoint(entity.center, entity.radius, entity.endDeg)) }
                is CadEntity.Ellipse -> {
                    fun point(angle: Double) = CadPoint(entity.center.x + entity.majorAxis.x * cos(angle) + entity.minorAxis.x * sin(angle), entity.center.y + entity.majorAxis.y * cos(angle) + entity.minorAxis.y * sin(angle))
                    add(entity.center); add(point(entity.startRad)); add(point(entity.endRad)); add(CadPoint(entity.center.x + entity.majorAxis.x, entity.center.y + entity.majorAxis.y)); add(CadPoint(entity.center.x - entity.majorAxis.x, entity.center.y - entity.majorAxis.y))
                }
                is CadEntity.PointEnt -> add(entity.point)
                is CadEntity.TextEnt -> add(entity.position)
            } }
            return CadSnapIndex(size, buckets)
        }
    }
}

private fun cellKey(x: Int, y: Int): Long = (x.toLong() shl 32) xor (y.toLong() and 0xffffffffL)

/**
 * يسجل كل هندسة الرسم مرة واحدة كـ Picture. في كل إطار zoom لا يبقى إلا تحويل
 * Canvas واحد وإعادة تشغيل أوامر Skia، بدلاً من إنشاء Path وحساب sin/cos لكل كيان.
 */
internal object CadStaticPath {
    fun build(entities: List<CadEntity>, bounds: Rect): CadPreparedScene {
        val geometry = Path()
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFE8F1FF.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 0f // hairline: ثابت بالبكسل بعد zoom ولا يحتاج إعادة تسجيل.
        }
        val marker = (max(bounds.width, bounds.height).toDouble() / 600.0).coerceAtLeast(1e-3)
        fun appendArc(center: CadPoint, radius: Double, startDeg: Double, endDeg: Double) {
            var end = endDeg; if (end < startDeg) end += 360.0
            val first = arcPoint(center, radius, startDeg)
            geometry.moveTo(first.x.toFloat(), first.y.toFloat())
            val steps = max(8, ((end - startDeg) / 6.0).toInt())
            for (step in 1..steps) {
                val point = arcPoint(center, radius, startDeg + (end - startDeg) * step / steps)
                geometry.lineTo(point.x.toFloat(), point.y.toFloat())
            }
        }
        entities.forEach { entity -> when (entity) {
            is CadEntity.Line -> { geometry.moveTo(entity.a.x.toFloat(), entity.a.y.toFloat()); geometry.lineTo(entity.b.x.toFloat(), entity.b.y.toFloat()) }
            is CadEntity.Polyline -> if (entity.points.size > 1) {
                geometry.moveTo(entity.points.first().x.toFloat(), entity.points.first().y.toFloat())
                entity.points.drop(1).forEach { geometry.lineTo(it.x.toFloat(), it.y.toFloat()) }
                if (entity.closed) geometry.close()
            }
            is CadEntity.Circle -> geometry.addCircle(entity.center.x.toFloat(), entity.center.y.toFloat(), entity.radius.toFloat(), Path.Direction.CW)
            is CadEntity.Arc -> appendArc(entity.center, entity.radius, entity.startDeg, entity.endDeg)
            is CadEntity.Ellipse -> {
                var end = entity.endRad; if (end < entity.startRad) end += Math.PI * 2
                val steps = max(12, ((end - entity.startRad) / (Math.PI / 24)).toInt())
                for (step in 0..steps) {
                    val angle = entity.startRad + (end - entity.startRad) * step / steps
                    val x = entity.center.x + entity.majorAxis.x * cos(angle) + entity.minorAxis.x * sin(angle)
                    val y = entity.center.y + entity.majorAxis.y * cos(angle) + entity.minorAxis.y * sin(angle)
                    if (step == 0) geometry.moveTo(x.toFloat(), y.toFloat()) else geometry.lineTo(x.toFloat(), y.toFloat())
                }
            }
            is CadEntity.PointEnt -> {
                geometry.moveTo((entity.point.x - marker).toFloat(), entity.point.y.toFloat())
                geometry.lineTo((entity.point.x + marker).toFloat(), entity.point.y.toFloat())
                geometry.moveTo(entity.point.x.toFloat(), (entity.point.y - marker).toFloat())
                geometry.lineTo(entity.point.x.toFloat(), (entity.point.y + marker).toFloat())
            }
            // النص يرسم كطبقة screen-space مستقلة كي لا ينقلب رأساً على عقب عند تحويل محور Y.
            is CadEntity.TextEnt -> Unit
        } }
        return CadPreparedScene(
            geometryPath = geometry,
            strokePaint = stroke,
            snapIndex = CadSnapIndex.build(entities, bounds),
            labels = entities.filterIsInstance<CadEntity.TextEnt>(),
            visibleEntityCount = entities.size
        )
    }
}
