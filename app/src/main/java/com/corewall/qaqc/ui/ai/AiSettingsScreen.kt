package com.corewall.qaqc.ui.ai

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.ai.AiProviderId
import com.corewall.qaqc.data.SavedKey
import com.corewall.qaqc.ui.design.CwBanner
import com.corewall.qaqc.ui.design.CwButton
import com.corewall.qaqc.ui.design.CwButtonStyle
import com.corewall.qaqc.ui.design.CwCard
import com.corewall.qaqc.ui.design.CwCardStyle
import com.corewall.qaqc.ui.design.CwField
import com.corewall.qaqc.ui.design.CwIconButton
import com.corewall.qaqc.ui.design.CwLeadingIcon
import com.corewall.qaqc.ui.design.CwListItem
import com.corewall.qaqc.ui.design.CwSectionHeader
import com.corewall.qaqc.ui.design.CwStatusBadge
import com.corewall.qaqc.ui.design.CwText
import com.corewall.qaqc.ui.design.CwTone
import com.corewall.qaqc.ui.design.IconSize
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Radius
import com.corewall.qaqc.ui.design.Space
import com.corewall.qaqc.ui.nav.Dest

/**
 * إعدادات المساعد — المزوّد والمفتاح والموديل، وخزنة المفاتيح.
 *
 * المفاتيح بتتخزّن على الجهاز بس، في ملف تفضيلات منفصل مستبعد من النسخ
 * الاحتياطي ومن نقل الجهاز. ومن غير مفتاح، التطبيق **مش** بيعمل أي نداء
 * شبكة خالص.
 *
 * الخزنة بتتملي لوحدها: أول ما أي طلب ينجح، الإعداد اللي اشتغل بيتحفظ.
 * يعني اللي في القايمة دي مفاتيح **اشتغلت فعلاً**، مش أي نص اتكتب في
 * الخانة. وتبديل المزوّد بيرجّع مفتاح المزوّد ده — قبل كده كان المفتاح
 * القديم بيفضل مكانه ويترفض، والمستخدم يفتكر إن مفتاحه باظ.
 */
