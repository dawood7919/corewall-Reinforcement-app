package com.corewall.qaqc.pdfengine

import android.content.Context

/**
 * آخر مكان كنت واقف فيه في كل ملف.
 *
 * ليه مش في Room: ده **تفضيل عرض** مش بيانات مشروع. مالوش لازمة في النسخة
 * الاحتياطية، ومالوش علاقة بأي حاجة تانية في القاعدة، وبيتكتب كل ما تقفل
 * ملف. حطّه في جدول معناه ترحيل وDAO ونسخة احتياطية أتخن — مقابل صفر فايدة.
 *
 * الموقع بيتخزّن **صفحة + تكبير** بس، من غير الإزاحة. الإزاحة بالبكسل
 * ومربوطة بمقاس الشاشة، فلو فتحت الملف بعد ما لفّيت الجهاز أو على تابلت
 * الرقم بيبقى بلا معنى. الصفحة والتكبير بيرجّعوك لنفس المكان بصرياً
 * على أي شاشة.
 */
object PdfSessionStore {

    private const val PREFS = "pdf_session"
    private const val MAX_ENTRIES = 60

    data class Spot(val page: Int, val zoom: Float)

    fun save(context: Context, path: String, page: Int, zoom: Float) {
        if (path.isBlank() || page < 0) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(key(path), "$page|$zoom")
            .putLong(stampKey(path), System.currentTimeMillis())
            .apply()
        prune(context)
    }

    fun load(context: Context, path: String): Spot? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(key(path), null) ?: return null
        val parts = raw.split('|')
        if (parts.size != 2) return null
        val page = parts[0].toIntOrNull() ?: return null
        val zoom = parts[1].toFloatOrNull() ?: return null
        if (page < 0 || !zoom.isFinite() || zoom <= 0f) return null
        return Spot(page, zoom.coerceIn(ZoomLadder.MIN_ZOOM, ZoomLadder.MAX_ZOOM))
    }

    fun forget(context: Context, path: String) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(key(path)).remove(stampKey(path)).apply()
    }

    /**
     * بيشيل أقدم المداخل لما العدد يعدّي الحد.
     *
     * من غير ده الملف بيكبر للأبد: كل PDF اتفتح مرة بيفضل مسجّل، والمهندس
     * بيفتح مئات الملفات على مدار مشروع.
     */
    private fun prune(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stamps = prefs.all.entries
            .filter { it.key.startsWith(STAMP_PREFIX) }
            .mapNotNull { e -> (e.value as? Long)?.let { e.key to it } }
        if (stamps.size <= MAX_ENTRIES) return

        val editor = prefs.edit()
        stamps.sortedBy { it.second }
            .take(stamps.size - MAX_ENTRIES)
            .forEach { (stampKey, _) ->
                editor.remove(stampKey)
                editor.remove(SPOT_PREFIX + stampKey.removePrefix(STAMP_PREFIX))
            }
        editor.apply()
    }

    private const val SPOT_PREFIX = "spot:"
    private const val STAMP_PREFIX = "at:"

    private fun key(path: String) = SPOT_PREFIX + path
    private fun stampKey(path: String) = STAMP_PREFIX + path
}
