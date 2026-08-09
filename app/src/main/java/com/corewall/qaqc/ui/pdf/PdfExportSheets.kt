package com.corewall.qaqc.ui.pdf

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.corewall.qaqc.pdfengine.PdfImageExport
import com.corewall.qaqc.pdfengine.PdfOps
import com.corewall.qaqc.ui.design.CwButton
import com.corewall.qaqc.ui.design.CwButtonStyle
import com.corewall.qaqc.ui.design.CwChip
import com.corewall.qaqc.ui.design.CwField
import com.corewall.qaqc.ui.design.CwText
import com.corewall.qaqc.ui.design.IconSize
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Radius
import com.corewall.qaqc.ui.design.Space
import com.corewall.qaqc.ui.design.Stroke as CwStroke
import java.io.File

/** نطاق العملية: الصفحة اللي انت فيها ولا المستند كله. */
enum class PageScope(val label: String) { CURRENT("الصفحة الحالية"), ALL("كل الصفحات") }

// ══════════════════════════════════════════════════════════ تصدير صور

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageExportSheet(
    currentPage: Int,
    pageCount: Int,
    running: Boolean,
    progress: Pair<Int, Int>?,
    onExport: (scope: PageScope, dpi: Int, format: PdfImageExport.Format, quality: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalCwColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var scope by remember { mutableStateOf(PageScope.CURRENT) }
    var format by remember { mutableStateOf(PdfImageExport.Format.PNG) }
    var dpi by remember { mutableIntStateOf(150) }
    var quality by remember { mutableIntStateOf(92) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.surface,
        shape = Radius.sheet
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = Space.lg)
                .padding(bottom = Space.lg)
        ) {
            SheetTitle("تصدير صور", "الصور بتتحفظ في مجلد جنب الملف")

            Label("النطاق")
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                PageScope.entries.forEach { option ->
                    CwChip(
                        label = if (option == PageScope.CURRENT) "صفحة ${currentPage + 1}"
                        else "كل الصفحات ($pageCount)",
                        selected = scope == option,
                        onClick = { scope = option }
                    )
                }
            }

            Label("الصيغة")
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                PdfImageExport.Format.entries.forEach { option ->
                    CwChip(
                        label = option.label,
                        selected = format == option,
                        onClick = { format = option }
                    )
                }
            }

            Label("الدقّة")
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                PdfImageExport.DPI_CHOICES.forEach { option ->
                    CwChip(
                        label = "$option",
                        selected = dpi == option,
                        onClick = { dpi = option }
                    )
                }
            }
            Text(
                dpiHint(dpi),
                style = MaterialTheme.typography.bodySmall,
                color = c.textTertiary,
                modifier = Modifier.padding(top = Space.xs)
            )

            if (format.lossy) {
                Label("الجودة — $quality٪")
                Slider(
                    value = quality.toFloat(),
                    onValueChange = { quality = it.toInt() },
                    valueRange = 40f..100f,
                    steps = 11
                )
            }

            if (running) {
                Spacer(Modifier.height(Space.md))
                val done = progress?.first ?: 0
                val total = progress?.second ?: 1
                LinearProgressIndicator(
                    progress = { (done.toFloat() / total.coerceAtLeast(1)).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = c.accent,
                    trackColor = c.surfaceAlt
                )
                Text(
                    "$done من $total",
                    style = CwText.codeSmall,
                    color = c.textTertiary,
                    modifier = Modifier.padding(top = Space.xs)
                )
            }

            Spacer(Modifier.height(Space.lg))
            CwButton(
                label = if (running) "بيصدّر…" else "صدّر",
                onClick = { onExport(scope, dpi, format, quality) },
                enabled = !running,
                fillWidth = true
            )
        }
    }
}

private fun dpiHint(dpi: Int): String = when {
    dpi <= 72 -> "مقاس الشاشة — خفيف وسريع، مش للطباعة."
    dpi <= 150 -> "وسط — يبان كويس على شاشة كبيرة وفي طباعة A4."
    dpi <= 300 -> "مقاس طباعة. الرسومات الكبيرة ممكن تتخفّض تلقائياً لو الذاكرة ما استحملتش."
    else -> "دقّة عالية جداً — الرسمة الكبيرة غالباً هتتخفّض، والتطبيق هيقولك خفّضها لكام."
}

