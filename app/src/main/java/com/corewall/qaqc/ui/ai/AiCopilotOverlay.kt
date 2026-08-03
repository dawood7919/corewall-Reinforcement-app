package com.corewall.qaqc.ui.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lightbulb
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.ai.agent.Suggestion
import com.corewall.qaqc.ui.theme.LocalSrtColors
import com.corewall.qaqc.ui.theme.LocalVizColors

/**
 * المساعد الطايف — موجود فوق **كل** شاشة في التطبيق.
 *
 * مقفول = زرار صغير مع عدّاد للحاجات المهمة. مفتوح = لوحة فيها
 * الاقتراحات الاستباقية وسجل الإجراءات ومدخل سؤال سريع.
 *
 * الاقتراحات كلها محسوبة محلياً، فالزرار بيشتغل وإنت أوفلاين ومن غير
 * أي تكلفة. الشبكة بتتنده بس لما تضغط على اقتراح.
 */
@Composable
fun AiCopilotOverlay(vm: MainViewModel, modifier: Modifier = Modifier) {
    val srt = LocalSrtColors.current
    val open by vm.copilotOpen.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val log by vm.actionLog.collectAsStateWithLifecycle()
    val busy by vm.chatBusy.collectAsStateWithLifecycle()

    val urgent = suggestions.count {
        it.severity == Suggestion.Severity.CRITICAL || it.severity == Suggestion.Severity.WARNING
    }

    Box(modifier.fillMaxSize()) {
        // اللوحة
        AnimatedVisibility(
            visible = open,
            enter = fadeIn(tween(180)) + slideInVertically(tween(240)) { it / 3 },
            exit = fadeOut(tween(140)) + slideOutVertically(tween(200)) { it / 3 },
            modifier = Modifier.align(Alignment.BottomStart)
        ) {
            CopilotPanel(
                suggestions = suggestions,
                log = log,
                busy = busy,
                onAsk = { vm.askAi(it); vm.setCopilotOpen(false); vm.openAppScreen(com.corewall.qaqc.AppScreen.AI_CHAT) },
                onOpenChat = { vm.setCopilotOpen(false); vm.openAppScreen(com.corewall.qaqc.AppScreen.AI_CHAT) },
                onClose = { vm.setCopilotOpen(false) }
            )
        }

        // الزرار الطايف
        if (!open) {
            CopilotFab(
                urgent = urgent,
                busy = busy,
                onClick = { vm.setCopilotOpen(true) },
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 96.dp)
            )
        }
    }
}

@Composable
private fun CopilotFab(urgent: Int, busy: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val srt = LocalSrtColors.current
    val viz = LocalVizColors.current

    // نبضة خفيفة لما يكون فيه حاجة مهمة — بتلفت من غير ما تزنّ
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (urgent > 0) 1.06f else 1f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "scale"
    )

    Box(modifier) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = srt.blue,
            shadowElevation = 8.dp,
            modifier = Modifier.size(52.dp).scale(scale)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (busy) Icons.Filled.Bolt else Icons.Filled.AutoAwesome,
                    contentDescription = "المساعد",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        if (urgent > 0) {
            Surface(
                shape = CircleShape,
                color = viz.critical,
                modifier = Modifier.align(Alignment.TopEnd).size(20.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "$urgent",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun CopilotPanel(
    suggestions: List<Suggestion>,
    log: List<com.corewall.qaqc.ai.agent.ActionLogEntry>,
    busy: Boolean,
    onAsk: (String) -> Unit,
    onOpenChat: () -> Unit,
    onClose: () -> Unit
) {
    val srt = LocalSrtColors.current
    var showLog by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 16.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(srt.blueTint),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null,
                        tint = srt.blue, modifier = Modifier.size(17.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("المساعد الهندسي", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold)
                    Text(
                        if (busy) "بيشتغل دلوقتي…" else "شايف الدور والملفات والجدول",
                        style = MaterialTheme.typography.labelSmall, color = srt.text3
                    )
                }
                Icon(
                    Icons.Filled.History,
                    contentDescription = "سجل الإجراءات",
                    tint = if (showLog) srt.blue else srt.text3,
                    modifier = Modifier.size(20.dp).clip(CircleShape)
                        .clickable { showLog = !showLog }
                )
                Spacer(Modifier.width(12.dp))
                Icon(
                    Icons.Filled.Close, contentDescription = "إغلاق", tint = srt.text3,
                    modifier = Modifier.size(20.dp).clip(CircleShape).clickable { onClose() }
                )
            }

            Spacer(Modifier.height(14.dp))

            if (showLog) {
                if (log.isEmpty()) {
                    Text(
                        "المساعد لسه ماعملش أي إجراء.",
                        style = MaterialTheme.typography.bodySmall, color = srt.text3
                    )
                } else {
                    LazyColumn(
                        Modifier.heightIn(max = 260.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(log) { e ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (e.ok) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                                    contentDescription = null,
                                    tint = if (e.ok) LocalVizColors.current.good else LocalVizColors.current.critical,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    e.detail,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    Modifier.heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(suggestions) { s -> SuggestionRow(s) { onAsk(s.prompt) } }
                }
            }

            Spacer(Modifier.height(14.dp))
            Surface(
                onClick = onOpenChat,
                shape = RoundedCornerShape(14.dp),
                color = srt.blue,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Chat, contentDescription = null, tint = Color.White,
                        modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("افتح المحادثة", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun SuggestionRow(s: Suggestion, onClick: () -> Unit) {
    val viz = LocalVizColors.current
    val srt = LocalSrtColors.current
    val (tone, icon) = when (s.severity) {
        Suggestion.Severity.CRITICAL -> viz.critical to Icons.Filled.ErrorOutline
        Suggestion.Severity.WARNING -> viz.warning to Icons.Filled.WarningAmber
        Suggestion.Severity.INFO -> srt.blue to Icons.Filled.AutoAwesome
        Suggestion.Severity.IDEA -> srt.text3 to Icons.Filled.Lightbulb
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, tint = tone, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    s.title, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    s.detail, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
