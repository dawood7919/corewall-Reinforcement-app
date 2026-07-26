package com.corewall.qaqc.ui.manpower

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.corewall.qaqc.data.db.AttendanceFileEntity
import com.corewall.qaqc.data.db.DailyAttendanceEntity
import java.io.OutputStream

/** تصدير تقرير العمالة لدور معيّن — PDF مرتّب أو CSV (يفتح في Excel). */
object ManpowerExport {

    fun writeCsv(os: OutputStream, level: String, files: List<AttendanceFileEntity>, daily: List<DailyAttendanceEntity>) {
        val byId = files.associateBy { it.id }
        val sb = StringBuilder()
        sb.append("﻿") // BOM عشان العربي يظهر صح في Excel
        sb.appendLine("Level,Company,Trade,Date,Workers,Foremen,Engineers,Supervisors,Overtime,Weather,Remarks")
        daily.filter { byId.containsKey(it.fileId) }.sortedBy { it.date }.forEach { r ->
            val f = byId[r.fileId]!!
            fun esc(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
            sb.appendLine(
                listOf(
                    level, esc(f.company), Trade.from(f.trade).label, shortDate(r.date),
                    r.workers, r.foremen, r.engineers, r.supervisors, r.overtimeHours,
                    Weather.from(r.weather).label, esc(r.remarks)
                ).joinToString(",")
            )
        }
        os.write(sb.toString().toByteArray(Charsets.UTF_8))
    }

    fun writePdf(os: OutputStream, level: String, files: List<AttendanceFileEntity>, daily: List<DailyAttendanceEntity>) {
        val doc = PdfDocument()
        val pageW = 595; val pageH = 842 // A4 portrait pt
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, 1).create())
        var canvas = page.canvas
        var pageNo = 1

        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 20f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
        val head = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 10f; typeface = Typeface.DEFAULT_BOLD }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 10f }
        val fillHead = Paint().apply { color = Color.rgb(91, 102, 214) }
        val line = Paint().apply { color = Color.LTGRAY }

        val byId = files.associateBy { it.id }
        val rows = daily.filter { byId.containsKey(it.fileId) }.sortedBy { it.date }
        val totalW = rows.sumOf { it.workers }
        val totalF = rows.sumOf { it.foremen }
        val avg = if (rows.isNotEmpty()) totalW.toDouble() / rows.map { shortDate(it.date) }.distinct().size else 0.0

        var y = 50f
        canvas.drawText("Manpower Report — Level $level", 40f, y, title)
        y += 24f
        canvas.drawText("Total workers: $totalW    Total foremen: $totalF    Avg workers/day: ${"%.1f".format(avg)}", 40f, y, body)
        y += 24f

        val cols = floatArrayOf(40f, 150f, 250f, 330f, 400f, 470f)
        canvas.drawRect(40f, y - 12f, 555f, y + 6f, fillHead)
        listOf("Company", "Trade", "Date", "Workers", "Foremen", "Weather").forEachIndexed { i, h ->
            canvas.drawText(h, cols[i] + 2f, y, head)
        }
        y += 20f

        for (r in rows) {
            if (y > pageH - 40f) {
                doc.finishPage(page)
                pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
                canvas = page.canvas
                y = 50f
            }
            val f = byId[r.fileId]!!
            canvas.drawText(f.company.take(16), cols[0] + 2f, y, body)
            canvas.drawText(Trade.from(f.trade).label.take(12), cols[1] + 2f, y, body)
            canvas.drawText(shortDate(r.date), cols[2] + 2f, y, body)
            canvas.drawText("${r.workers}", cols[3] + 2f, y, body)
            canvas.drawText("${r.foremen}", cols[4] + 2f, y, body)
            canvas.drawText(Weather.from(r.weather).label, cols[5] + 2f, y, body)
            canvas.drawLine(40f, y + 4f, 555f, y + 4f, line)
            y += 18f
        }

        doc.finishPage(page)
        doc.writeTo(os)
        doc.close()
    }
}
