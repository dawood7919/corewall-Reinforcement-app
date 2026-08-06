package com.corewall.qaqc.data

import com.corewall.qaqc.data.db.ImportedMarkEntity
import com.corewall.qaqc.data.model.BeamRange
import com.corewall.qaqc.data.model.WallRange
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * استيراد أكواد (marks) من ملف المستخدم.
 *
 * جدول المكتب في الأصول للقراية بس. الملف اللي بيتستورد هنا بيتحطّ **فوقه**
 * في طبقة منفصلة، فالمهندس يقدر يضيف كمراته الداخلية من غير ما حد يلمس
 * الجدول الأصلي، ويقدر يمسح اللي ضافه ويرجع للأصل في أي وقت.
 *
 * الصيغتين مدعومتين عن قصد:
 * • **CSV** — اللي طالع من الإكسل، وده اللي المهندس معاه فعلاً.
 * • **JSON** — بنفس شكل `schedule-data.json`، عشان لو المكتب باعت جزء من
 *   ملفه يتلزق زي ما هو.
 *
 * التحقق هنا مش رفاهية: كود أدوار غلط بيعدّي بصمت وبعدين الكمرة ما بتظهرش
 * في أي دور، والمهندس يفتكر إن الاستيراد نجح. فالسطر الغلط بيترفض
 * **باسمه ورقمه وسببه**.
 */
object ScheduleImport {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** نتيجة الاستيراد — ناجحة جزئياً برضه نتيجة، المهم إنها مفهومة. */
    data class Outcome(
        val marks: List<ImportedMarkEntity> = emptyList(),
        /** أسطر اترفضت، كل واحد برقمه وسببه. */
        val rejected: List<String> = emptyList(),
        /** أكواد بتغطّي على جدول المكتب — تحذير مش خطأ. */
        val overrides: List<String> = emptyList(),
        val fatal: String? = null
    ) {
        val rowCount: Int get() = marks.sumOf { it.rowCount }

        /** سطر واحد يتعرض للمستخدم. */
        fun message(): String = when {
            fatal != null -> "فشل الاستيراد: $fatal"
            marks.isEmpty() -> "الملف مافيهوش أي صف صالح." +
                if (rejected.isNotEmpty()) " اترفض ${rejected.size} سطر." else ""
            else -> buildString {
                append("اتستورد ${marks.size} كود و$rowCount صف")
                if (overrides.isNotEmpty()) append(" • ${overrides.size} كود بيغطّي على جدول المكتب")
                if (rejected.isNotEmpty()) append(" • اترفض ${rejected.size} سطر")
            }
        }
    }

    /** الشكل اللي بيتقبل من JSON — نفس مفاتيح `schedule-data.json`. */
    @Serializable
    private data class ScheduleSlice(
        val beams: Map<String, List<BeamRange>> = emptyMap(),
        val walls: Map<String, List<WallRange>> = emptyMap()
    )

    /**
     * [knownLevels] أكواد الأدوار المسموح بيها — الترتيب مهم لأن التحقق من
     * أن النهاية بعد البداية بيتعمل بالترتيب مش بالاسم.
     * [existingMarks] أكواد جدول المكتب — عشان نعرف مين بيغطّي على مين.
     */
    fun parse(
        content: String,
        source: String,
        knownLevels: List<String>,
        existingMarks: Set<String>
    ): Outcome {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return Outcome(fatal = "الملف فاضي")
        return if (trimmed.startsWith("{")) parseJson(trimmed, source, knownLevels, existingMarks)
        else parseCsv(trimmed, source, knownLevels, existingMarks)
    }

    // ─────────────────────────────────────────────────────────── JSON

