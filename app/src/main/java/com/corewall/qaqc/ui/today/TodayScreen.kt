package com.corewall.qaqc.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.Lens
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.domain.FloorSummary
import com.corewall.qaqc.domain.PourReadiness
import com.corewall.qaqc.ui.design.CwBanner
import com.corewall.qaqc.ui.design.CwButton
import com.corewall.qaqc.ui.design.CwButtonStyle
import com.corewall.qaqc.ui.design.CwCard
import com.corewall.qaqc.ui.design.CwCardStyle
import com.corewall.qaqc.ui.design.CwMetric
import com.corewall.qaqc.ui.design.CwMetricRow
import com.corewall.qaqc.ui.design.CwProgressBar
import com.corewall.qaqc.ui.design.CwSectionHeader
import com.corewall.qaqc.ui.design.CwStatusBadge
import com.corewall.qaqc.ui.design.CwText
import com.corewall.qaqc.ui.design.CwTone
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Sizes
import com.corewall.qaqc.ui.design.Space
import com.corewall.qaqc.ui.design.semantic
import com.corewall.qaqc.ui.nav.DataSection
import com.corewall.qaqc.ui.nav.Dest

/**
 * اليوم — حالة الدور الشغّال.
 *
 * الشاشة القديمة (٨٠٢ سطر) كانت بترمي ٩ أقسام ورا بعض من غير أي ترتيب
 * أولويات: تحية، رحلة مبنى، ملخّص، شبكة مقاييس، مهمة اليوم، تنبيهات، حلقات
 * صحّة، نقاط إنتاجية، إجراءات. العين مكانتش تعرف تبص على إيه الأول، وكان
 * فيها أرقام متلفّقة (السلامة ٩٢٪ ثابتة في الكود).
 *
 * دلوقتي القراية ليها ترتيب واحد: **الحكم** (أقدر أصبّ؟) → **اللي محتاج
 * تدخّل** → **التقدّم** → **الشغل المفتوح** → **الموقع** → إجراءات.
 * الكثافة مسموحة، بس كل سطح لازم يكون له عنصر أساسي واضح.
 */
