package com.corewall.qaqc.ui.ai

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.corewall.qaqc.ai.model.AiSeverity
import com.corewall.qaqc.ai.model.DashCard
import com.corewall.qaqc.ai.model.DashboardState
import com.corewall.qaqc.ui.theme.LocalSrtColors

/**
 * لوحة الدور اللي **الـ AI بيقرّر محتواها** — مش كروت ثابتة.
 * كل دور ممكن يعرض حاجات مختلفة حسب البيانات المتاحة فيه.
 */
@Composable
fun DynamicDashboard(
    state: DashboardState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val srt = LocalSrtColors.current

    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = srt.blue, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("لوحة الدور الذكية", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            DashRefresh(state is DashboardState.Loading, onRefresh)
        }
        Spacer(Modifier.height(10.dp))

        when (state) {
            DashboardState.NotConfigured -> Hint("ضيف مفتاح API عشان الـAI يبني لوحة الدور تلقائي.")
            DashboardState.Idle -> Hint("اضغط تحديث عشان الـAI يقرّر أهم حاجة تشوفها في الدور ده.")
            DashboardState.Loading -> Hint("بيقرّر إيه أهم حاجة تشوفها دلوقتي…")
            is DashboardState.Error -> Hint(state.message, srt.red)
            is DashboardState.Ready -> {
                if (state.spec.headline.isNotBlank()) {
                    Text(state.spec.headline, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(10.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.spec.cards.forEach { DashCardView(it) }
                }
                if (state.spec.cards.isEmpty()) Hint("مفيش بيانات كفاية للدور ده لسه.")
            }
        }
    }
}

@Composable
private fun DashRefresh(loading: Boolean, onClick: () -> Unit) {
    val srt = LocalSrtColors.current
    val spin = rememberInfiniteTransition(label = "dash")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "a")
    Surface(
        onClick = { if (!loading) onClick() },
        shape = RoundedCornerShape(10.dp),
        color = srt.blueTint
    ) {
        Icon(Icons.Filled.Refresh, contentDescription = "تحديث اللوحة", tint = srt.blue,
            modifier = Modifier.padding(7.dp).size(18.dp).then(if (loading) Modifier.rotate(angle) else Modifier))
    }
}

@Composable
private fun Hint(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color)
}

@Composable
private fun DashCardView(card: DashCard) {
    val srt = LocalSrtColors.current
    val accent = when (AiSeverity.from(card.severity)) {
        AiSeverity.CRITICAL, AiSeverity.HIGH -> srt.red
        AiSeverity.MEDIUM -> srt.orange
        AiSeverity.LOW -> srt.blue
        AiSeverity.INFO -> srt.blue
    }
    val isAlert = card.type.equals("ALERT", true)

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isAlert) accent.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, if (isAlert) accent.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outline
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isAlert) {
                    Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(card.title, style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold, color = if (isAlert) accent else MaterialTheme.colorScheme.onSurface)
                    if (card.subtitle.isNotBlank()) {
                        Text(card.subtitle, style = MaterialTheme.typography.labelSmall, color = srt.text3)
                    }
                }
            }

            when (card.type.uppercase()) {
                "METRICS" -> if (card.metrics.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    card.metrics.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { m ->
                                Column(Modifier.weight(1f)) {
                                    Text(m.value, style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold, color = accent)
                                    Text(m.label, style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                                    if (m.hint.isNotBlank()) {
                                        Text(m.hint, style = MaterialTheme.typography.labelSmall, color = srt.text3, maxLines = 1)
                                    }
                                }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
                "PROGRESS" -> {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LinearProgressIndicator(
                            progress = { card.percent.coerceIn(0, 100) / 100f },
                            color = accent,
                            modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp))
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("${card.percent}%", style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold, color = accent)
                    }
                }
                "TEXT" -> if (card.body.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(card.body, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> if (card.items.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    card.items.take(8).forEach { item ->
                        Row(Modifier.padding(vertical = 3.dp)) {
                            Box(Modifier.padding(top = 6.dp).size(6.dp).clip(CircleShape).background(accent))
                            Spacer(Modifier.width(10.dp))
                            Text(item, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }

            if (card.marks.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(card.marks.joinToString(" · "), style = MaterialTheme.typography.labelSmall,
                    color = accent, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/** بانر استباقي: الـAI بيقول اتحلّل إيه بعد الرفع من غير ما تسأله. */
@Composable
fun UploadInsightBanner(text: String, onDismiss: () -> Unit, onOpenKnowledge: () -> Unit) {
    val srt = LocalSrtColors.current
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = srt.green.copy(alpha = 0.10f),
        border = androidx.compose.foundation.BorderStroke(1.dp, srt.green.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = srt.green, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(text, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                Surface(onClick = onDismiss, shape = CircleShape, color = Color.Transparent) {
                    Icon(Icons.Filled.Close, contentDescription = "إغلاق", tint = srt.text3,
                        modifier = Modifier.padding(2.dp).size(16.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Surface(onClick = onOpenKnowledge, shape = RoundedCornerShape(10.dp), color = srt.green.copy(alpha = 0.16f)) {
                Text("شوف التفاصيل", Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium, color = srt.green, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
