package com.corewall.qaqc.ui.pdf

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.corewall.qaqc.pdfengine.PdfOps
import com.corewall.qaqc.pdfengine.RevisionMerge
import com.corewall.qaqc.ui.design.CwBanner
import com.corewall.qaqc.ui.design.CwButton
import com.corewall.qaqc.ui.design.CwButtonStyle
import com.corewall.qaqc.ui.design.CwCard
import com.corewall.qaqc.ui.design.CwCardStyle
import com.corewall.qaqc.ui.design.CwChip
import com.corewall.qaqc.ui.design.CwField
import com.corewall.qaqc.ui.design.CwIconButton
import com.corewall.qaqc.ui.design.CwProgressBar
import com.corewall.qaqc.ui.design.CwSectionHeader
import com.corewall.qaqc.ui.design.CwText
import com.corewall.qaqc.ui.design.CwTone
import com.corewall.qaqc.ui.design.Elevation
import com.corewall.qaqc.ui.design.IconSize
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Radius
import com.corewall.qaqc.ui.design.Sizes
import com.corewall.qaqc.ui.design.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * الإصدار النهائي — دمج إصدارات الإرسالية في ست واحد بآخر ريفيجن.
 *
 * الشاشة متقسّمة نفس تقسيمة الشغل في الموقع: الملفات بترتيبها الزمني،
 * بعدين الإعدادات، بعدين **جدول اللوحات للمراجعة** — والملف مابيتكتبش
 * غير بعد ما تبص على الجدول ده. الخطوة دي مش رفاهية: الدمج بياخد قرار
 * "أنهي نسخة من كل لوحة"، وده قرار هندسي لازم حد يشوفه قبل ما يتنفّذ.
 */
