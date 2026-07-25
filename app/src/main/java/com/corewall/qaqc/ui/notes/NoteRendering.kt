package com.corewall.qaqc.ui.notes

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

fun parseImagePaths(jsonStr: String): List<String> =
    runCatching { json.decodeFromString<List<String>>(jsonStr) }.getOrDefault(emptyList())

fun encodeImagePaths(paths: List<String>): String = json.encodeToString(paths)

/** **غامق** داخل السطر → AnnotatedString. */
private fun inlineBold(line: String): AnnotatedString = buildAnnotatedString {
    val regex = Regex("""\*\*(.+?)\*\*""")
    var last = 0
    for (m in regex.findAll(line)) {
        append(line.substring(last, m.range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(m.groupValues[1]) }
        last = m.range.last + 1
    }
    if (last < line.length) append(line.substring(last))
}

/**
 * عارض Markdown خفيف: عناوين (# ## ###)، نقاط (-)، ترقيم (1.)، اقتباس (>)،
 * فاصل (---)، وغامق (**..**) داخل السطر.
 */
@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) {
    val lines = text.split("\n")
    Column(modifier) {
        var shown = 0
        for (raw in lines) {
            if (shown >= maxLines) break
            val line = raw.trimEnd()
            when {
                line.isBlank() -> Spacer(Modifier.width(0.dp).padding(vertical = 3.dp))
                line.startsWith("### ") -> Text(
                    inlineBold(line.removePrefix("### ")),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                line.startsWith("## ") -> Text(
                    inlineBold(line.removePrefix("## ")),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                line.startsWith("# ") -> Text(
                    inlineBold(line.removePrefix("# ")),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                line == "---" -> androidx.compose.material3.HorizontalDivider(
                    Modifier.padding(vertical = 4.dp)
                )
                line.startsWith("> ") -> Row {
                    Text("▍", color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        inlineBold(line.removePrefix("> ")),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                line.startsWith("- ") || line.startsWith("* ") -> Row {
                    Text("•  ", style = MaterialTheme.typography.bodyMedium)
                    Text(inlineBold(line.drop(2)), style = MaterialTheme.typography.bodyMedium)
                }
                Regex("""^\d+\.\s""").containsMatchIn(line) -> Row {
                    val num = line.substringBefore('.') + ". "
                    Text(num, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(
                        inlineBold(line.substringAfter(". ")),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                else -> Text(
                    inlineBold(line),
                    style = MaterialTheme.typography.bodyMedium,
                    overflow = TextOverflow.Ellipsis
                )
            }
            shown++
        }
    }
}

/** تحميل صورة مصغّرة من مسار ملف (منخفضة الدقة عشان الأداء). */
@Composable
fun rememberThumb(path: String, targetPx: Int = 400): Bitmap? {
    val state = produceState<Bitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                var sample = 1
                val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
                while (maxDim / sample > targetPx) sample *= 2
                BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
            }.getOrNull()
        }
    }
    return state.value
}
