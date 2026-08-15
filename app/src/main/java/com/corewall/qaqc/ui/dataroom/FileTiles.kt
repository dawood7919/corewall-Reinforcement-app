package com.corewall.qaqc.ui.dataroom

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Architecture
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import com.corewall.qaqc.data.db.FileMetaEntity
import com.corewall.qaqc.ui.design.CwStatusBadge
import com.corewall.qaqc.ui.design.CwIconButton
import com.corewall.qaqc.ui.design.CwTone
import com.corewall.qaqc.ui.design.IconSize
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Motion
import com.corewall.qaqc.ui.design.Radius
import com.corewall.qaqc.ui.design.Sizes
import com.corewall.qaqc.ui.design.Space
import com.corewall.qaqc.ui.design.Stroke
import java.io.File

/**
 * بلاطات الملفات — شبكة وقايمة.
 *
 * الصور المصغّرة بقت من Coil بدل `rememberThumb` المكتوب بالإيد. الفرق مش
 * تجميلي: Coil عنده كاش ذاكرة وكاش قرص و**بيلغي الطلب لما البلاطة تخرج من
 * الشاشة**. القديم كان بيفك ترميز كل صورة من الأول في كل تمرير، وده كان
 * أكتر مكان في التطبيق معرّض إنه يهتهت أو يقع من الذاكرة في مجلد فيه ٢٠٠
 * صورة موقع.
 */

fun isImageFile(f: File) =
    f.extension.lowercase() in listOf("jpg", "jpeg", "png", "webp", "gif", "heic", "bmp")

fun isPdfFile(f: File) = f.extension.equals("pdf", ignoreCase = true)

fun isCadFile(f: File) = f.extension.lowercase() in listOf("dxf", "dwg")

fun iconForFile(f: File): ImageVector = when {
    f.isDirectory -> Icons.Filled.Folder
    isPdfFile(f) -> Icons.Filled.PictureAsPdf
    isImageFile(f) -> Icons.Filled.Image
    isCadFile(f) -> Icons.Filled.Architecture
    else -> Icons.Filled.Description
}

fun toneForFile(f: File): CwTone = when {
    f.isDirectory -> CwTone.Info
    isPdfFile(f) -> CwTone.Danger
    isImageFile(f) -> CwTone.Success
    isCadFile(f) -> CwTone.Pending
    else -> CwTone.Neutral
}

/** صورة مصغّرة أو أيقونة نوع — نفس المكوّن للشبكة والقايمة. */
@Composable
fun FileThumb(
    file: File,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val c = LocalCwColors.current
    val tone = toneForFile(file)
    val s = when (tone) {
        CwTone.Danger -> c.danger
        CwTone.Success -> c.success
        CwTone.Pending -> c.pending
        CwTone.Info -> c.info
        else -> c.neutral
    }

    Box(
        modifier
            .clip(Radius.shapeMd)
            .background(if (isImageFile(file)) c.surfaceAlt else s.container),
        contentAlignment = Alignment.Center
    ) {
        if (isImageFile(file)) {
            AsyncImage(
                model = file,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                iconForFile(file),
                contentDescription = null,
                tint = s.onContainer,
                modifier = Modifier.size(IconSize.xl)
            )
        }
    }
}

/** بلاطة في الشبكة. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileGridTile(
    file: File,
    meta: FileMetaEntity?,
    selected: Boolean,
    selectionMode: Boolean,
    onOpen: () -> Unit,
    onToggleSelect: () -> Unit,
    analysisLabel: String? = null,
    onAnalyze: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val c = LocalCwColors.current
    val border by animateColorAsState(
        if (selected) c.accent else c.outline,
        Motion.standard(), label = "tileBorder"
    )

    Surface(
        modifier = modifier.combinedClickable(
            onClick = { if (selectionMode) onToggleSelect() else onOpen() },
            onLongClick = onToggleSelect
        ),
        shape = Radius.shapeLg,
        color = c.surface,
        border = androidx.compose.foundation.BorderStroke(
            if (selected) Stroke.thick else Stroke.hair, border
        )
    ) {
        Column(Modifier.padding(Space.sm)) {
            Box {
                FileThumb(
                    file,
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.3f)
                )
                if (selected) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "مختار",
                        tint = c.accent,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(Space.xs)
                            .size(IconSize.lg)
                    )
                }
                if (meta?.favourite == true) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = "مفضّل",
                        tint = c.warning.fg,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(Space.xs)
                            .size(IconSize.md)
                    )
                }
                if (!selectionMode && onAnalyze != null) {
                    CwIconButton(
                        icon = Icons.Filled.AutoAwesome,
                        contentDescription = "حلّل PDF",
                        onClick = onAnalyze,
                        modifier = Modifier.align(Alignment.BottomEnd)
                    )
                }
            }
            Spacer(Modifier.height(Space.sm))
            Text(
                file.name,
                style = MaterialTheme.typography.bodySmall,
                color = c.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (meta != null && meta.tagList.isNotEmpty()) {
                Spacer(Modifier.height(Space.xs))
                Text(
                    meta.tagList.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            analysisLabel?.let {
                Spacer(Modifier.height(Space.xs))
                Text(it, style = MaterialTheme.typography.labelSmall, color = c.accent)
            }
        }
    }
}

/** صف في القايمة — كثافة أعلى، معلومات أكتر. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListRow(
    file: File,
    meta: FileMetaEntity?,
    subtitle: String,
    selected: Boolean,
    selectionMode: Boolean,
    onOpen: () -> Unit,
    onToggleSelect: () -> Unit,
    analysisLabel: String? = null,
    onAnalyze: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val c = LocalCwColors.current
    val bg by animateColorAsState(
        if (selected) c.accentContainer else c.surface,
        Motion.standard(), label = "rowBg"
    )

    Surface(
        modifier = modifier.combinedClickable(
            onClick = { if (selectionMode) onToggleSelect() else onOpen() },
            onLongClick = onToggleSelect
        ),
        shape = Radius.shapeMd,
        color = bg,
        border = androidx.compose.foundation.BorderStroke(Stroke.hair, c.outline)
    ) {
        Row(
            Modifier.padding(Space.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.md)
        ) {
            FileThumb(file, Modifier.size(Sizes.avatarLg))
            Column(Modifier.weight(1f)) {
                Text(
                    file.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(Space.xxs))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = c.textTertiary,
                    maxLines = 1
                )
                if (meta != null && meta.tagList.isNotEmpty()) {
                    Spacer(Modifier.height(Space.xs))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                        meta.tagList.take(3).forEach { tag ->
                            CwStatusBadge(tag, CwTone.Neutral, compact = true)
                        }
                    }
                }
                analysisLabel?.let {
                    Spacer(Modifier.height(Space.xxs))
                    Text(it, style = MaterialTheme.typography.labelSmall, color = c.accent, maxLines = 1)
                }
            }
            if (meta?.favourite == true) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = "مفضّل",
                    tint = c.warning.fg,
                    modifier = Modifier.size(IconSize.md)
                )
            }
            if (!selectionMode && onAnalyze != null) {
                CwIconButton(Icons.Filled.AutoAwesome, "حلّل PDF", onAnalyze)
            }
            if (selected) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "مختار",
                    tint = c.accent,
                    modifier = Modifier.size(IconSize.lg)
                )
            }
        }
    }
}
