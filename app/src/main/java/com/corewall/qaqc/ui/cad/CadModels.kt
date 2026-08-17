package com.corewall.qaqc.ui.cad

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** نقطة في إحداثيات الرسم (وحدات CAD — عادة متر أو مم حسب الملف). */
@Serializable
data class CadPoint(val x: Double, val y: Double) {
    fun toOffset() = Offset(x.toFloat(), y.toFloat())
    fun distanceTo(o: CadPoint) = hypot(o.x - x, o.y - y)
}

sealed class CadEntity {
    abstract val layer: String
    data class Line(val a: CadPoint, val b: CadPoint, override val layer: String) : CadEntity()
    data class Polyline(
        val points: List<CadPoint>,
        val closed: Boolean,
        override val layer: String
    ) : CadEntity()
    data class Circle(val center: CadPoint, val radius: Double, override val layer: String) : CadEntity()
    data class Arc(
        val center: CadPoint,
        val radius: Double,
        val startDeg: Double,
        val endDeg: Double,
        override val layer: String
    ) : CadEntity()
    /** محوران متجهان، لذلك تبقى القطوع الناقصة صحيحة بعد INSERT غير منتظم المقياس. */
    data class Ellipse(
        val center: CadPoint,
        val majorAxis: CadPoint,
        val minorAxis: CadPoint,
        val startRad: Double = 0.0,
        val endRad: Double = Math.PI * 2,
        override val layer: String
    ) : CadEntity()
    data class PointEnt(val point: CadPoint, override val layer: String) : CadEntity()
    data class TextEnt(
        val position: CadPoint,
        val height: Double,
        val value: String,
        val rotationDeg: Double,
        override val layer: String
    ) : CadEntity()
}

data class CadLayer(val name: String, val colorIndex: Int = 7, var visible: Boolean = true)

data class CadDrawing(
    val entities: List<CadEntity>,
    val layers: List<CadLayer>,
    val bounds: Rect,
    val insUnits: Int = 0
) {
    fun visibleEntities(activeLayers: List<CadLayer> = layers): List<CadEntity> {
        val vis = activeLayers.filter { it.visible }.map { it.name }.toSet()
        return entities.filter { it.layer in vis || activeLayers.none { layer -> layer.name == it.layer } }
    }
}

enum class CadMeasureTool {
    PAN, DISTANCE, CONTINUOUS, AREA, ANGLE, RADIUS, CALIBRATE
}

enum class MeasureUnit(val label: String, val toMeters: Double) {
    MM("مم", 0.001),
    CM("سم", 0.01),
    M("م", 1.0),
    FT("قدم", 0.3048);

    fun format(valueInDrawingUnits: Double, unitsPerMeter: Double): String {
        val meters = valueInDrawingUnits / unitsPerMeter
        val v = meters / toMeters
        return when {
            abs(v) >= 1000 -> "%.1f %s".format(v, label)
            abs(v) >= 10 -> "%.2f %s".format(v, label)
            else -> "%.3f %s".format(v, label)
        }
    }

    fun formatArea(areaInDrawingUnitsSq: Double, unitsPerMeter: Double): String {
        val m2 = areaInDrawingUnitsSq / (unitsPerMeter * unitsPerMeter)
        val factor = toMeters * toMeters
        val v = m2 / factor
        val unitLabel = when (this) {
            MM -> "مم²"
            CM -> "سم²"
            M -> "م²"
            FT -> "قدم²"
        }
        return when {
            abs(v) >= 1000 -> "%.1f %s".format(v, unitLabel)
            abs(v) >= 10 -> "%.2f %s".format(v, unitLabel)
            else -> "%.3f %s".format(v, unitLabel)
        }
    }
}

sealed class CadMeasurement {
    abstract val id: Long
    abstract fun label(unit: MeasureUnit, unitsPerMeter: Double): String

    data class Distance(
        override val id: Long,
        val a: CadPoint,
        val b: CadPoint
    ) : CadMeasurement() {
        val length get() = a.distanceTo(b)
        override fun label(unit: MeasureUnit, unitsPerMeter: Double) =
            "مسافة: ${unit.format(length, unitsPerMeter)}"
    }

    data class Continuous(
        override val id: Long,
        val points: List<CadPoint>
    ) : CadMeasurement() {
        val segments: List<Double>
            get() = points.zipWithNext { p, q -> p.distanceTo(q) }
        val total get() = segments.sum()
        override fun label(unit: MeasureUnit, unitsPerMeter: Double) =
            "متصل: ${unit.format(total, unitsPerMeter)} (${segments.size} قطعة)"
    }

