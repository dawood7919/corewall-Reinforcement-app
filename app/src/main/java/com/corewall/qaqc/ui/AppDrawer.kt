package com.corewall.qaqc.ui

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.AppScreen
import com.corewall.qaqc.Lens
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.Section
import com.corewall.qaqc.data.AppTheme
import com.corewall.qaqc.ui.theme.LocalAppGradients

/**
 * القائمة الجانبية الرئيسية (زي الموك أب): هيدر بروفايل + الأدوات الرئيسية
 * بعناوين فرعية + قسم عام + مبدّل ثيم سريع.
 */
@Composable
fun AppDrawer(vm: MainViewModel, onNavigate: () -> Unit) {
    val section by vm.section.collectAsStateWithLifecycle()
    val lens by vm.lens.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val unread by vm.unreadNotifications.collectAsStateWithLifecycle()
    val gradient = LocalAppGradients.current.header

    fun go(action: () -> Unit) { action(); onNavigate() }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        // هيدر بروفايل بتدرّج + جرس إشعارات
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(gradient))
                .statusBarsPadding()
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp).background(Color.White.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("م. أحمد حسن", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                    Text("QA/QC Engineer", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
                }
                Box(contentAlignment = Alignment.TopEnd) {
                    Surface(
                        onClick = { go { vm.openAppScreen(AppScreen.NOTIFICATIONS) } },
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.2f)
                    ) {
                        Icon(Icons.Filled.Notifications, contentDescription = "الإشعارات", tint = Color.White, modifier = Modifier.padding(9.dp).size(22.dp))
                    }
                    if (unread > 0) {
                        Box(
                            Modifier.size(18.dp).background(Color(0xFFFF3B30), CircleShape),
                            contentAlignment = Alignment.Center
                        ) { Text("$unread", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionLabel("الأدوات الرئيسية")

        DrawerItem(
            icon = Icons.Filled.ViewInAr, title = "Corewall Reinforcement", subtitle = "متابعة تسليح الحوائط",
            selected = section == Section.COREWALL && lens == Lens.REINF,
            onClick = { vm.goToLens(Lens.REINF); onNavigate() }
        )
        DrawerItem(
            icon = Icons.Filled.Calculate, title = "Corewall Counting", subtitle = "عدّ التسليح الرأسي",
            selected = section == Section.COREWALL && lens == Lens.COUNT,
            onClick = { vm.goToLens(Lens.COUNT); onNavigate() }
        )
        DrawerItem(
            icon = Icons.Filled.Groups, title = "Manpower (الحضور)", subtitle = "إدارة الحضور والعمالة",
            selected = section == Section.MANPOWER,
            onClick = { vm.goToManpower(); onNavigate() }
        )
        DrawerItem(
            icon = Icons.Filled.Folder, title = "Data", subtitle = "الملفات · الملاحظات · المهام",
            selected = section == Section.COREWALL && lens == Lens.DATA,
            onClick = { vm.goToLens(Lens.DATA); onNavigate() }
        )
        DrawerItem(
            icon = Icons.Filled.PhotoCamera, title = "Site Photos", subtitle = "معرض صور الدور · تعليق + تاريخ",
            selected = false,
            onClick = { go { vm.openAppScreen(AppScreen.SITE_PHOTOS) } }
        )

        DrawerItem(
            icon = Icons.Filled.Summarize, title = "التقارير", subtitle = "Reports & Analytics",
            selected = section == Section.MANPOWER,
            onClick = { go { vm.goToManpower(); vm.setTabIndex(1) } }
        )
        DrawerItem(
            icon = Icons.Filled.Notifications, title = "الإشعارات", subtitle = "Notifications",
            selected = false, badge = if (unread > 0) unread else null,
            onClick = { go { vm.openAppScreen(AppScreen.NOTIFICATIONS) } }
        )

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(8.dp))
        SectionLabel("عام")

        DrawerItem(
            icon = Icons.Filled.Storage, title = "ذاكرة المشروع", subtitle = "الملفات اللي الـAI حلّلها",
            selected = false,
            onClick = { go { vm.openAppScreen(AppScreen.AI_KNOWLEDGE) } }
        )
        DrawerItem(
            icon = Icons.Filled.Forum, title = "المساعد الهندسي", subtitle = "اسأل عن المشروع بالعربي",
            selected = false,
            onClick = { go { vm.openAppScreen(AppScreen.AI_CHAT) } }
        )
        DrawerItem(
            icon = Icons.Filled.AutoAwesome, title = "المساعد الذكي", subtitle = "تحليل الدور بالذكاء الاصطناعي",
            selected = false,
            onClick = { go { vm.openAppScreen(AppScreen.AI_SETTINGS) } }
        )
        DrawerItem(
            icon = Icons.Filled.Settings, title = "الإعدادات", subtitle = null, selected = false,
            onClick = { go { vm.openAppScreen(AppScreen.SETTINGS) } }
        )
        DrawerItem(
            icon = Icons.Filled.Sync, title = "مزامنة البيانات", subtitle = null, selected = false,
            onClick = { go { vm.openAppScreen(AppScreen.SYNC) } }
        )
        DrawerItem(
            icon = Icons.Filled.Info, title = "عن التطبيق", subtitle = null, selected = false,
            onClick = { go { vm.openAppScreen(AppScreen.ABOUT) } }
        )

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(Modifier.padding(horizontal = 20.dp))
        // مبدّل الثيم السريع
        Row(
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThemeChip("فاتح", Icons.Filled.LightMode, settings.theme == AppTheme.IOS_LIGHT) { vm.updateSettings { it.copy(theme = AppTheme.IOS_LIGHT) } }
            ThemeChip("دارك", Icons.Filled.DarkMode, settings.theme == AppTheme.DARK_OLED) { vm.updateSettings { it.copy(theme = AppTheme.DARK_OLED) } }
            ThemeChip("Blueprint", Icons.Outlined.GridView, settings.theme == AppTheme.BLUEPRINT) { vm.updateSettings { it.copy(theme = AppTheme.BLUEPRINT) } }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun DrawerItem(icon: ImageVector, title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit, badge: Int? = null) {
    Surface(
        onClick = onClick,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon, contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (badge != null) {
                Box(
                    Modifier.size(24.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) { Text("$badge", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun ThemeChip(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.height(2.dp))
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}
