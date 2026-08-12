package com.corewall.qaqc.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** ربط عنصر المسقط باسمه المرجعي (T1-W… / T1-CB…). */
@Serializable
@Entity(tableName = "element_names")
data class ElementNameEntity(
    @PrimaryKey val elementId: String,
    val mark: String
)

/** حالة الفحص لكل عنصر لكل دور. */
@Serializable
@Entity(tableName = "inspections", primaryKeys = ["elementId", "level"])
data class InspectionEntity(
    val elementId: String,
    val level: String,
    val status: String
)

/** كومنت على عنصر بتاريخ ووقت (ومربوط بالدور اللي كان مختار وقت كتابته). */
@Serializable
@Entity(tableName = "comments")
data class CommentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val elementId: String,
    val level: String,
    val text: String,
    val timestamp: Long
)

/**
 * عدّاد أسياخ (عدسة العدّ): صف واحد = عدد + قطر لعنصر معيّن **في دور معيّن**
 * من مصدر "SITE" (الموقع) أو "DRAWING" (الشوب دروينج).
 * كل دور معزول تماماً عن غيره.
 */
@Serializable
@Entity(tableName = "bar_counts")
data class BarCountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val elementId: String,
    @ColumnInfo(defaultValue = "GROUND") val level: String = "GROUND",
    val source: String,
    val diameter: Int,
    val count: Int
) {
    companion object {
        const val SOURCE_SITE = "SITE"
        const val SOURCE_DRAWING = "DRAWING"
    }
}

/**
 * مرفق/كومنت على عنصر في دور معيّن (أداة Data — قسم بلان فيل).
 * type = COMMENT: كومنت نصي في text.
 * type = FILE: ملف متخزن على القرص في filePath واسمه المعروض في text.
 */
@Serializable
@Entity(tableName = "element_attachments")
data class ElementAttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val elementId: String,
    val level: String,
    val type: String,
    val text: String,
    val filePath: String? = null,
    val timestamp: Long
) {
    companion object {
        const val TYPE_COMMENT = "COMMENT"
        const val TYPE_FILE = "FILE"
    }
}

/**
 * ملف حضور (مقاول/تخصص) في دور معيّن — أداة Manpower.
 * المشروع ← الدور ← ملف الحضور ← سجلات الحضور اليومية.
 */
@Serializable
@Entity(tableName = "attendance_files")
data class AttendanceFileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val level: String,
    val company: String,
    val trade: String,
    val startDate: Long,
    val notes: String = "",
    val colorTag: Long = 0xFF5B66D6,
    val logoPath: String? = null,
    val createdAt: Long
)

/** سجل حضور يومي داخل ملف حضور. */
@Serializable
@Entity(tableName = "daily_attendance")
data class DailyAttendanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileId: Long,
    val date: Long,
    val workers: Int = 0,
    val foremen: Int = 0,
    val engineers: Int = 0,
    val supervisors: Int = 0,
    val overtimeHours: Double = 0.0,
    val weather: String = "SUNNY",
    val remarks: String = "",
    val updatedAt: Long
)

/**
 * ملاحظة غنية على عنصر في دور معيّن (عزل كامل لكل دور):
 * نص قابل للتنسيق (Markdown) + صور مرفقة (مسارات ملفات) + عنوان.
 */
