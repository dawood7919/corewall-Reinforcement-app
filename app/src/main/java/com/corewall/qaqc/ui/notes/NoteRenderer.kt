package com.corewall.qaqc.ui.notes

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.corewall.qaqc.ui.design.CwTone
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.semantic
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.corewall.qaqc.ui.theme.PlexMono
import java.io.File
import com.corewall.qaqc.ui.design.Radius
import com.corewall.qaqc.ui.design.Space

@Composable
fun rememberInlineColors(): InlineColors {
    val cs = MaterialTheme.colorScheme
    return InlineColors(
        code = cs.primary,
        codeBg = cs.surfaceVariant,
        highlight = LocalCwColors.current.warning.container,
        tag = cs.primary,
        tagBg = cs.primaryContainer,
        mention = cs.tertiary,
        mentionBg = cs.tertiaryContainer
    )
}

/**
 * عرض الملاحظة بشكل احترافي (زي GitHub/Notion): عناوين، قوايم، تشيك ليست
 * تفاعلية، اقتباسات، أكواد، Callouts، جداول، صور وملفات كـكروت.
 * onToggleCheck(sourceLine) لتبديل عنصر تشيك ليست، onOpenImage/onOpenFile للفتح.
 */
@Composable
fun NoteContent(
    markdown: String,
    modifier: Modifier = Modifier,
    onToggleCheck: (Int) -> Unit = {},
    onOpenImage: (String) -> Unit = {},
    onOpenFile: (String) -> Unit = {}
) {
    val blocks = parseNote(markdown)
    val ic = rememberInlineColors()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        for (b in blocks) {
            when (b) {
                is NoteBlock.Heading -> Text(
                    inlineAnnotated(b.text, ic),
                    style = when (b.level) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleLarge
                        else -> MaterialTheme.typography.titleMedium
                    },
                    fontWeight = FontWeight.Bold
                )
                is NoteBlock.Paragraph -> Text(
                    inlineAnnotated(b.text, ic),
                    style = MaterialTheme.typography.bodyLarge
                )
                is NoteBlock.BulletList -> Column(verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                    b.items.forEach {
                        Row {
                            Text("•  ", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                            Text(inlineAnnotated(it, ic), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                is NoteBlock.NumberedList -> Column(verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                    b.items.forEachIndexed { idx, it ->
                        Row {
                            Text(
                                "${idx + 1}.  ",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(inlineAnnotated(it, ic), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                is NoteBlock.CheckItem -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = b.checked, onCheckedChange = { onToggleCheck(b.sourceLine) })
                    Text(
                        inlineAnnotated(b.text, ic),
                        style = MaterialTheme.typography.bodyLarge,
                        textDecoration = if (b.checked) TextDecoration.LineThrough else null,
                        color = if (b.checked) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
                is NoteBlock.Quote -> Row(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, Radius.shapeSm)
                        .padding(Space.md)
                ) {
                    Box(
                        Modifier
                            .width(Space.xxs)
                            .height(Space.xl)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(Modifier.width(Space.md))
                    Text(inlineAnnotated(b.text, ic), style = MaterialTheme.typography.bodyMedium)
                }
                is NoteBlock.Code -> Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = Radius.shapeMd,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.horizontalScroll(rememberScrollState()).padding(Space.md)) {
                        if (b.language.isNotBlank()) Text(
                            b.language,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(b.code, fontFamily = PlexMono, style = MaterialTheme.typography.bodySmall)
                    }
                }
                NoteBlock.Divider -> HorizontalDivider(Modifier.padding(vertical = Space.xs))
                is NoteBlock.Callout -> CalloutCard(b, ic)
                is NoteBlock.Image -> ImageCard(b.path, b.caption, onOpen = { onOpenImage(b.path) })
                is NoteBlock.FileCard -> FileAttachmentCard(b.path, onOpen = { onOpenFile(b.path) })
                is NoteBlock.Audio -> AudioAttachmentCard(b.path)
                is NoteBlock.Table -> NoteTable(b, ic)
            }
        }
    }
}

/**
 * نبرة الكولاوت. كانت ٦ ألوان مكتوبة بالإيد بخلفية alpha 8% — والخلفية
 * الشفافة دي كانت بتخلّي التباين يتغيّر حسب اللي وراها. دلوقتي كل نوع
 * بياخد نبرة من اللوحة بحاوية مصمتة متفحوصة.
 */
private fun toneOfCallout(kind: CalloutKind): CwTone = when (kind) {
    CalloutKind.INFO -> CwTone.Info
    CalloutKind.WARNING -> CwTone.Warning
    CalloutKind.DANGER -> CwTone.Danger
    CalloutKind.INSPECTION -> CwTone.Pending
    CalloutKind.APPROVED -> CwTone.Success
    CalloutKind.REJECTED -> CwTone.Danger
}

private fun iconOfCallout(kind: CalloutKind): ImageVector = when (kind) {
    CalloutKind.INFO -> Icons.Filled.Info
    CalloutKind.WARNING -> Icons.Filled.Warning
    CalloutKind.DANGER -> Icons.Filled.Dangerous
    CalloutKind.INSPECTION -> Icons.Filled.Search
    CalloutKind.APPROVED -> Icons.Filled.CheckCircle
    CalloutKind.REJECTED -> Icons.Filled.Dangerous
}

@Composable
private fun CalloutCard(b: NoteBlock.Callout, ic: InlineColors) {
    val tone = toneOfCallout(b.kind)
    val s = tone.semantic()
    val icon = iconOfCallout(b.kind)
    Surface(
        color = s.container,
        shape = Radius.shapeMd,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(Space.md)) {
            Icon(icon, contentDescription = null, tint = s.onContainer, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Space.md))
            Column {
                Text(
                    b.title.ifBlank { b.kind.label },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = s.onContainer
                )
                b.body.forEach {
                    if (it.isNotBlank()) Text(inlineAnnotated(it, ic), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
fun ImageCard(path: String, caption: String, onOpen: () -> Unit) {
    // ملاحظة فيها عشر صور كانت بتفكّ ترميز عشر صور من الأول في كل مرة
    // تفتحها. Coil بيكاش في الذاكرة وعلى القرص وبيلغي وقت التمرير.
    val file = remember(path) { java.io.File(path) }
    val dim = rememberImageDim(path)
    val meta = rememberFileMeta(path)
    Surface(
        onClick = onOpen,
        shape = Radius.shapeLg,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Box {
                coil3.compose.AsyncImage(
                    model = file,
                    contentDescription = caption,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
                Surface(
                    color = Color.Black.copy(alpha = 0.45f),
                    contentColor = Color.White,
                    shape = Radius.shapeSm,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(Space.sm)
                ) {
                    Icon(Icons.Filled.Fullscreen, contentDescription = "تكبير", modifier = Modifier.padding(Space.xs).size(18.dp))
                }
            }
            Row(Modifier.padding(Space.md), verticalAlignment = Alignment.CenterVertically) {
                if (caption.isNotBlank()) {
                    Text(caption, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(Space.sm))
                }
                Spacer(Modifier.weight(1f))
                Text(
                    listOfNotNull(dim, meta.sizeText.ifBlank { null }).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun FileAttachmentCard(path: String, onOpen: () -> Unit) {
    val meta = rememberFileMeta(path)
    val isPdf = meta.ext == "pdf"
    val pdfThumb = if (isPdf) rememberPdfThumb(path) else null
    Surface(
        onClick = onOpen,
        shape = Radius.shapeLg,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(Space.md), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = Radius.shapeMd,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (pdfThumb != null) {
                        Image(
                            bitmap = pdfThumb.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Icon(
                            if (isPdf) Icons.Filled.PictureAsPdf else Icons.Filled.InsertDriveFile,
                            contentDescription = null,
                            tint = if (isPdf) LocalCwColors.current.danger.fg else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(meta.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1)
                Text(
                    buildString {
                        append(meta.ext.uppercase())
                        if (meta.sizeText.isNotBlank()) append(" · ${meta.sizeText}")
                        meta.pdfPages?.let { append(" · $it صفحة") }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = Radius.shapeMd
            ) {
                Text("فتح", Modifier.padding(horizontal = Space.lg, vertical = Space.sm), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun NoteTable(b: NoteBlock.Table, ic: InlineColors) {
    Surface(
        shape = Radius.shapeMd,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            Row(Modifier.background(MaterialTheme.colorScheme.primaryContainer)) {
                b.header.forEach { cell ->
                    Text(
                        cell,
                        Modifier.width(120.dp).padding(Space.md),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            b.rows.forEach { row ->
                HorizontalDivider()
                Row {
                    row.forEach { cell ->
                        Text(
                            inlineAnnotated(cell, ic),
                            Modifier.width(120.dp).padding(Space.md),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}
