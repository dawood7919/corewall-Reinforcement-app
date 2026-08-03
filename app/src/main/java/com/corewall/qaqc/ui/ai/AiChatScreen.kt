package com.corewall.qaqc.ui.ai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Source
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.ai.model.AnswerBlock
import com.corewall.qaqc.ai.model.ChatAnswer
import com.corewall.qaqc.data.db.ChatMessageEntity
import com.corewall.qaqc.ui.ai.blocks.AnswerBlockCard
import com.corewall.qaqc.ui.ai.blocks.Collapsible
import com.corewall.qaqc.ui.ai.blocks.ThinkingRow
import com.corewall.qaqc.ui.theme.LocalSrtColors
import kotlinx.serialization.json.Json

/** أمثلة أسئلة بتظهر لما المحادثة تكون فاضية. */
private val SUGGESTIONS = listOf(
    "لخّص حالة الدور ده",
    "فين فجوات البيانات؟",
    "إيه الفحوصات المعلّقة؟",
    "عدّ الأسياخ مطابق للرسمة؟",
    "إيه المستندات المرفوعة للدور؟",
    "كام عامل النهاردة؟"
)

private val answerJson = Json {
    ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true
}

/**
 * بيفكّ الرد المتخزّن. الردود القديمة كانت نص عادي —
 * بنلفّها في بلوك نصي بدل ما نكسرها.
 */
private fun parseAnswer(content: String): ChatAnswer =
    runCatching { answerJson.decodeFromString(ChatAnswer.serializer(), content) }
        .getOrElse { ChatAnswer(blocks = listOf(AnswerBlock(type = "TEXT", body = content))) }

/**
 * المساعد الهندسي: بيشوف بيانات المشروع + المستندات المحلّلة،
 * وبيرُدّ ببيانات مرسومة — كروت أرقام ورسوم وجداول، مش نص خام.
 */
@Composable
fun AiChatScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val srt = LocalSrtColors.current
    val level by vm.currentLevel.collectAsStateWithLifecycle()
    val messages by vm.chat.collectAsStateWithLifecycle()
    val busy by vm.chatBusy.collectAsStateWithLifecycle()
    val error by vm.chatError.collectAsStateWithLifecycle()
    val docs by vm.documents.collectAsStateWithLifecycle()
    val cfg by vm.aiConfig.collectAsStateWithLifecycle()

    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(level) { vm.loadKnowledge() }
    LaunchedEffect(messages.size, busy) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size)
    }

    Column(modifier.fillMaxSize().imePadding()) {
        Surface(color = srt.blueTint, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = srt.blue, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "دور $level · ${docs.count { it.status == "DONE" }} مستند في الذاكرة" +
                        (docs.count { it.status == "PENDING" }.takeIf { it > 0 }?.let { " · $it بانتظار التحليل" } ?: ""),
                    style = MaterialTheme.typography.labelMedium, color = srt.blue, modifier = Modifier.weight(1f)
                )
                if (messages.isNotEmpty()) {
                    Icon(
                        Icons.Filled.DeleteSweep, contentDescription = "مسح المحادثة", tint = srt.blue,
                        modifier = Modifier.size(18.dp).clip(CircleShape).clickable { vm.clearChat() }
                    )
                }
            }
        }

        Box(Modifier.weight(1f)) {
            if (messages.isEmpty()) {
                EmptyChat(cfg.isConfigured) { input = it }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(), state = listState,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(messages, key = { it.id.takeIf { id -> id != 0L } ?: it.createdAt }) { m ->
                        if (m.role == "user") UserBubble(m) else AnswerCard(m) { vm.askAi(it) }
                    }
                    if (busy) item { ThinkingRow("بيراجع بيانات الدور…") }
                }
            }
        }

        error?.let {
            Surface(color = srt.red.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth()) {
                Text(it, Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall, color = srt.red)
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Bottom) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.weight(1f)
                ) {
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(srt.blue),
                        maxLines = 5,
                        decorationBox = { inner ->
                            Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                if (input.isEmpty()) Text(
                                    "اسأل عن الدور، التسليح، المستندات…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                inner()
                            }
                        }
                    )
                }
                Spacer(Modifier.width(8.dp))
                val canSend = input.isNotBlank() && !busy
                Surface(
                    onClick = { if (canSend) { vm.askAi(input); input = "" } },
                    shape = CircleShape,
                    color = if (canSend) srt.blue else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send, contentDescription = "إرسال",
                            tint = if (canSend) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UserBubble(m: ChatMessageEntity) {
    val srt = LocalSrtColors.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp),
            color = srt.blue,
            modifier = Modifier.fillMaxWidth(0.86f)
        ) {
            Text(
                m.content,
                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
        }
    }
}

/**
 * رد المساعد: الخلاصة في سطر بارز، بعدين البلوكات بتدخل واحد ورا التاني،
 * وتحت المصادر وأسئلة المتابعة.
 */
@Composable
private fun AnswerCard(m: ChatMessageEntity, onFollowUp: (String) -> Unit) {
    val srt = LocalSrtColors.current
    val answer = remember(m.id, m.content) { parseAnswer(m.content) }
    var showSources by remember(m.id) { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (answer.headline.isNotBlank()) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier.size(26.dp).clip(CircleShape).background(srt.blueTint),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null,
                        tint = srt.blue, modifier = Modifier.size(15.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    answer.headline,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        answer.blocks.take(6).forEachIndexed { i, b -> AnswerBlockCard(b, i) }

        if (answer.sources.isNotEmpty()) {
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).clickable { showSources = !showSources }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Source, contentDescription = null, tint = srt.text3, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "المصادر (${answer.sources.size})",
                    style = MaterialTheme.typography.labelSmall, color = srt.text3
                )
            }
            Collapsible(showSources) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    answer.sources.take(8).forEach {
                        Text("• $it", style = MaterialTheme.typography.labelSmall, color = srt.text3)
                    }
                }
            }
        }

        if (answer.followUps.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                answer.followUps.take(4).forEach { q ->
                    Surface(
                        onClick = { onFollowUp(q) },
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Text(
                            q, Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = srt.blue, maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyChat(configured: Boolean, onPick: (String) -> Unit) {
    val srt = LocalSrtColors.current
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(76.dp).clip(CircleShape).background(srt.blueTint),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = srt.blue, modifier = Modifier.size(38.dp)) }
        Spacer(Modifier.height(14.dp))
        Text("المساعد الهندسي", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            if (configured) "اسأل عن أي حاجة في الدور — الرد بيجي أرقام ورسوم، مش نص."
            else "ضيف مفتاح API من إعدادات المساعد الذكي عشان تبدأ.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (configured) {
            Spacer(Modifier.height(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SUGGESTIONS.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { s ->
                            Surface(
                                onClick = { onPick(s) },
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(s, Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.labelMedium, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}
