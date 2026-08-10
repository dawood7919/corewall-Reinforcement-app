package com.corewall.qaqc.pdfengine

/**
 * ماسك جلسة مستند — بيمنع تسريب المستند لو الشاشة اتقفلت وهي بتفتحه.
 *
 * النمط اللي كان مكتوب:
 *
 * ```
 * LaunchedEffect(path) { session = withContext(IO) { open(...) } }
 * DisposableEffect(path) { onDispose { session?.close() } }
 * ```
 *
 * فيه ثغرة حقيقية: لو المستخدم فتح رسمة تقيلة وضغط رجوع قبل ما تخلص
 * فتح، الـ`LaunchedEffect` بيتلغي **بعد** ما `PdfiumCore` فتح المستند
 * فعلاً وقبل ما القيمة توصل للحالة. ساعتها `session` لسه `null`، فالـ
 * `onDispose` مابيقفلش حاجة — والمستند الأصلي والـ`ParcelFileDescriptor`
 * وخيط الرندر بيفضلوا عايشين لحد ما العملية تموت. تكرار الحركة دي على
 * مجلد رسمات بيراكم خيوط وذاكرة أصلية مش شايفها الـGC.
 *
 * الماسك ده بيخلّي القفل قرار **مشترك**: اللي يوصل الأول يكسب. لو الشاشة
 * اتقفلت الأول، الجلسة اللي بتوصل متأخرة بتتقفل فوراً.
 *
 * كل الوصول من خيط الواجهة، فمفيش داعي لأقفال.
 */
class PdfSessionHolder {

    private var session: PdfDocumentSession? = null
    private var disposed = false

    /**
     * بيسلّم جلسة اتفتحت. بيرجّع `true` لو الجلسة اتقبلت، و`false` لو
     * الشاشة كانت اتقفلت خلاص — وساعتها بتكون اتقفلت هنا.
     */
    fun accept(opened: PdfDocumentSession): Boolean {
        if (disposed) {
            runCatching { opened.close() }
            return false
        }
        session = opened
        return true
    }

    /** بيتنده من `onDispose`. بيقفل اللي موجود وبيمنع أي جلسة جاية بعده. */
    fun dispose() {
        disposed = true
        session?.let { runCatching { it.close() } }
        session = null
    }
}