@Composable
fun AiSettingsScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val c = LocalCwColors.current
    val context = LocalContext.current
    val cfg by vm.aiConfig.collectAsStateWithLifecycle()
    val keys by vm.savedKeys.collectAsStateWithLifecycle()
    val testing by vm.testingKey.collectAsStateWithLifecycle()

    var showKey by rememberSaveable { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf<String?>(null) }
    var renameDraft by rememberSaveable { mutableStateOf("") }
    var confirmDelete by rememberSaveable { mutableStateOf<String?>(null) }

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

        // ── الخزنة أول حاجة: أسرع طريق لمستخدم راجع
        if (keys.isNotEmpty()) {
            item(key = "keys-header") { CwSectionHeader("مفاتيح محفوظة", count = keys.size) }
            items(keys, key = { it.id }) { k ->
                SavedKeyCard(
                    key = k,
                    active = k.provider == cfg.provider.name && k.apiKey == cfg.apiKey,
                    onUse = { vm.useSavedKey(k.id) },
                    onRename = { renaming = k.id; renameDraft = k.label },
                    onDelete = { confirmDelete = k.id }
                )
            }
        }

        item(key = "provider-header") { CwSectionHeader("المزوّد") }
        items(AiProviderId.entries.size, key = { AiProviderId.entries[it].name }) { i ->
            val provider = AiProviderId.entries[i]
            ProviderCard(
                label = provider.label,
                // المحلي مالوش موديل افتراضي بالاسم — بيتوصف بدل ما
                // يفضل السطر فاضي تحت اسمه.
                model = provider.defaultModel.ifBlank { "ملف على الجهاز · بيشتغل من غير إنترنت" },
                selected = cfg.provider == provider,
                savedCount = keys.count { it.provider == provider.name },
                onClick = { vm.switchAiProvider(provider) }
            )
        }

        // المحلي مالوش مفتاح ولا عنوان ولا موديل بالاسم — عنده ملف.
        // فبيتعرض بكارت مختلف بدل ما يفضّي خانات مالهاش معنى عنده.
        if (cfg.provider == AiProviderId.LOCAL) {
            item(key = "local-header") { CwSectionHeader("ملف الموديل") }
            item(key = "local") { LocalModelCard(vm, cfg) }
        } else {

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
                    placeholder = cfg.provider.defaultModel,
                    helper = modelHelp(cfg.provider)
                )
                Spacer(Modifier.height(Space.md))
                CwField(
                    value = cfg.imageModel,
                    onValueChange = { m -> vm.updateAiConfig { it.copy(imageModel = m.trim()) } },
                    label = "موديل الصور (اختياري)",
                    placeholder = imageModelHint(cfg.provider),
                    helper = imageModelHelp(cfg.provider)
                )
                Spacer(Modifier.height(Space.md))
                CwField(
                    value = cfg.baseUrl,
                    onValueChange = { u -> vm.updateAiConfig { it.copy(baseUrl = u.trim()) } },
                    label = "عنوان الخدمة",
                    placeholder = "https://…",
                    helper = "سيبه زي ما هو إلا لو بتستخدم بروكسي بتاعك."
                )
                Spacer(Modifier.height(Space.md))
                CwButton(
                    if (testing) "بيجرّب…" else "اختبر واحفظ المفتاح",
                    { vm.testAndSaveKey { msg -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show() } },
                    icon = Icons.Filled.Key,
                    enabled = !testing && cfg.isConfigured
                )
                Spacer(Modifier.height(Space.xs))
                Text(
                    "الاختبار بيبعت أصغر طلب ممكن. لو نجح، المفتاح بيتحفظ في الخزنة " +
                        "فوق وتقدر ترجعله في أي وقت من غير ما تكتبه تاني.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textTertiary
                )
            }
        }
        }

        item(key = "prompts") {
            CwCard(contentPadding = PaddingValues(vertical = Space.xs)) {
                CwListItem(
                    title = "مكتبة البرومبت",
                    subtitle = "تعليمات تحليل لكل نوع مستند — بتتختار وقت تحليل الملف",
                    leading = { CwLeadingIcon(Icons.Filled.Description, tone = CwTone.Info) },
                    onClick = { vm.go(Dest.Prompts) }
                )
            }
        }

        item(key = "privacy") {
            CwBanner(
                title = "إيه اللي بيتبعت؟",
                detail = "لما تشغّل التحليل، ملخّص بيانات الدور (أكواد العناصر، التسليح، " +
                    "الحالات، عناوين الملاحظات، وأرقام العمالة) بيروح لمزوّد الـAI اللي " +
                    "انت مختاره. المفاتيح نفسها بتفضل على الجهاز — مستبعدة من النسخ " +
                    "الاحتياطي السحابي ومن نقل الجهاز.",
                tone = CwTone.Info
            )
        }
    }

    // ── إعادة التسمية
    val renameId = renaming
    if (renameId != null) {
        AlertDialog(
            onDismissRequest = { renaming = null },
            shape = Radius.shapeLg,
            containerColor = c.surface,
            title = { Text("اسم المفتاح", color = c.textPrimary) },
            text = {
                CwField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it },
                    label = "الاسم",
                    placeholder = "مفتاح الشغل"
                )
            },
            confirmButton = {
                CwButton("حفظ", { vm.renameSavedKey(renameId, renameDraft); renaming = null })
            },
            dismissButton = { CwButton("رجوع", { renaming = null }, style = CwButtonStyle.Ghost) }
        )
    }

    // ── الحذف
    val deleteId = confirmDelete
    if (deleteId != null) {
        val label = keys.firstOrNull { it.id == deleteId }?.label.orEmpty()
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            shape = Radius.shapeLg,
            containerColor = c.surface,
            title = { Text("تمسح \"$label\"؟", color = c.textPrimary) },
            text = {
                Text(
                    "المفتاح هيتشال من الجهاز خالص. لو هو الشغّال دلوقتي، المساعد " +
                        "هيقف لحد ما تحطّ مفتاح تاني.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textSecondary
                )
            },
            confirmButton = {
                CwButton("امسح", { vm.deleteSavedKey(deleteId); confirmDelete = null },
                    style = CwButtonStyle.Danger)
            },
            dismissButton = { CwButton("رجوع", { confirmDelete = null }, style = CwButtonStyle.Ghost) }
        )
    }
}

