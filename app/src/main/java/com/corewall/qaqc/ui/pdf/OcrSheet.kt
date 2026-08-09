package com.corewall.qaqc.ui.pdf

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.corewall.qaqc.ocr.OcrEngine
import com.corewall.qaqc.ocr.OcrPacks
import com.corewall.qaqc.ui.design.CwButton
import com.corewall.qaqc.ui.design.CwButtonStyle
import com.corewall.qaqc.ui.design.CwChip
import com.corewall.qaqc.ui.design.CwIconButton
import com.corewall.qaqc.ui.design.CwText
import com.corewall.qaqc.ui.design.IconSize
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Radius
import com.corewall.qaqc.ui.design.Space

/** حالة تحميل حزمة — بتتحرّك من الشاشة اللي فوق. */
data class PackState(
    val installed: Boolean,
    val downloading: Boolean = false,
    val progress: Float = 0f
)

/**
 * ورقة الـOCR.
 *
 * أول حاجة في الورقة هي **الحزم**، مش زرار التشغيل. من غير حزمة مفيش
 * تعرّف أصلاً، وزرار بيشتغل ويفشل بيبان كعطل. والحجم مكتوب جنب كل لغة
 * قبل الضغط — تحميل ٢٢ ميجا على بيانات الموبايل في الموقع لازم يبقى
 * قرار واعي.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrSheet(
    currentPage: Int,
    packs: Map<OcrPacks.Language, PackState>,
    selected: Set<OcrPacks.Language>,
    running: Boolean,
    result: OcrEngine.Outcome?,
    onToggleLanguage: (OcrPacks.Language) -> Unit,
    onDownload: (OcrPacks.Language) -> Unit,
    onDelete: (OcrPacks.Language) -> Unit,
    onRun: () -> Unit,
    onCopy: () -> Unit,
    onMakeSearchable: () -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalCwColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val ready = selected.isNotEmpty() && selected.all { packs[it]?.installed == true }

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
            Text(
                "استخراج النص (OCR)",
                style = MaterialTheme.typography.titleMedium,
                color = c.textPrimary
            )
            Spacer(Modifier.height(Space.xxs))
            Text(
                "التعرّف بيحصل على الجهاز — مفيش أي صفحة بتترفع لأي خادم.",
                style = MaterialTheme.typography.bodySmall,
                color = c.textTertiary
            )

            Label("حزم اللغات")
            OcrPacks.Language.entries.forEach { language ->
                PackRow(
                    language = language,
                    state = packs[language] ?: PackState(installed = false),
                    selected = language in selected,
                    onToggle = { onToggleLanguage(language) },
                    onDownload = { onDownload(language) },
                    onDelete = { onDelete(language) }
                )
            }

            Label("الصفحة")
            CwChip(label = "صفحة ${currentPage + 1}", selected = true, onClick = {})
            Text(
                "صفحة واحدة في المرة: التعرّف على صفحة A3 بياخد ثواني، وملف كامل ممكن ياخد دقايق.",
                style = MaterialTheme.typography.bodySmall,
                color = c.textTertiary,
                modifier = Modifier.padding(top = Space.xs)
            )

            Spacer(Modifier.height(Space.lg))
            CwButton(
                label = if (running) "بيقرا الصفحة…" else "استخرج النص",
                onClick = onRun,
                enabled = ready && !running,
                fillWidth = true
            )
            if (!ready) {
                Text(
                    if (selected.isEmpty()) "اختار لغة واحدة على الأقل."
                    else "نزّل حزمة اللغة المختارة الأول.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.warning.fg,
                    modifier = Modifier.padding(top = Space.xs)
                )
            }

            if (result != null) ResultBlock(result, onCopy, onMakeSearchable)
        }
    }
}

@Composable
private fun PackRow(
    language: OcrPacks.Language,
    state: PackState,
    selected: Boolean,
    onToggle: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    val c = LocalCwColors.current
    Column(Modifier.padding(vertical = Space.xs)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            CwChip(
                label = language.label,
                selected = selected,
                onClick = onToggle
            )
            Text(
                if (state.installed) "مثبّتة" else megabytes(language.bytes),
                style = CwText.codeSmall,
                color = if (state.installed) c.success.fg else c.textTertiary,
                modifier = Modifier.weight(1f)
            )
            when {
                state.downloading -> Text(
                    "${(state.progress * 100).toInt()}٪",
                    style = CwText.codeSmall,
                    color = c.accent
                )
                state.installed -> CwIconButton(
                    Icons.Filled.DeleteOutline, "احذف الحزمة", onDelete,
                    tint = c.textTertiary
                )
                else -> CwIconButton(
                    Icons.Filled.CloudDownload, "نزّل الحزمة", onDownload,
                    tint = c.accent
                )
            }
        }
        if (state.downloading) {
            LinearProgressIndicator(
                progress = { state.progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Space.xs)
                    .height(PROGRESS),
                color = c.accent,
                trackColor = c.surfaceAlt
            )
        }
    }
}

@Composable
private fun ResultBlock(
    result: OcrEngine.Outcome,
    onCopy: () -> Unit,
    onMakeSearchable: () -> Unit
) {
    val c = LocalCwColors.current
    val low = result.confidence < LOW_CONFIDENCE

    Label("النتيجة")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm)
    ) {
        Icon(
            Icons.Filled.DoneAll,
            contentDescription = null,
            tint = if (low) c.warning.fg else c.success.fg,
            modifier = Modifier.size(IconSize.md)
        )
        Text(
            "${result.words.size} كلمة · ثقة ${result.confidence}٪",
            style = CwText.codeSmall,
            color = if (low) c.warning.fg else c.textSecondary,
            fontWeight = FontWeight.SemiBold
        )
    }
    if (low) {
        Text(
            "الثقة واطية — غالباً المسح مش واضح أو الصفحة مايلة. راجع النص قبل ما تعتمد عليه.",
            style = MaterialTheme.typography.bodySmall,
            color = c.warning.fg,
            modifier = Modifier.padding(top = Space.xs)
        )
    }

    Spacer(Modifier.height(Space.sm))
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(max = PREVIEW_MAX)
            .background(c.surfaceAlt, Radius.shapeSm)
            .padding(Space.md)
    ) {
        Text(
            result.text.ifBlank { "مافيش نص اتعرف عليه في الصفحة دي." },
            style = MaterialTheme.typography.bodySmall,
            color = c.textSecondary,
            modifier = Modifier.verticalScroll(rememberScrollState())
        )
    }

    Spacer(Modifier.height(Space.md))
    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        CwButton(
            label = "انسخ النص",
            onClick = onCopy,
            style = CwButtonStyle.Secondary,
            enabled = result.text.isNotBlank(),
            modifier = Modifier.weight(1f)
        )
        CwButton(
            label = "خلّي الملف قابل للبحث",
            onClick = onMakeSearchable,
            enabled = result.words.isNotEmpty(),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun Label(text: String) {
    val c = LocalCwColors.current
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = c.textSecondary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = Space.lg, bottom = Space.xs)
    )
}

private fun megabytes(bytes: Long): String = "${(bytes / 1_048_576.0).toInt()} ميجا"

private const val LOW_CONFIDENCE = 60
private val PROGRESS = 3.dp
private val PREVIEW_MAX = 200.dp
