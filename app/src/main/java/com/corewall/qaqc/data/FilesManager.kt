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

    /** مجلد صور الموقع (Site Photos) لكل دور. */
    fun sitePhotosDir(level: String): File =
        File(root, "site-photos/${sanitize(level)}").apply { mkdirs() }

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

    /** إعادة تسمية مع الحفاظ على الامتداد لو المستخدم مكتبوش. */
    fun rename(file: File, newName: String): Boolean {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return false
        val ext = file.extension
        val hasExt = trimmed.contains('.')
        val finalName = sanitize(if (hasExt || file.isDirectory || ext.isEmpty()) trimmed else "$trimmed.$ext")
        val target = File(file.parentFile, finalName)
        if (target.exists()) return false
        return file.renameTo(target)
    }

    /** الحجم الكلي (بايت) لمجلد أو ملف. */
    fun sizeOf(file: File): Long =
        if (file.isDirectory) file.walkTopDown().filter { it.isFile }.sumOf { it.length() } else file.length()

    /** اسم فريد داخل مجلد (بيضيف رقم لو الاسم موجود). */
    private fun uniqueDest(dir: File, name: String): File {
        var dest = File(dir, name)
        if (!dest.exists()) return dest
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        var i = 2
        while (dest.exists()) {
            dest = File(dir, if (ext.isEmpty()) "$base ($i)" else "$base ($i).$ext")
            i++
        }
        return dest
    }

    /** نسخ ملف/مجلد لمجلد هدف (بيحافظ على النسخة الأصلية). */
    fun copyInto(file: File, targetDir: File): Boolean = runCatching {
        targetDir.mkdirs()
        val dest = uniqueDest(targetDir, file.name)
        if (file.isDirectory) file.copyRecursively(dest, overwrite = false)
        else { file.copyTo(dest, overwrite = false); dest.exists() }
    }.getOrDefault(false)

    /** نقل ملف/مجلد لمجلد هدف. */
    fun moveInto(file: File, targetDir: File): Boolean {
        if (targetDir.absolutePath.startsWith(file.absolutePath)) return false
        return if (copyInto(file, targetDir)) file.deleteRecursively() else false
    }

    /** تكرار الملف/المجلد في نفس المكان. */
    fun duplicate(file: File): Boolean = file.parentFile?.let { copyInto(file, it) } ?: false

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

    /** ملف صورة جديد فاضي لالتقاط صورة بالكاميرا (جوّه مجلد صور الملاحظات). */
    fun newImageFile(level: String, elementId: String): File {
        val dir = File(root, "notes/${sanitize(level)}/${sanitize(elementId)}").apply { mkdirs() }
        return File(dir, "IMG_${System.currentTimeMillis()}.jpg")
    }

    /** ملف صورة جديد لالتقاط صورة موقع (Site Photos) للدور. */
    fun newSitePhotoFile(level: String): File {
        val dir = sitePhotosDir(level)
        return File(dir, "SITE_${System.currentTimeMillis()}.jpg")
    }

    /** نسخ صور مختارة من المعرض لمجلد صور الملاحظات — بترجع مساراتها. */
    fun importNoteImages(uris: List<Uri>, level: String, elementId: String): List<File> {
        val dir = File(root, "notes/${sanitize(level)}/${sanitize(elementId)}").apply { mkdirs() }
        return importUris(uris, dir)
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
