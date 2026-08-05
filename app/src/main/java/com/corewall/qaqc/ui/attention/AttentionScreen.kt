package com.corewall.qaqc.ui.attention

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.domain.AttentionItem
import com.corewall.qaqc.ui.design.CwCard
import com.corewall.qaqc.ui.design.CwCardStyle
import com.corewall.qaqc.ui.design.CwEmptyState
import com.corewall.qaqc.ui.design.CwSectionHeader
import com.corewall.qaqc.ui.design.CwSegmented
import com.corewall.qaqc.ui.design.CwStatusBadge
import com.corewall.qaqc.ui.design.CwText
import com.corewall.qaqc.ui.design.CwTone
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Space
import com.corewall.qaqc.ui.design.semantic

/**
 * الفجوات والتغييرات — الشاشة اللي كانت **مبنيّة ومحدش يقدر يوصلها**.
 *
 * دي واحدة من أهم شاشات المنتج: الفجوة معناها إن العنصر داخل مداه في الدور
 * ده بس مفيش صف في الجدول بيغطّيه — يعني حد هيقف في الموقع من غير تسليح
 * معروف. ومع ذلك مكانش ليها أي مدخل في التطبيق.
 *
 * الشاشة القديمة كمان كانت بتخلط الفجوات مع التغييرات العادية في قايمة
 * واحدة، والفجوة (مشكلة بيانات) مش نفس التغيير (معلومة تنفيذية).
 */
private enum class Filter(val label: String) {
    ALL("الكل"),
    GAPS("فجوات"),
    CHANGES("تغييرات")
}

@Composable
fun AttentionScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val c = LocalCwColors.current
    val level by vm.currentLevel.collectAsStateWithLifecycle()
    val schedule by vm.schedule.collectAsStateWithLifecycle()

    val items = remember(schedule, level) { vm.attentionFor(level) }
    val gaps = remember(items) { items.filter { it.gapHere } }
    val changes = remember(items) { items.filter { !it.gapHere } }

    var filter by rememberSaveable { mutableStateOf(Filter.ALL) }
    val filters = Filter.entries
    val shown = when (filter) {
        Filter.ALL -> items
        Filter.GAPS -> gaps
        Filter.CHANGES -> changes
    }

    Column(modifier.fillMaxSize()) {
        CwSegmented(
            options = filters,
            selectedIndex = filters.indexOf(filter),
            label = { f ->
                val n = when (f) {
                    Filter.ALL -> items.size
                    Filter.GAPS -> gaps.size
                    Filter.CHANGES -> changes.size
                }
                "${f.label} ($n)"
            },
            onSelect = { filter = filters[it] },
            modifier = Modifier.padding(horizontal = Space.screen, vertical = Space.sm)
        )

        if (shown.isEmpty()) {
            CwEmptyState(
                icon = if (filter == Filter.GAPS) Icons.Filled.CheckCircle else Icons.Filled.CompareArrows,
                title = when (filter) {
                    Filter.GAPS -> "مفيش فجوات في دور $level"
                    Filter.CHANGES -> "التسليح زي الدور اللي قبله بالظبط"
                    Filter.ALL -> "مفيش حاجة محتاجة انتباه في دور $level"
                },
                detail = when (filter) {
                    Filter.GAPS ->
                        "كل عنصر في الدور ده له صف بيغطّيه في الجدول. " +
                            "الفجوة بتظهر هنا لما عنصر يكون داخل مداه بس مفيش صف بيوصف تسليحه."
                    else ->
                        "المقارنة بتتعمل مع الدور اللي قبله واللي بعده مباشرة، " +
                            "والحساب من الجدول نفسه مش من الذكاء الاصطناعي."
                }
            )
            return@Column
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Space.screen, end = Space.screen,
                top = Space.xs, bottom = Space.bottomInset
            ),
            verticalArrangement = Arrangement.spacedBy(Space.stack)
        ) {
            // الفجوات فوق دايماً — دي مشكلة بيانات، مش معلومة.
            if (filter != Filter.CHANGES && gaps.isNotEmpty()) {
                item(key = "gaps-header") { CwSectionHeader("فجوات في الجدول", count = gaps.size) }
                items(gaps, key = { "gap-${it.mark}" }) { AttentionCard(it) }
            }
            if (filter != Filter.GAPS && changes.isNotEmpty()) {
                item(key = "changes-header") { CwSectionHeader("تغييرات التسليح", count = changes.size) }
                items(changes, key = { "chg-${it.mark}" }) { AttentionCard(it) }
            }
        }
    }
}

@Composable
private fun AttentionCard(item: AttentionItem) {
    val c = LocalCwColors.current
    val tone = if (item.gapHere) CwTone.Danger else CwTone.Info
    val s = tone.semantic()

    CwCard(style = CwCardStyle.Accent, accent = s.solid) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            Text(item.mark, style = CwText.code, color = c.textPrimary)
            CwStatusBadge(
                label = if (item.isWall) "حائط" else "كمرة",
                tone = CwTone.Neutral,
                compact = true
            )
            Spacer(Modifier.weight(1f))
            if (item.gapHere) CwStatusBadge("فجوة", CwTone.Danger, compact = true)
        }

        if (item.gapHere) {
            Spacer(Modifier.height(Space.sm))
            Text(
                "مفيش صف في الجدول بيغطّي الدور ده، رغم إن العنصر داخل مداه. " +
                    "يعني التسليح المطلوب هنا مش متعرّف.",
                style = MaterialTheme.typography.bodySmall,
                color = s.fg
            )
        }

        if (item.vsPrev.isNotEmpty()) {
            Spacer(Modifier.height(Space.md))
            DiffBlock("مقارنة بالدور اللي قبله", item.vsPrev)
        }
        if (item.vsNext.isNotEmpty()) {
            Spacer(Modifier.height(Space.md))
            DiffBlock("مقارنة بالدور اللي بعده", item.vsNext)
        }
        item.note?.let { note ->
            Spacer(Modifier.height(Space.sm))
            Text(note, style = MaterialTheme.typography.labelMedium, color = c.warning.fg)
        }
    }
}

@Composable
private fun DiffBlock(title: String, changes: List<com.corewall.qaqc.domain.FieldChange>) {
    val c = LocalCwColors.current
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Text(title, style = CwText.sectionLabel, color = c.textTertiary)
        changes.forEach { ch ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                Text(
                    ch.field,
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textTertiary,
                    modifier = Modifier.weight(0.8f)
                )
                // القديم ← الجديد. السهم في RTL بيقرا صح من غير قلب.
                Text(
                    ch.before,
                    style = CwText.codeSmall,
                    color = c.textTertiary,
                    modifier = Modifier.weight(1f)
                )
                Text("←", style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
                Text(
                    ch.after,
                    style = CwText.codeSmall,
                    color = c.textPrimary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
