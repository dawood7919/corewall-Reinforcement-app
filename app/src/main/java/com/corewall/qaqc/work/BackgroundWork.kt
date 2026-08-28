package com.corewall.qaqc.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.corewall.qaqc.MainActivity
import com.corewall.qaqc.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * شغل بيكمّل وإنت في تطبيق تاني.
 *
 * ## المشكلة الحقيقية
 *
 * أندرويد بيقتل عملية التطبيق اللي في الخلفية تحت ضغط الذاكرة، من غير
 * إنذار. طلب المساعد ممكن ياخد دقيقة (أربع جولات × مهلة قراءة ٩٠ ثانية)،
 * وتحميل التحديث ٧٠ ميجا — والاتنين بيتحصلوا بالظبط في الوقت اللي
 * المستخدم بيسيب التطبيق فيه، لأن مفيش حاجة تتفرّج عليها وهي بتحصل.
 *
 * **خدمة أمامية** هي الطريقة الوحيدة المدعومة إن العملية تفضل عايشة:
 * النظام بيعامل التطبيق اللي ليه إشعار ظاهر كشغل المستخدم عارفه وطالبه،
 * فبيبقى آخر حاجة تتقتل مش أولها.
 *
 * ## ليه سجل مش خدمة لكل حاجة
 *
 * الشغلانتين مختلفتين في المحتوى بس متطابقتين في المطلوب: خليك عايش
 * واعرض بتعمل إيه. خدمة واحدة بسجل شغلانات بتعرض إشعار واحد ملخّص —
 * وبتقفل نفسها أول ما آخر شغلانة تخلص، فمفيش إشعار فاضل بعد الشغل.
 */
object BackgroundWork {

    const val CHANNEL_ID = "background_work"
    private const val NOTIFICATION_ID = 4711

    /** id الشغلانة → السطر اللي بيتعرض للمستخدم. */
    internal val jobs = MutableStateFlow<Map<String, String>>(emptyMap())

    /** id ثابت لكل نوع شغل — نفس النوع مايتسجّلش مرتين. */
    const val JOB_AI = "ai"
    const val JOB_UPDATE = "update"

    /**
     * بيبدأ (أو بيحدّث) شغلانة.
     *
     * التنده بنفس [id] بيحدّث السطر بس — الخدمة بتشتغل مرة واحدة.
     */
    fun start(context: Context, id: String, label: String) {
        val first = jobs.value.isEmpty()
        jobs.value = jobs.value + (id to label)
        if (!first) return

        // ── ليه كل ده جوّه `runCatching`
        //
        // الإشعار ده **راحة**، مش الشغل نفسه. وتشغيل خدمة أمامية بقى
        // مليان قيود بتتغيّر مع كل إصدار أندرويد: ممنوع من الخلفية من
        // 12، النوع إجباري من 14، وميزانية يومية من 15 — وكل واحدة
        // بترمي استثناء مختلف.
        //
        // من غير الحارس ده، أي قيد من دول بيقفل التطبيق **وانت بتبعت
        // رسالة**. يعني ميزة كل غرضها إنك تسيب التطبيق وهو شغّال بتمنعك
        // تستخدمه أصلاً. أسوأ نتيجة مقبولة هنا: مفيش إشعار، والرسالة
        // بتكمّل عادي.
        runCatching {
            val intent = Intent(context, BackgroundWorkService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    /** بينهي شغلانة. آخر واحدة بتقفل الخدمة والإشعار. */
    fun finish(context: Context, id: String) {
        jobs.value = jobs.value - id
    }
}

/**
 * الخدمة نفسها — مالهاش منطق، بس بتعرض [BackgroundWork.jobs] وبتفضل
 * عايشة طول ما فيه حاجة فيه.
 */
class BackgroundWorkService : Service() {

    private var watcher: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        runCatching { ensureChannel() }
        // لازم يتنده فوراً — النظام بيقتل الخدمة لو اتأخّر.
        //
        // ولو رفض (ميزانية خلصت، أو قيد إصدار)، بنوقف الخدمة بهدوء بدل
        // ما الاستثناء يطلع للنظام ويقفل العملية كلها.
        val started = runCatching {
            startInForeground(notification(BackgroundWork.jobs.value.values.toList()))
        }.isSuccess
        if (!started) {
            runCatching { stopSelf() }
            return START_NOT_STICKY
        }

        if (watcher == null) {
            watcher = scope.launch {
                BackgroundWork.jobs.collect { current ->
                    runCatching {
                        if (current.isEmpty()) {
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        } else {
                            getSystemService(NotificationManager::class.java)
                                ?.notify(NOTIFICATION_ID, notification(current.values.toList()))
                        }
                    }
                }
            }
        }
        // الخدمة عمرها مربوط بالشغل الجاري، مش بالـintent. لو النظام قتل
        // العملية، إعادة تشغيل الخدمة من غير الشغل نفسه مالهاش أي معنى.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        watcher?.cancel()
        watcher = null
        super.onDestroy()
    }

    private fun startInForeground(n: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // من أندرويد 14 النوع إجباري، والنوع الصح هنا نقل بيانات
            // على الشبكة — ده كل اللي الشغلانتين بيعملوه.
            startForeground(
                NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
    }

    private fun notification(labels: List<String>): Notification {
        val open = android.app.PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            android.app.PendingIntent.FLAG_IMMUTABLE or
                android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )
        val title = labels.firstOrNull() ?: "شغل جاري"
        return NotificationCompat.Builder(this, BackgroundWork.CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(if (labels.size > 1) "و${labels.size - 1} حاجة تانية" else null)
            .setSmallIcon(R.drawable.ic_stat_note)
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        // أهمية منخفضة عن قصد: ده إشعار "لسه شغّال"، مش حاجة تقطع على
        // المستخدم بصوت واهتزاز كل مرة يسأل سؤال.
        manager.createNotificationChannel(
            NotificationChannel(
                BackgroundWork.CHANNEL_ID,
                "شغل في الخلفية",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "المساعد الذكي وتحميل التحديثات وهما شغّالين ورا."
                setShowBadge(false)
            }
        )
    }

    private companion object {
        const val NOTIFICATION_ID = 4711
    }
}