@Composable
fun RevisionMergeScreen(
    paths: List<String>,
    onClose: () -> Unit,
    onOpenFile: (String) -> Unit
) {
    val c = LocalCwColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val files = remember(paths) {
        mutableStateListOf<File>().apply {
            addAll(RevisionMerge.autoOrder(paths.map(::File).filter { it.exists() }))
        }
    }
    var pattern by rememberSaveable { mutableStateOf(RevisionMerge.DEFAULT_PATTERN) }
    var coversOrdinal by rememberSaveable { mutableIntStateOf(RevisionMerge.Covers.LATEST.ordinal) }
    var orderOrdinal by rememberSaveable { mutableIntStateOf(RevisionMerge.Order.NUMBER.ordinal) }
    val covers = RevisionMerge.Covers.entries[coversOrdinal]
    val order = RevisionMerge.Order.entries[orderOrdinal]

    var plan by remember { mutableStateOf<RevisionMerge.Plan?>(null) }
    /** تعديلات المستخدم على مصدر كل لوحة — بتفضل فوق الخطة. */
    val overrides = remember { mutableStateMapOf<String, Int>() }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var stage by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun invalidate() {
        plan = null
        overrides.clear()
        error = null
    }

    fun analyse() {
        val problem = RevisionMerge.validatePattern(pattern)
        if (problem != null) { error = problem; return }
        busy = true; error = null; progress = 0f; stage = "بنقرا الملفات"
        scope.launch {
            val result = RevisionMerge.scan(files.toList(), pattern) { p ->
                withContext(Dispatchers.Main) { progress = p }
            }
            busy = false; progress = 0f
            result.onSuccess { scans ->
                val built = RevisionMerge.plan(scans, order)
                if (built.numbers.isEmpty()) {
                    plan = null
                    error = "مفيش أي رقم لوحة اتلقى. غيّر النمط — مثلاً " +
                        "RFT-(\\d{5}) أو -(\\d{5})- حسب ترقيم المشروع."
                } else {
                    overrides.clear()
                    plan = built
                }
            }.onFailure { e ->
                plan = null
                error = "القراءة فشلت: ${e.message ?: "خطأ غير معروف"}"
            }
        }
    }

    fun build() {
        val current = plan ?: return
        val newest = files.lastOrNull() ?: return
        busy = true; error = null; progress = 0f; stage = "بنبني الملف"
        scope.launch {
            val dir = newest.parentFile ?: newest.absoluteFile.parentFile!!
            val base = RevisionMerge.suggestedName(newest).removeSuffix(".pdf")
            var dest = File(dir, "$base.pdf")
            var attempt = 2
            while (dest.exists()) {
                dest = File(dir, "$base ($attempt).pdf")
                attempt++
            }
            val chosen = current.chosen + overrides
            val result = RevisionMerge.build(
                plan = current,
                chosen = chosen,
                covers = covers,
                dest = dest,
                workDir = File(context.cacheDir, "revision-merge"),
                onProgress = { p -> withContext(Dispatchers.Main) { progress = p } }
            )
            busy = false; progress = 0f
            result.onSuccess { pages ->
                Toast.makeText(context, "اتعمل ${dest.name} — $pages صفحة", Toast.LENGTH_LONG).show()
                onOpenFile(dest.absolutePath)
            }.onFailure { e ->
                dest.delete()
                error = "البناء فشل: ${e.message ?: "خطأ غير معروف"}"
            }
        }
    }

    LaunchedEffect(Unit) { PdfOps.ensureInit(context) }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
    ) {
        MergeTopBar(
            fileCount = files.size,
            sheetCount = plan?.numbers?.size,
            onClose = onClose
        )

        if (busy) {
            Column(Modifier.padding(horizontal = Space.screen, vertical = Space.sm)) {
                Text(stage, style = CwText.codeSmall, color = c.textTertiary)
                Spacer(Modifier.height(Space.xs))
                CwProgressBar(fraction = progress)
            }
        }

        LazyColumn(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                start = Space.screen, end = Space.screen,
                top = Space.sm, bottom = Space.bottomInset
            ),
            verticalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            item("head-files") {
                CwSectionHeader("١ · الملفات — من الأقدم للأحدث", count = files.size)
            }

            itemsIndexed(files, key = { _, f -> f.absolutePath }) { index, file ->
                val scan = plan?.scans?.getOrNull(index)?.takeIf { it.file == file }
                SourceRow(
                    index = index,
                    last = index == files.lastIndex,
                    file = file,
                    sheets = scan?.drawings?.size,
                    covers = scan?.covers?.size,
                    enabled = !busy,
                    onUp = {
                        if (index > 0) {
                            val moved = files.removeAt(index); files.add(index - 1, moved); invalidate()
                        }
                    },
                    onDown = {
                        if (index < files.lastIndex) {
                            val moved = files.removeAt(index); files.add(index + 1, moved); invalidate()
                        }
                    },
                    onRemove = { files.removeAt(index); invalidate() }
                )
            }

            item("head-settings") { CwSectionHeader("٢ · الإعدادات") }

            item("settings") {
                CwCard(style = CwCardStyle.Plain) {
                    CwField(
                        value = pattern,
                        onValueChange = { pattern = it; invalidate() },
                        label = "نمط رقم اللوحة",
                        helper = "تعبير نمطي، المجموعة الأولى هي الرقم. الافتراضي بتاع باكارات: RFT-(\\d{5})",
                        singleLine = true
                    )
                    Spacer(Modifier.height(Space.md))

                    Text(
                        "صفحات الإرسالية",
                        style = MaterialTheme.typography.labelMedium,
                        color = c.textSecondary
                    )
                    Spacer(Modifier.height(Space.xs))
                    ChipRow(
                        options = listOf(
                            RevisionMerge.Covers.LATEST to "من الأحدث",
                            RevisionMerge.Covers.FIRST to "من الأقدم",
                            RevisionMerge.Covers.ALL to "من الكل",
                            RevisionMerge.Covers.NONE to "بدون"
                        ),
                        selected = covers,
                        onSelect = { coversOrdinal = it.ordinal }
                    )
                    Spacer(Modifier.height(Space.md))

                    Text(
                        "ترتيب اللوحات",
                        style = MaterialTheme.typography.labelMedium,
                        color = c.textSecondary
                    )
                    Spacer(Modifier.height(Space.xs))
                    ChipRow(
                        options = listOf(
                            RevisionMerge.Order.NUMBER to "برقم اللوحة",
                            RevisionMerge.Order.FILE to "زي أقدم ملف"
                        ),
                        selected = order,
                        onSelect = { orderOrdinal = it.ordinal; plan?.let { p -> plan = RevisionMerge.plan(p.scans, it) } }
                    )
                    Spacer(Modifier.height(Space.lg))

                    CwButton(
                        "افحص الملفات",
                        { analyse() },
                        style = CwButtonStyle.Secondary,
                        enabled = !busy && files.isNotEmpty(),
                        fillWidth = true
                    )
                }
            }

            val current = plan
            if (error != null) {
                item("error") {
                    CwBanner(title = error!!, tone = CwTone.Danger)
                }
            }

            if (current != null) {
                item("head-table") {
                    CwSectionHeader("٣ · جدول اللوحات", count = current.numbers.size)
                }

                item("summary") {
                    CwCard(style = CwCardStyle.Inset, contentPadding = PaddingValues(Space.md)) {
                        current.scans.forEachIndexed { index, scan ->
                            val taken = current.numbers.count {
                                (overrides[it] ?: current.chosen[it]) == index
                            }
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = Space.xxs),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Space.sm)
                            ) {
                                SeqBadge(index, highlight = index == current.scans.lastIndex)
                                Text(
                                    scan.name,
                                    style = CwText.codeSmall,
                                    color = c.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "$taken لوحة",
                                    style = CwText.codeSmall,
                                    color = if (taken == 0) c.textTertiary else c.accent
                                )
                            }
                        }
                    }
                }

                itemsIndexed(current.warnings) { _, warning ->
                    CwBanner(title = warning, tone = CwTone.Warning)
                }

                items(current.numbers, key = { "sheet-$it" }) { number ->
                    val source = overrides[number] ?: current.chosen[number] ?: 0
                    SheetRow(
                        number = number,
                        source = source,
                        newest = current.scans.lastIndex,
                        sources = current.scans.mapIndexedNotNull { index, scan ->
                            if (scan.drawings.containsKey(number)) index to scan.name else null
                        },
                        enabled = !busy,
                        onPick = { picked ->
                            if (picked == current.chosen[number]) overrides.remove(number)
                            else overrides[number] = picked
                        }
                    )
                }

                item("build") {
                    Column {
                        Spacer(Modifier.height(Space.md))
                        CwButton(
                            "ابنِ الملف النهائي",
                            { build() },
                            enabled = !busy,
                            fillWidth = true
                        )
                        Spacer(Modifier.height(Space.sm))
                        Text(
                            "المطابقة برقم اللوحة، مش بترتيب الصفحة. راجع الجدول قبل ما تبني.",
                            style = MaterialTheme.typography.labelMedium,
                            color = c.textTertiary
                        )
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════ أجزاء الشاشة

@Composable
private fun MergeTopBar(fileCount: Int, sheetCount: Int?, onClose: () -> Unit) {
    val c = LocalCwColors.current
    Surface(color = c.surface, shadowElevation = Elevation.raised) {
        Row(
            Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = Space.sm, vertical = Space.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CwIconButton(Icons.Filled.Close, "إغلاق", onClose)
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = Space.sm)
            ) {
                Text(
                    "الإصدار النهائي",
                    style = MaterialTheme.typography.titleSmall,
                    color = c.textPrimary,
                    maxLines = 1
                )
                Text(
                    if (sheetCount == null) "$fileCount ملف"
                    else "$fileCount ملف · $sheetCount لوحة",
                    style = CwText.codeSmall,
                    color = c.textTertiary,
                    maxLines = 1
                )
            }
        }
    }
}

