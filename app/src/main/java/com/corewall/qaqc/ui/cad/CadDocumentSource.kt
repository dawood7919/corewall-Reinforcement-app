package com.corewall.qaqc.ui.cad

import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * مصدر رسم CAD مستقل عن صيغة الملف. يبقى العارض وأدوات القياس على CadDrawing
 * ولا تعرف إن كانت الهندسة آتية من DXF أو من قارئ DWG أصلي.
 */
interface CadDocumentSource {
    fun supports(file: File): Boolean
    fun load(file: File): CadLoadResult
}

enum class CadSourceFormat { DXF, DWG }

data class CadLoadResult(
    val drawing: CadDrawing?,
    val format: CadSourceFormat,
    val error: String? = null,
    val warnings: List<String> = emptyList()
)

object CadDocumentLoader {
    private val sources = listOf(NativeDwgDocumentSource, DxfDocumentSource)

    fun load(file: File): CadLoadResult =
        sources.firstOrNull { it.supports(file) }?.load(file)
            ?: CadLoadResult(null, CadSourceFormat.DXF, "صيغة CAD غير مدعومة")
}

private object DxfDocumentSource : CadDocumentSource {
    override fun supports(file: File): Boolean = file.extension.equals("dxf", ignoreCase = true)

    override fun load(file: File): CadLoadResult {
        val parsed = DxfParser.parseFile(file)
        return CadLoadResult(
            drawing = parsed.drawing,
            format = CadSourceFormat.DXF,
            error = parsed.error
        )
    }
}

/**
 * جسر DWG المحلي: يحمّل libcorewall_dwg مرة واحدة فقط. لا توجد شبكة أو تحويل
 * إلى PDF/صورة؛ تعاد الهندسة مباشرة بإحداثيات CAD الأصلية.
 */
private object NativeDwgBridge {
    private val loadFailure: Throwable? = runCatching {
        System.loadLibrary("corewall_dwg")
    }.exceptionOrNull()

    val available: Boolean get() = loadFailure == null
    val failureMessage: String get() = loadFailure?.javaClass?.simpleName ?: "غير معروف"

    external fun readDwgPayload(path: String): String
}

private object NativeDwgDocumentSource : CadDocumentSource {
    private val json = Json { ignoreUnknownKeys = true }

    override fun supports(file: File): Boolean = file.extension.equals("dwg", ignoreCase = true)

    override fun load(file: File): CadLoadResult {
        if (!file.exists() || !file.canRead()) {
            return CadLoadResult(null, CadSourceFormat.DWG, "الملف غير موجود أو غير قابل للقراءة")
        }
        if (!NativeDwgBridge.available) {
            return CadLoadResult(null, CadSourceFormat.DWG, "تعذر تحميل محرك DWG المحلي: ${NativeDwgBridge.failureMessage}")
        }
        return runCatching {
            val payload = json.decodeFromString<NativeDwgPayload>(NativeDwgBridge.readDwgPayload(file.absolutePath))
            if (!payload.ok) {
                CadLoadResult(null, CadSourceFormat.DWG, payload.error ?: "تعذر قراءة ملف DWG", payload.warnings)
            } else {
                val entities = payload.entities.mapNotNull(::toEntity)
                val layers = payload.layers
                    .map { CadLayer(it.name.ifBlank { "0" }, it.colorIndex, it.visible) }
                    .ifEmpty { listOf(CadLayer("0")) }
                val drawing = CadDrawing(entities, layers, computeBounds(entities), payload.insUnits)
                if (entities.isEmpty()) {
                    CadLoadResult(null, CadSourceFormat.DWG, "فُتح ملف DWG لكن لم تُستخرج كيانات هندسية مدعومة", payload.warnings)
                } else {
                    CadLoadResult(drawing, CadSourceFormat.DWG, warnings = payload.warnings)
                }
            }
        }.getOrElse { error ->
            CadLoadResult(null, CadSourceFormat.DWG, "فشل تحويل هندسة DWG: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    private fun toEntity(entity: NativeDwgEntity): CadEntity? {
        val v = entity.values
        val layer = entity.layer.ifBlank { "0" }
        return when (entity.type) {
            "LINE" -> v.takeIf { it.size >= 4 }?.let { CadEntity.Line(CadPoint(it[0], it[1]), CadPoint(it[2], it[3]), layer) }
            "POLYLINE" -> v.takeIf { it.size >= 4 && it.size % 2 == 0 }?.let {
                CadEntity.Polyline(it.chunked(2) { p -> CadPoint(p[0], p[1]) }, entity.closed, layer)
            }
            "CIRCLE" -> v.takeIf { it.size >= 3 && it[2] > 0 }?.let { CadEntity.Circle(CadPoint(it[0], it[1]), it[2], layer) }
            "ARC" -> v.takeIf { it.size >= 5 && it[2] > 0 }?.let { CadEntity.Arc(CadPoint(it[0], it[1]), it[2], it[3], it[4], layer) }
            "ELLIPSE" -> v.takeIf { it.size >= 8 }?.let {
                CadEntity.Ellipse(
                    center = CadPoint(it[0], it[1]),
                    majorAxis = CadPoint(it[2], it[3]),
                    minorAxis = CadPoint(it[4], it[5]),
                    startRad = it[6],
                    endRad = it[7],
                    layer = layer
                )
            }
            "POINT" -> v.takeIf { it.size >= 2 }?.let { CadEntity.PointEnt(CadPoint(it[0], it[1]), layer) }
            "TEXT" -> v.takeIf { it.size >= 4 }?.let {
                CadEntity.TextEnt(CadPoint(it[0], it[1]), it[2], entity.text.orEmpty(), it[3], layer)
            }
            else -> null
        }
    }
}

@Serializable
private data class NativeDwgPayload(
    val ok: Boolean,
    val error: String? = null,
    val warnings: List<String> = emptyList(),
    val insUnits: Int = 0,
    val layers: List<NativeDwgLayer> = emptyList(),
    val entities: List<NativeDwgEntity> = emptyList()
)

@Serializable
private data class NativeDwgLayer(val name: String, val colorIndex: Int = 7, val visible: Boolean = true)

@Serializable
private data class NativeDwgEntity(
    val type: String,
    val layer: String = "0",
    val values: List<Double> = emptyList(),
    val text: String? = null,
    val closed: Boolean = false
)
