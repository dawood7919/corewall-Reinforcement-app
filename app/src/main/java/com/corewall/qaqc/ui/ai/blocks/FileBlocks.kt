package com.corewall.qaqc.ui.ai.blocks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.corewall.qaqc.ai.model.AnswerFile
import com.corewall.qaqc.ui.media.rememberPdfPreview
import com.corewall.qaqc.ui.theme.LocalSrtColors
import java.io.File

private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp", "heic", "bmp", "gif")

private fun iconFor(ext: String): ImageVector = when (ext.lowercase()) {
    "pdf" -> Icons.Filled.PictureAsPdf
    in IMAGE_EXT -> Icons.Filled.Image
    "xlsx", "xlsm", "csv" -> Icons.Filled.GridOn
    else -> Icons.Filled.Description
}

private fun sizeText(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

/**
 * الملفات اللي المساعد بيعرضها في الرد.
 *
 * الملفات اللي مش موجودة على القرص بتتشال قبل العرض: لو الموديل اخترع
 * مسار، الكارت بيختفي بدل ما يفضل موجود ويودّي لحاجة مش هناك.
 */
@Composable
fun FileList(files: List<AnswerFile>, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val real = remember(files) { files.filter { it.path.isNotBlank() && File(it.path).exists() } }
    if (real.isEmpty()) {
        Text(
            "الملفات المطلوبة مش موجودة على الجهاز.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        real.take(12).forEach { af -> FileRow(af, onOpen) }
    }
}

@Composable
private fun FileRow(af: AnswerFile, onOpen: (String) -> Unit) {
    val srt = LocalSrtColors.current
    val f = remember(af.path) { File(af.path) }
    val ext = f.extension.lowercase()
    val isImage = ext in IMAGE_EXT

    Surface(
        onClick = { onOpen(af.path) },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).background(srt.blueTint),
                contentAlignment = Alignment.Center
            ) {
                // الأيقونة تحت الصورة دايماً: بتغطّي وقت التحميل وحالة
                // الملف اللي مابيتفكّش ترميزه، من غير فرع فاضي.
                Icon(
                    if (f.isDirectory) Icons.Filled.Folder else iconFor(ext),
                    contentDescription = null, tint = srt.blue,
                    modifier = Modifier.size(22.dp)
                )
                // الصور من Coil (كاش + إلغاء)، والـPDF لسه محتاج
                // `PdfRenderer` عشان يطلع أول صفحة — مفيش فاكّ ترميز
                // جاهز ليه في Coil.
                if (isImage) {
                    coil3.compose.AsyncImage(
                        model = f,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (ext == "pdf") {
                    rememberPdfPreview(af.path, 120)?.let { thumb ->
                        androidx.compose.foundation.Image(
                            bitmap = thumb.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    f.name, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    buildString {
                        append(if (f.isDirectory) "مجلد" else ext.uppercase().ifBlank { "ملف" })
                        if (f.isFile) append(" · ${sizeText(f.length())}")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (af.caption.isNotBlank()) {
                    Text(
                        af.caption, style = MaterialTheme.typography.labelSmall,
                        color = srt.blue, maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** معرض صور — للردود اللي بتعرض صور موقع. */
@Composable
fun ImageGallery(files: List<AnswerFile>, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val real = remember(files) { files.filter { it.path.isNotBlank() && File(it.path).exists() } }
    if (real.isEmpty()) {
        Text(
            "مفيش صور متاحة للعرض.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        real.take(9).chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { af ->
                    Thumb(af, onOpen, Modifier.weight(1f))
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        val captioned = real.filter { it.caption.isNotBlank() }
        if (captioned.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            captioned.take(4).forEach {
                Text(
                    "• ${File(it.path).name}: ${it.caption}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun Thumb(af: AnswerFile, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val file = remember(af.path) { File(af.path) }
    Surface(
        onClick = { onOpen(af.path) },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.aspectRatio(1f)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Filled.Image, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            coil3.compose.AsyncImage(
                model = file,
                contentDescription = af.caption.ifBlank { file.name },
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * كارت الموافقة على إجراء بيغيّر بيانات.
 *
 * الوكيل بيقترح، والمستخدم بيضغط. الحذف بلون تحذيري وكلمة صريحة —
 * الفرق بين "ضيف مهمة" و"امسح مجلد" لازم يبان قبل الضغط مش بعده.
 */
@Composable
fun ActionConfirmCard(
    title: String,
    detail: String,
    destructive: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viz = com.corewall.qaqc.ui.theme.LocalVizColors.current
    val srt = LocalSrtColors.current
    val tone = if (destructive) viz.critical else srt.blue

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = tone.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, tone.copy(alpha = 0.35f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (destructive) Icons.Filled.DeleteForever else Icons.Filled.Bolt,
                    contentDescription = null, tint = tone, modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (destructive) "إجراء حذف — مستني موافقتك" else "إجراء مستني موافقتك",
                    style = MaterialTheme.typography.labelMedium,
                    color = tone, fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            if (detail.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    detail, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    onClick = onConfirm,
                    shape = RoundedCornerShape(10.dp),
                    color = tone,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        if (destructive) "امسح" else "نفّذ",
                        Modifier.fillMaxWidth().padding(vertical = 9.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = androidx.compose.ui.graphics.Color.White,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
                Surface(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        "إلغاء",
                        Modifier.fillMaxWidth().padding(vertical = 9.dp),
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}
