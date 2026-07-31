package com.corewall.qaqc.ui.cad

import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.charset.Charset

/**
 * محلل DXF نصي (ASCII) — LINE, LWPOLYLINE, POLYLINE, CIRCLE, ARC, TEXT, MTEXT.
 * ملفات DWG الثنائية تُكتشف ويُطلب تحويلها لـ DXF.
 */
object DxfParser {

    data class ParseResult(
        val drawing: CadDrawing?,
        val error: String? = null,
        val isBinaryDwg: Boolean = false
    )

    fun parseFile(file: File): ParseResult {
        if (!file.exists() || !file.canRead()) {
            return ParseResult(null, "الملف غير موجود أو غير قابل للقراءة")
        }
        val header = ByteArray(6)
        FileInputStream(file).use { ins ->
            val n = ins.read(header)
            if (n >= 4) {
                val sig = String(header, 0, minOf(n, 6), Charsets.US_ASCII)
                if (sig.startsWith("AC10") || sig.startsWith("AC1.")) {
                    return ParseResult(
                        null,
                        "هذا ملف DWG ثنائي (صيغة أوتوكاد المغلقة).\n" +
                            "من أوتوكاد: File → Save As → DXF ثم افتحه هنا.\n" +
                            "أو افتحه بتطبيق CAD خارجي.",
                        isBinaryDwg = true
                    )
                }
            }
        }
        return try {
            val pairs = readGroupCodes(file)
            val drawing = buildDrawing(pairs)
            if (drawing.entities.isEmpty()) {
                ParseResult(null, "اتفتح الملف لكن مفيش كيانات هندسية مدعومة")
            } else ParseResult(drawing)
        } catch (t: Throwable) {
            ParseResult(null, "فشل تحليل DXF: ${t.message ?: t.javaClass.simpleName}")
        }
    }

    private fun readGroupCodes(file: File): List<Pair<Int, String>> {
        val out = ArrayList<Pair<Int, String>>(4096)
        BufferedReader(InputStreamReader(FileInputStream(file), Charsets.UTF_8)).use { br ->
            while (true) {
                val codeLine = br.readLine() ?: break
                val valueLine = br.readLine() ?: break
                val code = codeLine.trim().toIntOrNull() ?: continue
                out.add(code to valueLine.trimEnd('\r'))
            }
        }
        return out
    }

    private fun buildDrawing(pairs: List<Pair<Int, String>>): CadDrawing {
        var insUnits = 0
        val entities = mutableListOf<CadEntity>()
        val layerNames = linkedSetOf<String>()
        var i = 0
        var inEntities = false
        var inHeader = false
        while (i < pairs.size) {
            val (code, value) = pairs[i]
            if (code == 0 && value.equals("SECTION", true)) {
                val name = pairs.getOrNull(i + 1)?.takeIf { it.first == 2 }?.second.orEmpty()
                inHeader = name.equals("HEADER", true)
                inEntities = name.equals("ENTITIES", true)
                i += 2; continue
            }
            if (code == 0 && value.equals("ENDSEC", true)) {
                inHeader = false; inEntities = false; i++; continue
            }
            if (inHeader && code == 9 && value.equals("\$INSUNITS", true)) {
                val next = pairs.getOrNull(i + 1)
                if (next != null && next.first == 70) insUnits = next.second.trim().toIntOrNull() ?: 0
            }
            if (inEntities && code == 0) {
                val ent = value.uppercase()
                val (entity, nextI) = when (ent) {
                    "LINE" -> parseLine(pairs, i, layerNames)
                    "LWPOLYLINE" -> parseLwPolyline(pairs, i, layerNames)
                    "POLYLINE" -> parsePolyline(pairs, i, layerNames)
                    "CIRCLE" -> parseCircle(pairs, i, layerNames)
                    "ARC" -> parseArc(pairs, i, layerNames)
                    "TEXT", "MTEXT" -> parseText(pairs, i, layerNames, ent == "MTEXT")
                    else -> null to skipEntity(pairs, i)
                }
                if (entity != null) entities.add(entity)
                i = nextI; continue
            }
            i++
        }
        val layers = layerNames.map { CadLayer(it) }.ifEmpty { listOf(CadLayer("0")) }
        return CadDrawing(entities, layers, computeBounds(entities), insUnits)
    }

    private fun skipEntity(pairs: List<Pair<Int, String>>, start: Int): Int {
        var i = start + 1
        while (i < pairs.size) { if (pairs[i].first == 0) return i; i++ }
        return pairs.size
    }

    private fun layerOf(map: Map<Int, String>, layerNames: MutableSet<String>): String {
        val l = map[8] ?: "0"; layerNames.add(l); return l
    }

    private fun collectUntilNext0(pairs: List<Pair<Int, String>>, start: Int): Pair<Map<Int, String>, Int> {
        val map = mutableMapOf<Int, String>()
        var i = start + 1
        while (i < pairs.size && pairs[i].first != 0) { map[pairs[i].first] = pairs[i].second; i++ }
        return map to i
    }