@Serializable
@Entity(
    tableName = "notes",
    // الفهارس دي بتخدم استعلامات الشاشة الرئيسية: التثبيت أولاً، بعدين
    // المؤرشف والمهملات مستبعدين. من غيرها كل فتحة للملاحظات بتمسح
    // الجدول كله — وده بيبان مع أول ٥٠٠ ملاحظة.
    indices = [
        Index(value = ["archived", "deletedAt"]),
        Index(value = ["pinned"]),
        Index(value = ["level"])
    ]
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /**
     * العنصر اللي الملاحظة متعلّقة بيه، أو [com.corewall.qaqc.FLOOR_NOTE_ID]
     * للملاحظة الحرّة (اللي مش مربوطة بعنصر).
     *
     * الحقل ده **مش اختياري** عن قصد: هو اللي بيربط الملاحظة بسياق
     * المشروع، وهو اللي بيخلّي المساعد الذكي يعرف الملاحظة دي بتتكلم عن
     * إيه. الملاحظة الحرّة مجرد حالة خاصة منه.
     */
    val elementId: String,
    val level: String,
    val title: String = "",
    val body: String = "",
    val imagePathsJson: String = "[]",
    val createdAt: Long,
    val updatedAt: Long,

    // ─────────────────────────────────────────────────────────────
    // تنظيم الملاحظات
    // ─────────────────────────────────────────────────────────────

    val pinned: Boolean = false,
    val archived: Boolean = false,

    /**
     * وقت الحذف — `null` يعني الملاحظة حيّة.
     *
     * الحذف بينقل للمهملات مش بيمسح. الوقت متخزّن مش مجرد علم عشان
     * التنظيف التلقائي بعد [TRASH_RETENTION_DAYS] يوم يبقى ممكن، وعشان
     * المستخدم يشوف "اتحذفت امبارح".
     */
    val deletedAt: Long? = null,

    /** ٠ = لون الثيم الافتراضي. غير كده ARGB من لوحة محدودة. */
    val colorArgb: Long = 0,

    /** TEXT أو CHECKLIST — بيحدّد المحرّر اللي بيتفتح، مش شكل التخزين. */
    val kind: String = KIND_TEXT,

    /** وقت التذكير بالميلي ثانية، أو `null`. */
    val reminderAt: Long? = null,

    // ─────────────────────────────────────────────────────────────
    // حقول هندسية — كلها اختيارية
    //
    // موجودة عشان الملاحظة في الموقع تقدر تبقى ملاحظة **هندسية**: ملاحظة
    // جودة، أو RFI، أو نتيجة تفتيش. سايبينها فاضية افتراضياً عشان إنشاء
    // ملاحظة سريعة يفضل ضغطة وكتابة.
    // ─────────────────────────────────────────────────────────────

    /** QA / RFI / INSPECTION / SAFETY / "" */
    val noteType: String = "",

    /** ٠ عادي · ١ مهم · ٢ عاجل — نفس سلّم المهام. */
    val priority: Int = 0
) {
    val isTrashed: Boolean get() = deletedAt != null

    /** ملاحظة حيّة تظهر في الشاشة الرئيسية. */
    val isActive: Boolean get() = !archived && deletedAt == null

    companion object {
        const val KIND_TEXT = "TEXT"
        const val KIND_CHECKLIST = "CHECKLIST"

        const val TYPE_QA = "QA"
        const val TYPE_RFI = "RFI"
        const val TYPE_INSPECTION = "INSPECTION"
        const val TYPE_SAFETY = "SAFETY"

        /** بعدها الملاحظة المحذوفة بتتشال نهائياً. */
        const val TRASH_RETENTION_DAYS = 30
    }
}

/**
 * تصنيف ملاحظات.
 *
 * جدول حقيقي مش نصّ في الملاحظة: التسمية لازم يكون ليها **هوية**. من
 * غيرها، إعادة تسمية "جودة" لـ"QA/QC" معناها تعديل كل ملاحظة فيها الكلمة
 * دي، والحذف معناه بحث نصّي في كل الجدول.
 */
@Serializable
@Entity(tableName = "note_labels", indices = [Index(value = ["name"], unique = true)])
data class NoteLabelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorArgb: Long = 0,
    val createdAt: Long
)

/** ربط ملاحظة بتصنيف — علاقة متعدّد لمتعدّد. */
@Serializable
@Entity(
    tableName = "note_label_links",
    primaryKeys = ["noteId", "labelId"],
    indices = [Index(value = ["labelId"])]
)
data class NoteLabelLinkEntity(
    val noteId: Long,
    val labelId: Long
)

