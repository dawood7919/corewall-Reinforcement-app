package com.corewall.qaqc.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.corewall.qaqc.data.model.ElementCategory
import com.corewall.qaqc.data.model.InspectionStatus
import com.corewall.qaqc.data.model.PlanData
import com.corewall.qaqc.data.model.ScheduleData
import com.corewall.qaqc.domain.ActiveRangeResult
import com.corewall.qaqc.domain.ScheduleLogic
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

/**
 * تصدير المسقط PDF/PNG بـ android.graphics.pdf.PdfDocument وCanvas/Bitmap —
 * بدون أي مكتبات خارجية. ألوان الطباعة ثابتة (نسخة فاتحة) بغض النظر عن ثيم التطبيق.
 */
object PlanExporter {

    data class Config(
        val planData: PlanData,
        val schedule: ScheduleData,
        val logic: ScheduleLogic,
        val names: Map<String, String>,
        val inspections: Map<Pair<String, String>, String>,
        val level: String,
        /** لو متحددة: وضع المقارنة بين دورين بدل عرض دور واحد. */
        val compareWith: String? = null,
        val showStatuses: Boolean = true
    )

    private const val WALL_COLOR = 0xFF324A70.toInt()
    private const val CB_COLOR = 0xFFC0392B.toInt()
    private const val IB_COLOR = 0xFFA35D34.toInt()
    private const val GAP_COLOR = 0xFFE8890C.toInt()
    private const val CHANGED_COLOR = 0xFFD32F2F.toInt()

    private fun categoryColor(cat: ElementCategory): Int = when (cat) {
        ElementCategory.WALL -> WALL_COLOR
        ElementCategory.COUPLING_BEAM -> CB_COLOR
        ElementCategory.INTERNAL_BEAM, ElementCategory.OTHER -> IB_COLOR
    }

    private fun statusColor(status: InspectionStatus): Int = when (status) {
        InspectionStatus.NONE -> 0xFF9E9E9E.toInt()
        InspectionStatus.WIR_SUBMITTED -> 0xFFF39C12.toInt()
        InspectionStatus.APPROVED -> 0xFF27AE60.toInt()
        InspectionStatus.CAST -> 0xFF2980B9.toInt()
        InspectionStatus.REJECTED -> 0xFFE74C3C.toInt()
    }

    /** توقيع تسليح العنصر في دور معيّن للمقارنة بين دورين. */
    private fun specSignature(cfg: Config, mark: String, level: String): String? =
        when (val r = cfg.logic.activeRange(cfg.schedule, mark, level)) {
            is ActiveRangeResult.Wall -> with(r.row) { "$w|$v|$h|$t" }
            is ActiveRangeResult.Beam -> with(r.row) {
                "$w|$d|${bottom.joinToString()}|${top.joinToString()}|$side|$links"
            }
            else -> null
        }

    fun draw(canvas: Canvas, width: Float, height: Float, cfg: Config) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        canvas.drawColor(Color.WHITE)

