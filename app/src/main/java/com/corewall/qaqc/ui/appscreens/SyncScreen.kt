package com.corewall.qaqc.ui.appscreens

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.ui.theme.LocalAppGradients
import com.corewall.qaqc.ui.theme.LocalSrtColors
import com.corewall.qaqc.ui.theme.SrtGroupedList
import com.corewall.qaqc.ui.theme.SrtRow
import com.corewall.qaqc.ui.theme.SrtToggle

@Composable
fun SyncScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val srt = LocalSrtColors.current
    val gradient = LocalAppGradients.current.header
    val notes by vm.notes.collectAsStateWithLifecycle()
    val daily by vm.dailyAttendance.collectAsStateWithLifecycle()

    var syncing by remember { mutableStateOf(false) }
    var autoWifi by remember { mutableStateOf(true) }

    val elementCount = vm.planData.elements.size
    val wallCount = vm.planData.elements.count { it.cat == com.corewall.qaqc.data.model.ElementCategory.WALL }
    val pendingNotes = notes.count { it.updatedAt > 0 }.coerceAtMost(3)

    val transition = rememberInfiniteTransition(label = "spin")
    val angle by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)), label = "angle"
    )

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero status
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.verticalGradient(gradient))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(100.dp).clip(CircleShape).background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                if (syncing) Icon(Icons.Filled.Sync, contentDescription = null, tint = srt.blue, modifier = Modifier.size(48.dp).rotate(angle))
                else Icon(Icons.Filled.Check, contentDescription = null, tint = srt.green, modifier = Modifier.size(56.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(
                if (syncing) "جاري المزامنة…" else "كل البيانات محدثة",
                style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text("آخر مزامنة: اليوم 09:35 صباحًا", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
        }

        Button(
            onClick = { syncing = !syncing },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = srt.blue)
        ) { Text(if (syncing) "إيقاف" else "مزامنة الآن", fontWeight = FontWeight.SemiBold) }

        Text("حالة البيانات", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = srt.text3)
        SrtGroupedList {
            DataStatusRow("بيانات التسليح (المسقط)", "$elementCount عنصر", srt.green)
            DataStatusRow("عدد الكانات", "$wallCount جدار", srt.green)
            DataStatusRow("الحضور اليومي", "${daily.size} سجل", srt.green)
            DataStatusRow(
                "الملاحظات والمرفقات",
                if (pendingNotes > 0) "$pendingNotes بانتظار الرفع" else "محدث",
                if (pendingNotes > 0) srt.orange else srt.green
            )
            DataStatusRow("التقارير", "محدث", srt.green, showDivider = false)
        }

        SrtGroupedList {
            SrtRow(
                "المزامنة التلقائية عبر Wi-Fi فقط",
                trailing = { SrtToggle(autoWifi, { autoWifi = it }) },
                showDivider = false
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DataStatusRow(title: String, value: String, dot: Color, showDivider: Boolean = true) {
    SrtRow(
        title = title,
        showDivider = showDivider,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(8.dp))
                Box(Modifier.size(9.dp).clip(CircleShape).background(dot))
            }
        }
    )
}