/**
 * مهمة في الـTo-Do — **مربوطة بدور واحد** (عزل كامل: مهام الدور بتظهر
 * لما تكون شغّال عليه بس). priority: 0 عادي / 1 مهم / 2 عاجل.
 */
@Serializable
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String = "",
    val done: Boolean = false,
    val priority: Int = 0,
    val dueDate: Long? = null,
    @ColumnInfo(defaultValue = "GROUND") val level: String = "GROUND",
    val createdAt: Long,
    val completedAt: Long? = null
)

/**
 * تعليق مرسوم على صفحة PDF (هايلايت/سهم/مستطيل/دايرة/رسم حر).
 * النقط متخزنة منسّبة (0..1) لأبعاد الصفحة عشان تثبت مع أي زوم أو تصدير.
 */
@Serializable
@Entity(tableName = "pdf_annotations", indices = [Index(value = ["filePath"])])
data class PdfAnnotationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filePath: String,
    val page: Int,
    val tool: String,
    val color: Long,
    val pointsJson: String,
    val createdAt: Long,
    /**
     * سُمك الخط **بنقط الـPDF** مش بالبكسل.
     *
     * الفرق مهم: النقطة وحدة على الورقة نفسها، فالعلامة بتكبر وتصغر مع
     * التكبير زي ما لو كانت متحبّرة على الرسمة فعلاً. لو خزّنّاه بالبكسل،
     * الخط كان هيفضل بنفس السُمك على الشاشة مهما قرّبت — يعني عند ٢٠×
     * بيبقى شعرة، وعند ٠.٥× بيغطّي نص الرسمة.
     */
    val strokeWidth: Float = 2.5f,
    /** ٠..١ — التظليل بيستخدم قيم منخفضة عشان اللي تحته يفضل مقروء. */
    val opacity: Float = 1f
) {
    companion object {
        const val TOOL_HIGHLIGHT = "HIGHLIGHT"
        const val TOOL_RECT = "RECT"
        const val TOOL_CIRCLE = "CIRCLE"
        const val TOOL_ARROW = "ARROW"
        const val TOOL_FREEHAND = "FREEHAND"

        // أدوات جديدة. القديمة فوق سايبينها بأسمائها عشان العلامات
        // المتخزّنة من قبل تفضل تتقري.
        const val TOOL_MARKER = "MARKER"
        const val TOOL_LINE = "LINE"
        const val TOOL_CLOUD = "CLOUD"
    }
}

/**
 * علامة مرجعية في ملف PDF — صفحة بعنوان يكتبه المستخدم.
 *
 * منفصلة عن فهرس المستند (outline) عن قصد: الفهرس بيجي من الملف نفسه
 * ومش قابل للتعديل، والعلامات دي بتاعت المستخدم — "تفاصيل الكانات" في
 * صفحة ٤٧ عمر ما هتكون مكتوبة في فهرس المكتب الاستشاري.
 *
 * الفهرس على `filePath` مطلوب: العارض بيفلتر بيه في كل فتحة ملف.
 */
@Serializable
@Entity(
    tableName = "pdf_bookmarks",
    indices = [Index(value = ["filePath"])]
)
data class PdfBookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filePath: String,
    val page: Int,
    val label: String,
    val createdAt: Long
)

/**
 * قياس مرسوم على صفحة PDF — مسافة أو مساحة أو نقطة عدّ.
 *
 * النقط منسّبة (٠..١) لأبعاد الصفحة زي التعليقات، فبتفضل مظبوطة مع أي
 * تكبير. القيمة نفسها **مش متخزّنة**: بتتحسب من النقط × معايرة الصفحة
 * وقت العرض. لو خزّنّاها، تغيير المعايرة كان هيسيب أرقام قديمة غلط
 * جنب أرقام جديدة صح في نفس الصفحة.
 */