/** ملف مصدر في الترتيب الزمني. */
@Composable
private fun SourceRow(
    index: Int,
    last: Boolean,
    file: File,
    sheets: Int?,
    covers: Int?,
    enabled: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: () -> Unit
) {
    val c = LocalCwColors.current
    CwCard(style = CwCardStyle.Plain, contentPadding = PaddingValues(Space.md)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            SeqBadge(index, highlight = last)
            Column(Modifier.weight(1f)) {
                Text(
                    file.name,
                    style = CwText.codeSmall,
                    color = c.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    buildString {
                        append(
                            when {
                                last && index == 0 -> "الملف الوحيد"
                                last -> "الأحدث — له الأولوية"
                                index == 0 -> "الأقدم"
                                else -> "وسط"
                            }
                        )
                        if (sheets != null) append(" · $sheets لوحة")
                        if (covers != null && covers > 0) append(" · $covers صفحة إرسالية")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (last) c.accent else c.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            CwIconButton(
                Icons.Filled.ArrowUpward, "حرّكه للأقدم", onUp,
                enabled = enabled && index > 0
            )
            CwIconButton(
                Icons.Filled.ArrowDownward, "حرّكه للأحدث", onDown,
                enabled = enabled && !last
            )
            CwIconButton(
                Icons.Filled.DeleteOutline, "شيله من الدمج", onRemove,
                tint = c.danger.fg, enabled = enabled
            )
        }
    }
}

/** صف لوحة واحدة — رقمها ومن أنهي ملف هتتاخد. */
@Composable
private fun SheetRow(
    number: String,
    source: Int,
    newest: Int,
    sources: List<Pair<Int, String>>,
    enabled: Boolean,
    onPick: (Int) -> Unit
) {
    val c = LocalCwColors.current
    var open by remember { mutableStateOf(false) }
    val updated = source == newest && newest > 0

    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(Radius.shapeMd)
                .clickable(enabled = enabled && sources.size > 1) { open = true }
                // مساحة اللمس مش بتقل عن الحد الأدنى — الصف ده هو اللي
                // بيفتح قايمة تغيير المصدر، والصفوف دي بتتقري بإيد واحدة
                // في الموقع.
                .heightIn(min = Sizes.touch)
                .padding(horizontal = Space.sm, vertical = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            Text(
                number,
                style = CwText.code,
                fontWeight = FontWeight.SemiBold,
                color = if (updated) c.accent else c.textPrimary,
                modifier = Modifier.weight(1f)
            )
            SeqBadge(source, highlight = updated)
            if (sources.size > 1) {
                Icon(
                    Icons.Filled.UnfoldMore,
                    contentDescription = "غيّر المصدر",
                    tint = c.textTertiary,
                    modifier = Modifier.size(IconSize.sm)
                )
            } else {
                Spacer(Modifier.width(IconSize.sm))
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            sources.forEach { (index, name) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            "${index.toString().padStart(2, '0')} · $name",
                            style = CwText.codeSmall,
                            maxLines = 2
                        )
                    },
                    onClick = { open = false; onPick(index) }
                )
            }
        }
    }
}

/** رقم الملف في الترتيب — نفس الرقم بيظهر في الجدول فبيربط الاتنين. */
@Composable
private fun SeqBadge(index: Int, highlight: Boolean) {
    val c = LocalCwColors.current
    Box(
        Modifier
            .size(30.dp)
            .clip(Radius.shapeSm)
            .background(if (highlight) c.accentContainer else c.surfaceAlt),
        contentAlignment = Alignment.Center
    ) {
        Text(
            index.toString().padStart(2, '0'),
            style = CwText.codeSmall,
            color = if (highlight) c.onAccentContainer else c.textSecondary
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
        verticalArrangement = Arrangement.spacedBy(Space.xs)
    ) {
        options.forEach { (value, label) ->
            CwChip(label = label, selected = value == selected, onClick = { onSelect(value) })
        }
    }
}