// ══════════════════════════════════════════════════════════ علامة مائية

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatermarkSheet(
    currentPage: Int,
    pageCount: Int,
    running: Boolean,
    onApply: (spec: PdfOps.Watermark, scope: PageScope, overwrite: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalCwColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var text by remember { mutableStateOf("مسودة — غير معتمد للتنفيذ") }
    var scope by remember { mutableStateOf(PageScope.ALL) }
    var opacity by remember { mutableFloatStateOf(0.18f) }
    var angle by remember { mutableFloatStateOf(35f) }
    var color by remember { mutableStateOf(PDF_PALETTE.first()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.surface,
        shape = Radius.sheet
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = Space.lg)
                .padding(bottom = Space.lg)
        ) {
            SheetTitle("علامة مائية", "بتتكتب في نسخة جديدة — الأصل مابيتلمسش")

            CwField(
                value = text,
                onValueChange = { text = it },
                label = "النص",
                placeholder = "مسودة / للمراجعة / نسخة مراقبة"
            )

            Label("النطاق")
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                PageScope.entries.forEach { option ->
                    CwChip(
                        label = if (option == PageScope.CURRENT) "صفحة ${currentPage + 1}"
                        else "كل الصفحات ($pageCount)",
                        selected = scope == option,
                        onClick = { scope = option }
                    )
                }
            }

            Label("اللون")
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                PDF_PALETTE.forEach { argb ->
                    val chosen = argb == color
                    Box(
                        Modifier
                            .size(SWATCH)
                            .background(Color(argb), CircleShape)
                            .border(
                                if (chosen) CwStroke.thick else CwStroke.hair,
                                if (chosen) c.textPrimary else c.outline,
                                CircleShape
                            )
                            .clickable { color = argb }
                    )
                }
            }

            Label("الشفافية — ${(opacity * 100).toInt()}٪")
            Slider(
                value = opacity,
                onValueChange = { opacity = it },
                valueRange = 0.05f..0.6f
            )

            Label("الميل — ${angle.toInt()}°")
            Slider(
                value = angle,
                onValueChange = { angle = it },
                valueRange = 0f..90f
            )

            Spacer(Modifier.height(Space.lg))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                CwButton(
                    label = "احفظ كنسخة",
                    onClick = {
                        onApply(
                            PdfOps.Watermark(
                                text = text, opacity = opacity,
                                angle = angle, colorArgb = color
                            ),
                            scope, false
                        )
                    },
                    enabled = !running && text.isNotBlank(),
                    modifier = Modifier.weight(1f)
                )
                CwButton(
                    label = "اكتب فوق الأصل",
                    onClick = {
                        onApply(
                            PdfOps.Watermark(
                                text = text, opacity = opacity,
                                angle = angle, colorArgb = color
                            ),
                            scope, true
                        )
                    },
                    style = CwButtonStyle.Ghost,
                    enabled = !running && text.isNotBlank(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private val SWATCH = 32.dp

// ══════════════════════════════════════════════════════════ دمج

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergeSheet(
    candidates: List<File>,
    running: Boolean,
    onMerge: (List<File>) -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalCwColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val picked = remember { mutableStateListOf<File>() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.surface,
        shape = Radius.sheet
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(bottom = Space.lg)
        ) {
            Column(Modifier.padding(horizontal = Space.lg)) {
                SheetTitle(
                    "دمج ملفات",
                    "الملف الحالي أولاً، وبعده اللي تختاره بالترتيب ده"
                )
            }

            if (candidates.isEmpty()) {
                Text(
                    "مفيش ملفات PDF تانية في نفس المجلد.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textTertiary,
                    modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.md)
                )
            } else {
                LazyColumn(Modifier.heightIn(max = MERGE_LIST_MAX)) {
                    items(candidates, key = { it.absolutePath }) { candidate ->
                        val order = picked.indexOf(candidate)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (order >= 0) picked.remove(candidate) else picked.add(candidate)
                                }
                                .padding(horizontal = Space.lg, vertical = Space.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Space.md)
                        ) {
                            Icon(
                                Icons.Filled.PictureAsPdf,
                                contentDescription = null,
                                tint = if (order >= 0) c.accent else c.textTertiary,
                                modifier = Modifier.size(IconSize.lg)
                            )
                            Text(
                                candidate.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (order >= 0) c.accent else c.textPrimary,
                                fontWeight = if (order >= 0) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            // الرقم بيقول الترتيب في الناتج — مش مجرد "مختار".
                            if (order >= 0) {
                                Text(
                                    "${order + 2}",
                                    style = CwText.codeSmall,
                                    color = c.accent,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(Space.md))
            Column(Modifier.padding(horizontal = Space.lg)) {
                CwButton(
                    label = if (running) "بيدمج…" else "ادمج (${picked.size + 1} ملفات)",
                    onClick = { onMerge(picked.toList()) },
                    enabled = !running && picked.isNotEmpty(),
                    fillWidth = true
                )
            }
        }
    }
}

private val MERGE_LIST_MAX = 320.dp

// ══════════════════════════════════════════════════════════ مشترك

@Composable
private fun SheetTitle(title: String, subtitle: String) {
    val c = LocalCwColors.current
    Column(Modifier.padding(bottom = Space.md)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
        Spacer(Modifier.height(Space.xxs))
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
    }
}

@Composable
private fun Label(text: String) {
    val c = LocalCwColors.current
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = c.textTertiary,
        modifier = Modifier.padding(top = Space.md, bottom = Space.xs)
    )
}