@Serializable
@Entity(
    tableName = "pdf_measurements",
    indices = [Index(value = ["filePath"])]
)
data class PdfMeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filePath: String,
    val page: Int,
    /** DISTANCE / AREA / COUNT */
    val kind: String,
    val pointsJson: String,
    val label: String = "",
    val colorArgb: Long,
    val createdAt: Long
)

/**
 * معايرة المقياس لصفحة.
 *
 * [page] = −١ معناها معايرة للمستند كله. البحث بيدوّر على الصفحة الأول
 * وبعدين على العام — عشان رسمة بمقياس مختلف جوّه ملف واحد تفضل ممكنة،
 * وده بيحصل كتير في ملفات التسليم المجمّعة.
 */
@Serializable
@Entity(tableName = "pdf_scales", primaryKeys = ["filePath", "page"])
data class PdfScaleEntity(
    val filePath: String,
    val page: Int,
    /** وحدات حقيقية لكل نقطة PDF. */
    val unitsPerPoint: Double,
    /** mm / cm / m */
    val unit: String,
    val note: String = "",
    val updatedAt: Long
) {
    companion object {
        /** معايرة على مستوى المستند كله. */
        const val WHOLE_DOCUMENT = -1
    }
}

/**
 * تعديل يدوي على صف من جدول التسليح المرجعي.
 * patchJson: خريطة {اسم الحقل -> القيمة الجديدة} بصيغة JSON،
 * بتتطبق فوق بيانات الأصول (read-only) وقت العرض.
 */
@Serializable
@Entity(tableName = "range_edits", primaryKeys = ["mark", "rowIndex"])
data class RangeEditEntity(
    val mark: String,
    val rowIndex: Int,
    val patchJson: String
)

/**
 * صورة موقع (Site Photo) مربوطة بدور واحد فقط.
 * folder: مسار نسبي من جذر صور الدور ("" = الجذر، "Inspection" = مجلد فرعي).
 * التعليق يظهر **مكتوبًا فوق الصورة** (overlay) وليس كعنوان منفصل فقط.
 */
@Serializable
@Entity(tableName = "site_photos")
data class SitePhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val level: String,
    val filePath: String,
    val comment: String = "",
    val timestamp: Long,
    /** مجلد نسبي داخل site-photos/{level}/ — فارغ = الجذر */
    @ColumnInfo(defaultValue = "") val folder: String = ""
)

/**
 * آخر تحليل AI لكل دور — متخزّن عشان يتعرض فوراً وأوفلاين
 * من غير ما نستدعي الخدمة كل مرة. مفتاح الجدول = الدور.
 */
@Entity(tableName = "ai_analysis")
data class AiAnalysisEntity(
    @PrimaryKey val level: String,
    val json: String,
    val model: String,
    val createdAt: Long
)

/**
 * مستند اتحلّل بالـ AI. الملف نفسه بيفضل على القرص —
 * ده "المعرفة" المستخرجة منه عشان يبقى قابل للبحث والفهم.
 */
@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filePath: String,
    val fileName: String,
    /** الدور اللي اتربط بيه تلقائياً (أو الدور الشغّال وقت الرفع). */
    val level: String,
    /** DRAWING | BBS | INSPECTION | METHOD_STATEMENT | SUBMITTAL | REPORT | PHOTO | OTHER */
    val docType: String,
    val title: String,
    val drawingNumber: String,
    val revision: String,
    val discipline: String,
    val company: String,
    val engineer: String,
    val docDate: String,
    val summary: String,
    /** PENDING | ANALYZING | DONE | FAILED | UNSUPPORTED */
    val status: String,
    val error: String,
    val analyzedAt: Long,
    val createdAt: Long,
    /**
     * اسم البرومبت اللي اتحلّل بيه. فاضي = التحليل الافتراضي.
     * بيتخزّن عشان "حلّل تاني" يستخدم نفس البرومبت بدل ما يرجع للعام —
     * غير كده إعادة التحليل بتلغي اختيار المستخدم من غير ما يحس.
     */
    val promptName: String = ""
)

