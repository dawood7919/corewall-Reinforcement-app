package com.corewall.qaqc.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * مكتبة المكوّنات — لغة بصرية **واحدة** لكل التطبيق.
 *
 * القياس القديم لقى ٦ لغات كروت مختلفة ومالهاش علاقة ببعض (SrtRow /
 * MetricCard / FileGridCard / FileListRow / SuggestionRow / HeadlineCard).
 * كل واحدة ليها حشو ونصف قطر وحدود مختلفة، فالتطبيق كان بيتقري "مركّب".
 * الملف ده بيستبدلهم كلهم.
 *
 * قواعد ملزمة:
 * - كل المسافات من [Space]، وكل الأقطار من [Radius]، وكل الأيقونات من [IconSize].
 * - كل عنصر تفاعلي مساحة لمسه ≥ [Sizes.touch].
 * - الحالة عمرها ما بتتشال باللون لوحده — دايماً أيقونة + نص كمان.
 */

// ══════════════════════════════════════════════════════════ الحاويات

enum class CwCardStyle {
    /** الافتراضي — سطح بحدّ شعرة. الظل ممنوع في المحتوى العادي. */
    Plain,

    /** سطح غاطس — للحقول والمناطق الداخلية جوّه كارت تاني. */
    Inset,

    /** كارت عليه شريط جانبي ملوّن — للحاجة اللي محتاجة لفت نظر. */
    Accent
}

/**
 * الحاوية الوحيدة في التطبيق. أي "كارت" في أي شاشة بيتبني من هنا.
 */
@Composable
fun CwCard(
    modifier: Modifier = Modifier,
    style: CwCardStyle = CwCardStyle.Plain,
    accent: Color? = null,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(Space.xl),
    content: @Composable ColumnScope.() -> Unit
) {
    val c = LocalCwColors.current
    val container = when (style) {
        CwCardStyle.Plain, CwCardStyle.Accent -> c.surface
        CwCardStyle.Inset -> c.surfaceAlt
    }
    val border = if (style == CwCardStyle.Inset) null else BorderStroke(Stroke.hair, c.outline.copy(alpha = 0.78f))
    val edge = accent.takeIf { style == CwCardStyle.Accent }

    // الشريط الجانبي بيترسم بدل ما يكون Box بـfillMaxHeight — عشان الكارت
    // ممكن يكون جوّه عمود قابل للتمرير (ارتفاع غير محدود) والرسم بيشتغل هناك.
    val edgeModifier = if (edge != null) {
        Modifier.drawBehind {
            val w = Sizes.accentEdge.toPx()
            val x = if (layoutDirection == LayoutDirection.Rtl) size.width - w else 0f
            drawRect(color = edge, topLeft = Offset(x, 0f), size = Size(w, size.height))
        }
    } else Modifier

    val body: @Composable () -> Unit = {
        Column(
            Modifier
                .fillMaxWidth()
                .then(edgeModifier)
                .padding(contentPadding),
            content = content
        )
    }

    if (onClick != null) {
        // ردّ فعل فوري تحت الإصبع: انكماش بسيط + ارتفاع.
        //
        // الكارت هو الوحدة اللي المستخدم بيضغط عليها في نص التطبيق
        // (عنصر، ملف، مهمة، ملاحظة). من غير ردّ فعل، الفجوة بين اللمسة
        // وفتح الشاشة بتتقري كـ"بطء" حتى لو الفتح نفسه سريع.
        val interaction = remember { MutableInteractionSource() }
        val press by rememberPressScale(interaction)
        val elevation by animatedElevation(interaction, Elevation.flat, Elevation.raised)

        Surface(
            onClick = onClick,
            modifier = modifier
                .fillMaxWidth()
                .scale(press),
            shape = Radius.shapeXl,
            color = container,
            border = border,
            shadowElevation = elevation,
            interactionSource = interaction,
            content = body
        )
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = Radius.shapeXl,
            color = container,
            border = border,
            content = body
        )
    }
}

/**
 * عنوان قسم. بيدي الشاشة تسلسل هرمي — وده بالظبط اللي كان ناقص في لوحة
 * التحكّم القديمة: كثافة من غير ترتيب أولويات.
 */
