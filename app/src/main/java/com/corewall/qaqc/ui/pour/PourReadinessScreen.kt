package com.corewall.qaqc.ui.pour

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.Lens
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.domain.PourReadiness
import com.corewall.qaqc.ui.design.CwCard
import com.corewall.qaqc.ui.design.CwCardStyle
import com.corewall.qaqc.ui.design.CwIconButton
import com.corewall.qaqc.ui.design.CwProgressBar
import com.corewall.qaqc.ui.design.CwSectionHeader
import com.corewall.qaqc.ui.design.CwText
import com.corewall.qaqc.ui.design.CwTone
import com.corewall.qaqc.ui.design.IconSize
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Motion
import com.corewall.qaqc.ui.design.Radius
import com.corewall.qaqc.ui.design.Sizes
import com.corewall.qaqc.ui.design.Space
import com.corewall.qaqc.ui.design.semantic

/**
 * "جاهز للصبّة؟" — بوابة قرار مش شاشة استعلام.
 *
 * الحكم فوق وواضح، والأسباب تحته مرتّبة بالخطورة، والمهندس ياخد القرار من
 * هنا من غير ما يفتح حاجة تانية. كل رقم محسوب في [PourReadiness] — صفر
 * ذكاء اصطناعي في القرار ده.
 */
@Composable
fun PourReadinessScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val level by vm.currentLevel.collectAsStateWithLifecycle()
    val result by vm.pourReadiness.collectAsStateWithLifecycle()

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Space.screen, end = Space.screen,
            top = Space.md, bottom = Space.bottomInset
        ),
        verticalArrangement = Arrangement.spacedBy(Space.stack)
    ) {
        item(key = "verdict") { VerdictCard(result, level) { vm.sharePourReadiness() } }

        if (result.blockers.isNotEmpty()) {
            item(key = "h-blockers") { CwSectionHeader("الموانع", count = result.blockers.size) }
            items(result.blockers, key = { "b-${it.id}" }) { f -> FindingCard(f, vm) }
        }
        if (result.warnings.isNotEmpty()) {
            item(key = "h-warnings") { CwSectionHeader("تحذيرات", count = result.warnings.size) }
            items(result.warnings, key = { "w-${it.id}" }) { f -> FindingCard(f, vm) }
        }
        if (result.notes.isNotEmpty()) {
            item(key = "h-notes") { CwSectionHeader("ملاحظات", count = result.notes.size) }
            items(result.notes, key = { "n-${it.id}" }) { f -> FindingCard(f, vm) }
        }

        item(key = "method") { MethodNote() }
    }
}

private fun toneOf(r: PourReadiness.Result): CwTone = when {
    r.hasNothingToPour -> CwTone.Neutral
    r.blockers.isNotEmpty() -> CwTone.Danger
    r.warnings.isNotEmpty() -> CwTone.Warning
    else -> CwTone.Success
}

private fun toneOf(level: PourReadiness.Level): CwTone = when (level) {
    PourReadiness.Level.BLOCKER -> CwTone.Danger
    PourReadiness.Level.WARNING -> CwTone.Warning
    PourReadiness.Level.INFO -> CwTone.Info
}

/** الحكم — أكبر عنصر في الشاشة، وبيتقري من بعيد. */
@Composable
private fun VerdictCard(r: PourReadiness.Result, level: String, onShare: () -> Unit) {
    val c = LocalCwColors.current
    val tone = toneOf(r)
    val s = tone.semantic()
    val icon = when {
        r.hasNothingToPour -> Icons.Filled.Info
        r.blockers.isNotEmpty() -> Icons.Filled.Block
        r.warnings.isNotEmpty() -> Icons.Filled.WarningAmber
        else -> Icons.Filled.CheckCircle
    }

    CwCard(style = CwCardStyle.Accent, accent = s.solid) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(Sizes.avatarMd)
                    .clip(Radius.shapeMd)
                    .background(s.container),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = s.onContainer,
                    modifier = Modifier.size(IconSize.lg)
                )
            }
            Spacer(Modifier.size(Space.md))
            Column(Modifier.weight(1f)) {
                Text(r.verdict, style = MaterialTheme.typography.headlineSmall, color = s.fg)
                Text(
                    "دور $level",
                    style = MaterialTheme.typography.labelMedium,
                    color = c.textTertiary
                )
            }
            CwIconButton(Icons.Filled.IosShare, "شارك ملخّص الجاهزية", onShare)
        }

        if (!r.hasNothingToPour) {
            Spacer(Modifier.height(Space.lg))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "معتمد ${r.approvedCount} من ${r.scopeCount}",
                    style = MaterialTheme.typography.labelLarge,
                    color = c.textPrimary
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${r.approvedPercent}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = c.textPrimary
                )
            }
            Spacer(Modifier.height(Space.sm))
            CwProgressBar(
                fraction = r.approvedPercent / 100f,
                tone = if (r.blockers.isEmpty()) CwTone.Success else tone
            )

            Spacer(Modifier.height(Space.lg))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Stat("موانع", r.blockers.size, CwTone.Danger, Modifier.weight(1f))
                Stat("تحذيرات", r.warnings.size, CwTone.Warning, Modifier.weight(1f))
                Stat("اتصبّ", r.castCount, CwTone.Neutral, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: Int, tone: CwTone, modifier: Modifier = Modifier) {
    val c = LocalCwColors.current
    Column(
        modifier
            .clip(Radius.shapeMd)
            .background(c.surfaceAlt)
            .padding(vertical = Space.sm),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "$value",
            style = CwText.metricSmall,
            color = if (value == 0) c.textTertiary else tone.semantic().fg
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
    }
}

