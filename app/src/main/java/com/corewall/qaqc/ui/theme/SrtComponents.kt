package com.corewall.qaqc.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import com.corewall.qaqc.ui.design.CwBanner
import com.corewall.qaqc.ui.design.CwCard
import com.corewall.qaqc.ui.design.CwDivider
import com.corewall.qaqc.ui.design.CwLeadingIcon
import com.corewall.qaqc.ui.design.CwListItem
import com.corewall.qaqc.ui.design.CwSectionHeader
import com.corewall.qaqc.ui.design.CwSegmented
import com.corewall.qaqc.ui.design.CwTone
import com.corewall.qaqc.ui.design.IconSize
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Radius
import com.corewall.qaqc.ui.design.Sizes
import com.corewall.qaqc.ui.design.Space
import com.corewall.qaqc.ui.design.Stroke

/**
 * مكوّنات SRT القديمة — بقت **أغلفة** فوق مكتبة التصميم الجديدة.
 *
 * ليه كده بدل ما نمسحها: لسه فيه شاشات كتير بتستخدمها. لو غيّرناها كلها في
 * لقطة واحدة كان البناء هيقع في عشرة أماكن مرّة واحدة. بس لو سبناها بتعريفها
 * القديم كانت هتفضل لغة بصرية تانية موازية — وده بالظبط المرض اللي بنعالجه.
 *
 * الحل: نفس الأسماء ونفس التوقيعات، والتنفيذ من [CwCard] و[CwListItem]
 * وإخواتهم. يعني الشاشات اللي لسه ما اتحوّلتش بقت بتاخد نفس المسافات
 * والأقطار والألوان المتفحوصة من غير ما نلمسها.
 */

@Composable
fun SrtCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(Space.lg),
    content: @Composable ColumnScope.() -> Unit
) = CwCard(modifier = modifier, contentPadding = padding, content = content)

@Composable
fun SrtSectionHeader(text: String, modifier: Modifier = Modifier) =
    CwSectionHeader(title = text, modifier = modifier)

/** قايمة مجمّعة — كارت واحد جواه صفوف. */
@Composable
fun SrtGroupedList(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) =
    CwCard(
        modifier = modifier,
        contentPadding = PaddingValues(vertical = Space.xs),
        content = content
    )

@Composable
fun SrtRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    showDivider: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val leading: (@Composable () -> Unit)? =
        if (icon != null) ({ CwLeadingIcon(icon, tone = CwTone.Info) }) else null
    Column(modifier.fillMaxWidth()) {
        CwListItem(
            title = title,
            subtitle = subtitle,
            leading = leading,
            trailing = trailing,
            onClick = onClick
        )
        if (showDivider) CwDivider()
    }
}

@Composable
fun SrtSegmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) = CwSegmented(
    options = options,
    selectedIndex = selected,
    label = { it },
    onSelect = onSelect,
    modifier = modifier
)

/**
 * شريحة حالة. الدايرة الملوّنة لوحدها كانت بتحمل المعنى — دلوقتي فيه نص
 * جنبها دايماً، والدايرة بقت تأكيد مش مصدر.
 */
@Composable
fun SrtStatusChip(label: String, color: Color, modifier: Modifier = Modifier) {
    val c = LocalCwColors.current
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.xs)
    ) {
        Box(
            Modifier
                .size(IconSize.sm)
                .clip(CircleShape)
                .background(color)
        )
        Text(label, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
    }
}

@Composable
fun SrtCallout(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    accent: Color = LocalCwColors.current.warning.fg,
    icon: ImageVector = Icons.Filled.WarningAmber
) {
    val c = LocalCwColors.current
    // بنترجم اللون لأقرب نبرة دلالية عشان الحاوية تفضل من النظام.
    val tone = when (accent) {
        c.danger.fg, c.danger.solid -> CwTone.Danger
        c.success.fg, c.success.solid -> CwTone.Success
        c.accent, c.info.fg -> CwTone.Info
        c.pending.fg -> CwTone.Pending
        else -> CwTone.Warning
    }
    CwBanner(title = title, detail = body, tone = tone, modifier = modifier)
}

/** عدّاد [−] رقم [+] — مساحة اللمس على كل زرار ≥ [Sizes.touch]. */
@Composable
fun SrtStepper(
    value: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    min: Int = 0,
    max: Int = 9999
) {
    val c = LocalCwColors.current
    Row(
        modifier
            .heightIn(min = Sizes.touch)
            .clip(Radius.shapeMd)
            .background(c.surfaceAlt)
            .padding(Space.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepBtn("−", "نقّص", value > min) { onChange(value - 1) }
        Text(
            "$value",
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            color = c.textPrimary,
            textAlign = TextAlign.Center
        )
        StepBtn("+", "زوّد", value < max) { onChange(value + 1) }
    }
}

@Composable
private fun StepBtn(symbol: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    val c = LocalCwColors.current
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = Radius.shapeSm,
        color = c.surface,
        border = androidx.compose.foundation.BorderStroke(Stroke.hair, c.outline),
        modifier = Modifier.size(Sizes.control)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                symbol,
                style = MaterialTheme.typography.titleLarge,
                color = if (enabled) c.accent else c.textTertiary
            )
        }
    }
}

/**
 * مفتاح تشغيل. كان مرسوم بالإيد بمقاسات ثابتة (51×31) ولون رمادي مكتوب
 * صراحةً — دلوقتي [Switch] بتاع Material بألوان النظام، فبيحترم إعدادات
 * إمكانية الوصول في الجهاز.
 */
@Composable
fun SrtToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalCwColors.current
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = c.onAccent,
            checkedTrackColor = c.accent,
            uncheckedThumbColor = c.surface,
            uncheckedTrackColor = c.surfaceAlt,
            uncheckedBorderColor = c.outline
        )
    )
}
