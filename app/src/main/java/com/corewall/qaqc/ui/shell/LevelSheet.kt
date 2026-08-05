package com.corewall.qaqc.ui.shell

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import com.corewall.qaqc.ui.design.CwText
import com.corewall.qaqc.ui.design.IconSize
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Radius
import com.corewall.qaqc.ui.design.Sizes
import com.corewall.qaqc.ui.design.Space

/**
 * مبدّل الدور.
 *
 * أهم كنترول في التطبيق — كل حاجة معزولة بالدور، والمهندس بيبدّله عشرات
 * المرّات في اليوم. الحوار القديم كان شبكة ٤٨ خانة من غير `key`، من غير ما
 * يلفّ على الدور الحالي، ومن غير أي إشارة لحالة كل دور.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LevelSheet(
    levels: List<String>,
    current: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    title: String = "اختار الدور الشغّال",
    /** نسبة إنجاز كل دور — بتخلّي الاختيار مبني على معلومة مش على حفظ. */
    completion: (String) -> Int? = { null }
) {
    val c = LocalCwColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val gridState = rememberLazyGridState()
    val currentIndex = levels.indexOf(current)

    // نفتح على الدور الشغّال — مش على أول الليستة.
    LaunchedEffect(currentIndex) {
        if (currentIndex > 4) gridState.scrollToItem((currentIndex - 4).coerceAtLeast(0))
    }

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
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
            Spacer(Modifier.height(Space.xs))
            Text(
                "${levels.size} دور · الشغّال دلوقتي $current",
                style = MaterialTheme.typography.bodySmall,
                color = c.textTertiary
            )
            Spacer(Modifier.height(Space.lg))

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = Sizes.levelCell),
                state = gridState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = Sizes.sheetGridMax),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                items(levels, key = { it }) { level ->
                    LevelCell(
                        level = level,
                        selected = level == current,
                        completion = completion(level),
                        onClick = { onPick(level) }
                    )
                }
            }
            Spacer(Modifier.height(Space.lg))
        }
    }
}

@Composable
private fun LevelCell(
    level: String,
    selected: Boolean,
    completion: Int?,
    onClick: () -> Unit
) {
    val c = LocalCwColors.current
    val bg = if (selected) c.accentContainer else c.surfaceAlt
    val fg = if (selected) c.onAccentContainer else c.textPrimary
    Column(
        Modifier
            .clip(Radius.shapeMd)
            .background(bg)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .heightIn(min = Sizes.touch)
            .padding(vertical = Space.sm, horizontal = Space.xs),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "الدور الشغّال",
                    tint = fg,
                    modifier = Modifier.size(IconSize.sm)
                )
                Spacer(Modifier.size(Space.xxs))
            }
            Text(
                level,
                style = CwText.codeSmall,
                color = fg,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
        if (completion != null) {
            Spacer(Modifier.height(Space.xxs))
            Text(
                "$completion%",
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) fg else c.textTertiary,
                maxLines = 1
            )
        }
    }
}

/** غلاف متوافق مع النداءات القديمة اللي لسه شايلة اسم الحوار. */
@Composable
fun LevelPickerSheet(
    levels: List<String>,
    current: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    title: String = "اختار الدور"
) = LevelSheet(levels, current, onPick, onDismiss, title)
