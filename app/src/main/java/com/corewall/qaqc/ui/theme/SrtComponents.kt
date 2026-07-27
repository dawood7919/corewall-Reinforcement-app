package com.corewall.qaqc.ui.theme

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** كارت SRT قياسي: أبيض، radius 18، حدود رفيعة، ظل خفيف. */
@Composable
fun SrtCard(
    modifier: Modifier = Modifier,
    padding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(16.dp),
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 1.dp
    ) {
        Column(Modifier.padding(padding), content = content)
    }
}

/** عنوان قسم صغير (13/600) بلون text-3. */
@Composable
fun SrtSectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = LocalSrtColors.current.text3
    )
}

/** قائمة مجمّعة بستايل iOS (inset grouped): كارت واحد + rows + divider inset. */
@Composable
fun SrtGroupedList(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(content = content)
    }
}

/** صف داخل SrtGroupedList: أيقونة اختيارية يمين + عنوان + trailing. */
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
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Box(
                    Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(iconTint.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) { Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp)) }
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (trailing != null) { Spacer(Modifier.width(8.dp)); trailing() }
        }
        if (showDivider) {
            androidx.compose.material3.HorizontalDivider(
                Modifier.padding(start = if (icon != null) 58.dp else 16.dp),
                color = LocalSrtColors.current.divider
            )
        }
    }
}

/** صف من segmented pills. Active أزرق-tint، Inactive رمادي. */
@Composable
fun SrtSegmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val srt = LocalSrtColors.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, label ->
            val active = i == selected
            Surface(
                onClick = { onSelect(i) },
                shape = RoundedCornerShape(999.dp),
                color = if (active) srt.blueTint else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (active) srt.blue else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    label,
                    Modifier.fillMaxWidth().padding(vertical = 9.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

/** شريحة legend للحالة: دائرة 8px + نص 12px. */
@Composable
fun SrtStatusChip(label: String, color: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** صندوق تنبيه/كولاوت — orange افتراضي، أو red للتنبيهات الحرجة. */
@Composable
fun SrtCallout(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    accent: Color = LocalSrtColors.current.orange,
    icon: ImageVector = Icons.Filled.WarningAmber
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = 0.10f))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = accent)
            Spacer(Modifier.height(2.dp))
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** Stepper: [−] رقم [+] — container رمادي، أزرار زرقا على أبيض. */
@Composable
fun SrtStepper(
    value: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    min: Int = 0,
    max: Int = 9999
) {
    val srt = LocalSrtColors.current
    Row(
        modifier
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepBtn("−", srt.blue) { if (value > min) onChange(value - 1) }
        Text(
            "$value",
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        StepBtn("+", srt.blue) { if (value < max) onChange(value + 1) }
    }
}

@Composable
private fun StepBtn(symbol: String, tint: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.padding(6.dp).size(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(symbol, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = tint)
        }
    }
}

/** Toggle بستايل iOS — 51×31، ON أزرق. */
@Composable
fun SrtToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val srt = LocalSrtColors.current
    val track by animateColorAsState(if (checked) srt.blue else Color(0xFFD1D1D6), label = "track")
    val knobStart by animateDpAsState(if (checked) 22.dp else 2.dp, label = "knob")
    Box(
        modifier
            .size(width = 51.dp, height = 31.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(track)
            .clickable { onCheckedChange(!checked) }
    ) {
        Box(
            Modifier
                .padding(start = knobStart, top = 2.dp)
                .size(27.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}
