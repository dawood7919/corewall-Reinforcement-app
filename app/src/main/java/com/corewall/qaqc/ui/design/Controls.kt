package com.corewall.qaqc.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow

/**
 * عناصر التحكّم.
 *
 * أهم قاعدة هنا مفروضة بالـtypes مش بالتعليقات: [CwIconButton] بياخد
 * `contentDescription: String` **مش** `String?`. القياس القديم لقى ١١٠ أيقونة
 * بـ`contentDescription = null` من ١٦٥ — يعني ثلثين الأيقونات مش موجودة
 * لقارئ الشاشة، وكتير منها كان الأفورد الوحيد في الصف. المكوّن ده بيخلّي
 * نسيان الوصف خطأ compile مش خطأ مراجعة.
 */

enum class CwButtonStyle { Primary, Secondary, Ghost, Danger }

@Composable
fun CwButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: CwButtonStyle = CwButtonStyle.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    fillWidth: Boolean = false
) {
    val c = LocalCwColors.current
    val container: Color
    val content: Color
    val border: BorderStroke?
    when (style) {
        CwButtonStyle.Primary -> {
            container = c.accent; content = c.onAccent; border = null
        }
        CwButtonStyle.Secondary -> {
            container = c.surface; content = c.textPrimary
            border = BorderStroke(Stroke.hair, c.outline)
        }
        CwButtonStyle.Ghost -> {
            container = Color.Transparent; content = c.accent; border = null
        }
        CwButtonStyle.Danger -> {
            container = c.danger.solid; content = c.danger.onSolid; border = null
        }
    }
    val alpha = if (enabled) 1f else 0.4f
    val fill = if (container == Color.Transparent) container else container.copy(alpha = alpha)

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.then(if (fillWidth) Modifier.fillMaxWidth() else Modifier),
        shape = Radius.shapeMd,
        color = fill,
        border = border
    ) {
        Row(
            Modifier
                .heightIn(min = Sizes.touch)
                .padding(horizontal = Space.lg, vertical = Space.md),
            horizontalArrangement = Arrangement.spacedBy(Space.sm, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null, // النص جنبها هو التسمية
                    tint = content.copy(alpha = alpha),
                    modifier = Modifier.size(IconSize.md)
                )
            }
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = content.copy(alpha = alpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * زرار أيقونة. الأيقونة صغيرة، بس مساحة اللمس [Sizes.touch] دايماً.
 * [contentDescription] إجباري — مش قابل للـnull بقصد.
 */
@Composable
fun CwIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    active: Boolean = false,
    enabled: Boolean = true
) {
    val c = LocalCwColors.current
    val resolved = when {
        !enabled -> c.textTertiary.copy(alpha = 0.4f)
        active -> c.accent
        else -> tint ?: c.textSecondary
    }
    Box(
        modifier
            .size(Sizes.touch)
            .clip(Radius.pill)
            .then(
                if (active) Modifier.background(c.accentContainer) else Modifier
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = resolved,
            modifier = Modifier.size(IconSize.lg)
        )
    }
}

/** شيب اختيار واحد. */
@Composable
fun CwChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    count: Int? = null
) {
    val c = LocalCwColors.current
    val container by animateColorAsState(
        if (selected) c.accentContainer else c.surface,
        Motion.standard(), label = "chipBg"
    )
    val content = if (selected) c.onAccentContainer else c.textSecondary
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = Radius.pill,
        color = container,
        border = BorderStroke(Stroke.hair, if (selected) c.accent else c.outline)
    ) {
        Row(
            Modifier
                .heightIn(min = Sizes.touch)
                .padding(horizontal = Space.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.xs)
        ) {
            if (icon != null) {
                Icon(
                    icon, contentDescription = null, tint = content,
                    modifier = Modifier.size(IconSize.sm)
                )
            }
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = content,
                maxLines = 1
            )
            if (count != null) {
                Text(
                    "$count",
                    style = MaterialTheme.typography.labelSmall,
                    color = content.copy(alpha = 0.75f)
                )
            }
        }
    }
}