@Composable
fun TodayScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val c = LocalCwColors.current
    val summary by vm.floorSummary.collectAsStateWithLifecycle()
    val pour by vm.pourReadiness.collectAsStateWithLifecycle()
    val insight by vm.uploadInsight.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LazyColumn(
        modifier
            .fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(
            start = Space.screen, end = Space.screen,
            top = Space.md, bottom = Space.bottomInset
        ),
        verticalArrangement = Arrangement.spacedBy(Space.stack)
    ) {
        // ١) الحكم — أهم سؤال في المنتج، فهو أول حاجة والأكبر.
        item(key = "verdict") {
            VerdictCard(result = pour, onOpen = { vm.go(Dest.PourReadiness) })
        }

        // ٢) اللي محتاج تدخّل — بيظهر بس لما يكون فيه فعلاً حاجة.
        if (summary.rejected > 0) {
            item(key = "rejected") {
                CwBanner(
                    title = "${summary.rejected} عنصر مرفوض",
                    detail = "لازم يتصلّح ويتعاد فحصه قبل الصبّ.",
                    tone = CwTone.Danger,
                    onClick = { vm.goToLens(Lens.REINF) }
                )
            }
        }
        if (summary.gaps > 0) {
            item(key = "gaps") {
                CwBanner(
                    title = "${summary.gaps} فجوة في الجدول",
                    detail = "عناصر الدور ده داخلة في مداها بس مفيش صف بيغطّيها.",
                    tone = CwTone.Warning,
                    onClick = { vm.go(Dest.Gaps) }
                )
            }
        }
        insight?.let { text ->
            item(key = "insight") {
                CwBanner(
                    title = "المساعد حلّل الملف الجديد",
                    detail = text,
                    tone = CwTone.Info,
                    onClick = { vm.go(Dest.FloorKnowledge) }
                )
            }
        }

        // ٣) التقدّم
        item(key = "progress-header") { CwSectionHeader("تقدّم الدور") }
        item(key = "progress") { ProgressCard(summary) }

        // ٤) الشغل المفتوح
        item(key = "work-header") { CwSectionHeader("الشغل المفتوح") }
        item(key = "work") {
            CwMetricRow {
                CwMetric(
                    value = "${summary.pendingInspection}",
                    label = "فحوصات معلّقة",
                    tone = if (summary.pendingInspection > 0) CwTone.Warning else CwTone.Success,
                    modifier = Modifier.weight(1f),
                    onClick = { vm.goToLens(Lens.REINF) }
                )
                CwMetric(
                    value = "${summary.openTasks}",
                    label = "مهام مفتوحة",
                    tone = if (summary.openTasks > 0) CwTone.Info else CwTone.Success,
                    modifier = Modifier.weight(1f),
                    onClick = { vm.goToData(DataSection.TASKS) }
                )
                CwMetric(
                    value = "${summary.unnamed}",
                    label = "عناصر بدون كود",
                    tone = if (summary.unnamed > 0) CwTone.Warning else CwTone.Success,
                    modifier = Modifier.weight(1f),
                    onClick = { vm.goToLens(Lens.REINF) }
                )
            }
        }

        // ٥) الموقع النهاردة — بيظهر بس لو فيه كشف حضور فعلاً.
        if (summary.peopleOnSite > 0) {
            item(key = "site-header") { CwSectionHeader("الموقع النهاردة") }
            item(key = "site") {
                CwMetricRow {
                    CwMetric("${summary.workers}", "عامل", Modifier.weight(1f))
                    CwMetric("${summary.foremen}", "فورمان", Modifier.weight(1f))
                    CwMetric("${summary.engineers}", "مهندس", Modifier.weight(1f))
                }
            }
        }

        // ٦) إجراءات سريعة
        item(key = "actions-header") { CwSectionHeader("إجراءات") }
        item(key = "actions") { QuickActions(vm) }
    }
}

/**
 * كارت الحكم. الحكم نفسه هو العنصر الأساسي في السطح ده — نص كبير، نبرة
 * لونية، وأيقونة. مفيش حاجة تانية بتنافسه في الكارت.
 */
@Composable
private fun VerdictCard(result: PourReadiness.Result, onOpen: () -> Unit) {
    val c = LocalCwColors.current
    val tone = when {
        result.hasNothingToPour -> CwTone.Neutral
        result.blockers.isNotEmpty() -> CwTone.Danger
        result.warnings.isNotEmpty() -> CwTone.Warning
        result.ready -> CwTone.Success
        else -> CwTone.Neutral
    }
    val s = tone.semantic()

    CwCard(style = CwCardStyle.Accent, accent = s.solid, onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "جاهزية الصبّ",
                style = MaterialTheme.typography.labelMedium,
                color = c.textTertiary,
                modifier = Modifier.weight(1f)
            )
            CwStatusBadge(
                label = if (result.blockers.isEmpty()) "مفيش موانع" else "${result.blockers.size} مانع",
                tone = tone,
                compact = true
            )
        }
        Spacer(Modifier.height(Space.sm))
        Text(result.verdict, style = CwText.metric, color = s.fg)
        Spacer(Modifier.height(Space.sm))
        Text(
            verdictDetail(result),
            style = MaterialTheme.typography.bodyMedium,
            color = c.textSecondary,
            modifier = Modifier.widthIn(max = Sizes.readableMax)
        )
        if (result.scopeCount > 0) {
            Spacer(Modifier.height(Space.md))
            CwProgressBar(
                fraction = result.approvedPercent / 100f,
                tone = tone
            )
            Spacer(Modifier.height(Space.xs))
            Text(
                "${result.approvedCount} معتمد من ${result.scopeCount} في نطاق الصبّ · ${result.approvedPercent}%",
                style = MaterialTheme.typography.labelMedium,
                color = c.textTertiary
            )
        }
    }
}

