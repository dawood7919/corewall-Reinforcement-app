package com.corewall.qaqc.ui.appscreens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.domain.relativeTime
import com.corewall.qaqc.ui.design.CwButton
import com.corewall.qaqc.ui.design.CwButtonStyle
import com.corewall.qaqc.ui.design.CwCard
import com.corewall.qaqc.ui.design.CwCardStyle
import com.corewall.qaqc.ui.design.CwEmptyState
import com.corewall.qaqc.ui.design.CwLeadingIcon
import com.corewall.qaqc.ui.design.CwStatusBadge
import com.corewall.qaqc.ui.design.CwTone
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Space
import com.corewall.qaqc.ui.design.semantic
import com.corewall.qaqc.ui.nav.Dest

private data class Notif(
    val id: String,
    val icon: ImageVector,
    val tone: CwTone,
    val title: String,
    val body: String,
    val time: String,
    val dest: Dest?
)

/**
 * الإشعارات.
 *
 * كل إشعار هنا مشتقّ من بيانات حقيقية في الدور الشغّال — مفيش إشعارات
 * تجريبية. وكل واحد بيودّيك على الشاشة اللي بتشرحه بدل ما يقف عند الخبر.
 *
 * حالة "مقروء" بقت في [rememberSaveable]: قبل كده كانت بتضيع مع أي لفّة
 * شاشة، فالإشعارات كانت بترجع كلها غير مقروءة.
 */
@Composable
fun NotificationsScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val c = LocalCwColors.current
    val level by vm.currentLevel.collectAsStateWithLifecycle()
    val schedule by vm.schedule.collectAsStateWithLifecycle()
    val notes by vm.notes.collectAsStateWithLifecycle()
    val daily by vm.dailyAttendance.collectAsStateWithLifecycle()

    var readIds by rememberSaveable { mutableStateOf(emptySet<String>()) }

    val notifs = remember(level, schedule, notes, daily) {
        buildList {
            vm.allMarks().forEach { mark ->
                val gaps = runCatching { vm.logic.gapLevels(schedule, mark) }.getOrDefault(emptyList())
                if (level in gaps) {
                    add(
                        Notif(
                            id = "gap-$mark",
                            icon = Icons.Filled.WarningAmber,
                            tone = CwTone.Danger,
                            title = "فجوة بيانات — $mark",
                            body = "الدور $level داخل مدى العنصر بس مفيش صف بيغطّيه. " +
                                "الأدوار الناقصة: ${gaps.joinToString("، ")}",
                            time = "دلوقتي",
                            dest = Dest.Gaps
                        )
                    )
                }
            }
            notes.sortedByDescending { it.updatedAt }.take(5).forEach { n ->
                add(
                    Notif(
                        id = "note-${n.id}",
                        icon = Icons.Filled.EditNote,
                        tone = CwTone.Info,
                        title = "ملاحظة اتحدّثت",
                        body = n.title.ifBlank { "بدون عنوان" },
                        time = relativeTime(n.updatedAt),
                        dest = Dest.FloorNotes
                    )
                )
            }
            daily.sortedByDescending { it.updatedAt }.take(3).forEach { d ->
                add(
                    Notif(
                        id = "att-${d.id}",
                        icon = Icons.Filled.Groups,
                        tone = CwTone.Success,
                        title = "الحضور اتسجّل",
                        body = "${d.workers} عامل و${d.foremen} فورمان",
                        time = relativeTime(d.updatedAt),
                        dest = Dest.Manpower
                    )
                )
            }
        }
    }

    if (notifs.isEmpty()) {
        CwEmptyState(
            icon = Icons.Filled.NotificationsNone,
            title = "مفيش إشعارات",
            detail = "هنا هتلاقي تنبيهات نقص البيانات في الجدول، والملاحظات الجديدة، " +
                "وتحديثات الحضور — كلها للدور الشغّال.",
            modifier = modifier.fillMaxSize()
        )
        return
    }

    val unreadCount = notifs.count { it.id !in readIds }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.screen, vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (unreadCount > 0) "$unreadCount غير مقروء" else "كلها مقروءة",
                style = MaterialTheme.typography.labelLarge,
                color = c.textTertiary,
                modifier = Modifier.weight(1f)
            )
            if (unreadCount > 0) {
                CwButton(
                    "تحديد الكل كمقروء",
                    { readIds = notifs.map { it.id }.toSet() },
                    style = CwButtonStyle.Ghost
                )
            }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Space.screen, end = Space.screen,
                top = Space.xs, bottom = Space.bottomInset
            ),
            verticalArrangement = Arrangement.spacedBy(Space.stack)
        ) {
            items(notifs, key = { it.id }) { n ->
                NotifCard(
                    n = n,
                    unread = n.id !in readIds,
                    onClick = {
                        readIds = readIds + n.id
                        n.dest?.let { vm.go(it) }
                    }
                )
            }
        }
    }
}

@Composable
private fun NotifCard(n: Notif, unread: Boolean, onClick: () -> Unit) {
    val c = LocalCwColors.current
    CwCard(
        style = if (unread) CwCardStyle.Accent else CwCardStyle.Plain,
        accent = n.tone.semantic().solid,
        onClick = onClick
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(Space.md)
        ) {
            CwLeadingIcon(n.icon, tone = n.tone)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        n.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = c.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    // "جديد" مكتوبة — مش نقطة ملوّنة لوحدها.
                    if (unread) CwStatusBadge("جديد", n.tone, compact = true)
                }
                Spacer(Modifier.height(Space.xxs))
                Text(
                    n.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(Space.xs))
                Text(n.time, style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
            }
        }
    }
}
