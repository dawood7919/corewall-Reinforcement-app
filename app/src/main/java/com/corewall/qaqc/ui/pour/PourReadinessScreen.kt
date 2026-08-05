package com.corewall.qaqc.ui.pour

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.domain.PourReadiness
import com.corewall.qaqc.ui.theme.LocalSrtColors
import com.corewall.qaqc.ui.theme.LocalVizColors

/**
 * شاشة "جاهز للصبّة؟" — بوابة قرار مش شاشة استعلام.
 *
 * الحكم فوق وواضح، والأسباب تحته مرتّبة بالخطورة. الفكرة إن المهندس
 * ياخد القرار من الشاشة دي من غير ما يفتح حاجة تانية.
 *
 * كل رقم هنا محسوب في [PourReadiness] — صفر ذكاء اصطناعي في القرار.
 */
@Composable
fun PourReadinessScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val level by vm.currentLevel.collectAsStateWithLifecycle()
    val result by vm.pourReadiness.collectAsStateWithLifecycle()

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { VerdictCard(result, level) { vm.sharePourReadiness() } }

        if (result.blockers.isNotEmpty()) {
            item { GroupHeader("الموانع", result.blockers.size, LocalVizColors.current.critical) }
            items(result.blockers, key = { it.id }) { f -> FindingCard(f, vm) }
        }
        if (result.warnings.isNotEmpty()) {
            item { GroupHeader("تحذيرات", result.warnings.size, LocalVizColors.current.warning) }
            items(result.warnings, key = { it.id }) { f -> FindingCard(f, vm) }
        }
        if (result.notes.isNotEmpty()) {
            item { GroupHeader("ملاحظات", result.notes.size, LocalSrtColors.current.text3) }
            items(result.notes, key = { it.id }) { f -> FindingCard(f, vm) }
        }

        item { Spacer(Modifier.height(8.dp)) }
        item { MethodNote() }
    }
}