/**
 * حقيقة مستخرجة من مستند — دي حجر بناء الـKnowledge Graph:
 * key = الكيان (كود حائط / بار مارك / قطر)، والربط بيحصل بالـkey عبر المستندات.
 */
@Entity(tableName = "doc_facts")
data class DocFactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val level: String,
    /** BAR_MARK | WALL_REF | DIAMETER | QUANTITY | DIMENSION | GRID | NOTE | DATE | PARTY | OTHER */
    val kind: String,
    val key: String,
    val value: String,
    val unit: String,
    val numericValue: Double
)

/**
 * رسالة في محادثة المساعد الهندسي (لكل دور).
 *
 * الفهرس على [threadId] **لازم** يتعرّف هنا مش في الترحيل بس: Room بيقارن
 * المخطط المتولّد من الكيان بالقاعدة الحقيقية، وأي فهرس موجود في وحدة منهم
 * بس بيرمي `IllegalStateException` أول ما القاعدة تتفتح — يعني التطبيق
 * بيقفل بعد الفتح بثانيتين. الاسم سايبينه لـRoom عن قصد (`index_…`) عشان
 * الترحيل والكيان يطلّعوا نفس الجملة بالحرف.
 */
@Entity(
    tableName = "chat_messages",
    indices = [Index(value = ["threadId"])]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val level: String,
    /** المحادثة اللي الرسالة تبعها. صفر = رسايل قديمة من قبل ما المحادثات توجد. */
    val threadId: Long = 0,
    /** user | assistant */
    val role: String,
    val content: String,
    val createdAt: Long
)

// ═══════════════════════════════════════════════════════════════════
// إعادة تصميم الموديولات — الجداول الجديدة
// ═══════════════════════════════════════════════════════════════════

/**
 * بيانات إضافية لملف على القرص.
 *
 * الملفات نفسها بتفضل على نظام الملفات زي ما هي — الجدول ده بيعلّق عليها
 * وسوم ومفضّلة ونصّ مستخرج، من غير ما ينقل الملف نفسه لقاعدة البيانات.
 * المفتاح هو المسار، فلو الملف اتمسح من برّه السطر بيبقى يتيم وبيتنضّف
 * لوحده — أرخص من إننا نحاول نمسك كل تعديل على القرص.
 *
 * [ocrText] هو اللي بيخلّي البحث في الملفات مفيد فعلاً: "W12" يلاقي صفحة
 * الـBBS اللي فيها الكود، مش بس اسم الملف.
 */
@Entity(tableName = "file_meta")
data class FileMetaEntity(
    @PrimaryKey val path: String,
    val favourite: Boolean = false,
    /** وسوم مفصولة بفاصلة — قليلة العدد لكل ملف، فمش محتاجة جدول منفصل. */
    val tags: String = "",
    /** النص المستخرج بالتعرّف الضوئي — فاضي لحد ما التحليل يجري. */
    val ocrText: String = "",
    /** NONE | PENDING | RUNNING | DONE | FAILED | UNSUPPORTED */
    val ocrStatus: String = OCR_NONE,
    val lastOpenedAt: Long = 0,
    val updatedAt: Long = 0
) {
    companion object {
        const val OCR_NONE = "NONE"
        const val OCR_PENDING = "PENDING"
        const val OCR_RUNNING = "RUNNING"
        const val OCR_DONE = "DONE"
        const val OCR_FAILED = "FAILED"
        const val OCR_UNSUPPORTED = "UNSUPPORTED"
    }

    val tagList: List<String>
        get() = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}

