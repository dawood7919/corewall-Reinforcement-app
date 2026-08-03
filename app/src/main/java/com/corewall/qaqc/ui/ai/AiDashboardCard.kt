package com.corewall.qaqc.ui.ai

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.corewall.qaqc.ai.model.AiItem
import com.corewall.qaqc.ai.model.AiSeverity
import com.corewall.qaqc.ai.model.AiUiState
import com.corewall.qaqc.ui.theme.LocalAppGradients
import com.corewall.qaqc.ui.theme.LocalSrtColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val stamp = SimpleDateFormat("hh:mm a", Locale.ENGLISH)

/**
 * كارت مساعد الـ AI في الشاشة الرئيسية:
 * ملخّص + مؤشّر صحة + أهم النتائج والتحذيرات + زر تحديث،
 * مع حالات تحميل/خطأ/غير مهيّأ واضحة.
 */
@Composable
fun AiDashboardCard(
    state: AiUiState,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFull: () -> Unit,
    onOpenChat: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val srt = LocalSrtColors.current
    val gradient = LocalAppGradients.current.header

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 1.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            // هيدر متدرّج
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.horizontalGradient(gradient))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("مساعد CoreWall الذكي", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(subtitleFor(state), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
                }
                Surface(
                    onClick = onOpenChat,
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.2f)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "المساعد الهندسي",
                        tint = Color.White, modifier = Modifier.padding(9.dp).size(20.dp))
                }
                Spacer(Modifier.width(8.dp))
                RefreshButton(loading = state is AiUiState.Loading, onClick = onRefresh)
            }

            Column(Modifier.padding(16.dp)) {
                when (state) {
                    AiUiState.NotConfigured -> NotConfigured(onOpenSettings)
                    AiUiState.Idle -> IdlePrompt(onRefresh)
                    AiUiState.Loading -> LoadingBody()
                    is AiUiState.Error -> ErrorBody(state, onOpenSettings)
                    is AiUiState.Ready -> ReadyBody(state, onOpenFull)
                }
            }
        }
    }
}

private fun subtitleFor(state: AiUiState): String = when (state) {
    AiUiState.NotConfigured -> "محتاج مفتاح API"
    AiUiState.Idle -> "جاهز للتحليل"
    AiUiState.Loading -> "بيحلّل بيانات الدور…"
    is AiUiState.Error -> "حصلت مشكلة"
    is AiUiState.Ready -> if (state.cached) "آخر تحليل · ${stamp.format(Date(state.generatedAt))}"
    else "اتحدّث ${stamp.format(Date(state.generatedAt))}"
}

@Composable
private fun RefreshButton(loading: Boolean, onClick: () -> Unit) {
    val spin = rememberInfiniteTransition(label = "ai-spin")
    val angle by spin.animateFloat(
        0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "ai-angle"
    )
    Surface(
        onClick = { if (!loading) onClick() },
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.2f)
    ) {
        Icon(
            Icons.Filled.Refresh,
            contentDescription = "تحديث التحليل",
            tint = Color.White,
            modifier = Modifier.padding(9.dp).size(20.dp).then(if (loading) Modifier.rotate(angle) else Modifier)
        )
    }
}

