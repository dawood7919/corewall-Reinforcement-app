package com.corewall.qaqc.ui.home

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.Lens
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.data.model.ElementCategory
import com.corewall.qaqc.data.model.InspectionStatus
import com.corewall.qaqc.domain.ActiveRangeResult
import com.corewall.qaqc.ui.theme.LocalAppGradients
import com.corewall.qaqc.ui.theme.LocalSrtColors
import com.corewall.qaqc.ui.theme.TowerNumberStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * الشاشة الرئيسية = مركز قيادة الدور (Mission Control).
 * كل حاجة بتدور حوالين الدور الشغّال: رحلة البرج، ملخص الدور، مقاييس حية،
 * مهمة اليوم، تنبيهات ذكية، صحة المشروع، ونقاط الإنتاجية.
 */
@Composable
fun MissionControlScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val srt = LocalSrtColors.current
    val level by vm.currentLevel.collectAsStateWithLifecycle()
    val names by vm.names.collectAsStateWithLifecycle()
    val inspections by vm.inspections.collectAsStateWithLifecycle()
    val schedule by vm.schedule.collectAsStateWithLifecycle()
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val notes by vm.notes.collectAsStateWithLifecycle()
    val daily by vm.dailyAttendance.collectAsStateWithLifecycle()
    val files by vm.attendanceFiles.collectAsStateWithLifecycle()

    val levels = vm.levels
    val levelIdx = levels.indexOf(level).coerceAtLeast(0)

    // ----- مقاييس حقيقية للدور الشغّال -----
    val elements = vm.planData.elements
    val walls = elements.count { it.cat == ElementCategory.WALL }
    val couplingBeams = elements.count { it.cat == ElementCategory.COUPLING_BEAM }
    val internalBeams = elements.count { it.cat == ElementCategory.INTERNAL_BEAM }

    val namedEls = remember(names) { elements.filter { names[it.id] != null } }
    val statusFor = { id: String -> InspectionStatus.from(inspections[id to level]) }
    val completed = namedEls.count { statusFor(it.id).let { s -> s == InspectionStatus.APPROVED || s == InspectionStatus.CAST } }
    val approved = namedEls.count { statusFor(it.id) == InspectionStatus.APPROVED }
    val cast = namedEls.count { statusFor(it.id) == InspectionStatus.CAST }
    val wir = namedEls.count { statusFor(it.id) == InspectionStatus.WIR_SUBMITTED }
    val rejected = namedEls.count { statusFor(it.id) == InspectionStatus.REJECTED }
    val namedCount = namedEls.size
    val completion = if (namedCount == 0) 0 else (completed * 100 / namedCount)

    val gaps = remember(schedule, level, names) {
        namedEls.count { el ->
            names[el.id]?.let { vm.logic.activeRange(schedule, it, level) } is ActiveRangeResult.Gap
        }
    }
    val pendingInsp = (namedCount - completed).coerceAtLeast(0)

    val fileIds = files.map { it.id }.toSet()
    val today = todayStart()
    val todayRecords = daily.filter { it.fileId in fileIds && dayOf(it.date) == today }
    val workers = todayRecords.sumOf { it.workers }
    val foremen = todayRecords.sumOf { it.foremen }
    val engineers = todayRecords.sumOf { it.engineers }

    val openNotes = notes.count { it.level == level }
    val doneTasks = tasks.count { it.done }
    val totalTasks = tasks.size

    // نقاط الإنتاجية (لعبة تحفيزية) — مشتقة من نشاط اليوم
    val score = (completion + doneTasks * 8 + openNotes * 4 + workers).coerceAtMost(100)

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        GreetingHeader(project = "BHR Tower 1", level = level, completion = completion)

        // مساعد الـ AI — أول حاجة المهندس يشوفها بعد الترحيب
        Spacer(Modifier.height(16.dp))
        val aiState by vm.aiState.collectAsStateWithLifecycle()
        com.corewall.qaqc.ui.ai.AiDashboardCard(
            state = aiState,
            onRefresh = { vm.refreshAiAnalysis() },
            onOpenSettings = { vm.openAppScreen(com.corewall.qaqc.AppScreen.AI_SETTINGS) },
            onOpenFull = { vm.openAppScreen(com.corewall.qaqc.AppScreen.AI_ANALYSIS) },
            onOpenChat = { vm.openAppScreen(com.corewall.qaqc.AppScreen.AI_CHAT) }
        )

        Spacer(Modifier.height(16.dp))
        BuildingJourney(levels = levels, currentIdx = levelIdx, completion = completion,
            statusText = when {
                completion >= 100 -> "مكتمل"
                completion >= 50 -> "قيد التنفيذ"
                completion > 0 -> "في البداية"
                else -> "لم يبدأ"
            })

        Spacer(Modifier.height(16.dp))
        SectionTitle("ملخّص الدور")
        Spacer(Modifier.height(8.dp))
        FloorSummary(completion, approved, cast, wir, rejected, pendingInsp, gaps)

        Spacer(Modifier.height(20.dp))
        SectionTitle("مقاييس حيّة")
        Spacer(Modifier.height(8.dp))
        MetricsGrid(
            listOf(
                Metric("العمال", workers, Icons.Filled.Groups, srt.blue),
                Metric("الفورمان", foremen, Icons.Filled.Groups, srt.orange),
                Metric("المهندسين", engineers, Icons.Filled.Groups, srt.green),
                Metric("الحوائط", walls, Icons.Filled.Assignment, srt.blue),
                Metric("كمرات رابطة", couplingBeams, Icons.Filled.Assignment, srt.red),
                Metric("كمرات داخلية", internalBeams, Icons.Filled.Assignment, srt.purple),
                Metric("ملاحظات مفتوحة", openNotes, Icons.Filled.EditNote, srt.blue),
                Metric("فحوصات معلّقة", pendingInsp, Icons.Filled.WarningAmber, srt.orange),
                Metric("مهام مكتملة", doneTasks, Icons.Filled.CheckCircle, srt.green)
            )
        )

        Spacer(Modifier.height(20.dp))
        SectionTitle("مهمة اليوم")
        Spacer(Modifier.height(8.dp))
        MissionCard(tasks.map { it.title to it.done }, doneTasks, totalTasks, onOpen = { vm.setTabIndex(3) })

        Spacer(Modifier.height(20.dp))
        SectionTitle("تنبيهات ذكية")
        Spacer(Modifier.height(8.dp))
        SmartAlerts(gaps = gaps, pending = pendingInsp, workers = workers, level = level)

        Spacer(Modifier.height(20.dp))
        SectionTitle("صحّة المشروع")
        Spacer(Modifier.height(8.dp))
        HealthRings(
            listOf(
                "التقدّم" to completion,
                "الجودة" to (if (namedCount == 0) 0 else approved * 100 / namedCount),
                "التوثيق" to (openNotes * 12).coerceAtMost(100),
                "الحضور" to (workers * 2).coerceAtMost(100),
                "السلامة" to 92
            )
        )

        Spacer(Modifier.height(20.dp))
        ProductivityCard(score = score, doneTasks = doneTasks, notes = openNotes)

        Spacer(Modifier.height(20.dp))
        SectionTitle("إجراءات سريعة")
        Spacer(Modifier.height(8.dp))
        QuickActions(
            onPlan = { vm.setLens(Lens.REINF); vm.setTabIndex(1) },
            onNote = { vm.openAppScreen(com.corewall.qaqc.AppScreen.FLOOR_NOTES) },
            onAttendance = { vm.goToManpower() },
            onFiles = { vm.setTabIndex(2) },
            onTasks = { vm.setTabIndex(3) }
        )

        Spacer(Modifier.height(20.dp))
        SectionTitle("نشاط أخير")
        Spacer(Modifier.height(8.dp))
        RecentActivity(notes = notes.filter { it.level == level }, daily = todayRecords)

        Spacer(Modifier.height(24.dp))
    }
}