@Composable
fun CwSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    action: (@Composable () -> Unit)? = null
) {
    val c = LocalCwColors.current
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Space.xs, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm)
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
        if (count != null) CwCountPill(count)
        Spacer(Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
fun CwCountPill(count: Int, modifier: Modifier = Modifier, tone: CwTone = CwTone.Neutral) {
    val s = tone.semantic()
    Surface(shape = Radius.pill, color = s.container, modifier = modifier) {
        Text(
            "$count",
            style = MaterialTheme.typography.labelSmall,
            color = s.onContainer,
            modifier = Modifier.padding(horizontal = Space.sm, vertical = Space.xxs)
        )
    }
}

@Composable
fun CwDivider(modifier: Modifier = Modifier, inset: Boolean = true) {
    val c = LocalCwColors.current
    Box(
        modifier
            .fillMaxWidth()
            .padding(start = if (inset) Space.lg else 0.dp)
            .height(Stroke.hair)
            .background(c.divider)
    )
}

// ══════════════════════════════════════════════════════════ الحالة

/** نبرة دلالية واحدة — بتختار الأيقونة واللون مع بعض فمستحيل يتفصلوا. */
/**
 * بيعزل نص لاتيني جوّه سطر عربي.
 *
 * من غير العزل الوقت بيتقلب: `10:13 PM` بيتعرض `PM 10:13` لأن خوارزمية
 * الاتجاهين بتعتبر `PM` جزء من السطر العربي وترميه على الشمال. الرقم
 * والتاريخ بيتأثروا كمان لما يبقوا جنب علامات محايدة زي `·`.
 *
 * المحرفين دول (U+2066 عزل يسار-يمين، U+2069 نهاية العزل) بيقولوا للنظام
 * "المقطع ده اتجاهه ثابت"، من غير ما يأثّروا على باقي السطر.
 */
fun ltrIsolate(text: String): String = "\u2066$text\u2069"

enum class CwTone { Success, Warning, Danger, Info, Pending, Neutral }

@Composable
fun CwTone.semantic(): CwSemantic {
    val c = LocalCwColors.current
    return when (this) {
        CwTone.Success -> c.success
        CwTone.Warning -> c.warning
        CwTone.Danger -> c.danger
        CwTone.Info -> c.info
        CwTone.Pending -> c.pending
        CwTone.Neutral -> c.neutral
    }
}

val CwTone.icon: ImageVector
    get() = when (this) {
        CwTone.Success -> Icons.Filled.CheckCircle
        CwTone.Warning -> Icons.Filled.WarningAmber
        CwTone.Danger -> Icons.Filled.ErrorOutline
        CwTone.Info -> Icons.Filled.Info
        CwTone.Pending -> Icons.Filled.HourglassEmpty
        CwTone.Neutral -> Icons.Filled.RadioButtonUnchecked
    }

/**
 * شارة حالة — **أيقونة + نص + لون**، التلاتة مع بعض.
 *
 * ده تنفيذ مباشر لقاعدة "اللون لوحده عمره ما بيحمل معنى". قبل كده كان
 * "مقبول" و"مرفوض" الفرق بينهم درجة لون بس، والدرجتين ساقطين في التباين —
 * يعني مهندس عنده عمى ألوان، أو أي حد واقف في الشمس، كان بيفقد الفرق خالص.
 */
@Composable
fun CwStatusBadge(
    label: String,
    tone: CwTone,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val s = tone.semantic()
    Surface(shape = Radius.pill, color = s.container, modifier = modifier) {
        Row(
            Modifier.padding(
                horizontal = if (compact) Space.sm else Space.md,
                vertical = Space.xs
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.xs)
        ) {
            Icon(
                tone.icon,
                contentDescription = null, // النص جنبها بيقول نفس المعنى
                tint = s.onContainer,
                modifier = Modifier.size(IconSize.sm)
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = s.onContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** لافتة — رسالة مهمة جوّه سياق الشاشة. */
@Composable
fun CwBanner(
    title: String,
    tone: CwTone,
    modifier: Modifier = Modifier,
    detail: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val s = tone.semantic()
    Row(
        modifier
            .fillMaxWidth()
            .clip(Radius.shapeMd)
            .background(s.container)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(Space.md),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Space.sm)
    ) {
        Icon(
            tone.icon,
            contentDescription = null,
            tint = s.onContainer,
            modifier = Modifier.size(IconSize.md)
        )
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = s.onContainer)
            if (detail != null) {
                Spacer(Modifier.height(Space.xxs))
                Text(detail, style = MaterialTheme.typography.bodySmall, color = s.onContainer)
            }
        }
        trailing?.invoke()
    }
}

// ══════════════════════════════════════════════════════════ الصفوف والمقاييس

/**
 * الصف الموحّد — بيحلّ محل ٦ أشكال صفوف كانت متفرّقة في التطبيق.
 * مساحة اللمس مضمونة ≥ [Sizes.touch].
 */
@Composable
fun CwListItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    titleColor: Color? = null
) {
    val c = LocalCwColors.current
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = Sizes.touch)
            .padding(horizontal = Space.lg, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.md)
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor ?: c.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Spacer(Modifier.height(Space.xxs))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textTertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        trailing?.invoke()
    }
}

