package com.corewall.qaqc.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.corewall.qaqc.data.db.PromptEntity
import com.corewall.qaqc.ui.design.CwButton
import com.corewall.qaqc.ui.design.CwButtonStyle
import com.corewall.qaqc.ui.design.CwCard
import com.corewall.qaqc.ui.design.CwCardStyle
import com.corewall.qaqc.ui.design.CwLeadingIcon
import com.corewall.qaqc.ui.design.CwStatusBadge
import com.corewall.qaqc.ui.design.CwText
import com.corewall.qaqc.ui.design.CwTone
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Radius
import com.corewall.qaqc.ui.design.Sizes
import com.corewall.qaqc.ui.design.Space
import java.io.File

/**
 * "تحلّل الملف ده بأنهي برومبت؟"
 *
 * بيظهر قبل التحليل عشان الاختيار يبقى قرار واعي. من غيره التطبيق بيقرا كل
 * مستند بنفس التعليمات العامة — وده اللي بيخلّي جدول الحديد يتحلّل كأنه
 * رسمة عادية والنتيجة تطلع غلط.
 *
 * [onPick] بياخد `null` للتحليل العام، أو رقم البرومبت المختار.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyzePromptSheet(
    file: File,
    prompts: List<PromptEntity>,
    onPick: (Long?) -> Unit,
    onManagePrompts: () -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalCwColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.surface,
        shape = Radius.sheet
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Space.lg)
        ) {
            Text(
                "تحليل الملف",
                style = MaterialTheme.typography.titleMedium,
                color = c.textPrimary
            )
            Spacer(Modifier.height(Space.xs))
            Text(
                file.name,
                style = CwText.codeSmall,
                color = c.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(Space.lg))

            LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = Sizes.sheetGridMax),
                verticalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                item(key = "default") {
                    PromptOption(
                        title = "التحليل العام",
                        detail = "استخراج قياسي — نوع المستند، الأكواد، الأرقام، الملخّص.",
                        badge = null,
                        onClick = { onPick(null) }
                    )
                }
                items(prompts, key = { it.id }) { p ->
                    PromptOption(
                        title = p.name,
                        detail = p.body,
                        badge = if (p.usageCount > 0) "${p.usageCount} مرة" else null,
                        onClick = { onPick(p.id) }
                    )
                }
                item(key = "manage") {
                    Spacer(Modifier.height(Space.xs))
                    CwButton(
                        if (prompts.isEmpty()) "اعمل برومبت لنوع المستند ده" else "إدارة البرومبتات",
                        onManagePrompts,
                        style = CwButtonStyle.Ghost,
                        icon = if (prompts.isEmpty()) Icons.Filled.Add else Icons.Filled.AutoAwesome,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(Modifier.height(Space.lg))
        }
    }
}

@Composable
private fun PromptOption(
    title: String,
    detail: String,
    badge: String?,
    onClick: () -> Unit
) {
    val c = LocalCwColors.current
    CwCard(
        style = CwCardStyle.Plain,
        onClick = onClick,
        contentPadding = PaddingValues(Space.md)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            CwLeadingIcon(Icons.Filled.AutoAwesome, tone = CwTone.Info)
            Spacer(Modifier.width(Space.sm))
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.sm)
                ) {
                    Text(title, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                    if (badge != null) CwStatusBadge(badge, CwTone.Neutral, compact = true)
                }
                Spacer(Modifier.height(Space.xxs))
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textTertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
