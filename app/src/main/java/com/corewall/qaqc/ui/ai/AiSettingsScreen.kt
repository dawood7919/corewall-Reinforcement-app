package com.corewall.qaqc.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.ai.AiProviderId
import com.corewall.qaqc.ui.theme.LocalSrtColors
import com.corewall.qaqc.ui.theme.SrtCallout

/**
 * إعدادات المساعد الذكي: المزوّد + المفتاح + الموديل.
 * المفتاح بيتخزّن على الجهاز بس ومش بيتسجّل في أي نسخة احتياطية.
 */
@Composable
fun AiSettingsScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val srt = LocalSrtColors.current
    val cfg by vm.aiConfig.collectAsStateWithLifecycle()
    var showKey by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        SrtCallout(
            title = "خصوصية بياناتك",
            body = "لما تشغّل التحليل، ملخّص بيانات الدور (أكواد العناصر، التسليح، الحالات، " +
                "عناوين الملاحظات، وأرقام العمالة) بيتبعت لمزوّد الـ AI اللي انت مختاره. " +
                "من غير مفتاح، مفيش أي بيانات بتخرج من الجهاز خالص.",
            accent = srt.orange
        )

        Spacer(Modifier.height(20.dp))
        Text("المزوّد", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = srt.text3)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AiProviderId.entries.forEach { provider ->
                val selected = cfg.provider == provider
                Surface(
                    onClick = { vm.switchAiProvider(provider) },
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) srt.blueTint else MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (selected) srt.blue else MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                provider.label,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) srt.blue else MaterialTheme.colorScheme.onSurface
                            )
                            Text(provider.defaultModel, style = MaterialTheme.typography.labelSmall, color = srt.text3)
                        }
                        if (selected) Text("✓", color = srt.blue, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("مفتاح API", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = srt.text3)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = cfg.apiKey,
            onValueChange = { key -> vm.updateAiConfig { it.copy(apiKey = key.trim()) } },
            label = { Text("sk-or-v1-…") },
            singleLine = true,
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showKey = !showKey }) {
                    Icon(
                        if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (showKey) "إخفاء" else "إظهار"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        Text(
            when (cfg.provider) {
                AiProviderId.OPENROUTER -> "اعمل مفتاح من openrouter.ai/keys — مفتاح واحد بيديك موديلات كتير."
                AiProviderId.OPENAI -> "اعمل مفتاح من platform.openai.com/api-keys"
                AiProviderId.ANTHROPIC -> "اعمل مفتاح من console.anthropic.com"
                AiProviderId.GEMINI -> "اعمل مفتاح من aistudio.google.com/apikey"
            },
            style = MaterialTheme.typography.labelSmall,
            color = srt.text3
        )

        Spacer(Modifier.height(20.dp))
        Text("الموديل", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = srt.text3)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = cfg.model,
            onValueChange = { m -> vm.updateAiConfig { it.copy(model = m.trim()) } },
            label = { Text("اسم الموديل") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(20.dp))
        Text("عنوان الخدمة (Base URL)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = srt.text3)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = cfg.baseUrl,
            onValueChange = { u -> vm.updateAiConfig { it.copy(baseUrl = u.trim()) } },
            label = { Text("https://…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(20.dp))
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (cfg.isConfigured) srt.green.copy(alpha = 0.10f) else srt.orange.copy(alpha = 0.10f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (cfg.isConfigured) "✓ المساعد مفعّل — ارجع للرئيسية واضغط تحديث."
                else "المساعد متوقف — ضيف مفتاح عشان يشتغل.",
                Modifier.padding(14.dp),
                style = MaterialTheme.typography.bodySmall,
                color = if (cfg.isConfigured) srt.green else srt.orange,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}