    private fun parseLine(pairs: List<Pair<Int, String>>, start: Int, layerNames: MutableSet<String>): Pair<CadEntity?, Int> {
        val (m, next) = collectUntilNext0(pairs, start)
        val x1 = m[10]?.toDoubleOrNull() ?: return null to next
        val y1 = m[20]?.toDoubleOrNull() ?: return null to next
        val x2 = m[11]?.toDoubleOrNull() ?: return null to next
        val y2 = m[21]?.toDoubleOrNull() ?: return null to next
        return CadEntity.Line(CadPoint(x1, y1), CadPoint(x2, y2), layerOf(m, layerNames)) to next
    }

    private fun parseLwPolyline(pairs: List<Pair<Int, String>>, start: Int, layerNames: MutableSet<String>): Pair<CadEntity?, Int> {
        var i = start + 1; var layer = "0"; var closed = false
        val pts = mutableListOf<CadPoint>(); var pendingX: Double? = null
        while (i < pairs.size && pairs[i].first != 0) {
            when (pairs[i].first) {
                8 -> layer = pairs[i].second
                70 -> closed = (pairs[i].second.toIntOrNull() ?: 0) and 1 != 0
                10 -> pendingX = pairs[i].second.toDoubleOrNull()
                20 -> {
                    val x = pendingX; val y = pairs[i].second.toDoubleOrNull()
                    if (x != null && y != null) pts.add(CadPoint(x, y))
                    pendingX = null
                }
            }
            i++
        }
        layerNames.add(layer)
        if (pts.size < 2) return null to i
        return CadEntity.Polyline(pts, closed, layer) to i
    }

    private fun parsePolyline(pairs: List<Pair<Int, String>>, start: Int, layerNames: MutableSet<String>): Pair<CadEntity?, Int> {
        val (m, afterHeader) = collectUntilNext0(pairs, start)
        val layer = layerOf(m, layerNames)
        val closed = (m[70]?.toIntOrNull() ?: 0) and 1 != 0
        val pts = mutableListOf<CadPoint>(); var i = afterHeader
        while (i < pairs.size) {
            if (pairs[i].first == 0) {
                when (pairs[i].second.uppercase()) {
                    "VERTEX" -> {
                        val (vm, ni) = collectUntilNext0(pairs, i)
                        val x = vm[10]?.toDoubleOrNull(); val y = vm[20]?.toDoubleOrNull()
                        if (x != null && y != null) pts.add(CadPoint(x, y))
                        i = ni; continue
                    }
                    "SEQEND" -> { i = skipEntity(pairs, i); break }
                    else -> break
                }
            } else i++
        }
        if (pts.size < 2) return null to i
        return CadEntity.Polyline(pts, closed, layer) to i
    }

    private fun parseCircle(pairs: List<Pair<Int, String>>, start: Int, layerNames: MutableSet<String>): Pair<CadEntity?, Int> {
        val (m, next) = collectUntilNext0(pairs, start)
        val cx = m[10]?.toDoubleOrNull() ?: return null to next
        val cy = m[20]?.toDoubleOrNull() ?: return null to next
        val r = m[40]?.toDoubleOrNull() ?: return null to next
        if (r <= 0) return null to next
        return CadEntity.Circle(CadPoint(cx, cy), r, layerOf(m, layerNames)) to next
    }

    private fun parseArc(pairs: List<Pair<Int, String>>, start: Int, layerNames: MutableSet<String>): Pair<CadEntity?, Int> {
        val (m, next) = collectUntilNext0(pairs, start)
        val cx = m[10]?.toDoubleOrNull() ?: return null to next
        val cy = m[20]?.toDoubleOrNull() ?: return null to next
        val r = m[40]?.toDoubleOrNull() ?: return null to next
        val a0 = m[50]?.toDoubleOrNull() ?: 0.0
        val a1 = m[51]?.toDoubleOrNull() ?: 360.0
        if (r <= 0) return null to next
        return CadEntity.Arc(CadPoint(cx, cy), r, a0, a1, layerOf(m, layerNames)) to next
    }

    private fun parseText(pairs: List<Pair<Int, String>>, start: Int, layerNames: MutableSet<String>, mtext: Boolean): Pair<CadEntity?, Int> {
        val (m, next) = collectUntilNext0(pairs, start)
        val x = m[10]?.toDoubleOrNull() ?: return null to next
        val y = m[20]?.toDoubleOrNull() ?: return null to next
        val h = m[40]?.toDoubleOrNull() ?: 1.0
        val rot = m[50]?.toDoubleOrNull() ?: 0.0
        val raw = m[1] ?: return null to next
        val text = if (mtext) stripMtext(raw) else raw
        if (text.isBlank()) return null to next
        return CadEntity.TextEnt(CadPoint(x, y), h, text, rot, layerOf(m, layerNames)) to next
    }

    private fun stripMtext(s: String): String =
        s.replace(Regex("\\\\[A-Za-z][^;]*;"), "").replace(Regex("[{}]"), "").replace("\\P", "\n").trim()

    fun unitsPerMeterFromInsUnits(insUnits: Int): Double = when (insUnits) {
        1 -> 39.3700787402; 2 -> 3.280839895; 4 -> 1000.0; 5 -> 100.0; 6 -> 1.0; else -> 1.0
    }
}
