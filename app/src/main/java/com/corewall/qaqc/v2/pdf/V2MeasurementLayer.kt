package com.corewall.qaqc.v2.pdf

import kotlin.math.abs
import kotlin.math.hypot

/** أدوات مساحة العمل V2. الإصبع يتنقل دائماً؛ هذه الأدوات تستقبل S Pen فقط. */
internal enum class V2WorkspaceTool {
    NAVIGATE,
    AREA,
    LENGTH,
    COUNT,
    VOLUME,
    INK;

    val measurementKind: V2MeasurementKind?
        get() = when (this) {
            AREA -> V2MeasurementKind.AREA
            LENGTH -> V2MeasurementKind.LENGTH
            COUNT -> V2MeasurementKind.COUNT
            VOLUME -> V2MeasurementKind.VOLUME
            NAVIGATE, INK -> null
        }
}

internal enum class V2MeasurementKind(
    val minimumPoints: Int,
    val closesPath: Boolean,
    val unit: String
) {
    AREA(minimumPoints = 3, closesPath = true, unit = "m²"),
    LENGTH(minimumPoints = 2, closesPath = false, unit = "m"),
    COUNT(minimumPoints = 1, closesPath = false, unit = "pcs"),
    VOLUME(minimumPoints = 3, closesPath = true, unit = "m³")
}

/** نقطة محفوظة في فضاء الصفحة النسبي، لا في فضاء الشاشة أو البلاطات. */
internal data class V2DocumentPoint(val x: Float, val y: Float) {
    init {
        require(x.isFinite() && y.isFinite())
    }

    fun bounded() = V2DocumentPoint(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
}

/** المعايرة الموحّدة للصفحة؛ السمك مطلوب فقط لأداة الحجم. */
internal data class V2PageCalibration(
    val metresPerPoint: Double = Double.NaN,
    val thicknessMetres: Double? = null
) {
    val hasScale: Boolean get() = metresPerPoint.isFinite() && metresPerPoint > 0.0
    val hasThickness: Boolean get() = thicknessMetres?.let { it.isFinite() && it > 0.0 } == true
}

internal data class V2MeasurementRecord(
    val id: Long,
    val page: Int,
    val kind: V2MeasurementKind,
    val points: List<V2DocumentPoint>,
    val calibration: V2PageCalibration
)

internal data class V2InkStroke(
    val page: Int,
    val points: List<V2DocumentPoint>,
    /** عرض القلم بالبكسل المنطقي قبل تحويل المنظر. */
    val widthPx: Float
)

internal sealed interface V2MeasurementFinishResult {
    data object NoDraft : V2MeasurementFinishResult
    data class Incomplete(val requiredPoints: Int, val currentPoints: Int, val message: String) : V2MeasurementFinishResult
    data class Saved(val record: V2MeasurementRecord) : V2MeasurementFinishResult
}

/**
 * مخزن هندسة القياس والحبر لـV2. يُستدعى على خيط الواجهة فقط، لكنه لا يستخدم
 * Compose State، لذلك لا يتحول لمس القلم إلى سلسلة عمليات إعادة تركيب.
 */
internal class V2MeasurementLayer {
    private val completedMeasurements = mutableListOf<V2MeasurementRecord>()
    private val completedInk = mutableListOf<V2InkStroke>()

    private var selectedTool = V2WorkspaceTool.NAVIGATE
    private var calibration = V2PageCalibration()
    private var activeDraft: V2MeasurementDraft? = null
    private var activeInk: MutableInkStroke? = null
    private var nextMeasurementId = 1L

    val tool: V2WorkspaceTool get() = selectedTool
    val capturesStylus: Boolean get() = selectedTool != V2WorkspaceTool.NAVIGATE || activeInk != null
    val measurements: List<V2MeasurementRecord> get() = completedMeasurements
    val inkStrokes: List<V2InkStroke> get() = completedInk
    val draft: V2MeasurementDraft? get() = activeDraft
    val liveInk: MutableInkStroke? get() = activeInk

    fun selectTool(tool: V2WorkspaceTool) {
        selectedTool = tool
    }

    fun setCalibration(value: V2PageCalibration) {
        calibration = value
    }

