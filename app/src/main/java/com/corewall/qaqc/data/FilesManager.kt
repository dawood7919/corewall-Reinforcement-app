package com.corewall.qaqc.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File

/**
 * تخزين ملفات أداة Data على وحدة التخزين الخاصة بالتطبيق
 * (مفيش صلاحيات مطلوبة) — مجلد لكل دور + مجلد مرفقات لكل عنصر لكل دور.
 */
class FilesManager(private val context: Context) {

    val root: File
        get() = File(context.getExternalFilesDir(null) ?: context.filesDir, "corewall-files")
            .apply { mkdirs() }

    /** مجلد ملفات الدور (قسم "الملفات"). */
    fun levelDir(level: String): File =
        File(root, "levels/${sanitize(level)}").apply { mkdirs() }

    /** مجلد مرفقات عنصر في دور (قسم "بلان فيل"). */
    fun attachmentsDir(level: String, elementId: String): File =
        File(root, "attachments/${sanitize(level)}/${sanitize(elementId)}").apply { mkdirs() }

    private fun sanitize(name: String) = name.replace(Regex("[^A-Za-z0-9._\\- ]"), "_")

    fun list(dir: File): List<File> =
        dir.listFiles()?.sortedWith(
            compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() }
        ) ?: emptyList()

    fun createFolder(parent: File, name: String): Boolean {
        val clean = sanitize(name.trim())
        if (clean.isEmpty()) return false
        return File(parent, clean).mkdirs()
    }

    fun delete(file: File): Boolean = file.deleteRecursively()

    fun displayNameOf(uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) return cursor.getString(idx) ?: "file"
            }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "file"
    }

    /** نسخ ملفات من الـpicker لمجلد — بيرجع الملفات اللي اتنسخت. */
    fun importUris(uris: List<Uri>, target: File): List<File> {
        target.mkdirs()
        return uris.mapNotNull { uri ->
            runCatching {
                val name = displayNameOf(uri)
                var dest = File(target, name)
                var counter = 1
                while (dest.exists()) {
                    val base = name.substringBeforeLast('.', name)
                    val ext = name.substringAfterLast('.', "")
                    dest = File(target, if (ext.isEmpty()) "$base ($counter)" else "$base ($counter).$ext")
                    counter++
                }
                context.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                } ?: return@runCatching null
                dest
            }.getOrNull()
        }
    }

    fun mimeOf(file: File): String {
        val ext = file.extension.lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
    }

    fun uriFor(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /** فتح الملف بتطبيق خارجي مناسب. */
    fun openExternally(file: File): Boolean = runCatching {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uriFor(file), mimeOf(file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }.isSuccess

    fun share(file: File): Boolean = runCatching {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeOf(file)
            putExtra(Intent.EXTRA_STREAM, uriFor(file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, "مشاركة ${file.name}")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.isSuccess
}