/**
 * محادثة — مجموعة رسائل ليها عنوان وحياة أطول من الجلسة.
 *
 * قبل كده الرسايل كانت مربوطة بالدور بس، فمفيش "محادثات" أصلاً: مفيش
 * تاريخ ولا بحث ولا تثبيت ولا مجلدات. الجدول ده بيدّي الرسايل مالك.
 */
@Entity(tableName = "chat_threads")
data class ChatThreadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** الدور اللي المحادثة اتعملت فيه — العزل بالدور بيفضل قايم. */
    val level: String,
    val title: String,
    val pinned: Boolean = false,
    /** اسم المجلد، أو فاضي يعني مفيش تصنيف. */
    val folder: String = "",
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * رابط بين أي كيانين في التطبيق.
 *
 * ده اللي بيخلّي "كل ملاحظة مربوطة بالرسومات والأدوار والعناصر والفحوصات
 * والصبّات والعمالة" جملة حقيقية مش كلام. جدول واحد عام أرخص بكتير من
 * عمود مفتاح أجنبي في كل جدول لكل نوع ربط ممكن.
 *
 * الفهارس هنا مش رفاهية: كل قراءة رابط بتسأل بالطرف (نوع + معرّف)، ومن غير
 * فهرس دي بتبقى مسح كامل للجدول. ولازم تتعرّف على الكيان نفسه — بص على
 * التعليق فوق [ChatMessageEntity].
 */
@Entity(
    tableName = "links",
    indices = [
        Index(value = ["fromType", "fromId"]),
        Index(value = ["toType", "toId"])
    ]
)
data class LinkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** NOTE | FILE | PHOTO | ELEMENT | INSPECTION | TASK | THREAD | ATTENDANCE */
    val fromType: String,
    val fromId: String,
    val toType: String,
    val toId: String,
    val level: String,
    val createdAt: Long
) {
    companion object {
        const val NOTE = "NOTE"
        const val FILE = "FILE"
        const val PHOTO = "PHOTO"
        const val ELEMENT = "ELEMENT"
        const val INSPECTION = "INSPECTION"
        const val TASK = "TASK"
        const val THREAD = "THREAD"
        const val ATTENDANCE = "ATTENDANCE"
    }
}

/**
 * برومبت محفوظ باسم — "BBS"، "رسمة تسليح"، "طلب فحص"… إلخ.
 *
 * ليه ده موجود: البرومبت الواحد الجامد كان بيتعامل مع كل المستندات بنفس
 * الطريقة، فجدول حديد (BBS) وطلب فحص وكشف تسليح كلهم بيتقروا بنفس
 * التعليمات. النتيجة تحليل عام وغالباً غلط. المهندس هو اللي عارف الملف ده
 * بيتقري إزاي، فالمكان الصح للمعرفة دي عنده مش عندنا.
 *
 * [body] **تعليمات إضافية** — مش بديل للعقد. مخطط الرد بيفضل بتاع التطبيق
 * عشان الحقائق تتخزّن وتبقى قابلة للبحث؛ اللي بيتغيّر هو طريقة قراية
 * المستند نفسه.
 */
@Serializable
@Entity(tableName = "prompts")
data class PromptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val body: String,
    /** كام مرة اتستخدم — البرومبت الأكتر استخداماً بيطلع فوق في القايمة. */
    val usageCount: Int = 0,
    val lastUsedAt: Long = 0,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * كود (mark) اتضاف من المستخدم فوق جدول المكتب.
 *
 * جدول الأصول (`schedule-data.json`) للقراية بس — جاي من المكتب الفني وما
 * ينفعش يتكتب فوقه. الجدول ده بيشيل الأكواد اللي المهندس بيضيفها بنفسه
 * (كمرات داخلية مثلاً)، والاتنين بيتدمجوا وقت العرض.
 *
 * [rowsJson] صفوف المدى متسلسلة — `BeamRange` للكمرات و`WallRange` للحوائط.
 * الكود هو المفتاح، فاستيراد نفس الكود تاني بيستبدله بدل ما يكرّره.
 */
