package com.corewall.qaqc.update

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import com.corewall.qaqc.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/** إصدار متاح على السيرفر أحدث من المثبّت. */
data class AvailableUpdate(
    val versionCode: Int,
    val versionName: String,
    val url: String
)

/**
 * تحديث التطبيق من جوّه التطبيق.
 *
 * ## الحد اللي مايتعدّاش
 *
 * التثبيت الصامت **مستحيل** لتطبيق عادي — محجوز لتطبيقات النظام وأجهزة
 * الشركات المُدارة. أقصى المتاح: نجيب الملف ونفتح شاشة تثبيت النظام،
 * والمستخدم بيوافق. فالمكسب إن الخطوات بتقل من (متصفّح ← تحميل ← دوّر
 * على الملف ← ثبّت) لضغطة واحدة، مش إن التحديث بيحصل لوحده.
 *
 * ## ليه ملف نسخة منفصل
 *
 * وسم الإصدار ثابت والملفات بتتكتب فوق بعضها كل بناء، فمفيش حاجة في
 * الإصدار نفسه بتقول رقم النسخة. `version.json` اللي الـCI بيكتبه هو
 * المصدر، ومفتاحه `applicationId` عشان النسخة الكاملة ونسخة الحصر
 * بيتحدّثوا كل واحدة لوحدها.
 *
 * التوقيع لازم يكون نفسه عشان يتثبّت كتحديث ويحافظ على البيانات — وده
 * متحقّق: كل البناءات بتتوقّع بنفس المفتاح المتسجّل في الريبو.
 */
object AppUpdater {

    private const val MANIFEST_URL =
        "https://github.com/dawood7919/corewall-Reinforcement-app/releases/download/latest-debug/version.json"

    private val json = Json { ignoreUnknownKeys = true }

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    /**
     * بيرجّع التحديث المتاح، أو `null` لو مفيش أحدث من المثبّت.
     *
     * بيبلع أي فشل شبكة عن قصد: فحص التحديث مايستاهلش يزعج المستخدم لما
     * النت يبقى واقع — هو مش بيطلب حاجة، إحنا اللي بنسأل.
     */
    suspend fun check(): AvailableUpdate? = withContext(Dispatchers.IO) {
        runCatching {
            val body = client.newCall(Request.Builder().url(MANIFEST_URL).build())
                .execute().use { response ->
                    if (!response.isSuccessful) return@runCatching null
                    response.body?.string() ?: return@runCatching null
                }
            val entry = json.parseToJsonElement(body).jsonObject[BuildConfig.APPLICATION_ID]
                ?.jsonObject ?: return@runCatching null
            val code = entry["versionCode"]?.jsonPrimitive?.content?.toIntOrNull()
                ?: return@runCatching null
            if (code <= BuildConfig.VERSION_CODE) return@runCatching null
            AvailableUpdate(
                versionCode = code,
                versionName = entry["versionName"]?.jsonPrimitive?.content.orEmpty(),
                url = entry["url"]?.jsonPrimitive?.content ?: return@runCatching null
            )
        }.getOrNull()
    }

    /**
     * بينزّل الملف ويرجّع مساره.
     *
     * بينزّل لملف مؤقت وبيغيّر اسمه بعد ما يخلص: لو التحميل اتقطع، مايبقاش
     * فيه ملف ناقص باسم صحيح يفتح شاشة تثبيت على حاجة تالفة.
     */
    suspend fun download(
        context: Context,
        update: AvailableUpdate,
        onProgress: (Float) -> Unit = {}
    ): File? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "updates")
                .apply { mkdirs() }
            val target = File(dir, "update-${update.versionCode}.apk")
            if (target.exists() && target.length() > 0L) return@runCatching target
            val staging = File(dir, "update-${update.versionCode}.part")

            val ok = client.newCall(Request.Builder().url(update.url).build())
                .execute().use { response ->
                    val body = response.body
                    if (!response.isSuccessful || body == null) false
                    else {
                        val total = body.contentLength()
                        var read = 0L
                        body.byteStream().use { input ->
                            staging.outputStream().use { output ->
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    val n = input.read(buffer)
                                    if (n <= 0) break
                                    output.write(buffer, 0, n)
                                    read += n
                                    if (total > 0) {
                                        onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                                    }
                                }
                            }
                        }
                        true
                    }
                }
            if (!ok || staging.length() <= 0L) {
                staging.delete()
                return@runCatching null
            }
            // الملفات القديمة بتتشال: كل واحد بحجم التطبيق، ومحدش محتاجها
            // بعد ما التحديث يتثبّت.
            dir.listFiles()?.forEach { if (it != staging && it != target) it.delete() }
            staging.renameTo(target)
            target
        }.getOrNull()
    }

    /** هل النظام سامح للتطبيق ده يثبّت حزم؟ */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    /** بيفتح إعدادات "تثبيت تطبيقات غير معروفة" لهذا التطبيق. */
    fun openInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            context.startActivity(
                Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setData(android.net.Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** بيفتح شاشة تثبيت النظام على الملف اللي اتنزّل. */
    fun install(context: Context, apk: File): Boolean = runCatching {
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", apk
        )
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    }.getOrDefault(false)
}
