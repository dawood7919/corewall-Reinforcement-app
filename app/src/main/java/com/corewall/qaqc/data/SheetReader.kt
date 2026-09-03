package com.corewall.qaqc.data

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory

/**
 * قراية شيت — `.xlsx` أو `.csv` — لصفوف وأعمدة نص.
 *
 * ## ليه مكتوب بالإيد
 *
 * مكتبات الإكسل الجاهزة على أندرويد تقيلة: Apache POI بتضيف عشرات
 * الميجات وآلاف الدوال، والـAPK أصلاً ٩٤ ميجا والتولتشين مثبّت. وملف
 * الـ`xlsx` في الآخر **مجرد ZIP جواه XML**، واللي محتاجينه منه ملفين:
 * النصوص المشتركة وأول ورقة. الكود ده مالوش أي تبعية جديدة.
 *
 * ## اللي بيتقري
 *
 * - `xl/sharedStrings.xml` — مخزن النصوص. الإكسل بيحطّ كل نص مرة واحدة
 *   وبيشاور عليه برقم، فمن غير الملف ده الخلايا بترجع أرقام مش أسماء.
 * - أول ورقة في `xl/worksheets/` — بترتيب الاسم، فـ`sheet1` قبل `sheet10`.
 *
 * ## اللي مش بيتقري
 *
 * التنسيقات والصيغ والتواريخ كأرقام تسلسلية. الخلية بترجع **زي ما هي
 * متخزّنة**، والتفسير مسؤولية اللي بيستورد — عشان الشيت اللي فيه عمود
 * تاريخ مايتحوّلش لأرقام غامضة من غير ما حد ياخد باله.
 */
object SheetReader {

    /** صفوف الشيت. الصفوف مش مضمون إنها بنفس الطول — الإكسل بيسيب فراغات. */
    data class Sheet(val rows: List<List<String>>) {
        val isEmpty: Boolean get() = rows.all { row -> row.all { it.isBlank() } }

        /** أعرض صف — بيحدّد عدد الأعمدة الحقيقي. */
        val columnCount: Int get() = rows.maxOfOrNull { it.size } ?: 0

        fun cell(row: Int, column: Int): String =
            rows.getOrNull(row)?.getOrNull(column).orEmpty()
    }

    class SheetException(message: String) : Exception(message)

    fun read(file: File): Result<Sheet> = runCatching {
        if (!file.exists()) throw SheetException("الملف مش موجود")
        when (file.extension.lowercase()) {
            "xlsx", "xlsm" -> readXlsx(file)
            "csv", "txt" -> readCsv(file.readText(Charsets.UTF_8))
            "xls" -> throw SheetException(
                "صيغة xls القديمة مش مدعومة — احفظ الملف بصيغة xlsx أو csv"
            )
            else -> throw SheetException("صيغة مش مدعومة: .${file.extension}")
        }
    }

    // ══════════════════════════════════════════════════════════════ CSV

    /**
     * الفاصل بيتكتشف من أول سطر.
     *
     * الإكسل بيصدّر بفاصلة منقوطة في اللغات اللي بتستخدم الفاصلة كعلامة
     * عشرية — والعربي منهم. ملف اتصدّر من الإكسل عندك وبيتقري بالفاصلة
     * بيرجع عمود واحد فيه السطر كله.
     */
    fun readCsv(text: String): Sheet {
        // بالهروب مش بالحرف نفسه: علامة ترتيب البايت غير مرئية في
        // المحرر، وحرف مايتشافش في الكود بيتشال بالغلط في أول تعديل.
        val body = text.removePrefix("\uFEFF")
        val firstLine = body.lineSequence().firstOrNull().orEmpty()
        val separator = listOf(';', '\t', ',')
            .maxByOrNull { candidate -> firstLine.count { it == candidate } }
            ?.takeIf { candidate -> firstLine.any { it == candidate } }
            ?: ','
        return Sheet(splitCsv(body, separator))
    }

