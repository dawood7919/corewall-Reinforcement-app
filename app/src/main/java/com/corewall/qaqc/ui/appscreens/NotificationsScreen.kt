package com.corewall.qaqc.ui.appscreens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.ui.EmptyState
import com.corewall.qaqc.ui.theme.LocalSrtColors

private data class Notif(
    val id: String,
    val icon: ImageVector,
    val color: Color,
    val title: String,
    val body: String,
    val time: String
)

@Composable
fun NotificationsScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val srt = LocalSrtColors.current
    val level by vm.currentLevel.collectAsStateWithLifecycle()
    val schedule by vm.schedule.collectAsStateWithLifecycle()
    val notes by vm.notes.collectAsStateWithLifecycle()
    val daily by vm.dailyAttendance.collectAsStateWithLifecycle()

    val readIds = remember { mutableStateMapOf<String, Boolean>() }

    val notifs = remember(level, schedule, notes, daily) {
        val list = mutableListOf<Notif>()
        // فجوات بيانات حقيقية للدور الحالي
        vm.allMarks().forEach { mark ->
            val gaps = runCatching { vm.logic.gapLevels(schedule, mark) }.getOrDefault(emptyList())
            if (level in gaps) {
                list += Notif(
                    "gap-$mark", Icons.Filled.WarningAmber, srt.red,
                    "تنبيه: نقص بيانات — $mark",
                    "الدور $level ضمن مدى العنصر لكن لا يوجد صف يغطيه. الأدوار الناقصة: ${gaps.joinToString(", ")}",
                    "الآن"
                )
            }
        }
        // ملاحظات جديدة (آخر 5)
        notes.sortedByDescending { it.updatedAt }.take(5).forEach { n ->
            list += Notif(
                "note-${n.id}", Icons.Filled.EditNote, srt.blue,
                "ملاحظة جديدة",
                "تم إضافة \"${n.title.ifBlank { "بدون عنوان" }}\"",
                relTime(n.updatedAt)
            )
        }
        // تحديث حضور (آخر 3)
        daily.sortedByDescending { it.updatedAt }.take(3).forEach { d ->
            list += Notif(
                "att-${d.id}", Icons.Filled.Groups, srt.green,
                "تحديث الحضور",
                "تم تسجيل حضور ${d.workers} عامل و ${d.foremen} فورمان",
                relTime(d.updatedAt)
            )
        }
        list
    }

    Column(modifier.fillMaxSize()) {
        if (notifs.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    "تحديد الكل كمقروء",
                    Modifier.clickable { notifs.forEach { readIds[it.id] = true } },
                    style = MaterialTheme.typography.labelLarge,
                    color = srt.blue,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        if (notifs.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.NotificationsNone,
                title = "لا توجد إشعارات",
                subtitle = "هتظهر هنا تنبيهات نقص البيانات، الملاحظات الجديدة، وتحديثات الحضور.",
                modifier = Modifier.fillMaxSize()
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(notifs, key = { it.id }) { n ->
                    NotifCard(n, unread = readIds[n.id] != true, onClick = { readIds[n.id] = true })
                }
            }
        }
    }
}

@Composable
private fun NotifCard(n: Notif, unread: Boolean, onClick: () -> Unit) {
    val srt = LocalSrtColors.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(n.color),
                contentAlignment = Alignment.Center
            ) { Icon(n.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp)) }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(n.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.size(2.dp))
                Text(n.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                Spacer(Modifier.size(4.dp))
                Text(n.time, style = MaterialTheme.typography.labelSmall, color = srt.text3)
            }
            if (unread) {
                Spacer(Modifier.size(6.dp))
                Box(Modifier.size(8.dp).clip(CircleShape).background(srt.red))
            }
        }
    }
}

private fun relTime(ts: Long): String {
    if (ts <= 0) return ""
    val diff = System.currentTimeMillis() - ts
    val min = diff / 60000
    return when {
        min < 1 -> "الآن"
        min < 60 -> "منذ $min دقيقة"
        min < 1440 -> "منذ ${min / 60} ساعة"
        else -> "منذ ${min / 1440} يوم"
    }
}