@Composable
private fun SavedKeyCard(
    key: SavedKey,
    active: Boolean,
    onUse: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val c = LocalCwColors.current
    CwCard(
        style = if (active) CwCardStyle.Accent else CwCardStyle.Plain,
        accent = c.accent,
        onClick = if (active) null else onUse,
        contentPadding = PaddingValues(Space.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.sm)
                ) {
                    Text(
                        key.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (active) c.accent else c.textPrimary
                    )
                    if (active) CwStatusBadge("شغّال", CwTone.Success, compact = true)
                }
                Text(
                    "${key.masked} · ${key.model}",
                    style = CwText.codeSmall,
                    color = c.textTertiary
                )
            }
            CwIconButton(Icons.Filled.Edit, "غيّر اسم ${key.label}", onRename)
            CwIconButton(Icons.Filled.Delete, "امسح ${key.label}", onDelete, tint = c.danger.fg)
        }
    }
}

/**
 * كارت الموديل المحلي.
 *
 * الملف بيتنسخ لمجلد التطبيق عن قصد مش بيتقرا من مكانه: الإذن اللي
 * منتقي الملفات بيدّيه مؤقت وبيروح مع إعادة التشغيل، والمكتبة الأصلية
 * محتاجة **مسار حقيقي** مش `content://` — فقراءة من المكان الأصلي كانت
 * هتشتغل مرة وتقع بعدها.
 */
@Composable
private fun LocalModelCard(vm: MainViewModel, cfg: com.corewall.qaqc.ai.AiConfig) {
    val c = LocalCwColors.current
    val context = LocalContext.current
    var copying by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            copying = true
            vm.importLocalModel(uri) { copying = false }
        }
    }

    val path = cfg.localModelPath
    val ready = com.corewall.qaqc.ai.local.LocalLlm.isReady(path)
    val sizeMb = remember(path, ready) {
        if (!ready) 0L else runCatching { java.io.File(path).length() / (1024 * 1024) }
            .getOrDefault(0L)
    }

    CwCard {
        Text(
            when {
                copying -> "بينسخ الملف جوّه التطبيق…"
                ready -> "جاهز · ${java.io.File(path).name} · $sizeMb ميجا"
                path.isNotBlank() -> "الملف المختار مش موجود — اختاره تاني"
                else -> "مفيش موديل متحدّد"
            },
            style = MaterialTheme.typography.titleSmall,
            color = when {
                ready -> c.success.fg
                copying -> c.textPrimary
                else -> c.warning.fg
            }
        )
        Spacer(Modifier.height(Space.md))

        // الأنواع مفتوحة: منتقي الملفات في أندرويد مابيعرفش `.litertlm`،
        // وفلترة بنوع MIME كانت هتخفي الملف اللي المستخدم لسه نزّله.
        CwButton(
            if (ready) "غيّر الملف" else "اختار ملف الموديل",
            { picker.launch(arrayOf("*/*")) },
            enabled = !copying
        )

        if (ready) {
            Spacer(Modifier.height(Space.sm))
            CwButton(
                "شيل الموديل",
                { vm.clearLocalModel() },
                style = CwButtonStyle.Ghost,
                enabled = !copying
            )
        }

        Spacer(Modifier.height(Space.md))
        Text(
            "الموديل بيشتغل على الجهاز — من غير إنترنت ومن غير ما أي بيانات تخرج. " +
                "بس هو أصغر بكتير من الموديلات السحابية، فـ**الأدوات وتحليل المستندات " +
                "وتوليد الصور مش شغّالين عليه**، والإجابات أقصر وأضعف. " +
                "استخدمه لما الشبكة تقطع في الموقع، وارجع للسحابي لما تلاقي شبكة.",
            style = MaterialTheme.typography.bodySmall,
            color = c.textTertiary
        )
        Spacer(Modifier.height(Space.sm))
        Text(
            "الملف لازم يكون بصيغة .litertlm. نزّله من litert-community على " +
                "Hugging Face، وحطّه في التنزيلات، وبعدين اختاره من هنا. " +
                "أول تشغيل بياخد لحد عشر ثواني عشان الموديل بيتحمّل في الذاكرة.",
            style = CwText.codeSmall,
            color = c.textTertiary
        )
    }
}