/** الحكم — أكبر عنصر في الشاشة، وبيتقري من بعيد. */
@Composable
private fun VerdictCard(r: PourReadiness.Result, level: String, onShare: () -> Unit) {
    val viz = LocalVizColors.current
    val srt = LocalSrtColors.current

    val tone = when {
        r.hasNothingToPour -> srt.text3
        r.blockers.isNotEmpty() -> viz.critical
        r.warnings.isNotEmpty() -> viz.warning
        else -> viz.good
    }
    val icon = when {
        r.hasNothingToPour -> Icons.Filled.Info
        r.blockers.isNotEmpty() -> Icons.Filled.Block
        r.warnings.isNotEmpty() -> Icons.Filled.WarningAmber
        else -> Icons.Filled.CheckCircle
    }

    val progress by animateFloatAsState(
        targetValue = r.approvedPercent / 100f,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "progress"
    )

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = tone.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, tone.copy(alpha = 0.30f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(tone.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) { Icon(icon, contentDescription = null, tint = tone, modifier = Modifier.size(26.dp)) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        r.verdict,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "دور $level",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    onClick = onShare,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Icon(
                        Icons.Filled.IosShare, contentDescription = "مشاركة الملخّص",
                        tint = srt.blue,
                        modifier = Modifier.padding(9.dp).size(18.dp)
                    )
                }
            }

            if (!r.hasNothingToPour) {
                Spacer(Modifier.height(16.dp))

                // شريط الاعتماد — تقدّم، مش حكم
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "معتمد ${r.approvedCount} من ${r.scopeCount}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${r.approvedPercent}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier.fillMaxWidth().height(9.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(viz.track)
                ) {
                    Box(
                        Modifier.fillMaxHeight()
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (r.blockers.isEmpty()) viz.good else tone)
                    )
                }

                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stat("موانع", r.blockers.size, viz.critical, Modifier.weight(1f))
                    Stat("تحذيرات", r.warnings.size, viz.warning, Modifier.weight(1f))
                    Stat("اتصبّ", r.castCount, srt.text3, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: Int, tone: Color, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Column(
            Modifier.padding(vertical = 10.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "$value",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (value == 0) MaterialTheme.colorScheme.onSurfaceVariant else tone
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GroupHeader(title: String, count: Int, tone: Color) {
    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(tone))
        Spacer(Modifier.width(8.dp))
        Text(
            "$title ($count)",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** بند واحد — بيتفتح على أكواد العناصر، وكل كود بيوديك للعنصر نفسه. */
@Composable
private fun FindingCard(f: PourReadiness.Finding, vm: MainViewModel) {
    val viz = LocalVizColors.current
    val srt = LocalSrtColors.current
    var expanded by remember(f.id) { mutableStateOf(false) }

    val tone = when (f.level) {
        PourReadiness.Level.BLOCKER -> viz.critical
        PourReadiness.Level.WARNING -> viz.warning
        PourReadiness.Level.INFO -> srt.blue
    }
    val hasDetail = f.marks.isNotEmpty() || f.elementIds.isNotEmpty()
    val arrow by animateFloatAsState(if (expanded) 180f else 0f, label = "arrow")

    Surface(
        onClick = { if (hasDetail) expanded = !expanded },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier.padding(top = 4.dp).size(8.dp).clip(CircleShape).background(tone)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        f.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (f.detail.isNotBlank()) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            f.detail,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (hasDetail) {
                    Icon(
                        Icons.Filled.ExpandMore, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp).rotate(arrow)
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(Modifier.padding(top = 12.dp)) {
                    if (f.marks.isNotEmpty()) {
                        MarkGrid(f.marks) { mark ->
                            vm.elementForMark(mark)?.let {
                                vm.goToLens(com.corewall.qaqc.Lens.REINF)
                                vm.selectElement(it.id)
                                vm.closeAppScreen()
                            }
                        }
                    }
                    if (f.elementIds.isNotEmpty()) {
                        Text(
                            "${f.elementIds.size} عنصر — افتح المسقط وسمّيهم",
                            style = MaterialTheme.typography.labelSmall,
                            color = srt.text3
                        )
                    }
                }
            }
        }
    }
}

/** أكواد العناصر كشرايح — الضغط بيفتح العنصر على المسقط. */
@Composable
private fun MarkGrid(marks: List<String>, onClick: (String) -> Unit) {
    val srt = LocalSrtColors.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        marks.take(24).chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { m ->
                    Surface(
                        onClick = { onClick(m) },
                        shape = RoundedCornerShape(9.dp),
                        color = srt.blueTint,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            m,
                            Modifier.padding(vertical = 7.dp),
                            style = com.corewall.qaqc.ui.theme.CodeTextStyle,
                            color = srt.blue,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (marks.size > 24) {
            Text(
                "+${marks.size - 24} عنصر إضافي",
                style = MaterialTheme.typography.labelSmall,
                color = srt.text3
            )
        }
    }
}

/**
 * شرح طريقة الحساب.
 * المهندس اللي هيوقّع على صبّة من حقه يعرف الشاشة قرّرت إزاي —
 * من غير كده الشاشة بتبقى "صندوق أسود" ومش هيثق فيها.
 */
@Composable
private fun MethodNote() {
    val srt = LocalSrtColors.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "إزاي اتحسبت؟",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "النطاق = عناصر الدور اللي ليها كود وتسليح متعرّف، ناقص اللي اتصبّ خلاص.\n" +
                    "مانع = عنصر مرفوض · من غير فحص · طلب فحصه لسه ماتعتمدش · " +
                    "فجوة في الجدول · كود مش موجود في الجدول.\n" +
                    "تحذير = نقص توثيق (عدّ أسياخ، صور) أو اختلاف بين الموقع والرسمة.\n\n" +
                    "الحساب كله بكود حتمي — مفيش ذكاء اصطناعي في القرار ده.",
                style = MaterialTheme.typography.bodySmall,
                color = srt.text3
            )
        }
    }
}