    private fun parseJson(
        content: String,
        source: String,
        knownLevels: List<String>,
        existingMarks: Set<String>
    ): Outcome {
        val slice = runCatching { json.decodeFromString<ScheduleSlice>(content) }
            .getOrElse { return Outcome(fatal = "الـJSON مش مقروء — ${it.message.orEmpty().take(120)}") }

        if (slice.beams.isEmpty() && slice.walls.isEmpty())
            return Outcome(fatal = "مالقيتش مفتاح \"beams\" ولا \"walls\" في الملف")

        val now = System.currentTimeMillis()
        val rejected = mutableListOf<String>()
        val marks = mutableListOf<ImportedMarkEntity>()

        slice.beams.forEach { (mark, rows) ->
            val good = rows.filterIndexed { i, r ->
                val why = checkRange(r.from, r.to, knownLevels, endInclusive = true)
                if (why != null) rejected += "$mark صف ${i + 1}: $why"
                why == null
            }
            if (good.isNotEmpty()) marks += ImportedMarkEntity(
                mark = mark.trim(), kind = ImportedMarkEntity.BEAM,
                rowsJson = json.encodeToString(good), source = source,
                rowCount = good.size, createdAt = now
            )
        }
        slice.walls.forEach { (mark, rows) ->
            val good = rows.filterIndexed { i, r ->
                val why = checkRange(r.from, r.to, knownLevels, endInclusive = false)
                if (why != null) rejected += "$mark صف ${i + 1}: $why"
                why == null
            }
            if (good.isNotEmpty()) marks += ImportedMarkEntity(
                mark = mark.trim(), kind = ImportedMarkEntity.WALL,
                rowsJson = json.encodeToString(good), source = source,
                rowCount = good.size, createdAt = now
            )
        }

        return Outcome(
            marks = marks,
            rejected = rejected,
            overrides = marks.map { it.mark }.filter { it in existingMarks }
        )
    }

    // ──────────────────────────────────────────────────────────── CSV

    /**
     * أعمدة CSV للكمرات (العنوان مطلوب، والترتيب مش مهم):
     * `mark,from,to,w,d,B1,B2,B3,T1,T2,T3,side,links,note`
     *
     * للحوائط: `mark,from,to,w,v,h,t,note` مع `kind=WALL` في عمود اختياري
     * أو استخدام عمود `v` كعلامة إن ده حائط.
     */
    private fun parseCsv(
        content: String,
        source: String,
        knownLevels: List<String>,
        existingMarks: Set<String>
    ): Outcome {
        val lines = content.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        if (lines.size < 2) return Outcome(fatal = "الملف محتاج سطر عناوين وسطر بيانات على الأقل")

        val header = splitCsv(lines.first()).map { it.trim().lowercase() }
        fun col(name: String) = header.indexOf(name)

        val iMark = col("mark")
        val iFrom = col("from")
        if (iMark < 0 || iFrom < 0)
            return Outcome(fatal = "لازم يكون فيه عمود mark وعمود from في سطر العناوين")

        val iTo = col("to")
        val iW = col("w"); val iD = col("d")
        val iSide = col("side"); val iLinks = col("links"); val iNote = col("note")
        val iV = col("v"); val iH = col("h"); val iT = col("t")
        // وجود عمود v/h معناه إن الملف حوائط — الكمرة مالهاش تسليح رأسي/أفقي
        val isWallFile = iV >= 0 && iH >= 0

        val now = System.currentTimeMillis()
        val rejected = mutableListOf<String>()
        val beams = linkedMapOf<String, MutableList<BeamRange>>()
        val walls = linkedMapOf<String, MutableList<WallRange>>()

        lines.drop(1).forEachIndexed { idx, line ->
            val lineNo = idx + 2 // +1 للعنوان و+1 عشان الترقيم يبدأ من ١
            val cells = splitCsv(line)
            fun cell(i: Int): String = if (i in cells.indices) cells[i].trim() else ""

            val mark = cell(iMark)
            if (mark.isEmpty()) { rejected += "سطر $lineNo: الكود (mark) فاضي"; return@forEachIndexed }

            val from = cell(iFrom)
            val to = cell(iTo).takeIf { it.isNotEmpty() }
            val why = checkRange(from, to, knownLevels, endInclusive = !isWallFile)
            if (why != null) { rejected += "سطر $lineNo ($mark): $why"; return@forEachIndexed }

            if (isWallFile) {
                walls.getOrPut(mark) { mutableListOf() } += WallRange(
                    from = from, to = to,
                    w = cell(iW).toIntOrNull() ?: 0,
                    v = cell(iV).ifEmpty { "-" },
                    h = cell(iH).ifEmpty { "-" },
                    t = cell(iT).ifEmpty { "-" },
                    note = cell(iNote).takeIf { it.isNotEmpty() }
                )
            } else {
                fun layers(prefix: String): List<String> =
                    (1..3).map { n -> cell(col("$prefix$n")).ifEmpty { "-" } }
                beams.getOrPut(mark) { mutableListOf() } += BeamRange(
                    from = from, to = to,
                    w = cell(iW).toIntOrNull() ?: 0,
                    d = cell(iD).toIntOrNull() ?: 0,
                    bottom = layers("b"),
                    top = layers("t"),
                    side = cell(iSide).ifEmpty { "-" },
                    links = cell(iLinks).ifEmpty { "-" },
                    note = cell(iNote).takeIf { it.isNotEmpty() }
                )
            }
        }

        val marks = beams.map { (mark, rows) ->
            ImportedMarkEntity(
                mark = mark, kind = ImportedMarkEntity.BEAM,
                rowsJson = json.encodeToString(rows.toList()), source = source,
                rowCount = rows.size, createdAt = now
            )
        } + walls.map { (mark, rows) ->
            ImportedMarkEntity(
                mark = mark, kind = ImportedMarkEntity.WALL,
                rowsJson = json.encodeToString(rows.toList()), source = source,
                rowCount = rows.size, createdAt = now
            )
        }

        return Outcome(
            marks = marks,
            rejected = rejected,
            overrides = marks.map { it.mark }.filter { it in existingMarks }
        )
    }

