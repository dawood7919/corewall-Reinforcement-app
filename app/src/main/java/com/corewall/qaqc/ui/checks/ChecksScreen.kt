package com.corewall.qaqc.ui.checks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.ui.design.CwCard
import com.corewall.qaqc.ui.design.CwCardStyle
import com.corewall.qaqc.ui.design.CwLeadingIcon
import com.corewall.qaqc.ui.design.CwProgressBar
import com.corewall.qaqc.ui.design.CwSectionHeader
import com.corewall.qaqc.ui.design.CwStatusBadge
import com.corewall.qaqc.ui.design.CwText
import com.corewall.qaqc.ui.design.CwTone
import com.corewall.qaqc.ui.design.IconSize
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Space
import com.corewall.qaqc.ui.design.semantic
import com.corewall.qaqc.ui.nav.Dest

/**
 * الفحص — "فين المشكلة في الدور ده؟"
 *
 * الشاشة دي هي إجابة أخطر اكتشاف في المراجعة: **٥ شاشات من ٣١ كانت مبنيّة
 * ومفيش أي طريق يوصلها**، منهم كاشف الفجوات وتقرير عدّ الحديد — وهما من
 * صميم المنتج. النظام القديم مكانش عنده مكان طبيعي يحطّهم فيه، فضاعوا.
 *
 * كل صف هنا بيقول **الرقم الحقيقي** قبل ما تدخل — عشان تعرف تدخل فين.
 */
@Composable
fun ChecksScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val c = LocalCwColors.current
    val summary by vm.floorSummary.collectAsStateWithLifecycle()
    val pour by vm.pourReadiness.collectAsStateWithLifecycle()
    val level by vm.currentLevel.collectAsStateWithLifecycle()
    val schedule by vm.schedule.collectAsStateWithLifecycle()
    val barCounts by vm.barCounts.collectAsStateWithLifecycle()

    val changes = remember(schedule, level) { vm.attentionFor(level).size }
    val countedElements = remember(barCounts, level) {
        barCounts.filter { it.level == level }.map { it.elementId }.distinct().size
    }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Space.screen, end = Space.screen,
            top = Space.md, bottom = Space.bottomInset
        ),
        verticalArrangement = Arrangement.spacedBy(Space.stack)
    ) {
        item(key = "verdict") {
            PourSummaryCard(
                verdict = pour.verdict,
                blockers = pour.blockers.size,
                warnings = pour.warnings.size,
                approvedPercent = pour.approvedPercent,
                onOpen = { vm.go(Dest.PourReadiness) }
            )
        }

        item(key = "quality-header") { CwSectionHeader("جودة البيانات") }

        item(key = "gaps") {
            CheckRow(
                icon = Icons.Filled.LinkOff,
                title = "الفجوات في الجدول",
                detail = "عنصر داخل مداه في الدور ده بس مفيش صف بيغطّيه",
                count = summary.gaps,
                tone = if (summary.gaps > 0) CwTone.Warning else CwTone.Success,
                onClick = { vm.go(Dest.Gaps) }
            )
        }

        item(key = "changes") {
            CheckRow(
                icon = Icons.Filled.CompareArrows,
                title = "تغييرات التسليح",
                detail = "الفرق بين الدور ده واللي قبله واللي بعده",
                count = changes,
                tone = if (changes > 0) CwTone.Info else CwTone.Success,
                onClick = { vm.go(Dest.Gaps) }
            )
        }

        item(key = "unnamed") {
            CheckRow(
                icon = Icons.Filled.Handyman,
                title = "عناصر من غير كود",
                detail = "عنصر من غير كود مالوش حالة ومش داخل في أي حساب",
                count = summary.unnamed,
                tone = if (summary.unnamed > 0) CwTone.Warning else CwTone.Success,
                onClick = { vm.go(Dest.Tools) }
            )
        }

        item(key = "counting-header") { CwSectionHeader("العدّ والحساب") }

        item(key = "counting") {
            CheckRow(
                icon = Icons.Filled.Calculate,
                title = "تقرير عدّ الحديد",
                detail = "العدّ الرأسي المسجّل مقابل المطلوب في الجدول",
                count = countedElements,
                countLabel = "عنصر متعدّ",
                tone = if (countedElements > 0) CwTone.Info else CwTone.Neutral,
                onClick = { vm.go(Dest.CountingReport) }
            )
        }

        item(key = "tools") {
            CheckRow(
                icon = Icons.Filled.Handyman,
                title = "أدوات التحليل",
                detail = "حساب وزن الحديد وتوزيع الحالات وأدوات الجدول",
                tone = CwTone.Neutral,
                onClick = { vm.go(Dest.Tools) }
            )
        }

        item(key = "ai-header") { CwSectionHeader("قراية المساعد") }

        item(key = "ai") {
            CheckRow(
                icon = Icons.Filled.AutoAwesome,
                title = "تحليل الدور",
                detail = "المساعد بيفسّر الأرقام — الحساب نفسه بيتم في التطبيق",
                tone = CwTone.Info,
                onClick = { vm.go(Dest.FloorAnalysis) }
            )
        }
    }
}

@Composable
private fun PourSummaryCard(
    verdict: String,
    blockers: Int,
    warnings: Int,
    approvedPercent: Int,
    onOpen: () -> Unit
) {
    val c = LocalCwColors.current
    val tone = when {
        blockers > 0 -> CwTone.Danger
        warnings > 0 -> CwTone.Warning
        else -> CwTone.Success
    }
    val s = tone.semantic()
    CwCard(style = CwCardStyle.Accent, accent = s.solid, onClick = onOpen) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.md)
        ) {
            CwLeadingIcon(Icons.Filled.WaterDrop, tone = tone)
            Column(Modifier.weight(1f)) {
                Text("جاهزية الصبّ", style = MaterialTheme.typography.labelMedium, color = c.textTertiary)
                Text(verdict, style = MaterialTheme.typography.titleMedium, color = s.fg)
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowBackIos,
                contentDescription = null,
                tint = c.textTertiary,
                modifier = Modifier.size(IconSize.sm)
            )
        }
        Spacer(Modifier.height(Space.md))
        CwProgressBar(fraction = approvedPercent / 100f, tone = tone)
        Spacer(Modifier.height(Space.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            if (blockers > 0) CwStatusBadge("$blockers مانع", CwTone.Danger, compact = true)
            if (warnings > 0) CwStatusBadge("$warnings ملاحظة", CwTone.Warning, compact = true)
            if (blockers == 0 && warnings == 0) CwStatusBadge("مفيش ملاحظات", CwTone.Success, compact = true)
        }
    }
}

@Composable
private fun CheckRow(
    icon: ImageVector,
    title: String,
    detail: String,
    tone: CwTone,
    onClick: () -> Unit,
    count: Int? = null,
    countLabel: String? = null
) {
    val c = LocalCwColors.current
    val s = tone.semantic()
    CwCard(onClick = onClick) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.md)
        ) {
            CwLeadingIcon(icon, tone = tone)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                Spacer(Modifier.height(Space.xxs))
                Text(detail, style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
            }
            if (count != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$count", style = CwText.metricSmall, color = if (count == 0) c.textTertiary else s.fg)
                    if (countLabel != null) {
                        Text(countLabel, style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
                    }
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowBackIos,
                contentDescription = null,
                tint = c.textTertiary,
                modifier = Modifier.size(IconSize.sm)
            )
        }
    }
}