private fun keyHelp(provider: AiProviderId): String = when (provider) {
    AiProviderId.OPENROUTER -> "اعمل مفتاح من openrouter.ai/keys — مفتاح واحد بيديك موديلات كتير."
    AiProviderId.TOKENROUTER ->
        "اعمل مفتاح من tokenrouter.io — بتوجّه كل طلب لأنسب مزوّد لوحدها. " +
            "سيب الموديل auto:balance عشان توازن بين التكلفة والجودة."
    AiProviderId.OPENAI -> "اعمل مفتاح من platform.openai.com/api-keys"
    AiProviderId.ANTHROPIC -> "اعمل مفتاح من console.anthropic.com"
    AiProviderId.GEMINI ->
        "اعمل مفتاح مجاني من aistudio.google.com/apikey — اضغط \"Create API key\" " +
            "واختار مشروع. المفتاح بيبدأ بـAIza."
    AiProviderId.LOCAL ->
        "الموديل المحلي مالوش مفتاح ولا بيتصل بحاجة — الملف اللي على الجهاز هو كل حاجة."
}

/**
 * اقتراح موديل صور لكل مزوّد.
 *
 * مجرد نص إرشادي مش قيمة افتراضية: الموديلات دي بتتحاسب **بالصورة**،
 * وتشغيلها لوحدها معناه إن المستخدم يدفع من غير ما يطلب. فاضي = مقفول.
 */
private fun imageModelHint(provider: AiProviderId): String = when (provider) {
    AiProviderId.OPENAI -> "gpt-image-1"
    AiProviderId.GEMINI -> "gemini-2.5-flash-image"
    AiProviderId.OPENROUTER -> "google/gemini-2.5-flash-image"
    AiProviderId.TOKENROUTER -> "اسم موديل صور من الخدمة"
    AiProviderId.ANTHROPIC -> "مش متاح"
    AiProviderId.LOCAL -> "مش متاح"
}

private fun imageModelHelp(provider: AiProviderId): String = when (provider) {
    AiProviderId.ANTHROPIC ->
        "Anthropic مابتولّدش صور. عشان الميزة دي تشتغل اختار OpenAI أو Gemini أو OpenRouter."
    AiProviderId.LOCAL ->
        "الموديل المحلي بيكتب نص بس — الصور محتاجة مزوّد سحابي."
    else ->
        "سيبه فاضي = التوليد مقفول. لما تحطّه، تقدر تقول للمساعد \"اعملي صورة\" — " +
            "هو بيكتب الوصف من الأرقام الحقيقية، والموديل ده بيرسمها."
}

private fun modelHelp(provider: AiProviderId): String = when (provider) {
    // الـPDF بيتبعت صور، فالموديل لازم يشوف — ده أشهر سبب لتحليل غلط.
    AiProviderId.GEMINI ->
        "gemini-2.5-flash سريع ورخيص وبيشوف الصور، وgemini-2.5-pro أدق في " +
            "الجداول المزحومة. الاتنين بيقروا الـPDF."
    else -> "لازم يكون موديل بيشوف الصور (vision) عشان يقدر يحلّل الـPDF والصور."
}

@Composable
private fun ProviderCard(
    label: String,
    model: String,
    selected: Boolean,
    savedCount: Int,
    onClick: () -> Unit
) {
    val c = LocalCwColors.current
    CwCard(
        style = if (selected) CwCardStyle.Accent else CwCardStyle.Plain,
        accent = c.accent,
        onClick = onClick,
        contentPadding = PaddingValues(Space.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.sm)
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (selected) c.accent else c.textPrimary
                    )
                    if (savedCount > 0) {
                        CwStatusBadge("$savedCount مفتاح محفوظ", CwTone.Neutral, compact = true)
                    }
                }
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
