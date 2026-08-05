package com.corewall.qaqc.domain

import java.util.Calendar

/**
 * أدوات وقت مشتركة. كانت مكرّرة جوّه الشاشات — والتكرار ده هو اللي بيخلّي
 * شاشتين يحسبوا "النهاردة" بطريقتين مختلفتين.
 */

/** بداية اليوم الحالي بتوقيت الجهاز. */
fun startOfToday(): Long = startOfDay(System.currentTimeMillis())

/** بداية اليوم اللي فيه [ts]. */
fun startOfDay(ts: Long): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = ts
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

/** وقت نسبي مختصر بالعربي — "من ٥ دقايق"، "امبارح". */
fun relativeTime(ts: Long): String {
    if (ts <= 0) return ""
    val minutes = (System.currentTimeMillis() - ts) / 60_000
    return when {
        minutes < 1 -> "دلوقتي"
        minutes < 60 -> "من $minutes دقيقة"
        minutes < 60 * 24 -> "من ${minutes / 60} ساعة"
        minutes < 60 * 48 -> "امبارح"
        else -> "من ${minutes / (60 * 24)} يوم"
    }
}
