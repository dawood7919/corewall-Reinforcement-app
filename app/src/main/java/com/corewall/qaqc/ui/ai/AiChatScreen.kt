package com.corewall.qaqc.ui.ai

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.PhotoLibrary
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.ai.model.AnswerBlock
import com.corewall.qaqc.ai.model.ChatAnswer
import com.corewall.qaqc.data.db.ChatMessageEntity
import com.corewall.qaqc.ai.agent.ToolRisk
import com.corewall.qaqc.ui.ai.blocks.ActionConfirmCard
import com.corewall.qaqc.ui.ai.blocks.AnswerBlockCard
import com.corewall.qaqc.ui.ai.blocks.Collapsible
import com.corewall.qaqc.ui.ai.blocks.ThinkingRow
import com.corewall.qaqc.ui.theme.LocalSrtColors
import kotlinx.serialization.json.Json
import com.corewall.qaqc.ui.design.Radius
import com.corewall.qaqc.ui.design.Space

/** أمثلة أسئلة بتظهر لما المحادثة تكون فاضية. */
private val SUGGESTIONS = listOf(
    "التسليح هيتغيّر في الدور الجاي؟",
    "لخّص حالة الدور ده",
    "فين فجوات البيانات؟",
    "وريني ملفات الدور",
    "إيه الفحوصات المعلّقة؟",
    "الدور جاهز للصبّة؟"
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
    val status by vm.agentStatus.collectAsStateWithLifecycle()
    val pending by vm.pendingActions.collectAsStateWithLifecycle()
    val attachments by vm.chatAttachments.collectAsStateWithLifecycle()

    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> vm.attachToChat(uris) }
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> vm.attachToChat(uris) }

    var pickingSkill by rememberSaveable { mutableStateOf(false) }
    val skills by vm.skills.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.loadSkills() }
    LaunchedEffect(level) { vm.loadKnowledge() }
    LaunchedEffect(messages.size, busy) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size)
    }

    Column(modifier.fillMaxSize().imePadding()) {
        ChatHeader(
            level = level,
            analyzed = docs.count { it.status == "DONE" },
            pendingDocs = docs.count { it.status == "PENDING" },
            busy = busy,
            canClear = messages.isNotEmpty(),
            onClear = { vm.clearChat() },
            onLibrary = { vm.go(com.corewall.qaqc.ui.nav.Dest.ProjectKnowledge) }
        )

        Box(Modifier.weight(1f)) {
            if (messages.isEmpty()) {
                EmptyChat(cfg.isConfigured) { input = it }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(), state = listState,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(Space.lg)
                ) {
                    items(messages, key = { it.id.takeIf { id -> id != 0L } ?: it.createdAt }) { m ->
                        if (m.role == "user") UserBubble(m)
                        else AnswerCard(
                            m = m,
                            onFollowUp = { vm.askAi(it) },
                            onOpenFile = { vm.openAnyFile(it) }
                        )
                    }
                    // إجراءات الوكيل المستنية موافقة — بتظهر تحت آخر رد
                    items(pending, key = { it.id }) { p ->
                        ActionConfirmCard(
                            title = p.label,
                            detail = p.action.describe(),
                            destructive = p.tool.risk == ToolRisk.DESTRUCTIVE,
                            onConfirm = { vm.confirmAction(p.id) },
                            onDismiss = { vm.dismissAction(p.id) }
                        )
                    }
                    if (busy) item { ThinkingRow(status ?: "بيراجع بيانات الدور…") }
                }
            }
        }

        if (pickingSkill) {
            SkillPickerSheet(
                skills = skills,
                onPick = { name ->
                    pickingSkill = false
                    vm.askWithSkill(name, input)
                    input = ""
                },
                onDismiss = { pickingSkill = false }
            )
        }

        error?.let {
            Surface(color = srt.red.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth()) {
                Text(it, Modifier.padding(Space.lg), style = MaterialTheme.typography.bodySmall, color = srt.red)
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
            AttachmentStrip(attachments, onRemove = { vm.removeChatAttachment(it) })
            Row(Modifier.padding(Space.md), verticalAlignment = Alignment.Bottom) {
                AttachButtons(
                    onFiles = { filePicker.launch(arrayOf("*/*")) },
                    onImages = { imagePicker.launch(arrayOf("image/*")) },
                    onSkills = { pickingSkill = true }
                )
                Spacer(Modifier.width(Space.sm))
                Surface(
                    shape = Radius.shapeXl,
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
                            Box(Modifier.padding(horizontal = Space.lg, vertical = Space.md)) {
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
                Spacer(Modifier.width(Space.sm))
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
                Modifier.padding(horizontal = Space.lg, vertical = Space.md),
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
private fun AnswerCard(
    m: ChatMessageEntity,
    onFollowUp: (String) -> Unit,
    onOpenFile: (String) -> Unit
) {
    val srt = LocalSrtColors.current
    val answer = remember(m.id, m.content) { parseAnswer(m.content) }
    var showSources by remember(m.id) { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.md)) {
        if (answer.headline.isNotBlank()) HeadlineCard(answer.headline)

        answer.blocks.take(6).forEachIndexed { i, b -> AnswerBlockCard(b, i, onOpenFile) }

        if (answer.sources.isNotEmpty()) {
            Row(
                Modifier.clip(Radius.shapeSm).clickable { showSources = !showSources }
                    .padding(vertical = Space.xxs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Source, contentDescription = null, tint = srt.text3, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(Space.sm))
                Text(
                    "المصادر (${answer.sources.size})",
                    style = MaterialTheme.typography.labelSmall, color = srt.text3
                )
            }
            Collapsible(showSources) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    answer.sources.take(8).forEach {
                        Text("• $it", style = MaterialTheme.typography.labelSmall, color = srt.text3)
                    }
                }
            }
        }

        if (answer.followUps.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                answer.followUps.take(4).forEach { q ->
                    Surface(
                        onClick = { onFollowUp(q) },
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Text(
                            q, Modifier.padding(horizontal = Space.md, vertical = Space.sm),
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
        Modifier.fillMaxSize().padding(Space.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(76.dp).clip(CircleShape).background(srt.blueTint),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = srt.blue, modifier = Modifier.size(38.dp)) }
        Spacer(Modifier.height(Space.lg))
        Text("المساعد الهندسي", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(Space.sm))
        Text(
            if (configured) "اسأله عن أي حاجة، أو خلّيه ينفّذ — بيشوف الدور والملفات والجدول."
            else "ضيف مفتاح API من إعدادات المساعد الذكي عشان تبدأ.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (configured) {
            Spacer(Modifier.height(Space.lg))
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                SUGGESTIONS.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        row.forEach { s ->
                            Surface(
                                onClick = { onPick(s) },
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(s, Modifier.padding(horizontal = Space.md, vertical = Space.sm),
                                    style = MaterialTheme.typography.labelMedium, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * ترويسة المحادثة — بتقول للمستخدم المساعد شايف إيه دلوقتي.
 *
 * النطاق مكتوب صراحة (دور + مكتبة مشتركة) عشان محدّش يفتكر إن المساعد
 * بيشوف كل الأدوار. الأدوار معزولة، والوضوح هنا جزء من العزل.
 */
@Composable
private fun ChatHeader(
    level: String,
    analyzed: Int,
    pendingDocs: Int,
    busy: Boolean,
    canClear: Boolean,
    onClear: () -> Unit,
    onLibrary: () -> Unit
) {
    val srt = LocalSrtColors.current
    val pulse = rememberInfiniteTransition(label = "hdr")
    val glow by pulse.animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "glow"
    )

    Surface(color = srt.blueTint, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = Space.lg, vertical = Space.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(30.dp).clip(CircleShape)
                    .background(srt.blue.copy(alpha = if (busy) glow * 0.30f else 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.AutoAwesome, contentDescription = null,
                    tint = srt.blue, modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(
                    if (busy) "بيشتغل…" else "المساعد الهندسي",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold, color = srt.blue
                )
                Text(
                    buildString {
                        append("دور $level · $analyzed مستند")
                        if (pendingDocs > 0) append(" · $pendingDocs مستني")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = srt.blue.copy(alpha = 0.75f)
                )
            }
            TapTarget(Icons.Filled.Hub, "معرفة المشروع", srt.purple, onLibrary)
            if (canClear) TapTarget(Icons.Filled.DeleteSweep, "مسح المحادثة", srt.blue, onClear)
        }
    }
}

/**
 * منتقي المهارة.
 *
 * المهارة بتتطبّق على اللي مكتوب في الخانة **دلوقتي**. لو الخانة
 * فاضية، المهارة بتشتغل على الدور الشغّال لوحدها — أغلب المهارات
 * ("فحص قبل الصبّ"، "تقرير يومي") مالهاش سؤال أصلاً، هي الطلب نفسه.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SkillPickerSheet(
    skills: List<com.corewall.qaqc.data.db.PromptEntity>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val srt = LocalSrtColors.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = Space.lg)
                .padding(bottom = Space.lg)
        ) {
            Text(
                "المهارات",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(Space.xs))
            Text(
                "المهارة بتقول للمساعد يشتغل إزاي: يبصّ على إيه، بأي ترتيب، " +
                    "وشكل الإخراج. بتتطبّق على اللي مكتوب في الخانة.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Space.md))

            if (skills.isEmpty()) {
                Text(
                    "مفيش مهارات. اعملها من مكتبة البرومبت في إعدادات المساعد.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(skills, key = { it.id }) { skill ->
                        Surface(
                            onClick = { onPick(skill.name) },
                            shape = Radius.shapeMd,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Column(Modifier.padding(Space.md)) {
                                Text(
                                    skill.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = srt.purple
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    // أول سطر من المهارة بيقول بتعمل إيه —
                                    // العنوان لوحده مش كفاية للاختيار.
                                    skill.body.lineSequence().firstOrNull { it.isNotBlank() }
                                        .orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** أزرار الإرفاق — ملفات وصور. */
@Composable
private fun AttachButtons(
    onFiles: () -> Unit,
    onImages: () -> Unit,
    onSkills: () -> Unit
) {
    val srt = LocalSrtColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        // المهارات جنب الإرفاق مش في قايمة مخبّية: طريقة الشغل بتتختار
        // وانت بتكتب السؤال، مش قبل ما تفتح الشاشة.
        Surface(
            onClick = onSkills, shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(40.dp)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = "مهارات",
                    tint = srt.purple,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.width(Space.xs))
        Surface(
            onClick = onImages, shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = "أرفق صور",
                    tint = srt.purple, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(Space.sm))
        Surface(
            onClick = onFiles, shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.AttachFile, contentDescription = "أرفق ملفات",
                    tint = srt.blue, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/**
 * شريط المرفقات المستنية الإرسال.
 * بيوضّح إن الملف اتسجّل واتحلّل بالفعل — مش مجرد اسم متعلّق بالرسالة.
 */
@Composable
private fun AttachmentStrip(files: List<java.io.File>, onRemove: (java.io.File) -> Unit) {
    val srt = LocalSrtColors.current
    AnimatedVisibility(
        visible = files.isNotEmpty(),
        enter = fadeIn() + expandVertically(),
        exit = shrinkVertically()
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.sm)) {
            Text(
                "${files.size} مرفق مع السؤال الجاي — اتسجّلوا في ذاكرة الدور",
                style = MaterialTheme.typography.labelSmall, color = srt.text3
            )
            Spacer(Modifier.height(Space.sm))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                files.forEach { f ->
                    Surface(
                        shape = Radius.shapeMd,
                        color = srt.blueTint,
                        border = BorderStroke(1.dp, srt.blue.copy(alpha = 0.25f))
                    ) {
                        Row(
                            Modifier.padding(start = Space.md, end = Space.sm, top = Space.sm, bottom = Space.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.AttachFile, contentDescription = null,
                                tint = srt.blue, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(Space.sm))
                            Text(
                                f.name, style = MaterialTheme.typography.labelSmall,
                                color = srt.blue, maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 150.dp)
                            )
                            Spacer(Modifier.width(Space.xs))
                            Icon(
                                Icons.Filled.Close, contentDescription = "شيل",
                                tint = srt.text3,
                                modifier = Modifier.size(15.dp).clip(CircleShape)
                                    .clickable { onRemove(f) }
                            )
                        }
                    }
                }
            }
        }
    }
}


/**
 * الخلاصة — أهم سطر في الرد، فبياخد أوضح معالجة بصرية.
 *
 * شريط لوني على الحافة بدل إطار كامل: بيدّي وزن بصري من غير ما يزوّد
 * حبر حوالين النص. الخلفية متدرّجة خفيفة عشان يتميّز عن الكروت اللي تحته.
 */
@Composable
private fun HeadlineCard(text: String) {
    val srt = LocalSrtColors.current
    Surface(
        shape = Radius.shapeLg,
        color = androidx.compose.ui.graphics.Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier
                .clip(Radius.shapeLg)
                .background(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        listOf(srt.blue.copy(alpha = 0.14f), srt.blue.copy(alpha = 0.04f))
                    )
                )
                .fillMaxWidth()
        ) {
            // شريط الحافة — بيثبّت العين على بداية السطر
            Box(
                Modifier.width(Space.xs).heightIn(min = 52.dp).fillMaxHeight()
                    .background(srt.blue)
            )
            Row(
                Modifier.padding(Space.lg).weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(srt.blue),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.AutoAwesome, contentDescription = null,
                        tint = Color.White, modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(Modifier.width(Space.md))
                Text(
                    text,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}


/**
 * أيقونة قابلة للضغط بمساحة لمس حقيقية.
 *
 * الأيقونة بتفضل صغيرة بصرياً، لكن منطقة اللمس 44dp — الحد الأدنى
 * المتعارف عليه. أيقونة 19dp معناها إنك لازم تصيبها في نصّها بالظبط،
 * وده شبه مستحيل بجوانتي في الموقع.
 */
@Composable
private fun TapTarget(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(19.dp))
    }
}
