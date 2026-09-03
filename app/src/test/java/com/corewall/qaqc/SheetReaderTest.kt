package com.corewall.qaqc

import com.corewall.qaqc.data.SheetReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * قارئ الشيت.
 *
 * الملف بيتبنى جوّه الاختبار نفسه — `xlsx` مجرد ZIP فيه XML، فمفيش داعي
 * لملف ثنائي مرفوع في المستودع محدش يعرف جواه إيه.
 */
class SheetReaderTest {

    @get:Rule
    val folder = TemporaryFolder()

    // ────────────────────────────────────────────── مراجع الأعمدة

    @Test
    fun `column letters map to indices`() {
        assertEquals(0, SheetReader.columnOf("A1"))
        assertEquals(25, SheetReader.columnOf("Z9"))
        assertEquals(26, SheetReader.columnOf("AA1"))
        assertEquals(54, SheetReader.columnOf("BC12"))
        assertEquals(-1, SheetReader.columnOf(""))
    }

    // ──────────────────────────────────────────────────────── CSV

    @Test
    fun `a comma file splits on commas`() {
        val sheet = SheetReader.readCsv("الاسم,الكود,التخصص\nأحمد,1021,نجار")
        assertEquals(listOf("الاسم", "الكود", "التخصص"), sheet.rows[0])
        assertEquals("أحمد", sheet.cell(1, 0))
        assertEquals(3, sheet.columnCount)
    }

    /**
     * الإكسل العربي بيصدّر بفاصلة منقوطة. من غير الاكتشاف ده الملف بيرجع
     * عمود واحد فيه السطر كله — وشكله في التطبيق "الاستيراد نجح" بصف واحد.
     */
    @Test
    fun `a semicolon file from arabic excel splits correctly`() {
        val sheet = SheetReader.readCsv("الاسم;الكود\nأحمد;1021\nمحمود;1022")
        assertEquals(2, sheet.columnCount)
        assertEquals("1022", sheet.cell(2, 1))
    }

    @Test
    fun `quoted cells keep their separators and newlines`() {
        val sheet = SheetReader.readCsv("name,note\n\"عبد الله, محمد\",\"سطر\nتاني\"")
        assertEquals("عبد الله, محمد", sheet.cell(1, 0))
        assertEquals("سطر\nتاني", sheet.cell(1, 1))
    }

    @Test
    fun `a doubled quote inside a quoted cell is one quote`() {
        val sheet = SheetReader.readCsv("a\n\"قال \"\"تمام\"\"\"")
        assertEquals("قال \"تمام\"", sheet.cell(1, 0))
    }

    @Test
    fun `a byte order mark does not become part of the first heading`() {
        val sheet = SheetReader.readCsv("\uFEFFالاسم,الكود")
        assertEquals("الاسم", sheet.cell(0, 0))
    }

    // ─────────────────────────────────────────────────────── XLSX

    private fun xlsx(shared: List<String>, sheetXml: String): File {
        val file = folder.newFile("roster.xlsx")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("xl/sharedStrings.xml"))
            zip.write(
                buildString {
                    append("<sst>")
                    shared.forEach { append("<si><t>").append(it).append("</t></si>") }
                    append("</sst>")
                }.toByteArray()
            )
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            zip.write(sheetXml.toByteArray())
            zip.closeEntry()
        }
        return file
    }

    @Test
    fun `shared strings are resolved to their text`() {
        val file = xlsx(
            shared = listOf("الاسم", "أحمد"),
            sheetXml = """
                <worksheet><sheetData>
                  <row r="1"><c r="A1" t="s"><v>0</v></c></row>
                  <row r="2"><c r="A2" t="s"><v>1</v></c></row>
                </sheetData></worksheet>
            """.trimIndent()
        )
        val sheet = SheetReader.read(file).getOrThrow()
        assertEquals("الاسم", sheet.cell(0, 0))
        assertEquals("أحمد", sheet.cell(1, 0))
    }

    /**
     * الإكسل مابيكتبش الخلايا الفاضية أصلاً. من غير الملء بمرجع الخلية،
     * صف ناقص خلية بيزحلق كل الأعمدة اللي بعده — والاسم بيروح تحت عمود
     * التاريخ من غير ما حد ياخد باله.
     */
    @Test
    fun `a skipped cell keeps the later columns in place`() {
        val file = xlsx(
            shared = listOf("أحمد", "نجار"),
            sheetXml = """
                <worksheet><sheetData>
                  <row r="1"><c r="A1" t="s"><v>0</v></c><c r="C1" t="s"><v>1</v></c></row>
                </sheetData></worksheet>
            """.trimIndent()
        )
        val sheet = SheetReader.read(file).getOrThrow()
        assertEquals("أحمد", sheet.cell(0, 0))
        assertEquals("", sheet.cell(0, 1))
        assertEquals("نجار", sheet.cell(0, 2))
    }

    @Test
    fun `numbers and inline strings are read`() {
        val file = xlsx(
            shared = emptyList(),
            sheetXml = """
                <worksheet><sheetData>
                  <row r="1"><c r="A1"><v>1021</v></c>
                  <c r="B1" t="inlineStr"><is><t>حداد</t></is></c></row>
                </sheetData></worksheet>
            """.trimIndent()
        )
        val sheet = SheetReader.read(file).getOrThrow()
        assertEquals("1021", sheet.cell(0, 0))
        assertEquals("حداد", sheet.cell(0, 1))
    }

    @Test
    fun `the old xls format is refused with a message that says what to do`() {
        val file = folder.newFile("old.xls").apply { writeText("x") }
        val error = SheetReader.read(file).exceptionOrNull()
        assertTrue(error is SheetReader.SheetException)
        assertTrue(error!!.message!!.contains("xlsx"))
    }

    @Test
    fun `a zip with no worksheet is refused`() {
        val file = folder.newFile("empty.xlsx")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("docProps/app.xml")); zip.write("<x/>".toByteArray()); zip.closeEntry()
        }
        assertTrue(SheetReader.read(file).isFailure)
    }
}
