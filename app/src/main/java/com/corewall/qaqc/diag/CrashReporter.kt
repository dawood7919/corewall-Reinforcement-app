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
    private const val KEY_NATIVE = "insideNative"

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
    /**
     * علامة قبل الدخول في كود أصلي.
     *
     * ## ليه دي موجودة
     *
     * `Thread.setDefaultUncaughtExceptionHandler` بيمسك استثناءات جافا
     * بس. لما مكتبة أصلية تقع (`SIGSEGV`/`abort`) العملية بتموت **من
     * غير ما يتنده أصلاً** — فالتطبيق بيقفل ومفيش أي تقرير، والمستخدم
     * بيقول "بيقفل" وإحنا مالناش أي دليل.
     *
     * العلامة دي بتتكتب **قبل** النداء وبتتمسح بعده. لو التطبيق فتح
     * ولقى علامة لسه مكتوبة، يبقى المرة اللي فاتت مات وهو جوّه الحتة
     * دي بالظبط. مش أثر مكدّس — بس بيفرّق بين "فين" و"مش عارفين".
     *
     * `commit` مش `apply`: الكتابة لازم توصل القرص قبل النداء اللي ممكن
     * يقتل العملية، و`apply` غير متزامنة.
     */
    @Suppress("ApplySharedPref")
    fun enterNative(context: Context, what: String) {
        runCatching {
            prefs(context).edit().putString(KEY_NATIVE, what).commit()
        }
    }

    /** خرجنا بالسلامة — العلامة بتتشال. */
    fun leaveNative(context: Context) {
        runCatching { prefs(context).edit().remove(KEY_NATIVE).apply() }
    }

    /** علامة متعلّقة من تشغيلة فاتت، أو `null`. */
    fun pendingNative(context: Context): String? =
        runCatching { prefs(context).getString(KEY_NATIVE, null) }.getOrNull()

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

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

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