/** بند واحد — بيتفتح على أكواد العناصر، وكل كود بيوديك للعنصر نفسه. */
@Composable
private fun FindingCard(f: PourReadiness.Finding, vm: MainViewModel) {
    val c = LocalCwColors.current
    var expanded by remember(f.id) { mutableStateOf(false) }

    val tone = toneOf(f.level)
    val s = tone.semantic()
    val hasDetail = f.marks.isNotEmpty() || f.elementIds.isNotEmpty()
    val arrow by animateFloatAsState(if (expanded) 180f else 0f, Motion.standard(), label = "arrow")

    CwCard(
        style = CwCardStyle.Accent,
        accent = s.solid,
        onClick = if (hasDetail) ({ expanded = !expanded }) else null,
        contentPadding = PaddingValues(Space.md)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                tone.iconFor(),
                contentDescription = null,
                tint = s.fg,
                modifier = Modifier.size(IconSize.md)
            )
            Spacer(Modifier.size(Space.sm))
            Column(Modifier.weight(1f)) {
                Text(f.title, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                if (f.detail.isNotBlank()) {
                    Spacer(Modifier.height(Space.xxs))
                    Text(
                        f.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textTertiary
                    )
                }
            }
            if (hasDetail) {
                Icon(
                    Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "اقفل التفاصيل" else "افتح التفاصيل",
                    tint = c.textTertiary,
                    modifier = Modifier
                        .size(IconSize.md)
                        .rotate(arrow)
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(Motion.standard()) + expandVertically(Motion.enter()),
            exit = shrinkVertically(Motion.exit())
        ) {
            Column(Modifier.padding(top = Space.md)) {
                if (f.marks.isNotEmpty()) {
                    MarkGrid(f.marks) { mark ->
                        vm.elementForMark(mark)?.let { element ->
                            // الترتيب مهم: تبديل التبويب بيمسح الاختيار، فالاختيار
                            // لازم يتحطّ بعده. وممنوع back() هنا — التبويب اتبدّل
                            // خلاص، والرجوع كان بيرمي المستخدم برّه المسقط.
                            vm.goToLens(Lens.REINF)
                            vm.selectElement(element.id)
                        }
                    }
                }
                if (f.elementIds.isNotEmpty()) {
                    Spacer(Modifier.height(Space.sm))
                    Text(
                        "${f.elementIds.size} عنصر من غير كود — افتح المسقط وسمّيهم",
                        style = MaterialTheme.typography.labelMedium,
                        color = c.textTertiary
                    )
                }
            }
        }
    }
}

@Composable
private fun CwTone.iconFor() = when (this) {
    CwTone.Danger -> Icons.Filled.Block
    CwTone.Warning -> Icons.Filled.WarningAmber
    else -> Icons.Filled.Info
}

/** أكواد العناصر كشرايح — الضغط بيفتح العنصر على المسقط. */
@Composable
private fun MarkGrid(marks: List<String>, onClick: (String) -> Unit) {
    val c = LocalCwColors.current
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        marks.take(24).chunked(3).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                modifier = Modifier.fillMaxWidth()
            ) {
                row.forEach { m ->
                    Surface(
                        onClick = { onClick(m) },
                        shape = Radius.shapeSm,
                        color = c.accentContainer,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            Modifier
                                .height(Sizes.control)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                m,
                                style = CwText.codeSmall,
                                color = c.onAccentContainer,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (marks.size > 24) {
            Text(
                "+${marks.size - 24} عنصر إضافي",
                style = MaterialTheme.typography.labelSmall,
                color = c.textTertiary
            )
        }
    }
}

/**
 * شرح طريقة الحساب. المهندس اللي هيوقّع على صبّة من حقه يعرف الشاشة قرّرت
 * إزاي — من غير كده الشاشة بتبقى صندوق أسود ومش هيثق فيها.
 */
@Composable
private fun MethodNote() {
    val c = LocalCwColors.current
    CwCard(style = CwCardStyle.Inset) {
        Text("إزاي اتحسبت؟", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
        Spacer(Modifier.height(Space.sm))
        Text(
            "النطاق = عناصر الدور اللي ليها كود وتسليح متعرّف، ناقص اللي اتصبّ خلاص.\n" +
                "مانع = عنصر مرفوض · من غير فحص · طلب فحصه لسه ماتعتمدش · " +
                "فجوة في الجدول · كود مش موجود في الجدول.\n" +
                "تحذير = نقص توثيق (عدّ أسياخ، صور) أو اختلاف بين الموقع والرسمة.\n\n" +
                "الحساب كله بكود حتمي — مفيش ذكاء اصطناعي في القرار ده.",
            style = MaterialTheme.typography.bodySmall,
            color = c.textSecondary
        )
    }
}