    fun onStylusDown(point: V2DocumentPoint, page: Int, pressure: Float, isEraser: Boolean) {
        if (isEraser) {
            eraseNearestInk(point, page)
            return
        }
        when (selectedTool) {
            V2WorkspaceTool.INK -> activeInk = MutableInkStroke(page, point, pressure)
            V2WorkspaceTool.NAVIGATE -> Unit
            else -> beginMeasurementPoint(point, page)
        }
    }

    fun onStylusMove(point: V2DocumentPoint, page: Int, pressure: Float) {
        activeInk?.takeIf { it.page == page }?.append(point, pressure)
        activeDraft?.takeIf { it.page == page }?.append(point)
    }

    fun onStylusUp(point: V2DocumentPoint?, page: Int, pressure: Float) {
        activeInk?.takeIf { it.page == page }?.let { stroke ->
            point?.let { stroke.append(it, pressure) }
            if (stroke.points.size >= 2) completedInk += stroke.freeze()
            activeInk = null
        }
        point?.let { activeDraft?.takeIf { draft -> draft.page == page }?.append(it) }
    }

    /**
     * لا يمسح المسودة عند عدم اكتمالها. هذا هو الحاجز الصريح ضد عودة مشكلة
     * «إنهاء» التي كانت تخفي الرسم قبل أن يبلغ المستخدم بسبب عدم الحفظ.
     */
    fun finishMeasurement(): V2MeasurementFinishResult {
        val pending = activeDraft ?: return V2MeasurementFinishResult.NoDraft
        if (pending.points.size < pending.kind.minimumPoints) {
            return V2MeasurementFinishResult.Incomplete(
                requiredPoints = pending.kind.minimumPoints,
                currentPoints = pending.points.size,
                message = when (pending.kind) {
                    V2MeasurementKind.LENGTH -> "أداة الطول تحتاج نقطتين على الأقل"
                    V2MeasurementKind.COUNT -> "أداة العد تحتاج علامة واحدة على الأقل"
                    V2MeasurementKind.AREA, V2MeasurementKind.VOLUME -> "أداة المساحة تحتاج ثلاث نقاط على الأقل"
                }
            )
        }
        val saved = V2MeasurementRecord(
            id = nextMeasurementId++,
            page = pending.page,
            kind = pending.kind,
            points = pending.points.toList(),
            calibration = calibration
        )
        completedMeasurements += saved
        activeDraft = null
        return V2MeasurementFinishResult.Saved(saved)
    }

    fun undoLastDraftPoint(): Boolean {
        val pending = activeDraft ?: return false
        if (pending.points.isEmpty()) return false
        pending.points.removeAt(pending.points.lastIndex)
        if (pending.points.isEmpty()) activeDraft = null
        return true
    }

    fun cancelDraft() {
        activeDraft = null
        activeInk = null
    }

    /** إلغاء عيّنة قلم جارية فقط؛ المسودة السابقة تبقى قابلة للإنهاء. */
    fun cancelStylusStroke() {
        activeInk = null
    }

    fun clearPage(page: Int) {
        completedMeasurements.removeAll { it.page == page }
        completedInk.removeAll { it.page == page }
        if (activeDraft?.page == page) activeDraft = null
        if (activeInk?.page == page) activeInk = null
    }

    private fun beginMeasurementPoint(point: V2DocumentPoint, page: Int) {
        val kind = selectedTool.measurementKind ?: return
        if (kind == V2MeasurementKind.COUNT) {
            completedMeasurements += V2MeasurementRecord(
                id = nextMeasurementId++,
                page = page,
                kind = kind,
                points = listOf(point),
                calibration = calibration
            )
            return
        }

        val pending = activeDraft
        if (pending == null) {
            activeDraft = V2MeasurementDraft(page, kind, mutableListOf(point))
        } else if (pending.page == page && pending.kind == kind) {
            pending.append(point)
        }
        // لا نستبدل مسودة نوع/صفحة أخرى صامتاً؛ المستخدم ينهيها أو يلغيها صراحةً.
    }