@Serializable
@Entity(tableName = "imported_marks")
data class ImportedMarkEntity(
    @PrimaryKey val mark: String,
    /** BEAM | WALL */
    val kind: String,
    val rowsJson: String,
    /** اسم الملف اللي اتستورد منه — عشان تعرف الكود ده جه منين. */
    val source: String,
    val rowCount: Int,
    val createdAt: Long
) {
    companion object {
        const val BEAM = "BEAM"
        const val WALL = "WALL"
    }
}

/**
 * نسخة سابقة من ملاحظة. بتتكتب عند كل حفظ.
 *
 * في سياق ضبط جودة الملاحظة دليل — ولو اتعدّلت بعد واقعة، اللي كان مكتوب
 * قبل التعديل مهم. الجدول append-only عن قصد.
 */
@Entity(tableName = "note_revisions")
data class NoteRevisionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val title: String,
    val body: String,
    val savedAt: Long
)

// ══════════════════════════════════════════════════ حصر الكميات

/**
 * قسم حصر — **مستقل تماماً عن باقي التطبيق**.
 *
 * الحصر مش مربوط بدور ولا بملف من ملفات المشروع. المستخدم بيعمل قسم
 * ("عمارة الشيخ زايد"، "تشطيبات الدور الأرضي")، بيرفع جوّاه رسمات، وبيشتغل.
 * الفصل ده مقصود: الحصر شغل تسعير بيتعمل على رسمات جاية من برّه، وربطه
 * بشجرة أدوار المشروع كان هيجبر المستخدم يخترع دور لكل حاجة بيحصرها.
 */
@Serializable
@Entity(tableName = "takeoff_projects")
data class TakeoffProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val note: String = "",
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * رسمة جوّه قسم حصر.
 *
 * [filePath] نسخة **جوّه مساحة التطبيق**، مش المسار اللي المستخدم اختاره
 * منه. الـURI اللي جاي من منتقي الملفات صلاحيته مؤقتة، ولو اعتمدنا عليه
 * الرسمة بتختفي بعد إعادة التشغيل — والحصر اللي عليها يبقى معلّق على ملف
 * مش موجود.
 */
@Serializable
@Entity(
    tableName = "takeoff_drawings",
    indices = [Index(value = ["projectId"])]
)
data class TakeoffDrawingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val name: String,
    val filePath: String,
    val pageCount: Int = 1,
    val createdAt: Long
)

/**
 * معايرة صفحة في رسمة حصر.
 *
 * لكل صفحة معايرتها لوحدها: مجموعة رسمات ممكن تكون متطبوعة بمقاييس
 * مختلفة، ومعايرة واحدة للملف كله بتخلّي نص الأرقام غلط من غير ما حد
 * ياخد باله.
 */
@Serializable
@Entity(tableName = "takeoff_scales", primaryKeys = ["drawingId", "page"])
data class TakeoffScaleEntity(
    val drawingId: Long,
    val page: Int,
    /** متر حقيقي لكل نقطة PDF. */
    val metresPerPoint: Double,
    val note: String = ""
)

/**
 * فئة حصر — "خرسانة"، "بلاط"، "حوائط". **مستوى المشروع** مش الرسمة:
 * فئة واحدة بتتشارك بين كل رسمات القسم، لأن مشروع واحد بيستخدم نفس
 * التصنيف على أكتر من لوحة.
 *
 * [rate] و[wastePct] افتراضي الفئة — أي بند جواها بياخدهم إلا لو
 * اتحدّد له تجاوز خاص (`TakeoffItemEntity.rateOverride`).
 */
@Serializable
@Entity(tableName = "takeoff_categories", indices = [Index(value = ["projectId"])])
data class TakeoffCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val name: String,
    val colorArgb: Long,
    val rate: Double = 0.0,
    val wastePct: Double = 0.0,
    val sortOrder: Int = 0,
    val createdAt: Long
)

