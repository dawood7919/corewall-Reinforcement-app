package com.corewall.qaqc.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
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
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val elementId: String,
    val level: String,
    val title: String = "",
    val body: String = "",
    val imagePathsJson: String = "[]",
    val createdAt: Long,
    val updatedAt: Long
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
@Entity(tableName = "pdf_annotations")
data class PdfAnnotationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filePath: String,
    val page: Int,
    val tool: String,
    val color: Long,
    val pointsJson: String,
    val createdAt: Long
) {
    companion object {
        const val TOOL_HIGHLIGHT = "HIGHLIGHT"
        const val TOOL_RECT = "RECT"
        const val TOOL_CIRCLE = "CIRCLE"
        const val TOOL_ARROW = "ARROW"
        const val TOOL_FREEHAND = "FREEHAND"
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
