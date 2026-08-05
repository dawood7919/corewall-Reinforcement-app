package com.corewall.qaqc.ui.manpower

import androidx.compose.ui.graphics.Color
import com.corewall.qaqc.data.db.DailyAttendanceEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** التخصصات المتاحة لملف الحضور. */
enum class Trade(val label: String) {
    STEEL_FIXERS("حدادين تسليح"),
    CARPENTERS("نجارين"),
    HELPERS("مساعدين"),
    MASONS("بنّائين"),
    PAINTERS("نقّاشين"),
    ELECTRICIANS("كهربائيين"),
    PLUMBERS("سباكين"),
    HVAC("تكييف HVAC"),
    SCAFFOLDERS("سقالات"),
    ALUMINUM("ألومنيوم"),
    WATERPROOFING("عزل"),
    FINISHING("تشطيبات"),
    CONCRETE("خرسانة"),
    SURVEY("مساحة"),
    CLEANING("نظافة"),
    OTHER("أخرى");

    companion object {
        fun from(name: String) = entries.firstOrNull { it.name == name } ?: OTHER
    }
}

enum class Weather(val label: String, val emoji: String) {
    SUNNY("مشمس", "☀️"),
    CLOUDY("غائم", "☁️"),
    RAIN("ممطر", "🌧️"),
    HOT("حار", "🔥"),
    COLD("بارد", "❄️");

    companion object {
        fun from(name: String) = entries.firstOrNull { it.name == name } ?: SUNNY
    }
}

/** ألوان وسم الشركة المتاحة. */
val TAG_COLORS = listOf(
    0xFF5B66D6, 0xFF37B98A, 0xFFE8890C, 0xFFE53935,
    0xFF8E44AD, 0xFF2980B9, 0xFF16A085, 0xFFD64545
)

/**
 * اسم كل لون وسم. اللون لوحده ما ينفعش يبقى هو المعنى — قارئ الشاشة
 * محتاج اسم، والمستخدم اللي عنده عمى ألوان محتاج يفرّق بينهم.
 */
val TAG_COLOR_NAMES = listOf(
    "بنفسجي", "أخضر مزرق", "برتقالي", "أحمر",
    "بنفسجي غامق", "أزرق", "تركواز", "أحمر غامق"
)

fun tagColorName(color: Long): String {
    val i = TAG_COLORS.indexOf(color)
    return if (i >= 0) TAG_COLOR_NAMES[i] else "لون مخصّص"
}

private val dayFmt = SimpleDateFormat("EEEE", Locale("ar"))
private val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale("ar"))
private val shortDateFmt = SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH)
private val timeFmt = SimpleDateFormat("hh:mm a", Locale.ENGLISH)

fun dayName(millis: Long): String = dayFmt.format(Date(millis))
fun fullDate(millis: Long): String = dateFmt.format(Date(millis))
fun shortDate(millis: Long): String = shortDateFmt.format(Date(millis))
fun timeOf(millis: Long): String = timeFmt.format(Date(millis))

/** بداية اليوم (00:00) لأي وقت. */
fun dayStart(millis: Long): Long {
    val c = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    return c.timeInMillis
}

fun DailyAttendanceEntity.total(): Int = workers + foremen + engineers + supervisors

fun colorFor(argb: Long): Color = Color(argb)
