package com.corewall.qaqc.data.db

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