    /**
     * بيرجّع سبب الرفض، أو null لو المدى سليم.
     *
     * [endInclusive] بيفرّق: مدى الكمرة شامل النهاية (`from <= level <= to`)
     * ومدى الحائط لأ (`from <= level < to`). يعني حائط نهايته = بدايته مدى
     * فاضي — وده غلط حقيقي مش شكلي.
     */
    private fun checkRange(
        from: String,
        to: String?,
        knownLevels: List<String>,
        endInclusive: Boolean
    ): String? {
        val f = from.trim()
        if (f.isEmpty()) return "بداية المدى (from) فاضية"
        val fi = knownLevels.indexOf(f)
        if (fi < 0) return "كود الدور \"$f\" مش موجود في المشروع"

        val t = to?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        // قيم النهاية الخاصة معناها "لحد فوق خالص" — مقبولة زي ما هي.
        if (t.equals("TOP ROOF", true) || t.equals("FM LMR", true)) return null
        val ti = knownLevels.indexOf(t)
        if (ti < 0) return "كود الدور \"$t\" مش موجود في المشروع"
        if (endInclusive && ti < fi) return "النهاية \"$t\" قبل البداية \"$f\""
        if (!endInclusive && ti <= fi) return "المدى فاضي — نهاية الحائط غير شاملة، فلازم \"$t\" تكون بعد \"$f\""
        return null
    }

    /** تقسيم سطر CSV مع احترام علامات التنصيص. */
    private fun splitCsv(line: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> { cur.append('"'); i++ }
                ch == '"' -> inQuotes = !inQuotes
                (ch == ',' || ch == ';') && !inQuotes -> { out += cur.toString(); cur.clear() }
                else -> cur.append(ch)
            }
            i++
        }
        out += cur.toString()
        return out
    }

    /**
     * قالب CSV جاهز بأكواد أدوار المشروع الحقيقية.
     * أرخص طريقة نشرح بيها الصيغة: ملف شغّال بدل صفحة توثيق.
     */
    fun beamTemplate(knownLevels: List<String>): String {
        val first = knownLevels.firstOrNull() ?: "GROUND"
        val second = knownLevels.getOrNull(3) ?: first
        return buildString {
            appendLine("# قالب استيراد كمرات — امسح السطور اللي بتبدأ بـ # قبل الاستيراد")
            appendLine("# from و to أكواد أدوار من المشروع. مدى الكمرة شامل النهاية.")
            appendLine("# سيب to فاضية لو الكمرة في دور واحد بس.")
            appendLine("# w العرض بالمللي، d العمق بالمللي.")
            appendLine("# B1..B3 تسليح سفلي (طبقات)، T1..T3 تسليح علوي.")
            appendLine("mark,from,to,w,d,B1,B2,B3,T1,T2,T3,side,links,note")
            appendLine("CB-01,$first,$second,400,700,5T20,2T16,-,5T20,2T16,-,2T12,T10@150,كمرة داخلية")
            appendLine("CB-02,$first,,400,600,4T20,-,-,4T20,-,-,-,T10@200,")
        }
    }
}
