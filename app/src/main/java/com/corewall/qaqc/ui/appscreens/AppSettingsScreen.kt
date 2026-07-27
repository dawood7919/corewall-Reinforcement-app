package com.corewall.qaqc.ui.appscreens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.AppScreen
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.data.AppTheme
import com.corewall.qaqc.ui.theme.LocalSrtColors
import com.corewall.qaqc.ui.theme.SrtGroupedList
import com.corewall.qaqc.ui.theme.SrtRow
import com.corewall.qaqc.ui.theme.SrtSectionHeader
import com.corewall.qaqc.ui.theme.SrtToggle

@Composable
fun AppSettingsScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val srt = LocalSrtColors.current
    val settings by vm.settings.collectAsStateWithLifecycle()

    var pushNotif by remember { mutableStateOf(true) }
    var autoSync by remember { mutableStateOf(true) }
    var appLock by remember { mutableStateOf(false) }

    val chevron: @Composable () -> Unit = {
        Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = srt.text3, modifier = Modifier.size(14.dp))
    }
    fun valueTrailing(text: String): @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(6.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = srt.text3, modifier = Modifier.size(14.dp))
        }
    }

    val themeName = when (settings.theme) {
        AppTheme.IOS_LIGHT -> "فاتح"
        AppTheme.DARK_OLED -> "دارك"
        AppTheme.BLUEPRINT -> "Blueprint"
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Profile card
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(64.dp).clip(CircleShape).background(srt.blueTint),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Person, contentDescription = null, tint = srt.blue, modifier = Modifier.size(34.dp)) }
                Spacer(Modifier.size(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("م. أحمد حسن", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("QA/QC Engineer", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                chevron()
            }
        }

        Spacer(Modifier.height(20.dp))
        SrtSectionHeader("الحساب")
        SrtGroupedList {
            SrtRow("الاسم والبيانات الشخصية", trailing = chevron, onClick = {})
            SrtRow("الشركة", trailing = valueTrailing("Arabian Construction Co."), onClick = {})
            SrtRow("المشروع الحالي", trailing = valueTrailing("BHR Tower 1"), showDivider = false, onClick = {})
        }

        Spacer(Modifier.height(20.dp))
        SrtSectionHeader("التفضيلات")
        SrtGroupedList {
            SrtRow("اللغة", trailing = valueTrailing("العربية"), onClick = {})
            SrtRow("المظهر", trailing = valueTrailing(themeName), onClick = {
                // دورة بين الثيمات الثلاثة
                val next = when (settings.theme) {
                    AppTheme.IOS_LIGHT -> AppTheme.DARK_OLED
                    AppTheme.DARK_OLED -> AppTheme.BLUEPRINT
                    AppTheme.BLUEPRINT -> AppTheme.IOS_LIGHT
                }
                vm.updateSettings { it.copy(theme = next) }
            })
            SrtRow("الإشعارات الفورية", trailing = { SrtToggle(pushNotif, { pushNotif = it }) })
            SrtRow("المزامنة التلقائية", trailing = { SrtToggle(autoSync, { autoSync = it }) })
            SrtRow("الوحدات", trailing = valueTrailing("متري (mm)"), showDivider = false, onClick = {})
        }

        Spacer(Modifier.height(20.dp))
        SrtSectionHeader("الأمان والدعم")
        SrtGroupedList {
            SrtRow("قفل التطبيق (PIN / بصمة)", trailing = { SrtToggle(appLock, { appLock = it }) })
            SrtRow("تصدير البيانات", trailing = chevron, onClick = {})
            SrtRow("عن التطبيق", trailing = chevron, onClick = { vm.openAppScreen(AppScreen.ABOUT) })
            SrtRow("التواصل مع الدعم الفني", trailing = chevron, showDivider = false, onClick = {})
        }

        Spacer(Modifier.height(24.dp))
        Surface(
            onClick = {},
            shape = RoundedCornerShape(18.dp),
            color = srt.red.copy(alpha = 0.10f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = srt.red, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text("تسجيل الخروج", color = srt.red, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