private fun verdictDetail(r: PourReadiness.Result): String = when {
    r.hasNothingToPour -> "مفيش عناصر متسجّلة في الدور ده لسه."
    r.scopeCount == 0 -> "كل العناصر في الدور اتصبّت خلاص."
    r.blockers.isNotEmpty() ->
        "فيه ${r.blockers.size} مانع لازم يتحلّ الأول" +
            (if (r.warnings.isNotEmpty()) " و${r.warnings.size} ملاحظة." else ".")
    r.warnings.isNotEmpty() -> "مفيش مانع، بس فيه ${r.warnings.size} ملاحظة تستاهل تتراجع."
    else -> "كل العناصر معتمدة ومفيش أي مانع."
}

@Composable
private fun ProgressCard(s: FloorSummary) {
    val c = LocalCwColors.current
    val tone = when {
        s.completionPercent >= 100 -> CwTone.Success
        s.completionPercent >= 50 -> CwTone.Info
        else -> CwTone.Neutral
    }
    CwCard {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${s.completionPercent}%", style = CwText.metric, color = c.textPrimary)
            Spacer(Modifier.weight(1f))
            Text(
                "${s.done} من ${s.named} عنصر مسمّى",
                style = MaterialTheme.typography.bodySmall,
                color = c.textTertiary
            )
        }
        Spacer(Modifier.height(Space.md))
        CwProgressBar(fraction = s.completionPercent / 100f, tone = tone)
        Spacer(Modifier.height(Space.lg))

        // التفصيل بشارات — كل حالة أيقونة + نص + لون، مش لون لوحده.
        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            StatusLine("معتمد", s.approved, CwTone.Success)
            StatusLine("مصبوب", s.cast, CwTone.Info)
            StatusLine("WIR مقدّم", s.wirSubmitted, CwTone.Pending)
            StatusLine("مرفوض", s.rejected, CwTone.Danger)
            StatusLine("لسه ماتفحصش", s.notInspected, CwTone.Neutral)
        }
    }
}

@Composable
private fun StatusLine(label: String, count: Int, tone: CwTone) {
    val c = LocalCwColors.current
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CwStatusBadge(label = label, tone = tone, compact = true)
        Spacer(Modifier.weight(1f))
        Text(
            "$count",
            style = MaterialTheme.typography.titleSmall,
            color = if (count == 0) c.textTertiary else c.textPrimary
        )
    }
}

@Composable
private fun QuickActions(vm: MainViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            CwButton(
                "المسقط", { vm.goToLens(Lens.REINF) },
                style = CwButtonStyle.Secondary, icon = Icons.Filled.Map,
                modifier = Modifier.weight(1f)
            )
            CwButton(
                "جاهزية الصبّ", { vm.go(Dest.PourReadiness) },
                style = CwButtonStyle.Secondary, icon = Icons.Filled.WaterDrop,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            CwButton(
                "ملاحظة", { vm.go(Dest.FloorNotes) },
                style = CwButtonStyle.Secondary, icon = Icons.Filled.EditNote,
                modifier = Modifier.weight(1f)
            )
            CwButton(
                "صور", { vm.go(Dest.SitePhotos) },
                style = CwButtonStyle.Secondary, icon = Icons.Filled.PhotoCamera,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            CwButton(
                "المهام", { vm.goToData(DataSection.TASKS) },
                style = CwButtonStyle.Secondary, icon = Icons.Filled.Checklist,
                modifier = Modifier.weight(1f)
            )
            CwButton(
                "العمالة", { vm.goToManpower() },
                style = CwButtonStyle.Secondary, icon = Icons.Filled.Groups,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            CwButton(
                "الفحص", { vm.go(Dest.Checks) },
                style = CwButtonStyle.Secondary, icon = Icons.Filled.Analytics,
                modifier = Modifier.weight(1f)
            )
            CwButton(
                "الإعدادات", { vm.go(Dest.Settings) },
                style = CwButtonStyle.Secondary, icon = Icons.Filled.Assignment,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
