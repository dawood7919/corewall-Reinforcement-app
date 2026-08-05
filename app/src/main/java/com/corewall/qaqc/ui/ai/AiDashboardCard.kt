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
import com.corewall.qaqc.ui.design.Radius
import com.corewall.qaqc.ui.design.Space

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
        shape = Radius.shapeXl,
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
                    .padding(horizontal = Space.lg, vertical = Space.lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(Space.md))
                Column(Modifier.weight(1f)) {
                    Text("مساعد CoreWall الذكي", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(subtitleFor(state), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
                }
                Surface(
                    onClick = onOpenChat,
                    shape = Radius.shapeMd,
                    color = Color.White.copy(alpha = 0.2f)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "المساعد الهندسي",
                        tint = Color.White, modifier = Modifier.padding(Space.sm).size(20.dp))
                }
                Spacer(Modifier.width(Space.sm))
                RefreshButton(loading = state is AiUiState.Loading, onClick = onRefresh)
            }

            Column(Modifier.padding(Space.lg)) {
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
        shape = Radius.shapeMd,
        color = Color.White.copy(alpha = 0.2f)
    ) {
        Icon(
            Icons.Filled.Refresh,
            contentDescription = "تحديث التحليل",
            tint = Color.White,
            modifier = Modifier.padding(Space.sm).size(20.dp).then(if (loading) Modifier.rotate(angle) else Modifier)
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
        Spacer(Modifier.height(Space.md))
        Surface(
            onClick = onOpenSettings,
            shape = Radius.shapeLg,
            color = srt.blueTint,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.padding(Space.lg),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Settings, contentDescription = null, tint = srt.blue, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Space.sm))
                Text("أضف مفتاح OpenRouter", color = srt.blue, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(Space.sm))
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
        Spacer(Modifier.height(Space.md))
        Surface(
            onClick = onRefresh,
            shape = Radius.shapeLg,
            color = srt.blueTint,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(Space.lg), horizontalArrangement = Arrangement.Center) {
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
                    .height(Space.lg)
                    .clip(Radius.shapeSm)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Spacer(Modifier.height(Space.md))
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
                .clip(Radius.shapeLg)
                .background(srt.red.copy(alpha = 0.10f))
                .padding(Space.lg)
        ) {
            Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = srt.red, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text("تعذّر التحليل", fontWeight = FontWeight.Bold, color = srt.red, style = MaterialTheme.typography.bodyMedium)
                Text(state.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        // لو فيه تحليل قديم، نفضل نعرضه بدل ما الشاشة تفضى
        state.previous?.let {
            Spacer(Modifier.height(Space.md))
            Text("آخر تحليل ناجح:", style = MaterialTheme.typography.labelSmall, color = srt.text3)
            Spacer(Modifier.height(Space.sm))
            Text(it.analysis.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(Space.md))
        Text(
            "راجع الإعدادات",
            style = MaterialTheme.typography.labelMedium,
            color = srt.blue,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clip(Radius.shapeSm).padding(Space.xs)
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
            Spacer(Modifier.width(Space.lg))
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
            Spacer(Modifier.height(Space.md))
            Text(a.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }

        Section("أهم النتائج", a.findings.take(3), Icons.Filled.CheckCircle, srt.blue)
        Section("تحذيرات", a.warnings.take(3), Icons.Filled.WarningAmber, srt.orange)
        Section("توصيات", a.recommendations.take(3), Icons.Filled.Lightbulb, srt.green)

        Spacer(Modifier.height(Space.md))
        Surface(
            onClick = onOpenFull,
            shape = Radius.shapeLg,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(Space.md), horizontalArrangement = Arrangement.Center) {
                Text("عرض التحليل الكامل", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(Space.sm))
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
    Spacer(Modifier.height(Space.lg))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(Space.sm))
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = accent)
    }
    Spacer(Modifier.height(Space.sm))
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
    Row(Modifier.fillMaxWidth().padding(vertical = Space.xs)) {
        Box(Modifier.padding(top = Space.sm).size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            if (item.detail.isNotBlank()) {
                Text(item.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (item.marks.isNotEmpty()) {
                Spacer(Modifier.height(Space.xs))
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