// ---------------------------------------------------------------- الهيدر

@Composable
private fun GreetingHeader(project: String, level: String, completion: Int) {
    val gradient = LocalAppGradients.current.header
    val now = remember { Date() }
    val dateStr = remember { SimpleDateFormat("EEEE، d MMMM yyyy", Locale("ar")).format(now) }
    val timeStr = remember { SimpleDateFormat("hh:mm a", Locale("ar")).format(now) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.verticalGradient(gradient))
            .padding(22.dp)
    ) {
        Text("صباح الخير، أحمد 👷", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text("$project · Arabian Construction", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("☀️ 34°", style = MaterialTheme.typography.bodyMedium, color = Color.White)
            Spacer(Modifier.width(12.dp))
            Text("·", color = Color.White.copy(alpha = 0.6f))
            Spacer(Modifier.width(12.dp))
            Text(timeStr, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))
        }
        Text(dateStr, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f))
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("الدور الشغّال", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
                Text(level, style = TowerNumberStyle.copy(fontSize = 40.sp), color = Color.White)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("نسبة الإنجاز", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
                Text("$completion%", style = TowerNumberStyle.copy(fontSize = 40.sp), color = Color.White)
            }
        }
    }
}

// ---------------------------------------------------------------- رحلة البرج

@Composable
private fun BuildingJourney(levels: List<String>, currentIdx: Int, completion: Int, statusText: String) {
    val srt = LocalSrtColors.current
    val listState = rememberLazyListState()
    // عرض من الأعلى للأسفل (الروف فوق) — نعكس ترتيب القائمة
    val display = remember(levels) { levels.reversed() }
    val curDisplayIdx = display.indexOf(levels.getOrElse(currentIdx) { "" }).coerceAtLeast(0)
    androidx.compose.runtime.LaunchedEffect(currentIdx) {
        listState.animateScrollToItem((curDisplayIdx - 2).coerceAtLeast(0))
    }
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(14.dp)) {
            // البرج
            Box(
                Modifier
                    .width(150.dp)
                    .height(230.dp)
            ) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(display.size) { i ->
                        val name = display[i]
                        val realIdx = levels.indexOf(name)
                        val isCurrent = realIdx == currentIdx
                        val done = realIdx < currentIdx // الأدوار الأسفل اتصبّت
                        FloorBrick(name, isCurrent, done, srt.blue)
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                JourneyStat("الدور الحالي", levels.getOrElse(currentIdx) { "-" }, srt.blue)
                Spacer(Modifier.height(10.dp))
                JourneyStat("نسبة الإنجاز", "$completion%", srt.green)
                Spacer(Modifier.height(10.dp))
                JourneyStat("الحالة", statusText, srt.orange)
                Spacer(Modifier.height(10.dp))
                JourneyStat("المتبقّي تقريباً", "${(100 - completion)}%", srt.text3)
            }
        }
    }
}

