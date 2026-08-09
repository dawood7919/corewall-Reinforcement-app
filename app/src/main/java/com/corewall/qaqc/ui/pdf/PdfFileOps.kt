package com.corewall.qaqc.ui.pdf

import java.io.File

/**
 * حفظ آمن لعمليات المستندات.
 *
 * القاعدة الوحيدة هنا: **ماتكتبش فوق الأصل غير بعد ما البديل يخلص
 * ويتقري**. رسمة تنفيذية بتتعدّل في الموقع على بطارية ٧٪ ومع كل احتمال
 * إن التطبيق يتقفل وسط الكتابة؛ الكتابة المباشرة معناها ملف نصّه مكتوب
 * ونصّه قديم — وده ملف ضايع مش ملف ناقص.
 */

/** اسم ملف مش موجود جنب الأصل: `الرسمة (منظَّم).pdf`، وبعدين `(2)`. */
fun uniqueSibling(source: File, suffix: String): File {
    val dir = source.parentFile ?: source.absoluteFile.parentFile!!
    val base = source.nameWithoutExtension
    val ext = source.extension.ifBlank { "pdf" }

    var candidate = File(dir, "$base ($suffix).$ext")
    var i = 2
    while (candidate.exists()) {
        candidate = File(dir, "$base ($suffix $i).$ext")
        i++
    }
    return candidate
}

/**
 * بيكتب ملف مؤقّت بـ[produce]، وبعد ما يتأكد إنه اتكتب فعلاً بيبدّله
 * بالأصل. بيرجّع الملف الأصلي بعد التبديل.
 *
 * الملف المؤقّت جنب الأصل مش في مجلد مؤقّت: `renameTo` بيفشل عبر أنظمة
 * ملفات مختلفة، والتخزين الخارجي الخاص بالتطبيق مش نفس قسم `cacheDir`
 * على كل الأجهزة.
 */
suspend fun overwrite(target: File, produce: suspend (File) -> Result<Unit>): Result<File> {
    val temp = File(target.parentFile, ".${target.name}.tmp")
    val backup = File(target.parentFile, ".${target.name}.bak")
    return produce(temp).mapCatching {
        check(temp.exists() && temp.length() > MIN_PDF_BYTES) { "الملف الناتج فاضي" }

        // نسخة احتياطية قبل أي لمسة للأصل — لو التبديل وقع في النص،
        // بنرجّعها بدل ما نسيب المستخدم من غير ملف.
        if (backup.exists()) backup.delete()
        if (!target.renameTo(backup)) error("مقدرناش نجهّز الملف للاستبدال")

        if (!temp.renameTo(target)) {
            backup.renameTo(target)
            error("مقدرناش نكتب الملف الجديد")
        }
        backup.delete()
        target
    }.onFailure {
        temp.delete()
        if (!target.exists() && backup.exists()) backup.renameTo(target)
    }
}

/** أقل من كده مش ملف PDF سليم أصلاً (الترويسة والمقطورة لوحدهم أكبر). */
private const val MIN_PDF_BYTES = 200L
