package com.corewall.qaqc.diag

import android.content.Context
import android.os.Build
import com.corewall.qaqc.BuildConfig
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

/**
 * مسجّل الأعطال — بيكتب سبب أي قفلة على الجهاز نفسه.
 *
 * ليه موجود: النسخ بتتوزّع كملف APK مباشر، والمستخدم مش قاعد على كمبيوتر
 * فيه `adb logcat`. من غير ده، "التطبيق بيقفل لوحده" بيفضل صندوق أسود —
 * مفيش سطر ولا استثناء ولا حتى الشاشة اللي حصلت فيها، والتشخيص بيتحوّل
 * لتخمين على الكود.
 *
 * الفكرة بسيطة: نمسك الاستثناء اللي مامسكوش حد، نكتبه على القرص، وبعدين
 * نسيب التطبيق يقع زي ما هو (مانعطّلش السلوك الطبيعي). أول ما يفتح تاني،
 * الشاشة بتعرض التقرير عشان يتصوّر ويتبعت.
 */
object CrashReporter {

    private const val PREFS = "crash_reports"
    private const val KEY_REPORT = "lastReport"

    /**
     * بيتركّب **أول حاجة** في `onCreate` — أي كود بيجري قبله مش محمي.
     *
     * المعالِج القديم بيتنده بعدنا عشان مانكسرش سلوك النظام (ولا أي
     * معالِج تاني متركّب). لو مفيش معالِج قديم بنقفل العملية بنفسنا —
     * الرجوع من غير قفل بيسيب التطبيق شبح: عايش ومش بيرسم حاجة.
     */
    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { save(app, describe(thread.name, error)) }
            if (previous != null) {
                previous.uncaughtException(thread, error)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                exitProcess(10)
            }
        }
    }

    /**
     * تسجيل عطل **مش** قاتل — حاجة اتمسكت في `runCatching` وكانت هتعدّي
     * بصمت. فتح قاعدة البيانات أهم مثال: لو الترحيل وقع، الاستثناء
     * بيتبلع والتطبيق بيقفل بعدها بثانية من مكان تاني خالص.
     */
    fun record(context: Context, label: String, error: Throwable) {
        runCatching { save(context.applicationContext, describe(label, error)) }
    }

    /** التقرير المحفوظ، أو null لو مفيش. */
    fun pending(context: Context): String? =
        runCatching {
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_REPORT, null)
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()

    fun clear(context: Context) {
        runCatching {
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(KEY_REPORT).apply()
        }
    }

    private fun save(context: Context, report: String) {
        // `commit` مش `apply`: العملية بتموت بعد السطر ده مباشرة، و`apply`
        // بيكتب على خيط تاني — يعني التقرير ممكن يضيع بالظبط في الحالة
        // اللي اتعمل عشانها.
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_REPORT, report).commit()
    }

    private fun describe(where: String, error: Throwable): String {
        val stack = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        return buildString {
            appendLine("الوقت: $stamp")
            appendLine("الإصدار: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("الكوميت: ${BuildConfig.BUILD_COMMIT}")
            appendLine("الجهاز: ${Build.MANUFACTURER} ${Build.MODEL} · أندرويد ${Build.VERSION.RELEASE}")
            appendLine("المكان: $where")
            appendLine()
            append(stack.take(6000))
        }
    }
}