    data class AreaPoly(
        override val id: Long,
        val points: List<CadPoint>
    ) : CadMeasurement() {
        val area: Double
            get() {
                if (points.size < 3) return 0.0
                var s = 0.0
                for (i in points.indices) {
                    val j = (i + 1) % points.size
                    s += points[i].x * points[j].y - points[j].x * points[i].y
                }
                return abs(s) / 2.0
            }
        val perimeter: Double
            get() {
                if (points.size < 2) return 0.0
                var p = 0.0
                for (i in points.indices) {
                    val j = (i + 1) % points.size
                    p += points[i].distanceTo(points[j])
                }
                return p
            }
        override fun label(unit: MeasureUnit, unitsPerMeter: Double) =
            "مساحة: ${unit.formatArea(area, unitsPerMeter)} · محيط: ${unit.format(perimeter, unitsPerMeter)}"
    }

    data class Angle(
        override val id: Long,
        val vertex: CadPoint,
        val armA: CadPoint,
        val armB: CadPoint
    ) : CadMeasurement() {
        val degrees: Double
            get() {
                val a1 = atan2(armA.y - vertex.y, armA.x - vertex.x)
                val a2 = atan2(armB.y - vertex.y, armB.x - vertex.x)
                var d = Math.toDegrees(a2 - a1)
                while (d < 0) d += 360.0
                if (d > 180.0) d = 360.0 - d
                return d
            }
        override fun label(unit: MeasureUnit, unitsPerMeter: Double) =
            "زاوية: %.2f°".format(degrees)
    }

    data class Radius(
        override val id: Long,
        val center: CadPoint,
        val edge: CadPoint
    ) : CadMeasurement() {
        val r get() = center.distanceTo(edge)
        override fun label(unit: MeasureUnit, unitsPerMeter: Double) =
            "نق: ${unit.format(r, unitsPerMeter)} · قطر: ${unit.format(r * 2, unitsPerMeter)}"
    }
}

fun computeBounds(entities: List<CadEntity>): Rect {
    if (entities.isEmpty()) return Rect(0f, 0f, 100f, 100f)
    var minX = Double.POSITIVE_INFINITY
    var minY = Double.POSITIVE_INFINITY
    var maxX = Double.NEGATIVE_INFINITY
    var maxY = Double.NEGATIVE_INFINITY
    fun acc(p: CadPoint) {
        minX = min(minX, p.x); minY = min(minY, p.y)
        maxX = max(maxX, p.x); maxY = max(maxY, p.y)
    }
    for (e in entities) {
        when (e) {
            is CadEntity.Line -> { acc(e.a); acc(e.b) }
            is CadEntity.Polyline -> e.points.forEach(::acc)
            is CadEntity.Circle -> {
                acc(CadPoint(e.center.x - e.radius, e.center.y - e.radius))
                acc(CadPoint(e.center.x + e.radius, e.center.y + e.radius))
            }
            is CadEntity.Arc -> {
                acc(CadPoint(e.center.x - e.radius, e.center.y - e.radius))
                acc(CadPoint(e.center.x + e.radius, e.center.y + e.radius))
            }
            is CadEntity.Ellipse -> {
                var end = e.endRad
                if (end < e.startRad) end += Math.PI * 2
                repeat(37) { step ->
                    val a = e.startRad + (end - e.startRad) * step / 36.0
                    acc(CadPoint(
                        e.center.x + e.majorAxis.x * cos(a) + e.minorAxis.x * sin(a),
                        e.center.y + e.majorAxis.y * cos(a) + e.minorAxis.y * sin(a)
                    ))
                }
            }
            is CadEntity.PointEnt -> acc(e.point)
            is CadEntity.TextEnt -> acc(e.position)
        }
    }
    if (!minX.isFinite()) return Rect(0f, 0f, 100f, 100f)
    val pad = max(maxX - minX, maxY - minY) * 0.02 + 1.0
    return Rect(
        (minX - pad).toFloat(),
        (minY - pad).toFloat(),
        (maxX - minX + 2 * pad).toFloat(),
        (maxY - minY + 2 * pad).toFloat()
    )
}

fun arcPoint(center: CadPoint, radius: Double, deg: Double): CadPoint {
    val r = Math.toRadians(deg)
    return CadPoint(center.x + radius * cos(r), center.y + radius * sin(r))
}