    /** يحترم الاقتباس: فاصل أو سطر جوّه `"…"` جزء من الخلية مش نهايتها. */
    private fun splitCsv(text: String, separator: Char): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            when {
                quoted && ch == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                    cell.append('"'); i++
                }
                ch == '"' -> quoted = !quoted
                !quoted && ch == separator -> { row.add(cell.toString().trim()); cell.setLength(0) }
                !quoted && (ch == '\n' || ch == '\r') -> {
                    if (ch == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    row.add(cell.toString().trim()); cell.setLength(0)
                    rows.add(row); row = ArrayList()
                }
                else -> cell.append(ch)
            }
            i++
        }
        row.add(cell.toString().trim())
        if (row.any { it.isNotEmpty() } || rows.isEmpty()) rows.add(row)
        return rows.dropLastWhile { line -> line.all { it.isBlank() } }
    }

    // ═════════════════════════════════════════════════════════════ XLSX

    private fun readXlsx(file: File): Sheet {
        var sharedXml: ByteArray? = null
        val sheets = sortedMapOf<String, ByteArray>()

        ZipInputStream(file.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                when {
                    name == "xl/sharedStrings.xml" -> sharedXml = zip.readBytes()
                    name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml") ->
                        sheets[sheetOrderKey(name)] = zip.readBytes()
                }
                zip.closeEntry()
            }
        }

        val sheetXml = sheets.values.firstOrNull()
            ?: throw SheetException("الملف مافيهوش ورقة عمل — يمكن مش xlsx سليم")
        val shared = sharedXml?.let(::parseSharedStrings) ?: emptyList()
        return Sheet(parseSheet(sheetXml, shared))
    }

    /** `sheet10` بعد `sheet2`، مش قبله — الترتيب النصّي لوحده بيغلط. */
    private fun sheetOrderKey(name: String): String {
        val number = name.substringAfterLast("sheet").substringBefore(".").toIntOrNull()
        return number?.toString()?.padStart(6, '0') ?: name
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val out = ArrayList<String>()
        val text = StringBuilder()
        var inItem = false
        parse(bytes.inputStream(), object : DefaultHandler() {
            override fun startElement(u: String?, l: String?, q: String, a: Attributes?) {
                if (q == "si") { inItem = true; text.setLength(0) }
            }
            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inItem) text.appendRange(ch, start, start + length)
            }
            override fun endElement(u: String?, l: String?, q: String) {
                if (q == "si") { out.add(text.toString()); inItem = false }
            }
        })
        return out
    }

    private fun parseSheet(bytes: ByteArray, shared: List<String>): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        var column = -1
        var type = ""
        var value = StringBuilder()
        var reading = false

        parse(bytes.inputStream(), object : DefaultHandler() {
            override fun startElement(u: String?, l: String?, q: String, a: Attributes?) {
                when (q) {
                    "row" -> { row = ArrayList() }
                    "c" -> {
                        column = columnOf(a?.getValue("r").orEmpty())
                        type = a?.getValue("t").orEmpty()
                        value.setLength(0)
                    }
                    // `v` قيمة، و`t` جوّه `is` نص مكتوب في الخلية نفسها.
                    "v", "t" -> reading = true
                }
            }
            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (reading) value.appendRange(ch, start, start + length)
            }
            override fun endElement(u: String?, l: String?, q: String) {
                when (q) {
                    "v", "t" -> reading = false
                    "c" -> {
                        val raw = value.toString()
                        val text = if (type == "s") {
                            shared.getOrNull(raw.trim().toIntOrNull() ?: -1).orEmpty()
                        } else raw
                        // الخلايا الفاضية مش بتتكتب في الملف، فبنملا الفراغ
                        // بالمرجع (`C5` = العمود التالت) عشان الأعمدة تفضل
                        // متطابقة بين الصفوف.
                        if (column >= 0) {
                            while (row.size < column) row.add("")
                            if (row.size == column) row.add(text.trim()) else row[column] = text.trim()
                        }
                    }
                    "row" -> rows.add(row)
                }
            }
        })
        return rows.dropLastWhile { line -> line.all { it.isBlank() } }
    }

    /** `BC12` → ٥٤ (صفري). بيتجاهل رقم الصف. */
    internal fun columnOf(reference: String): Int {
        var index = 0
        var seen = false
        for (ch in reference) {
            val upper = ch.uppercaseChar()
            if (upper !in 'A'..'Z') break
            index = index * 26 + (upper - 'A' + 1)
            seen = true
        }
        return if (seen) index - 1 else -1
    }

    private fun parse(stream: InputStream, handler: DefaultHandler) {
        val factory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = false
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        stream.use { factory.newSAXParser().parse(it, handler) }
    }
}
