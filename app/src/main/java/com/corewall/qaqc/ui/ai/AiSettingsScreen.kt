package com.corewall.qaqc.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.ai.AiProviderId
import com.corewall.qaqc.ui.design.CwBanner
import com.corewall.qaqc.ui.design.CwCard
import com.corewall.qaqc.ui.design.CwCardStyle
import com.corewall.qaqc.ui.design.CwField
import com.corewall.qaqc.ui.design.CwIconButton
import com.corewall.qaqc.ui.design.CwSectionHeader
import com.corewall.qaqc.ui.design.CwText
import com.corewall.qaqc.ui.design.CwTone
import com.corewall.qaqc.ui.design.IconSize
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Space
import com.corewall.qaqc.ui.design.semantic

/**
 * إعدادات المساعد — المزوّد والمفتاح والموديل.
 *
 * المفتاح بيتخزّن على الجهاز بس، في ملف تفضيلات منفصل مستبعد من النسخ
 * الاحتياطي ومن نقل الجهاز. ومن غير مفتاح، التطبيق **مش** بيعمل أي نداء
 * شبكة خالص.
 */
@Composable
fun AiSettingsScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val c = LocalCwColors.current
    val cfg by vm.aiConfig.collectAsStateWithLifecycle()
    var showKey by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Space.screen, end = Space.screen,
            top = Space.md, bottom = Space.bottomInset
        ),
        verticalArrangement = Arrangement.spacedBy(Space.stack)
    ) {
        item(key = "status") {
            CwBanner(
                title = if (cfg.isConfigured) "المساعد شغّال" else "المساعد متوقّف",
                detail = if (cfg.isConfigured) "فيه مفتاح متسجّل — تقدر تشغّل التحليل والمحادثة."
                else "ضيف مفتاح عشان يشتغل. من غيره مفيش أي بيانات بتخرج من الجهاز.",
                tone = if (cfg.isConfigured) CwTone.Success else CwTone.Warning
            )
        }

        item(key = "privacy") {
            CwBanner(
                title = "إيه اللي بيتبعت؟",
                detail = "لما تشغّل التحليل، ملخّص بيانات الدور (أكواد العناصر، التسليح، " +
                    "الحالات، عناوين الملاحظات، وأرقام العمالة) بيروح لمزوّد الـAI اللي " +
                    "انت مختاره. من غير مفتاح، مفيش أي بيانات بتخرج من الجهاز خالص.",
                tone = CwTone.Info
            )
        }

        item(key = "provider-header") { CwSectionHeader("المزوّد") }
        items(AiProviderId.entries.size, key = { AiProviderId.entries[it].name }) { i ->
            val provider = AiProviderId.entries[i]
            ProviderCard(
                label = provider.label,
                model = provider.defaultModel,
                selected = cfg.provider == provider,
                onClick = { vm.switchAiProvider(provider) }
            )
        }

        item(key = "key-header") { CwSectionHeader("المفتاح والموديل") }
        item(key = "key") {
            CwCard {
                CwField(
                    value = cfg.apiKey,
                    onValueChange = { key -> vm.updateAiConfig { it.copy(apiKey = key.trim()) } },
                    label = "مفتاح API",
                    placeholder = "sk-…",
                    helper = keyHelp(cfg.provider),
                    visualTransformation = if (showKey) null else PasswordVisualTransformation(),
                    trailing = {
                        CwIconButton(
                            icon = if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (showKey) "اخفي المفتاح" else "اعرض المفتاح",
                            onClick = { showKey = !showKey }
                        )
                    }
                )
                Spacer(Modifier.height(Space.md))
                CwField(
                    value = cfg.model,
                    onValueChange = { m -> vm.updateAiConfig { it.copy(model = m.trim()) } },
                    label = "الموديل",
                    placeholder = cfg.provider.defaultModel
                )
                Spacer(Modifier.height(Space.md))
                CwField(
                    value = cfg.baseUrl,
                    onValueChange = { u -> vm.updateAiConfig { it.copy(baseUrl = u.trim()) } },
                    label = "عنوان الخدمة",
                    placeholder = "https://…",
                    helper = "سيبه زي ما هو إلا لو بتستخدم بروكسي بتاعك."
                )
            }
        }
    }
}

private fun keyHelp(provider: AiProviderId): String = when (provider) {
    AiProviderId.OPENROUTER -> "اعمل مفتاح من openrouter.ai/keys — مفتاح واحد بيديك موديلات كتير."
    AiProviderId.TOKENROUTER ->
        "اعمل مفتاح من tokenrouter.io — بتوجّه كل طلب لأنسب مزوّد لوحدها. " +
            "سيب الموديل auto:balance عشان توازن بين التكلفة والجودة."
    AiProviderId.OPENAI -> "اعمل مفتاح من platform.openai.com/api-keys"
    AiProviderId.ANTHROPIC -> "اعمل مفتاح من console.anthropic.com"
    AiProviderId.GEMINI -> "اعمل مفتاح من aistudio.google.com/apikey"
}

@Composable
private fun ProviderCard(label: String, model: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalCwColors.current
    CwCard(
        style = if (selected) CwCardStyle.Accent else CwCardStyle.Plain,
        accent = c.accent,
        onClick = onClick,
        contentPadding = PaddingValues(Space.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) c.accent else c.textPrimary
                )
                Text(model, style = CwText.codeSmall, color = c.textTertiary)
            }
            // "مختار" بأيقونة ونص — مش علامة ✓ نصّية بلون لوحدها.
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "المزوّد المختار",
                    tint = c.accent,
                    modifier = Modifier.size(IconSize.md)
                )
            }
        }
    }
}