@Composable
private fun NotConfigured(onOpenSettings: () -> Unit) {
    val srt = LocalSrtColors.current
    Column {
        Text(
            "فعّل المساعد الذكي عشان يحلّل بيانات الدور: الفجوات، الفروق عن الدور السابق، " +
                "الفحوصات المعلّقة، ومطابقة عدّ الأسياخ.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Surface(
            onClick = onOpenSettings,
            shape = RoundedCornerShape(14.dp),
            color = srt.blueTint,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Settings, contentDescription = null, tint = srt.blue, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("أضف مفتاح OpenRouter", color = srt.blue, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "من غير مفتاح، التطبيق بيفضل أوفلاين بالكامل ومفيش أي بيانات بتخرج منه.",
            style = MaterialTheme.typography.labelSmall,
            color = srt.text3
        )
    }
}

@Composable
private fun IdlePrompt(onRefresh: () -> Unit) {
    val srt = LocalSrtColors.current
    Column {
        Text(
            "اضغط تحديث عشان المساعد يحلّل الدور الشغّال دلوقتي.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Surface(
            onClick = onRefresh,
            shape = RoundedCornerShape(14.dp),
            color = srt.blueTint,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.Center) {
                Text("حلّل الدور", color = srt.blue, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun LoadingBody() {
    Column {
        repeat(3) { i ->
            Box(
                Modifier
                    .fillMaxWidth(if (i == 2) 0.6f else 1f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Spacer(Modifier.height(10.dp))
        }
        Text(
            "بيقرأ التسليح، الفجوات، الفحوصات، وعدّ الأسياخ…",
            style = MaterialTheme.typography.labelSmall,
            color = LocalSrtColors.current.text3
        )
    }
}

@Composable
private fun ErrorBody(state: AiUiState.Error, onOpenSettings: () -> Unit) {
    val srt = LocalSrtColors.current
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(srt.red.copy(alpha = 0.10f))
                .padding(14.dp)
        ) {
            Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = srt.red, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("تعذّر التحليل", fontWeight = FontWeight.Bold, color = srt.red, style = MaterialTheme.typography.bodyMedium)
                Text(state.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        // لو فيه تحليل قديم، نفضل نعرضه بدل ما الشاشة تفضى
        state.previous?.let {
            Spacer(Modifier.height(12.dp))
            Text("آخر تحليل ناجح:", style = MaterialTheme.typography.labelSmall, color = srt.text3)
            Spacer(Modifier.height(6.dp))
            Text(it.analysis.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "راجع الإعدادات",
            style = MaterialTheme.typography.labelMedium,
            color = srt.blue,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).padding(4.dp)
        )
    }
}

@Composable
private fun ReadyBody(state: AiUiState.Ready, onOpenFull: () -> Unit) {
    val srt = LocalSrtColors.current
    val a = state.analysis
    val statusColor = when (a.status.uppercase()) {
        "GOOD" -> srt.green
        "CRITICAL" -> srt.red
        else -> srt.orange
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(58.dp).clip(CircleShape).background(statusColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Text("${a.healthScore}", style = MaterialTheme.typography.titleLarge, color = statusColor, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when (a.status.uppercase()) {
                        "GOOD" -> "الدور في حالة جيدة"
                        "CRITICAL" -> "يحتاج تدخل عاجل"
                        else -> "يحتاج متابعة"
                    },
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = statusColor
                )
                Text("مؤشّر صحة الدور", style = MaterialTheme.typography.labelSmall, color = srt.text3)
            }
        }

        if (a.summary.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(a.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }

        Section("أهم النتائج", a.findings.take(3), Icons.Filled.CheckCircle, srt.blue)
        Section("تحذيرات", a.warnings.take(3), Icons.Filled.WarningAmber, srt.orange)
        Section("توصيات", a.recommendations.take(3), Icons.Filled.Lightbulb, srt.green)

        Spacer(Modifier.height(12.dp))
        Surface(
            onClick = onOpenFull,
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.Center) {
                Text("عرض التحليل الكامل", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "الموديل: ${state.model}",
            style = MaterialTheme.typography.labelSmall,
            color = srt.text3
        )
    }
}

@Composable
private fun Section(title: String, items: List<AiItem>, icon: ImageVector, accent: Color) {
    if (items.isEmpty()) return
    Spacer(Modifier.height(14.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = accent)
    }
    Spacer(Modifier.height(6.dp))
    items.forEach { AiItemRow(it) }
}

@Composable
fun AiItemRow(item: AiItem) {
    val srt = LocalSrtColors.current
    val sev = AiSeverity.from(item.severity)
    val color = when (sev) {
        AiSeverity.CRITICAL, AiSeverity.HIGH -> srt.red
        AiSeverity.MEDIUM -> srt.orange
        AiSeverity.LOW -> srt.blue
        AiSeverity.INFO -> srt.text3
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Box(Modifier.padding(top = 6.dp).size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            if (item.detail.isNotBlank()) {
                Text(item.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (item.marks.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    item.marks.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
