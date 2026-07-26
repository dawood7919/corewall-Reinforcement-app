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
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
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
fun AppDrawer(vm: MainViewModel, onNavigate: () -> Unit, onAbout: () -> Unit) {
    val section by vm.section.collectAsStateWithLifecycle()
    val lens by vm.lens.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val gradient = LocalAppGradients.current.header

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        // هيدر بروفايل بتدرّج
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
                Column {
                    Text("QA/QC Engineer", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
                    Text("Core Wall QA/QC", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
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

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(8.dp))
        SectionLabel("عام")

        DrawerItem(
            icon = Icons.Filled.Settings, title = "الإعدادات", subtitle = null, selected = false,
            onClick = { vm.goToCorewallTab(4); onNavigate() }
        )
        DrawerItem(
            icon = Icons.Filled.Backup, title = "النسخ الاحتياطي", subtitle = null, selected = false,
            onClick = { vm.goToCorewallTab(4); onNavigate() }
        )
        DrawerItem(
            icon = Icons.Filled.Info, title = "عن التطبيق", subtitle = null, selected = false,
            onClick = onAbout
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
private fun DrawerItem(icon: ImageVector, title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
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