        val margin = width * 0.03f
        val titleSize = width * 0.022f
        text.textSize = titleSize
        val compare = cfg.compareWith
        val title = if (compare == null)
            "Core Wall QA/QC — Level ${cfg.level}"
        else
            "Core Wall QA/QC — Level ${cfg.level} vs $compare"
        canvas.drawText(title, margin, margin + titleSize, text)
        text.textSize = titleSize * 0.6f
        text.typeface = Typeface.SANS_SERIF
        val stamp = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ENGLISH).format(Date())
        canvas.drawText(stamp, margin, margin + titleSize * 1.8f, text)

        // منطقة الرسم
        val vb = cfg.planData.viewBoxRect
        val top = margin + titleSize * 2.6f
        val legendH = titleSize * 2.6f
        val availW = width - margin * 2
        val availH = height - top - margin - legendH
        val scale = min(availW / vb[2], availH / vb[3]).toFloat()
        val offX = margin + (availW - vb[2] * scale).toFloat() / 2
        val offY = top + (availH - vb[3] * scale).toFloat() / 2

        fun rectOf(x: Double, y: Double, w: Double, h: Double) = RectF(
            ((x - vb[0]) * scale + offX).toFloat(),
            ((y - vb[1]) * scale + offY).toFloat(),
            ((x + w - vb[0]) * scale + offX).toFloat(),
            ((y + h - vb[1]) * scale + offY).toFloat()
        )

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = width * 0.008f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        for (el in cfg.planData.elements) {
            val r = rectOf(el.x, el.y, el.width, el.height)
            val mark = cfg.names[el.id]
            val active = mark?.let { cfg.logic.activeRange(cfg.schedule, it, cfg.level) }

            if (compare == null) {
                val status = InspectionStatus.from(cfg.inspections[el.id to cfg.level])
                fill.color = if (cfg.showStatuses && status != InspectionStatus.NONE)
                    statusColor(status) else categoryColor(el.cat)
                fill.alpha = if (active is ActiveRangeResult.OutOfRange) 45 else 255
                canvas.drawRect(r, fill)
                if (active is ActiveRangeResult.Gap) {
                    stroke.color = GAP_COLOR
                    stroke.strokeWidth = width * 0.0025f
                    stroke.pathEffect = DashPathEffect(floatArrayOf(8f, 5f), 0f)
                    canvas.drawRect(r, stroke)
                    stroke.pathEffect = null
                }
            } else {
                val sigA = mark?.let { specSignature(cfg, it, cfg.level) }
                val sigB = mark?.let { specSignature(cfg, it, compare) }
                val changed = mark != null && sigA != sigB
                fill.color = categoryColor(el.cat)
                fill.alpha = if (changed) 255 else 40
                canvas.drawRect(r, fill)
                if (changed) {
                    stroke.color = CHANGED_COLOR
                    stroke.strokeWidth = width * 0.0025f
                    canvas.drawRect(r, stroke)
                }
            }

            if (mark != null && (r.width() > width * 0.03f || r.height() > width * 0.03f)) {
                val tw = labelPaint.measureText(mark)
                val cx = r.centerX()
                val cy = r.centerY()
                if (el.height > el.width * 1.5) {
                    canvas.save()
                    canvas.rotate(-90f, cx, cy)
                    canvas.drawText(mark, cx - tw / 2, cy + labelPaint.textSize / 3, labelPaint)
                    canvas.restore()
                } else {
                    canvas.drawText(mark, cx - tw / 2, cy + labelPaint.textSize / 3, labelPaint)
                }
            }
        }

        // legend
        val legendY = height - margin - titleSize * 0.4f
        val boxSize = titleSize * 0.8f
        var lx = margin
        text.textSize = titleSize * 0.6f

        fun legendItem(color: Int, label: String) {
            fill.color = color
            fill.alpha = 255
            canvas.drawRect(lx, legendY - boxSize, lx + boxSize, legendY, fill)
            canvas.drawText(label, lx + boxSize * 1.3f, legendY - boxSize * 0.1f, text)
            lx += boxSize * 1.6f + text.measureText(label) + titleSize
        }

        if (compare == null) {
            legendItem(WALL_COLOR, "Wall")
            legendItem(CB_COLOR, "Coupling Beam")
            legendItem(IB_COLOR, "Internal Beam")
            if (cfg.showStatuses) {
                legendItem(statusColor(InspectionStatus.WIR_SUBMITTED), "WIR")
                legendItem(statusColor(InspectionStatus.APPROVED), "Approved")
                legendItem(statusColor(InspectionStatus.CAST), "Cast")
                legendItem(statusColor(InspectionStatus.REJECTED), "Rejected")
            }
            legendItem(GAP_COLOR, "Data gap!")
        } else {
            legendItem(CHANGED_COLOR, "Reinforcement changed between the two levels")
        }
    }

    // ---------------------------------------------------------------- Counting

    data class CountingConfig(
        val planData: PlanData,
        val names: Map<String, String>,
        /** elementId -> نص العدّ ("22Ø12+4Ø16" أو "22Ø12 / 20Ø12"). */
        val labels: Map<String, String>,
        val title: String,
        val totalsLine: String = ""
    )

    /**
     * دروينج العدّ: العناصر بألوان باهتة والأعداد فوقها بخط أسود واضح —
     * في منتصف كل جدار، موازية له، وحجمها نسبة من سُمك الجدار على البلان.
     */
    fun drawCounting(canvas: Canvas, width: Float, height: Float, cfg: CountingConfig) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        canvas.drawColor(Color.WHITE)

        val margin = width * 0.03f
        val titleSize = width * 0.022f
        text.textSize = titleSize
        canvas.drawText(cfg.title, margin, margin + titleSize, text)
        text.textSize = titleSize * 0.6f
        text.typeface = Typeface.SANS_SERIF
        val stamp = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ENGLISH).format(Date())
        canvas.drawText(stamp, margin, margin + titleSize * 1.8f, text)

        val vb = cfg.planData.viewBoxRect
        val top = margin + titleSize * 2.6f
        val legendH = titleSize * 2.2f
        val availW = width - margin * 2
        val availH = height - top - margin - legendH
        val scale = min(availW / vb[2], availH / vb[3]).toFloat()
        val offX = margin + (availW - vb[2] * scale).toFloat() / 2
        val offY = top + (availH - vb[3] * scale).toFloat() / 2

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        for (el in cfg.planData.elements) {
            val r = RectF(
                ((el.x - vb[0]) * scale + offX).toFloat(),
                ((el.y - vb[1]) * scale + offY).toFloat(),
                ((el.x + el.width - vb[0]) * scale + offX).toFloat(),
                ((el.y + el.height - vb[1]) * scale + offY).toFloat()
            )
            val label = cfg.labels[el.id]
            fill.color = categoryColor(el.cat)
            fill.alpha = if (label == null) 45 else 110
            canvas.drawRect(r, fill)

            if (label != null) {
                // حجم النص نسبة من سُمك العنصر على البلان نفسه
                val thickness = min(r.width(), r.height())
                labelPaint.textSize = (thickness * 0.8f).coerceAtLeast(width * 0.006f)
                val tw = labelPaint.measureText(label)
                val cx = r.centerX()
                val cy = r.centerY()
                if (el.height > el.width * 1.5) {
                    canvas.save()
                    canvas.rotate(-90f, cx, cy)
                    canvas.drawText(label, cx - tw / 2, cy + labelPaint.textSize / 3, labelPaint)
                    canvas.restore()
                } else {
                    canvas.drawText(label, cx - tw / 2, cy + labelPaint.textSize / 3, labelPaint)
                }
            }
        }

        if (cfg.totalsLine.isNotEmpty()) {
            text.textSize = titleSize * 0.7f
            text.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            text.color = Color.BLACK
            canvas.drawText(cfg.totalsLine, margin, height - margin, text)
        }
    }

    fun renderCountingBitmap(cfg: CountingConfig, widthPx: Int = 2400): Bitmap {
        val vb = cfg.planData.viewBoxRect
        val heightPx = (widthPx * (vb[3] / vb[2]) * 1.18).toInt()
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        drawCounting(Canvas(bitmap), widthPx.toFloat(), heightPx.toFloat(), cfg)
        return bitmap
    }

    fun writeCountingPng(os: OutputStream, cfg: CountingConfig) {
        renderCountingBitmap(cfg).compress(Bitmap.CompressFormat.PNG, 100, os)
    }

    fun writeCountingPdf(os: OutputStream, cfg: CountingConfig) {
        val pageW = 842
        val pageH = 595
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, 1).create())
        drawCounting(page.canvas, pageW.toFloat(), pageH.toFloat(), cfg)
        doc.finishPage(page)
        doc.writeTo(os)
        doc.close()
    }

    fun renderBitmap(cfg: Config, widthPx: Int = 2400): Bitmap {
        val vb = cfg.planData.viewBoxRect
        val heightPx = (widthPx * (vb[3] / vb[2]) * 1.18).toInt()
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        draw(Canvas(bitmap), widthPx.toFloat(), heightPx.toFloat(), cfg)
        return bitmap
    }

    fun writePng(os: OutputStream, cfg: Config) {
        renderBitmap(cfg).compress(Bitmap.CompressFormat.PNG, 100, os)
    }

    /** صفحة A4 عرضية — الرسم مباشر على PdfDocument canvas (فيكتور، مش صورة). */
    fun writePdf(os: OutputStream, cfg: Config) {
        val pageW = 842
        val pageH = 595
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, 1).create())
        draw(page.canvas, pageW.toFloat(), pageH.toFloat(), cfg)
        doc.finishPage(page)
        doc.writeTo(os)
        doc.close()
    }
}
