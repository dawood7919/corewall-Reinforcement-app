package com.corewall.qaqc.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.corewall.qaqc.ui.design.CwCountPill
import com.corewall.qaqc.ui.design.CwIconButton
import com.corewall.qaqc.ui.design.CwText
import com.corewall.qaqc.ui.design.CwTone
import com.corewall.qaqc.ui.design.IconSize
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Radius
import com.corewall.qaqc.ui.design.Sizes
import com.corewall.qaqc.ui.design.Space
import com.corewall.qaqc.ui.design.Stroke

/**
 * إطار الدور — **مش هيدر**.
 *
 * ده تنفيذ القرار إن الدور هو الحاوية اللي كل حاجة قاعدة جوّاها، مش لافتة
 * فوقها. التطبيق كله معزول بالدور، وعزل الأدوار اتكسر مرّة قبل كده ومحدش
 * لاحظ — لأن الواجهة مكانتش بتوضّح الحدّ كفاية.
 *
 * فالشريط ده ثابت فوق **كل** شاشة: في الجذر بيبان كامل بالدور كبير، وفوق
 * وجهة متفرّعة بيتحوّل لسطر رجوع + اسم الوجهة، ومعاه الدور فاضل ظاهر كشيب
 * عشان تفضل عارف إنت بتبص على داتا أنهي دور.
 */
@Composable
fun FloorBar(
    level: String,
    levelIndex: Int,
    levelCount: Int,
    modifier: Modifier = Modifier,
    destinationTitle: String? = null,
    unread: Int = 0,
    onPickLevel: () -> Unit,
    onMenu: () -> Unit,
    onBack: (() -> Unit)? = null,
    onNotifications: (() -> Unit)? = null
) {
    val c = LocalCwColors.current
    Surface(color = c.surface, modifier = modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .heightIn(min = Sizes.topBar)
                    .padding(horizontal = Space.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.xs)
            ) {
                if (onBack != null) {
                    CwIconButton(
                        // في RTL السهم بيتقلب تلقائياً مع AutoMirrored
                        icon = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "رجوع",
                        onClick = onBack
                    )
                } else {
                    CwIconButton(
                        icon = Icons.Filled.Menu,
                        contentDescription = "القائمة الجانبية",
                        onClick = onMenu
                    )
                }

                if (destinationTitle == null) {
                    // الجذر — الدور هو البطل
                    LevelChip(
                        level = level,
                        levelIndex = levelIndex,
                        levelCount = levelCount,
                        large = true,
                        onClick = onPickLevel,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Column(Modifier.weight(1f)) {
                        Text(
                            destinationTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = c.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "الدور $level",
                            style = MaterialTheme.typography.labelMedium,
                            color = c.textTertiary,
                            maxLines = 1
                        )
                    }
                    LevelChip(
                        level = level,
                        levelIndex = levelIndex,
                        levelCount = levelCount,
                        large = false,
                        onClick = onPickLevel
                    )
                }

                if (onNotifications != null) {
                    Box {
                        CwIconButton(
                            icon = Icons.Filled.NotificationsNone,
                            contentDescription = if (unread > 0) "الإشعارات — $unread جديدة" else "الإشعارات",
                            onClick = onNotifications
                        )
                        AnimatedVisibility(
                            visible = unread > 0,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            CwCountPill(unread, tone = CwTone.Danger)
                        }
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(Stroke.hair)
                    .background(c.divider)
            )
        }
    }
}

/**
 * الدور نفسه كعنصر قابل للضغط. الرقم بخط مضغوط عشان "B02" و"ROOF" ياخدوا
 * نفس المساحة تقريباً فالشريط ما يرقصش لما الدور يتغيّر.
 */
@Composable
private fun LevelChip(
    level: String,
    levelIndex: Int,
    levelCount: Int,
    large: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalCwColors.current
    Row(
        modifier
            .clip(Radius.shapeMd)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = Sizes.touch)
            .padding(horizontal = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.xs)
    ) {
        if (large) {
            Column {
                Text(
                    "الدور الشغّال",
                    style = MaterialTheme.typography.labelMedium,
                    color = c.textTertiary
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(level, style = CwText.metric, color = c.textPrimary, maxLines = 1)
                    Spacer(Modifier.size(Space.sm))
                    if (levelIndex >= 0) {
                        Text(
                            "${levelIndex + 1}/$levelCount",
                            style = MaterialTheme.typography.labelMedium,
                            color = c.textTertiary,
                            modifier = Modifier.padding(bottom = Space.xs)
                        )
                    }
                }
            }
        } else {
            Text(level, style = MaterialTheme.typography.titleSmall, color = c.textPrimary, maxLines = 1)
        }
        Icon(
            Icons.Filled.ExpandMore,
            contentDescription = "تبديل الدور",
            tint = c.textTertiary,
            modifier = Modifier.size(IconSize.md)
        )
    }
}