/** صف شيبات أفقي — بيتمرّر لوحده فمش بيكسر الشاشة لما العدد يكبر. */
@Composable
fun <T> CwChipRow(
    items: List<T>,
    selected: (T) -> Boolean,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    key: (T) -> Any = { it.toString() },
    count: (T) -> Int? = { null }
) {
    LazyRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Space.screen)
    ) {
        items(items, key = { key(it) }) { item ->
            CwChip(
                label = label(item),
                selected = selected(item),
                onClick = { onSelect(item) },
                count = count(item)
            )
        }
    }
}

/** مبدّل مقسّم — لاختيار واحد من ٢-٤ خيارات قصيرة. */
@Composable
fun <T> CwSegmented(
    options: List<T>,
    selectedIndex: Int,
    label: (T) -> String,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalCwColors.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(Radius.shapeMd)
            .background(c.surfaceAlt)
            .padding(Space.xs),
        horizontalArrangement = Arrangement.spacedBy(Space.xs)
    ) {
        options.forEachIndexed { i, option ->
            val active = i == selectedIndex
            val bg by animateColorAsState(
                if (active) c.surface else Color.Transparent,
                Motion.standard(), label = "segBg"
            )
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = Sizes.control)
                    .clip(Radius.shapeSm)
                    .background(bg)
                    .clickable(role = Role.Tab) { onSelect(i) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label(option),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) c.textPrimary else c.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = Space.sm)
                )
            }
        }
    }
}

/** صف بمفتاح تشغيل. الصف كله قابل للضغط — مش المفتاح بس. */
@Composable
fun CwSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    enabled: Boolean = true
) {
    val c = LocalCwColors.current
    CwListItem(
        title = title,
        subtitle = subtitle,
        leading = leading,
        modifier = modifier,
        onClick = if (enabled) ({ onCheckedChange(!checked) }) else null,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = if (enabled) onCheckedChange else null,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = c.onAccent,
                    checkedTrackColor = c.accent,
                    uncheckedThumbColor = c.surface,
                    uncheckedTrackColor = c.surfaceAlt,
                    uncheckedBorderColor = c.outline
                )
            )
        }
    )
}

/** حقل نص قياسي — تسمية ظاهرة دايماً، مش placeholder بس. */
@Composable
fun CwField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    helper: String? = null,
    error: String? = null,
    singleLine: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    minLines: Int = 1,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions =
        androidx.compose.foundation.text.KeyboardOptions.Default,
    /** لإخفاء المحتوى (مفاتيح الـAPI). null = عرض عادي. */
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation? = null
) {
    val c = LocalCwColors.current
    androidx.compose.foundation.layout.Column(modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = c.textSecondary,
            modifier = Modifier.padding(bottom = Space.xs, start = Space.xs)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = placeholder?.let {
                { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.textTertiary) }
            },
            singleLine = singleLine,
            minLines = minLines,
            isError = error != null,
            leadingIcon = leading,
            trailingIcon = trailing,
            shape = Radius.shapeMd,
            keyboardOptions = keyboardOptions,
            visualTransformation = visualTransformation
                ?: androidx.compose.ui.text.input.VisualTransformation.None,
            textStyle = MaterialTheme.typography.bodyLarge,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = c.surface,
                unfocusedContainerColor = c.surface,
                errorContainerColor = c.surface,
                focusedIndicatorColor = c.accent,
                unfocusedIndicatorColor = c.outline,
                errorIndicatorColor = c.danger.fg,
                focusedTextColor = c.textPrimary,
                unfocusedTextColor = c.textPrimary,
                cursorColor = c.accent
            )
        )
        // الخطأ جنب الحقل نفسه — مش مجمّع فوق الشاشة.
        val note = error ?: helper
        if (note != null) {
            Spacer(Modifier.height(Space.xs))
            Text(
                note,
                style = MaterialTheme.typography.labelMedium,
                color = if (error != null) c.danger.fg else c.textTertiary,
                modifier = Modifier.padding(start = Space.xs)
            )
        }
    }
}

/** شريط أفعال سفلي ثابت داخل شاشة — للفعل الأساسي في مهمة. */
@Composable
fun CwActionBar(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    val c = LocalCwColors.current
    Surface(color = c.surface, modifier = modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(Stroke.hair)
                    .background(c.divider)
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(Space.lg),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
    }
}