    private fun eraseNearestInk(point: V2DocumentPoint, page: Int) {
        val candidate = completedInk
            .withIndex()
            .filter { it.value.page == page }
            .minByOrNull { (_, stroke) -> stroke.points.minOfOrNull { distance(it, point) } ?: Double.MAX_VALUE }
            ?: return
        val nearest = candidate.value.points.minOfOrNull { distance(it, point) } ?: return
        if (nearest <= INK_ERASE_RADIUS) completedInk.removeAt(candidate.index)
    }

    private fun distance(a: V2DocumentPoint, b: V2DocumentPoint): Double =
        hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())

    internal data class V2MeasurementDraft(
        val page: Int,
        val kind: V2MeasurementKind,
        val points: MutableList<V2DocumentPoint>
    ) {
        fun append(point: V2DocumentPoint) {
            val previous = points.lastOrNull()
            if (previous == null || hypot((point.x - previous.x).toDouble(), (point.y - previous.y).toDouble()) >= MIN_POINT_DELTA) {
                points += point
            }
        }
    }

    internal class MutableInkStroke(page: Int, point: V2DocumentPoint, pressure: Float) {
        val page = page
        val points = mutableListOf(point)
        private var pressureTotal = normalizedPressure(pressure)
        private var pressureSamples = 1

        fun append(point: V2DocumentPoint, pressure: Float) {
            val previous = points.lastOrNull()
            if (previous == null || hypot((point.x - previous.x).toDouble(), (point.y - previous.y).toDouble()) >= MIN_POINT_DELTA) {
                points += point
                pressureTotal += normalizedPressure(pressure)
                pressureSamples++
            }
        }

        val widthPx: Float
            get() {
                val average = pressureTotal / pressureSamples.coerceAtLeast(1)
                return (1.7f + average * 2.1f).coerceIn(1.7f, 3.8f)
            }

        fun freeze(): V2InkStroke = V2InkStroke(page, points.toList(), widthPx)
    }

    private companion object {
        const val MIN_POINT_DELTA = 0.0015
        const val INK_ERASE_RADIUS = 0.018

        fun normalizedPressure(value: Float): Float = if (value > 0f) value.coerceIn(0f, 1f) else 0.5f
    }
}

/** حسابات V2 الخالصة؛ لا تستقبل تكبير المنظر أو إزاحته إطلاقاً. */
internal object V2MeasurementMath {
    fun quantity(record: V2MeasurementRecord, pageWidthPt: Float, pageHeightPt: Float): Double? {
        return when (record.kind) {
            V2MeasurementKind.COUNT -> record.points.size.toDouble()
            V2MeasurementKind.LENGTH -> length(record.points, pageWidthPt, pageHeightPt, record.calibration)
            V2MeasurementKind.AREA -> area(record.points, pageWidthPt, pageHeightPt, record.calibration)
            V2MeasurementKind.VOLUME -> {
                val thickness = record.calibration.thicknessMetres ?: return null
                area(record.points, pageWidthPt, pageHeightPt, record.calibration)?.times(thickness)
            }
        }
    }

    private fun length(
        points: List<V2DocumentPoint>, pageWidthPt: Float, pageHeightPt: Float, calibration: V2PageCalibration
    ): Double? {
        if (points.size < 2 || !calibration.hasScale) return null
        return points.zipWithNext().sumOf { (a, b) ->
            hypot(
                (b.x - a.x).toDouble() * pageWidthPt,
                (b.y - a.y).toDouble() * pageHeightPt
            )
        } * calibration.metresPerPoint
    }

    private fun area(
        points: List<V2DocumentPoint>, pageWidthPt: Float, pageHeightPt: Float, calibration: V2PageCalibration
    ): Double? {
        if (points.size < 3 || !calibration.hasScale) return null
        var shoelace = 0.0
        for (index in points.indices) {
            val a = points[index]
            val b = points[(index + 1) % points.size]
            shoelace += (a.x * pageWidthPt).toDouble() * (b.y * pageHeightPt) -
                (b.x * pageWidthPt).toDouble() * (a.y * pageHeightPt)
        }
        return abs(shoelace) * 0.5 * calibration.metresPerPoint * calibration.metresPerPoint
    }
}
