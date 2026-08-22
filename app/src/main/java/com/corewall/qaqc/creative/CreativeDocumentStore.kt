package com.corewall.qaqc.creative

import com.corewall.qaqc.data.db.CreativeDocumentDao
import com.corewall.qaqc.data.db.CreativeDocumentEntity
import com.corewall.qaqc.data.db.CreativeDocumentExportEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** مخزن المستندات: المسودة ونسخ التصدير يظلان منفصلين ويمكن الرجوع إليهما. */
class CreativeDocumentStore(private val dao: CreativeDocumentDao) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    fun documents(level: String): Flow<List<CreativeDocumentEntity>> = dao.observeForLevel(level)

    fun decode(entity: CreativeDocumentEntity): CreativeDocumentContent =
        runCatching { json.decodeFromString<CreativeDocumentContent>(entity.contentJson) }
            .getOrElse { template(entity.templateKey, entity.title, entity.level) }

    suspend fun create(level: String, templateKey: String, title: String = CreativeTemplate.label(templateKey)): Long {
        val now = System.currentTimeMillis()
        val content = template(templateKey, title, level)
        return dao.upsert(
            CreativeDocumentEntity(
                level = level,
                templateKey = templateKey,
                title = content.title,
                contentJson = json.encodeToString(content),
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun update(entity: CreativeDocumentEntity, content: CreativeDocumentContent, sources: List<CreativeSourceRef> = emptyList()): Long =
        dao.upsert(
            entity.copy(
                title = content.title,
                contentJson = json.encodeToString(content),
                sourceJson = json.encodeToString(sources),
                status = "READY",
                updatedAt = System.currentTimeMillis()
            )
        )

    suspend fun document(id: Long): CreativeDocumentEntity? = dao.byId(id)

    suspend fun recordExport(documentId: Long, format: String, path: String): Long {
        val now = System.currentTimeMillis()
        dao.updateStatus(documentId, "EXPORTED", now)
        return dao.insertExport(CreativeDocumentExportEntity(documentId = documentId, format = format, path = path, createdAt = now))
    }

    suspend fun exports(documentId: Long): List<CreativeDocumentExportEntity> = dao.exportsForDocument(documentId)

    private fun template(key: String, title: String, level: String): CreativeDocumentContent {
        val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        fun h(id: String, text: String) = CreativeBlock(id, CreativeBlockKind.HEADING, text)
        fun p(id: String, text: String) = CreativeBlock(id, CreativeBlockKind.PARAGRAPH, text)
        fun bullets(id: String, values: List<String>) = CreativeBlock(id, CreativeBlockKind.BULLETS, items = values)
        fun table(id: String, rows: List<List<String>>) = CreativeBlock(id, CreativeBlockKind.TABLE, rows = rows.map(::CreativeTableRow))
        val blocks = when (key) {
            CreativeTemplate.QUALITY -> listOf(
                p("summary", "ملخص فحص الجودة للدور $level بتاريخ $date."),
                h("scope", "نطاق الفحص"), p("scope-p", "أضف العناصر والمناطق التي تم فحصها."),
                h("observations", "الملاحظات والإجراءات"), table("observations-t", listOf(listOf("البند", "الحالة", "الإجراء التصحيحي"))),
                h("approval", "الاعتماد"), p("approval-p", "اسم المهندس: ____________________")
            )
            CreativeTemplate.TAKEOFF -> listOf(
                p("summary", "ملخص حصر كميات للدور $level بتاريخ $date."),
                h("calibration", "المعايرة والمصادر"), p("calibration-p", "أضف الرسمات وبيانات المعايرة المستخدمة."),
                h("quantities", "جدول الكميات"), table("quantities-t", listOf(listOf("البند", "الوحدة", "الكمية", "المصدر"))),
                h("notes", "ملاحظات الحصر"), p("notes-p", "أضف الافتراضات والاستثناءات هنا.")
            )
            CreativeTemplate.DAILY -> listOf(
                p("summary", "تقرير يومي للدور $level بتاريخ $date."),
                h("completed", "الأعمال المنفذة"), bullets("completed-b", listOf("أضف الأعمال المنفذة")),
                h("resources", "العمالة والمواد"), table("resources-t", listOf(listOf("الفئة", "البيان", "الكمية / العدد"))),
                h("risks", "المعوقات وخطة الغد"), p("risks-p", "أضف المعوقات والإجراءات المطلوبة." )
            )
            CreativeTemplate.MEETING -> listOf(
                p("summary", "محضر اجتماع مشروع Core Wall للدور $level بتاريخ $date."),
                h("attendees", "الحضور"), bullets("attendees-b", listOf("أضف أسماء الحضور")),
                h("decisions", "القرارات والإجراءات"), table("decisions-t", listOf(listOf("القرار / الإجراء", "المسؤول", "الموعد"))),
                h("next", "الاجتماع التالي"), p("next-p", "حدد الموعد والمحاور المقترحة.")
            )
            else -> listOf(
                p("opening", "التاريخ: $date\nإلى: ____________________"),
                h("subject", "الموضوع"), p("body", "اكتب نص الخطاب هنا."),
                p("signature", "وتفضلوا بقبول فائق الاحترام.\n____________________")
            )
        }
        return CreativeDocumentContent(title = title, subtitle = "Core Wall · الدور $level", blocks = blocks)
    }
}
