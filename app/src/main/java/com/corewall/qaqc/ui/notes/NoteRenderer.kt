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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.corewall.qaqc.ui.theme.PlexMono
import java.io.File

@Composable
fun rememberInlineColors(): InlineColors {
    val cs = MaterialTheme.colorScheme
    return InlineColors(
        code = cs.primary,
        codeBg = cs.surfaceVariant,
        highlight = Color(0xFFFFE9A8),
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
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                is NoteBlock.BulletList -> Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    b.items.forEach {
                        Row {
                            Text("•  ", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                            Text(inlineAnnotated(it, ic), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                is NoteBlock.NumberedList -> Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
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
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .height(20.dp)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(inlineAnnotated(b.text, ic), style = MaterialTheme.typography.bodyMedium)
                }
                is NoteBlock.Code -> Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.horizontalScroll(rememberScrollState()).padding(12.dp)) {
                        if (b.language.isNotBlank()) Text(
                            b.language,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(b.code, fontFamily = PlexMono, style = MaterialTheme.typography.bodySmall)
                    }
                }
                NoteBlock.Divider -> HorizontalDivider(Modifier.padding(vertical = 4.dp))
                is NoteBlock.Callout -> CalloutCard(b, ic)
                is NoteBlock.Image -> ImageCard(b.path, b.caption, onOpen = { onOpenImage(b.path) })
                is NoteBlock.FileCard -> FileAttachmentCard(b.path, onOpen = { onOpenFile(b.path) })
                is NoteBlock.Table -> NoteTable(b, ic)
            }
        }
    }
}

private fun calloutStyle(kind: CalloutKind): Triple<Color, Color, ImageVector> = when (kind) {
    CalloutKind.INFO -> Triple(Color(0xFF2F80ED), Color(0x142F80ED), Icons.Filled.Info)
    CalloutKind.WARNING -> Triple(Color(0xFFE8890C), Color(0x14E8890C), Icons.Filled.Warning)
    CalloutKind.DANGER -> Triple(Color(0xFFE53935), Color(0x14E53935), Icons.Filled.Dangerous)
    CalloutKind.INSPECTION -> Triple(Color(0xFF8E44AD), Color(0x148E44AD), Icons.Filled.Search)
    CalloutKind.APPROVED -> Triple(Color(0xFF2E9E5B), Color(0x142E9E5B), Icons.Filled.CheckCircle)
    CalloutKind.REJECTED -> Triple(Color(0xFFD64545), Color(0x14D64545), Icons.Filled.Dangerous)
}

@Composable
private fun CalloutCard(b: NoteBlock.Callout, ic: InlineColors) {
    val (accent, bg, icon) = calloutStyle(b.kind)
    Surface(
        color = bg,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
    ) {
        Row(Modifier.padding(12.dp)) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    b.title.ifBlank { b.kind.label },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = accent
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
    val bmp = rememberThumb(path)
    val dim = rememberImageDim(path)
    val meta = rememberFileMeta(path)
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Box {
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = caption,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )
                } else {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.Fullscreen, contentDescription = null) }
                }
                Surface(
                    color = Color.Black.copy(alpha = 0.45f),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Icon(Icons.Filled.Fullscreen, contentDescription = "تكبير", modifier = Modifier.padding(5.dp).size(18.dp))
                }
            }
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (caption.isNotBlank()) {
                    Text(caption, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(8.dp))
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
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(10.dp),
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
                            tint = if (isPdf) Color(0xFFE53935) else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
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
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("فتح", Modifier.padding(horizontal = 14.dp, vertical = 8.dp), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun NoteTable(b: NoteBlock.Table, ic: InlineColors) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            Row(Modifier.background(MaterialTheme.colorScheme.primaryContainer)) {
                b.header.forEach { cell ->
                    Text(
                        cell,
                        Modifier.width(120.dp).padding(10.dp),
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
                            Modifier.width(120.dp).padding(10.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}
