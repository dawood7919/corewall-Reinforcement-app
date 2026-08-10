package com.corewall.qaqc.notify

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.corewall.qaqc.MainActivity
import com.corewall.qaqc.R
import com.corewall.qaqc.data.db.AppDatabase
import com.corewall.qaqc.data.db.NoteEntity
import com.corewall.qaqc.domain.NotesLogic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * تذكيرات الملاحظات.
 *
 * التذكير منبّه حقيقي من النظام مش مؤقّت جوّه التطبيق: الملاحظة اللي
 * بتقول "كلّم المقاول ٧ الصبح" مالهاش قيمة لو التطبيق لازم يكون مفتوح
 * عشان تشتغل.
 *
 * **مش منبّه مضبوط بالثانية.** `setExactAndAllowWhileIdle` بيطلب صلاحية
 * `SCHEDULE_EXACT_ALARM` من أندرويد ١٢، ودي مخصّصة للمنبّهات والمواعيد
 * الحرجة. تذكير ملاحظة موقع مش منها — تأخير كام دقيقة مالوش أثر، وطلب
 * صلاحية زيادة على المستخدم ليه.
 */
object NoteReminders {

    const val CHANNEL_ID = "note_reminders"
    private const val EXTRA_NOTE_ID = "noteId"
    private const val EXTRA_TITLE = "title"
    private const val EXTRA_TEXT = "text"

    /** القناة بتتعمل مرة — إعادة إنشائها بنفس الـid مالهاش أثر. */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "تذكيرات الملاحظات",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "التذكيرات اللي بتحطّها على ملاحظاتك."
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * بيجدول (أو بيلغي) تذكير ملاحظة.
     *
     * التذكير اللي وقته عدّى مابيتجدولش: تشغيل التطبيق بعد أسبوع
     * ماينفعش يفجّر إشعارات قديمة كلها مرة واحدة.
     */
    fun schedule(context: Context, note: NoteEntity) {
        val at = note.reminderAt
        if (at == null || !note.isActive || at <= System.currentTimeMillis()) {
            cancel(context, note.id)
            return
        }
        ensureChannel(context)
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent(context, note))
    }

    fun cancel(context: Context, noteId: Long) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, NoteReminderReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context, noteId.toInt(), intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pending != null) {
            alarms.cancel(pending)
            pending.cancel()
        }
        NotificationManagerCompat.from(context).cancel(noteId.toInt())
    }

    private fun pendingIntent(context: Context, note: NoteEntity): PendingIntent {
        val intent = Intent(context, NoteReminderReceiver::class.java).apply {
            putExtra(EXTRA_NOTE_ID, note.id)
            putExtra(EXTRA_TITLE, note.title.ifBlank { "ملاحظة" })
            putExtra(EXTRA_TEXT, preview(note))
        }
        return PendingIntent.getBroadcast(
            context, note.id.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** أول سطر مفيد من الملاحظة — الإشعار مساحته سطرين. */
    private fun preview(note: NoteEntity): String {
        val source =
            if (note.kind == NoteEntity.KIND_CHECKLIST)
                NotesLogic.checklist(note.body).filterNot { it.done }
                    .joinToString(" · ") { it.text }
            else note.body
        return source.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
            .take(PREVIEW_CHARS)
    }

    /** بيبني الإشعار نفسه — مستخدم من المستقبِل. */
    fun notify(context: Context, noteId: Long, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, noteId.toInt(),
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_note)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(noteId.toInt(), notification)
        }
    }

    /**
     * إعادة جدولة كل التذكيرات المستقبلية.
     *
     * المنبّهات بتضيع مع إعادة التشغيل — دي طريقة أندرويد، مش عطل. فبنعيد
     * بناءها بعد الإقلاع، وكمان عند فتح التطبيق عشان الجهاز اللي التطبيق
     * اتشال منه من الذاكرة يفضل صادق.
     */
    suspend fun rescheduleAll(context: Context) {
        val now = System.currentTimeMillis()
        AppDatabase.get(context).noteDao().getAll()
            .filter { note -> note.isActive && (note.reminderAt ?: 0L) > now }
            .forEach { schedule(context, it) }
    }

    fun readIntent(intent: Intent): Triple<Long, String, String> = Triple(
        intent.getLongExtra(EXTRA_NOTE_ID, 0L),
        intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "ملاحظة" },
        intent.getStringExtra(EXTRA_TEXT).orEmpty()
    )

    private const val PREVIEW_CHARS = 180
}

/** بيتنادى في ميعاد التذكير. */
class NoteReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val (id, title, text) = NoteReminders.readIntent(intent)
        if (id == 0L) return
        NoteReminders.notify(context, id, title, text)
    }
}

/** بعد إقلاع الجهاز: المنبّهات بتترجّع من القاعدة. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                NoteReminders.rescheduleAll(app)
            } finally {
                pending.finish()
            }
        }
    }
}