@Composable
private fun FloorBrick(name: String, isCurrent: Boolean, done: Boolean, blue: Color) {
    val bg = when {
        isCurrent -> blue
        done -> blue.copy(alpha = 0.22f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = when {
        isCurrent -> Color.White
        done -> blue
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .padding(horizontal = 12.dp, vertical = if (isCurrent) 12.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isCurrent) {
            Text("▶", color = fg, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            name,
            color = fg,
            style = if (isCurrent) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun JourneyStat(label: String, value: String, accent: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = accent)
    }
}

// ---------------------------------------------------------------- ملخص الدور

@Composable
private fun FloorSummary(completion: Int, approved: Int, cast: Int, wir: Int, rejected: Int, pending: Int, gaps: Int) {
    val srt = LocalSrtColors.current
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RingProgress(completion, srt.blue, size = 72.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("الإنجاز الكلي للدور", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AnimatedNumber(completion, suffix = "%", style = MaterialTheme.typography.headlineMedium, color = srt.blue)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill("مقبول", approved, srt.green, Modifier.weight(1f))
                StatusPill("مصبوب", cast, srt.blue, Modifier.weight(1f))
                StatusPill("WIR", wir, srt.purple, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill("مرفوض", rejected, srt.red, Modifier.weight(1f))
                StatusPill("معلّق", pending, srt.orange, Modifier.weight(1f))
                StatusPill("فجوات", gaps, srt.orange, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatusPill(label: String, value: Int, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = 0.10f))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedNumber(value, style = MaterialTheme.typography.titleLarge, color = accent)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------- المقاييس

private data class Metric(val label: String, val value: Int, val icon: ImageVector, val accent: Color)

@Composable
private fun MetricsGrid(metrics: List<Metric>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        metrics.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { m -> MetricCard(m, Modifier.weight(1f)) }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun MetricCard(m: Metric, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
    ) {
        Column(Modifier.padding(12.dp)) {
            Box(
                Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(m.accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) { Icon(m.icon, contentDescription = null, tint = m.accent, modifier = Modifier.size(17.dp)) }
            Spacer(Modifier.height(8.dp))
            AnimatedNumber(m.value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(m.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------- مهمة اليوم

@Composable
private fun MissionCard(items: List<Pair<String, Boolean>>, done: Int, total: Int, onOpen: () -> Unit) {
    val srt = LocalSrtColors.current
    val pct = if (total == 0) 0 else done * 100 / total
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RingProgress(pct, srt.blue, size = 64.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("مهمة اليوم", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("$done من $total مكتملة", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (items.isEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("مفيش مهام للدور ده لسه — أضف مهمة من تبويب المهام.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Spacer(Modifier.height(12.dp))
                items.take(5).forEach { (title, isDone) ->
                    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (isDone) Icons.Filled.CheckCircle else Icons.Filled.Assignment,
                            contentDescription = null,
                            tint = if (isDone) srt.green else srt.text3,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            textDecoration = if (isDone) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- تنبيهات ذكية

@Composable
private fun SmartAlerts(gaps: Int, pending: Int, workers: Int, level: String) {
    val srt = LocalSrtColors.current
    val alerts = buildList {
        if (gaps > 0) add(Triple("فجوات بيانات", "$gaps عنصر في دور $level بدون صف تسليح يغطيه", srt.red))
        if (pending > 0) add(Triple("فحوصات مطلوبة", "$pending عنصر بانتظار الفحص/الاعتماد", srt.orange))
        if (workers in 1..14) add(Triple("عمالة منخفضة", "عدد العمال النهاردة $workers — أقل من المعدّل", srt.orange))
        if (workers == 0) add(Triple("لا يوجد حضور", "لم يُسجّل أي عامل في دور $level النهاردة", srt.red))
    }
    if (alerts.isEmpty()) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = srt.green.copy(alpha = 0.10f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = srt.green)
                Spacer(Modifier.width(10.dp))
                Text("كل حاجة تمام في الدور ده ✓", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        alerts.forEach { (title, body, color) ->
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = color) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- صحة المشروع

@Composable
private fun HealthRings(rings: List<Pair<String, Int>>) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            rings.forEach { (label, pct) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    RingProgress(pct, ringColor(pct), size = 52.dp, stroke = 6f, showPct = true)
                    Spacer(Modifier.height(6.dp))
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ringColor(pct: Int): Color {
    val srt = LocalSrtColors.current
    return when {
        pct >= 75 -> srt.green
        pct >= 40 -> srt.orange
        else -> srt.red
    }
}

// ---------------------------------------------------------------- الإنتاجية

@Composable
private fun ProductivityCard(score: Int, doneTasks: Int, notes: Int) {
    val gradient = LocalAppGradients.current.header
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.horizontalGradient(gradient))
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Bolt, contentDescription = null, tint = Color.White)
            Spacer(Modifier.width(8.dp))
            Text("نقاط الإنتاجية اليوم", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        AnimatedNumber(score, style = TowerNumberStyle.copy(fontSize = 48.sp), color = Color.White)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Badge("🏆 خبير الفحص", Icons.Filled.EmojiEvents)
            Badge("🔥 نشاط 7 أيام", Icons.Filled.LocalFireDepartment)
        }
    }
}

@Composable
private fun Badge(text: String, icon: ImageVector) {
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.18f))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = Color.White, fontWeight = FontWeight.Medium)
    }
}

// ---------------------------------------------------------------- إجراءات سريعة

@Composable
private fun QuickActions(
    onPlan: () -> Unit,
    onNote: () -> Unit,
    onAttendance: () -> Unit,
    onFiles: () -> Unit,
    onTasks: () -> Unit
) {
    val srt = LocalSrtColors.current
    val actions = listOf(
        QA("المسقط", Icons.Filled.Map, srt.blue, onPlan),
        QA("ملاحظة", Icons.Filled.NoteAdd, srt.green, onNote),
        QA("حضور", Icons.Filled.Groups, srt.orange, onAttendance),
        QA("الملفات", Icons.Filled.UploadFile, srt.purple, onFiles),
        QA("مهمة", Icons.Filled.Assignment, srt.blue, onTasks),
        QA("صورة", Icons.Filled.PhotoCamera, srt.red, onNote)
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        actions.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { a -> QuickActionCard(a, Modifier.weight(1f)) }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private data class QA(val label: String, val icon: ImageVector, val accent: Color, val onClick: () -> Unit)

@Composable
private fun QuickActionCard(a: QA, modifier: Modifier = Modifier) {
    Surface(
        onClick = a.onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
    ) {
        Column(
            Modifier.padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(a.accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) { Icon(a.icon, contentDescription = null, tint = a.accent, modifier = Modifier.size(24.dp)) }
            Spacer(Modifier.height(8.dp))
            Text(a.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
        }
    }
}

// ---------------------------------------------------------------- نشاط أخير

@Composable
private fun RecentActivity(
    notes: List<com.corewall.qaqc.data.db.NoteEntity>,
    daily: List<com.corewall.qaqc.data.db.DailyAttendanceEntity>
) {
    val srt = LocalSrtColors.current
    data class Act(val title: String, val sub: String, val ts: Long, val icon: ImageVector, val color: Color)
    val acts = buildList {
        notes.sortedByDescending { it.updatedAt }.take(4).forEach {
            add(Act("ملاحظة: ${it.title.ifBlank { "بدون عنوان" }}", relTime(it.updatedAt), it.updatedAt, Icons.Filled.EditNote, srt.blue))
        }
        daily.sortedByDescending { it.updatedAt }.take(3).forEach {
            add(Act("تسجيل حضور ${it.workers} عامل", relTime(it.updatedAt), it.updatedAt, Icons.Filled.Groups, srt.green))
        }
    }.sortedByDescending { it.ts }.take(6)

    if (acts.isEmpty()) {
        Text("مفيش نشاط لسه في الدور ده.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(6.dp)) {
            acts.forEachIndexed { i, a ->
                Row(Modifier.padding(horizontal = 10.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(38.dp).clip(CircleShape).background(a.color.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) { Icon(a.icon, contentDescription = null, tint = a.color, modifier = Modifier.size(20.dp)) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1)
                        Text(a.sub, style = MaterialTheme.typography.labelSmall, color = srt.text3)
                    }
                }
                if (i < acts.lastIndex) androidx.compose.material3.HorizontalDivider(Modifier.padding(start = 60.dp), color = srt.divider)
            }
        }
    }
}

// ---------------------------------------------------------------- عناصر مشتركة

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

/** رقم بعدّاد متحرّك ناعم. */
@Composable
private fun AnimatedNumber(
    target: Int,
    suffix: String = "",
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleLarge,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    val animated by animateIntAsState(targetValue = target, animationSpec = tween(700), label = "count")
    Text("$animated$suffix", style = style, color = color, fontWeight = FontWeight.Bold)
}

/** حلقة تقدّم دائرية. */
@Composable
private fun RingProgress(pct: Int, color: Color, size: androidx.compose.ui.unit.Dp, stroke: Float = 8f, showPct: Boolean = true) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val animated by animateIntAsState(pct, tween(800), label = "ring")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.dp.toPx()
            drawArc(track, 0f, 360f, false, style = Stroke(sw, cap = StrokeCap.Round))
            drawArc(color, -90f, 360f * (animated / 100f), false, style = Stroke(sw, cap = StrokeCap.Round))
        }
        if (showPct) Text("$animated%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color)
    }
}

// ---------------------------------------------------------------- أدوات وقت

private fun todayStart(): Long {
    val c = java.util.Calendar.getInstance()
    c.set(java.util.Calendar.HOUR_OF_DAY, 0); c.set(java.util.Calendar.MINUTE, 0)
    c.set(java.util.Calendar.SECOND, 0); c.set(java.util.Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

private fun dayOf(ts: Long): Long {
    val c = java.util.Calendar.getInstance()
    c.timeInMillis = ts
    c.set(java.util.Calendar.HOUR_OF_DAY, 0); c.set(java.util.Calendar.MINUTE, 0)
    c.set(java.util.Calendar.SECOND, 0); c.set(java.util.Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

private fun relTime(ts: Long): String {
    if (ts <= 0) return ""
    val min = (System.currentTimeMillis() - ts) / 60000
    return when {
        min < 1 -> "الآن"
        min < 60 -> "منذ $min د"
        min < 1440 -> "منذ ${min / 60} س"
        else -> "منذ ${min / 1440} يوم"
    }
}