/** مجموعة جوّه فئة — تقسيم فرعي اختياري ("حوائط ← الدور الأرضي"). */
@Serializable
@Entity(tableName = "takeoff_groups", indices = [Index(value = ["categoryId"])])
data class TakeoffGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val name: String,
    val sortOrder: Int = 0
)

/**
 * بند حصر متخزّن.
 *
 * النقط متخزّنة **منسّبة ٠..١** لصفحتها في `pointsJson` — نفس مبدأ
 * تعليقات الـPDF. يعني الكمية مستقلة عن التكبير وعن دقة الرندر، وبتفضل
 * صح لو الملف اتفتح على جهاز تاني بشاشة مختلفة.
 */
@Serializable
@Entity(
    tableName = "takeoff_items",
    indices = [
        Index(value = ["drawingId", "page"]), Index(value = ["parentId"]),
        Index(value = ["categoryId"]), Index(value = ["groupId"])
    ]
)
data class TakeoffItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val drawingId: Long,
    val page: Int,
    /** AREA · LENGTH · COUNT · DEDUCT */
    val tool: String,
    val name: String,
    /** قديم — من قبل الفئات الحقيقية. الصفوف الجديدة بتفضّل [categoryId]. */
    val category: String = "",
    val colorArgb: Long,
    val visible: Boolean = true,
    /** `[[x,y],[x,y],…]` منسّبة ٠..١. */
    val pointsJson: String,
    /** حلقات إضافية متجمّعة — `[[[x,y],…],…]`. */
    val extraRingsJson: String = "[]",
    /** خطوط إضافية متجمّعة. */
    val extraSegmentsJson: String = "[]",
    /** البند اللي الخصم ده بيتقطع منه. للخصم بس. */
    val parentId: Long? = null,
    val zone: String = "",
    val createdAt: Long,
    val categoryId: Long? = null,
    val groupId: Long? = null,
    val progressPercent: Double? = null,
    val rateOverride: Double? = null,
    /** بالمتر — [TakeoffTool.VOLUME] بس. */
    val thickness: Double? = null,
    /** أبعاد عمود واحد بالمتر — [TakeoffTool.COLUMN] بس. */
    val colLength: Double? = null,
    val colWidth: Double? = null,
    val colHeight: Double? = null
)

/**
 * صيغة محسوبة — مربوطة **برسمة واحدة** مش بالقسم كله.
 *
 * تقييم صيغة محتاج معايرة كل صفحة فيها بند متضمّن — والمعايرة دي مخزّنة
 * لكل رسمة لوحدها. توسيع النطاق لكل رسمات القسم كان محتاج نفتح كل
 * ملفات الـPDF بتاعتها مرة واحدة، وده تمدّد معماري أكبر من مستاهل
 * المرحلة دي.
 */
@Serializable
@Entity(tableName = "takeoff_formulas", indices = [Index(value = ["drawingId"])])
data class TakeoffFormulaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val drawingId: Long,
    val name: String,
    val expr: String,
    val unit: String = "",
    val roundTo: Int = 2,
    val colorArgb: Long,
    /** `{"توكن": itemId, …}` — مربوطة بالـid مش بالاسم. */
    val refsJson: String = "{}",
    val createdAt: Long
)

/**
 * تعليق — سحابة/سهم/نص. **مش** بند حصر: مالوش كمية ولا فئة، ومستبعد من
 * كل حساب بحكم إنه في جدول تاني خالص، مش بس علَم على صف موجود.
 */
@Serializable
@Entity(
    tableName = "takeoff_annotations",
    indices = [Index(value = ["drawingId", "page"])]
)
data class TakeoffAnnotationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val drawingId: Long,
    val page: Int,
    /** CLOUD · ARROW · TEXT */
    val type: String,
    val pointsJson: String,
    val colorArgb: Long,
    val text: String = "",
    val visible: Boolean = true,
    val createdAt: Long
)
