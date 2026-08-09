package com.corewall.qaqc.ui.pdf

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.corewall.qaqc.pdfengine.PdfSearchState
import com.corewall.qaqc.pdfengine.SearchHit
import com.corewall.qaqc.ui.design.CwIconButton
import com.corewall.qaqc.ui.design.CwText
import com.corewall.qaqc.ui.design.Elevation
import com.corewall.qaqc.ui.design.IconSize
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Radius
import com.corewall.qaqc.ui.design.Sizes
import com.corewall.qaqc.ui.design.Space
import com.corewall.qaqc.ui.design.Stroke as CwStroke

/**
 * شريط البحث — بيحلّ محل الشريط العلوي لما يفتح.
 *
 * ليه بديل مش إضافة: البحث في رسمة معناه إن الشاشة كلها بقت للنتيجة.
 * شريطين فوق بعض بياكلوا ٢٠٪ من ارتفاع الشاشة على موبايل، وده بالظبط
 * الجزء اللي المستخدم عايز يشوف فيه اللي لقاه.
 *
 * والعدّاد (٣ / ١٧) مش زينة: من غيره المستخدم مش عارف لو "التالي" هتلفّ
 * ولا لسه قدّامه نتايج، ولا لو البحث خلص أصلاً.
 */
@Composable
fun PdfSearchBar(
    search: PdfSearchState,
    pageCount: Int,
    currentPage: Int,
    onJump: (SearchHit) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalCwColors.current
    var field by remember { mutableStateOf(TextFieldValue(search.query)) }
    var listOpen by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        // لوحة المفاتيح بتفتح لوحدها. البحث اللي محتاج ضغطتين عشان تبدأ
        // تكتب مش بحث سريع.
        field = field.copy(selection = TextRange(field.text.length))
        runCatching { focus.requestFocus() }
    }

    Surface(
        modifier.fillMaxWidth(),
        color = c.surface.copy(alpha = 0.97f),
        shadowElevation = Elevation.floating
    ) {
        Column(Modifier.statusBarsPadding()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.sm, vertical = Space.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.xxs)
            ) {
                CwIconButton(Icons.Filled.Close, "إقفال البحث", onClose)

                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = Sizes.control)
                        .background(c.surfaceAlt, Radius.pill)
                        .padding(horizontal = Space.md),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (field.text.isEmpty()) {
                        Text(
                            "دوّر في الملف…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.textTertiary
                        )
                    }
                    BasicTextField(
                        value = field,
                        onValueChange = {
                            field = it
                            search.setQuery(it.text, currentPage)
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = c.textPrimary),
                        cursorBrush = SolidColor(c.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = { search.next()?.let(onJump) }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focus)
                    )
                }

                Counter(search)

                CwIconButton(
                    Icons.Filled.KeyboardArrowUp, "النتيجة السابقة",
                    { search.prev()?.let(onJump) },
                    enabled = search.hits.isNotEmpty()
                )
                CwIconButton(
                    Icons.Filled.KeyboardArrowDown, "النتيجة التالية",
                    { search.next()?.let(onJump) },
                    enabled = search.hits.isNotEmpty()
                )
                CwIconButton(
                    Icons.AutoMirrored.Filled.FormatListBulleted, "كل النتائج",
                    { listOpen = !listOpen },
                    active = listOpen,
                    enabled = search.hits.isNotEmpty()
                )
            }

            // ── مفاتيح الدقّة
            Row(
                Modifier.padding(start = Space.huge, end = Space.md, bottom = Space.xs),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Toggle("حسّاس لحالة الأحرف", "Aa", search.matchCase) {
                    search.setMatchCase(it, currentPage)
                }
                Toggle("كلمة كاملة", "‏كلمة", search.wholeWord) {
                    search.setWholeWord(it, currentPage)
                }
            }

            // ── تقدّم المسح: بيبان وقت الشغل بس
            if (search.running && pageCount > 0) {
                LinearProgressIndicator(
                    progress = { (search.scanned.toFloat() / pageCount).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(PROGRESS_HEIGHT),
                    color = c.accent,
                    trackColor = c.surfaceAlt
                )
            }

            if (search.noTextLayer && !search.running) {
                NoTextNotice()
            }

            if (listOpen && search.hits.isNotEmpty()) {
                ResultList(search, onJump = { hit -> listOpen = false; onJump(hit) })
            }
        }
    }
}

private val PROGRESS_HEIGHT = 2.dp

@Composable
private fun Counter(search: PdfSearchState) {
    val c = LocalCwColors.current
    val label = when {
        search.query.trim().length < 2 -> ""
        search.hits.isEmpty() && search.running -> "…"
        search.hits.isEmpty() -> "٠"
        else -> "${search.active + 1}‏/${search.hits.size}"
    }
    if (label.isEmpty()) return
    Text(
        label,
        style = CwText.codeSmall,
        color = if (search.hits.isEmpty()) c.textTertiary else c.textSecondary,
        maxLines = 1,
        modifier = Modifier.padding(horizontal = Space.xs)
    )
}

@Composable
private fun Toggle(
    label: String,
    short: String,
    on: Boolean,
    onChange: (Boolean) -> Unit
) {
    val c = LocalCwColors.current
    Surface(
        onClick = { onChange(!on) },
        shape = Radius.pill,
        color = if (on) c.accentContainer else Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(
            CwStroke.hair,
            if (on) c.accent else c.outline
        )
    ) {
        Text(
            short,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
            color = if (on) c.accent else c.textSecondary,
            maxLines = 1,
            modifier = Modifier
                .semantics { contentDescription = label }
                .padding(horizontal = Space.md, vertical = Space.xs)
        )
    }
}

@Composable
private fun NoTextNotice() {
    val c = LocalCwColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.warning.container)
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm)
    ) {
        Icon(
            Icons.Filled.Info,
            contentDescription = null,
            tint = c.warning.fg,
            modifier = Modifier.size(IconSize.sm)
        )
        Text(
            "الملف ده صور ممسوحة — مفيهوش طبقة نص، فالبحث مش هيلاقي حاجة.",
            style = MaterialTheme.typography.bodySmall,
            color = c.warning.onContainer
        )
    }
}

/**
 * قائمة النتائج بالسياق.
 *
 * رقم الصفحة لوحده مش كفاية: "T10" موجودة في ٤٠ صفحة، والمستخدم عايز
 * اللي جنبها مكتوب "TOP" مش اللي جنبها "STIRRUPS". السطر ده بيوفّر
 * أربع نطّات على الأقل.
 */
@Composable
private fun ResultList(search: PdfSearchState, onJump: (SearchHit) -> Unit) {
    val c = LocalCwColors.current
    LazyColumn(
        Modifier
            .fillMaxWidth()
            .heightIn(max = RESULTS_MAX_HEIGHT)
            .background(c.surface)
    ) {
        itemsIndexed(search.hits) { index, hit ->
            Surface(
                onClick = { search.selectAt(index)?.let(onJump) },
                color = if (index == search.active) c.accentContainer else Color.Transparent,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(horizontal = Space.md, vertical = Space.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.md)
                ) {
                    Text(
                        "${hit.page + 1}",
                        style = CwText.codeSmall,
                        color = c.accent,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        hit.snippet.ifBlank { "…" },
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(CwStroke.hair)
                    .background(c.divider)
            )
        }
    }
}

private val RESULTS_MAX_HEIGHT = 260.dp