/** أيقونة داخل مربّع ملوّن — القيادة القياسية للصفوف. */
@Composable
fun CwLeadingIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tone: CwTone = CwTone.Info,
    size: Dp = Sizes.avatarSm
) {
    val s = tone.semantic()
    Box(
        modifier
            .size(size)
            .clip(Radius.shapeSm)
            .background(s.container),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = s.onContainer,
            modifier = Modifier.size(IconSize.md)
        )
    }
}

/**
 * مقياس واحد. الرقم هو البطل، والتسمية تحته — عكس الكروت القديمة اللي كانت
 * بتدي التسمية والرقم نفس الوزن فالعين ما كانتش تعرف تبص على إيه الأول.
 */
@Composable
fun CwMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    tone: CwTone? = null,
    caption: String? = null,
    onClick: (() -> Unit)? = null
) {
    val c = LocalCwColors.current
    val valueColor = tone?.semantic()?.fg ?: c.textPrimary
    CwCard(modifier = modifier, onClick = onClick, contentPadding = PaddingValues(Space.md)) {
        Text(value, style = CwText.metricSmall, color = valueColor, maxLines = 1)
        Spacer(Modifier.height(Space.xxs))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = c.textSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (caption != null) {
            Spacer(Modifier.height(Space.xxs))
            Text(
                caption,
                style = MaterialTheme.typography.labelSmall,
                color = c.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** صف مقاييس بعرض متساوي. */
@Composable
fun CwMetricRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        content = content
    )
}

/** شريط تقدّم — بلون دلالي، والنسبة دايماً مكتوبة جنبه (مش لون لوحده). */
@Composable
fun CwProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    tone: CwTone = CwTone.Info,
    height: Dp = Sizes.bar
) {
    val c = LocalCwColors.current
    val s = tone.semantic()
    val safe = fraction.coerceIn(0f, 1f)
    val fill by animateColorAsState(s.solid, Motion.standard(), label = "progressTone")
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(Radius.pill)
            .background(c.surfaceAlt)
    ) {
        Box(
            Modifier
                .fillMaxWidth(safe)
                .height(height)
                .clip(Radius.pill)
                .background(fill)
        )
    }
}

// ══════════════════════════════════════════════════════════ الحالات الفاضية

/**
 * حالة فاضية بمعنى. القاعدة: تقول **إيه اللي ناقص** و**إيه الخطوة الجاية**،
 * مش مجرد "لا توجد بيانات".
 */
@Composable
fun CwEmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    action: (@Composable () -> Unit)? = null
) {
    val c = LocalCwColors.current
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Space.xl, vertical = Space.xxl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(Sizes.avatarLg)
                .clip(Radius.shapeLg)
                .background(c.surfaceAlt),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = c.textTertiary,
                modifier = Modifier.size(IconSize.xl)
            )
        }
        Spacer(Modifier.height(Space.lg))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = c.textPrimary,
            textAlign = TextAlign.Center
        )
        if (detail != null) {
            Spacer(Modifier.height(Space.sm))
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = c.textTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 320.dp)
            )
        }
        if (action != null) {
            Spacer(Modifier.height(Space.lg))
            action()
        }
    }
}

/** صفّ محمّل — بديل الدوران الفاضي. */
@Composable
fun CwLoadingRow(label: String, modifier: Modifier = Modifier) {
    val c = LocalCwColors.current
    Row(
        modifier
            .fillMaxWidth()
            .padding(Space.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.md)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(IconSize.md),
            strokeWidth = Stroke.thick,
            color = c.accent
        )
        Text(label, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
    }
}

@Immutable
data class CwKeyValue(val key: String, val value: String, val tone: CwTone? = null)

/** جدول مفتاح/قيمة — الشكل القياسي لعرض تفاصيل عنصر. */
@Composable
fun CwKeyValueList(items: List<CwKeyValue>, modifier: Modifier = Modifier) {
    val c = LocalCwColors.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        items.forEach { kv ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(
                    kv.key,
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textTertiary,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(Space.md))
                Text(
                    kv.value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = kv.tone?.semantic()?.fg ?: c.textPrimary,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1.4f)
                )
            }
        }
    }
}
