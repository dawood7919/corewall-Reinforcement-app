package com.corewall.qaqc.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ElementNameDao {
    @Query("SELECT * FROM element_names")
    fun observeAll(): Flow<List<ElementNameEntity>>

    @Query("SELECT * FROM element_names")
    suspend fun getAll(): List<ElementNameEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ElementNameEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ElementNameEntity>)

    @Query("DELETE FROM element_names WHERE elementId = :elementId")
    suspend fun delete(elementId: String)
}

@Dao
interface InspectionDao {
    @Query("SELECT * FROM inspections")
    fun observeAll(): Flow<List<InspectionEntity>>

    @Query("SELECT * FROM inspections")
    suspend fun getAll(): List<InspectionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: InspectionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<InspectionEntity>)

    @Query("DELETE FROM inspections WHERE elementId = :elementId AND level = :level")
    suspend fun delete(elementId: String, level: String)
}

@Dao
interface CommentDao {
    @Query("SELECT * FROM comments ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<CommentEntity>>

    @Query("SELECT * FROM comments")
    suspend fun getAll(): List<CommentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CommentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<CommentEntity>)

    @Query("DELETE FROM comments WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface BarCountDao {
    @Query("SELECT * FROM bar_counts")
    fun observeAll(): Flow<List<BarCountEntity>>

    @Query("SELECT * FROM bar_counts")
    suspend fun getAll(): List<BarCountEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<BarCountEntity>)

    @Query("DELETE FROM bar_counts WHERE elementId = :elementId AND level = :level")
    suspend fun deleteForElement(elementId: String, level: String)
}

@Dao
interface ElementAttachmentDao {
    @Query("SELECT * FROM element_attachments ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<ElementAttachmentEntity>>

    @Query("SELECT * FROM element_attachments")
    suspend fun getAll(): List<ElementAttachmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ElementAttachmentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ElementAttachmentEntity>)

    @Query("DELETE FROM element_attachments WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY done ASC, priority DESC, createdAt DESC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks")
    suspend fun getAll(): List<TaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<TaskEntity>)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM tasks WHERE done = 1")
    suspend fun deleteCompleted()
}

@Dao
interface AttendanceFileDao {
    @Query("SELECT * FROM attendance_files ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<AttendanceFileEntity>>

    @Query("SELECT * FROM attendance_files")
    suspend fun getAll(): List<AttendanceFileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AttendanceFileEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<AttendanceFileEntity>)

    @Query("DELETE FROM attendance_files WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface DailyAttendanceDao {
    @Query("SELECT * FROM daily_attendance ORDER BY date DESC")
    fun observeAll(): Flow<List<DailyAttendanceEntity>>

    @Query("SELECT * FROM daily_attendance")
    suspend fun getAll(): List<DailyAttendanceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DailyAttendanceEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<DailyAttendanceEntity>)

    @Query("DELETE FROM daily_attendance WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM daily_attendance WHERE fileId = :fileId")
    suspend fun deleteForFile(fileId: Long)
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes")
    suspend fun getAll(): List<NoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: NoteEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<NoteEntity>)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: Long)

    /** مسح نهائي لكل المهملات. */
    @Query("DELETE FROM notes WHERE deletedAt IS NOT NULL")
    suspend fun emptyTrash()

    /** تنظيف تلقائي: بيشيل اللي قعد في المهملات أكتر من المدة المسموحة. */
    @Query("DELETE FROM notes WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    suspend fun purgeTrashOlderThan(before: Long): Int
}

@Dao
interface NoteLabelDao {
    @Query("SELECT * FROM note_labels ORDER BY name")
    fun observeAll(): Flow<List<NoteLabelEntity>>

    @Query("SELECT * FROM note_labels ORDER BY name")
    suspend fun getAll(): List<NoteLabelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: NoteLabelEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<NoteLabelEntity>)

    @Query("DELETE FROM note_labels WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface NoteLabelLinkDao {
    @Query("SELECT * FROM note_label_links")
    fun observeAll(): Flow<List<NoteLabelLinkEntity>>

    @Query("SELECT * FROM note_label_links")
    suspend fun getAll(): List<NoteLabelLinkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun link(entity: NoteLabelLinkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun linkAll(entities: List<NoteLabelLinkEntity>)

    @Query("DELETE FROM note_label_links WHERE noteId = :noteId AND labelId = :labelId")
    suspend fun unlink(noteId: Long, labelId: Long)

    /** بيتنادى مع حذف التصنيف — الروابط اليتيمة بتخلّي الفلترة تكدب. */
    @Query("DELETE FROM note_label_links WHERE labelId = :labelId")
    suspend fun unlinkLabel(labelId: Long)

    @Query("DELETE FROM note_label_links WHERE noteId = :noteId")
    suspend fun unlinkNote(noteId: Long)
}

@Dao
interface PdfAnnotationDao {
    @Query("SELECT * FROM pdf_annotations")
    fun observeAll(): Flow<List<PdfAnnotationEntity>>

    /**
     * تعليقات ملف واحد.
     *
     * الشاشة كانت بتحمّل تعليقات **كل** الملفات وتفلتر في Kotlin. على
     * مشروع فيه مئات الرسمات ده معناه إن فتح رسمة واحدة بيقرا الجدول كله،
     * وأي تعليقة على أي ملف تانٍ بتعيد بناء القايمة كلها.
     */
    @Query("SELECT * FROM pdf_annotations WHERE filePath = :filePath")
    fun observeForFile(filePath: String): Flow<List<PdfAnnotationEntity>>

    @Query("SELECT * FROM pdf_annotations")
    suspend fun getAll(): List<PdfAnnotationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PdfAnnotationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<PdfAnnotationEntity>)

    @Query("DELETE FROM pdf_annotations WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM pdf_annotations WHERE filePath = :filePath AND page = :page")
    suspend fun clearPage(filePath: String, page: Int)

    @Query("DELETE FROM pdf_annotations WHERE id = (SELECT MAX(id) FROM pdf_annotations WHERE filePath = :filePath AND page = :page)")
    suspend fun deleteLast(filePath: String, page: Int)

    @Query("DELETE FROM pdf_annotations WHERE filePath = :filePath")
    suspend fun clearForFile(filePath: String)

    /** تعليقات صفحة واحدة — للنسخ مع الصفحة لملف تاني. */
    @Query("SELECT * FROM pdf_annotations WHERE filePath = :filePath AND page = :page ORDER BY id")
    suspend fun forPage(filePath: String, page: Int): List<PdfAnnotationEntity>

    @Query("DELETE FROM pdf_annotations WHERE id IN (:ids)")
    suspend fun deleteAll(ids: List<Long>)
}

@Dao
interface PdfBookmarkDao {
    @Query("SELECT * FROM pdf_bookmarks ORDER BY filePath, page")
    fun observeAll(): Flow<List<PdfBookmarkEntity>>

    @Query("SELECT * FROM pdf_bookmarks WHERE filePath = :filePath ORDER BY page")
    fun observeForFile(filePath: String): Flow<List<PdfBookmarkEntity>>

    @Query("SELECT * FROM pdf_bookmarks ORDER BY filePath, page")
    suspend fun getAll(): List<PdfBookmarkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PdfBookmarkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<PdfBookmarkEntity>)

    @Query("DELETE FROM pdf_bookmarks WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface PdfMeasurementDao {
    @Query("SELECT * FROM pdf_measurements ORDER BY id")
    fun observeAll(): Flow<List<PdfMeasurementEntity>>

    @Query("SELECT * FROM pdf_measurements WHERE filePath = :filePath ORDER BY id")
    fun observeForFile(filePath: String): Flow<List<PdfMeasurementEntity>>

    @Query("SELECT * FROM pdf_measurements ORDER BY id")
    suspend fun getAll(): List<PdfMeasurementEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PdfMeasurementEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<PdfMeasurementEntity>)

    @Query("DELETE FROM pdf_measurements WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM pdf_measurements WHERE filePath = :filePath AND page = :page")
    suspend fun clearPage(filePath: String, page: Int)

    @Query("SELECT * FROM pdf_measurements WHERE filePath = :filePath AND page = :page ORDER BY id")
    suspend fun forPage(filePath: String, page: Int): List<PdfMeasurementEntity>
}

@Dao
interface PdfScaleDao {
    @Query("SELECT * FROM pdf_scales")
    fun observeAll(): Flow<List<PdfScaleEntity>>

    @Query("SELECT * FROM pdf_scales WHERE filePath = :filePath")
    fun observeForFile(filePath: String): Flow<List<PdfScaleEntity>>

    @Query("SELECT * FROM pdf_scales")
    suspend fun getAll(): List<PdfScaleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PdfScaleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<PdfScaleEntity>)

    @Query("SELECT * FROM pdf_scales WHERE filePath = :filePath AND page = :page LIMIT 1")
    suspend fun forPage(filePath: String, page: Int): PdfScaleEntity?

    @Query("DELETE FROM pdf_scales WHERE filePath = :filePath AND page = :page")
    suspend fun delete(filePath: String, page: Int)
}

@Dao
interface RangeEditDao {
    @Query("SELECT * FROM range_edits")
    fun observeAll(): Flow<List<RangeEditEntity>>

    @Query("SELECT * FROM range_edits")
    suspend fun getAll(): List<RangeEditEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RangeEditEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<RangeEditEntity>)

    @Query("DELETE FROM range_edits WHERE mark = :mark AND rowIndex = :rowIndex")
    suspend fun delete(mark: String, rowIndex: Int)
}

@Dao
interface SitePhotoDao {
    @Query("SELECT * FROM site_photos ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<SitePhotoEntity>>

    @Query("SELECT * FROM site_photos")
    suspend fun getAll(): List<SitePhotoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SitePhotoEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<SitePhotoEntity>)

    @Query("DELETE FROM site_photos WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface AiAnalysisDao {
    @Query("SELECT * FROM ai_analysis WHERE level = :level LIMIT 1")
    fun observeForLevel(level: String): Flow<AiAnalysisEntity?>

    @Query("SELECT * FROM ai_analysis WHERE level = :level LIMIT 1")
    suspend fun getForLevel(level: String): AiAnalysisEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AiAnalysisEntity)

    @Query("DELETE FROM ai_analysis WHERE level = :level")
    suspend fun deleteForLevel(level: String)
}

@Dao
interface AgentExecutionDao {
    @Query("SELECT * FROM agent_execution_plans WHERE level = :level ORDER BY updatedAt DESC")
    fun observePlans(level: String): Flow<List<AgentExecutionPlanEntity>>

    @Query("SELECT * FROM agent_execution_steps WHERE planId = :planId ORDER BY ordinal, id")
    fun observeSteps(planId: Long): Flow<List<AgentExecutionStepEntity>>

    @Query("SELECT * FROM agent_execution_steps WHERE id = :id LIMIT 1")
    suspend fun step(id: Long): AgentExecutionStepEntity?

    @Query("SELECT * FROM agent_execution_steps WHERE planId = :planId ORDER BY ordinal, id")
    suspend fun stepsForPlan(planId: Long): List<AgentExecutionStepEntity>

    @Insert
    suspend fun insertPlan(entity: AgentExecutionPlanEntity): Long

    @Insert
    suspend fun insertSteps(entities: List<AgentExecutionStepEntity>): List<Long>

    @Insert
    suspend fun insertAudit(entity: AgentActionAuditEntity): Long

    @Query("UPDATE agent_execution_plans SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updatePlanStatus(id: Long, status: String, updatedAt: Long)

    @Query("UPDATE agent_execution_steps SET status = :status, result = :result, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStepStatus(id: Long, status: String, result: String, updatedAt: Long)

    @Query("SELECT * FROM agent_action_audit WHERE level = :level ORDER BY at DESC LIMIT :limit")
    suspend fun latestAudit(level: String, limit: Int): List<AgentActionAuditEntity>
}

@Dao
interface CreativeDocumentDao {
    @Query("SELECT * FROM creative_documents WHERE level = :level AND status != 'ARCHIVED' ORDER BY updatedAt DESC")
    fun observeForLevel(level: String): Flow<List<CreativeDocumentEntity>>

    @Query("SELECT * FROM creative_documents WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): CreativeDocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CreativeDocumentEntity): Long

    @Insert
    suspend fun insertExport(entity: CreativeDocumentExportEntity): Long

    @Query("SELECT * FROM creative_document_exports WHERE documentId = :documentId ORDER BY createdAt DESC")
    suspend fun exportsForDocument(documentId: Long): List<CreativeDocumentExportEntity>

    @Query("UPDATE creative_documents SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, updatedAt: Long)
}

@Dao
interface CadMeasurementDao {
    @Query("SELECT * FROM cad_measurements WHERE filePath = :filePath ORDER BY id")
    fun observeMeasurements(filePath: String): Flow<List<CadMeasurementEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMeasurement(entity: CadMeasurementEntity): Long

    @Query("DELETE FROM cad_measurements WHERE id = :id")
    suspend fun deleteMeasurement(id: Long)

    @Query("DELETE FROM cad_measurements WHERE filePath = :filePath")
    suspend fun deleteAllMeasurements(filePath: String)

    @Query("SELECT * FROM cad_drawing_settings WHERE filePath = :filePath LIMIT 1")
    suspend fun settings(filePath: String): CadDrawingSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettings(entity: CadDrawingSettingsEntity)
}

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE level = :level ORDER BY createdAt DESC")
    suspend fun forLevel(level: String): List<DocumentEntity>

    @Query("SELECT * FROM documents WHERE filePath = :path LIMIT 1")
    suspend fun byPath(path: String): DocumentEntity?

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): DocumentEntity?

    /**
     * مستندات الدور **بالإضافة** لمكتبة المشروع المشتركة.
     * العزل بين الأدوار مطلق: الدور 10 عمره ما يشوف ملفات الدور 9.
     * اللي بيتشارك هو اللي المستخدم حطّه صراحة في "معرفة المشروع".
     */
    @Query(
        "SELECT * FROM documents WHERE level = :level OR level = :globalLevel " +
            "ORDER BY createdAt DESC"
    )
    suspend fun inScope(level: String, globalLevel: String): List<DocumentEntity>

    @Query("SELECT * FROM documents WHERE status = 'PENDING' ORDER BY createdAt ASC LIMIT :limit")
    suspend fun pending(limit: Int): List<DocumentEntity>

    @Query("SELECT * FROM documents")
    suspend fun getAll(): List<DocumentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DocumentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<DocumentEntity>)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface DocFactDao {
    @Query("SELECT * FROM doc_facts WHERE level = :level")
    suspend fun forLevel(level: String): List<DocFactEntity>

    @Query("SELECT * FROM doc_facts WHERE documentId = :docId")
    suspend fun forDocument(docId: Long): List<DocFactEntity>

    /**
     * بحث **مقيّد بالنطاق**. النسخة القديمة كانت بتدوّر في كل الأدوار،
     * فحقائق دور 9 كانت بتظهر وإنت في دور 10 — كسر لعزل الأدوار.
     */
    @Query(
        "SELECT * FROM doc_facts WHERE (level = :level OR level = :globalLevel) " +
            "AND (key LIKE '%' || :q || '%' OR value LIKE '%' || :q || '%') LIMIT :limit"
    )
    suspend fun searchInScope(q: String, level: String, globalLevel: String, limit: Int): List<DocFactEntity>

    @Query("SELECT * FROM doc_facts WHERE level = :level OR level = :globalLevel")
    suspend fun inScope(level: String, globalLevel: String): List<DocFactEntity>

    // ─────────────────────────── النص الأصلي وفهرسه

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunk(chunk: DocChunkEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun indexChunk(row: DocChunkFtsEntity)

    @Query("DELETE FROM doc_chunks WHERE documentId = :documentId")
    suspend fun clearChunks(documentId: Long)

    @Query("DELETE FROM doc_chunks_fts WHERE rowid IN (SELECT id FROM doc_chunks WHERE documentId = :documentId)")
    suspend fun clearChunkIndex(documentId: Long)

    /**
     * بحث بالكلمة في النص الأصلي.
     *
     * `MATCH` بيستخدم الفهرس: بيدوّر على الكلمة، مش على السلسلة جوّه النص،
     * وبيمشي على المطابق بس مش على كل الصفوف.
     *
     * الربط بالـ`rowid` لأن صف الفهرس بياخد نفس `id` الفقرة وقت الكتابة.
     */
    @Query(
        "SELECT c.* FROM doc_chunks c JOIN doc_chunks_fts f ON c.id = f.rowid " +
            "WHERE doc_chunks_fts MATCH :query AND (c.level = :level OR c.level = :globalLevel) " +
            "LIMIT :limit"
    )
    suspend fun searchChunks(
        query: String, level: String, globalLevel: String, limit: Int
    ): List<DocChunkEntity>

    @Query("SELECT COUNT(*) FROM doc_chunks WHERE level = :level OR level = :globalLevel")
    suspend fun chunkCount(level: String, globalLevel: String): Int

    @Query("SELECT * FROM doc_facts")
    suspend fun getAll(): List<DocFactEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<DocFactEntity>)

    @Query("DELETE FROM doc_facts WHERE documentId = :docId")
    suspend fun deleteForDocument(docId: Long)
}

@Dao
interface ChatMessageDao {
    // `role` بياخد قيمة تالتة غير user/assistant هي `memory` — ملاحظات
    // الوكيل عن نفسه. عمود موجود بقيمة جديدة يعني **مفيش ترحيل** ولا
    // مخاطرة كسر المخطط؛ الاستعلامات هي اللي بتفصل بينهم.
    @Query(
        "SELECT * FROM chat_messages WHERE level = :level AND role IN ('user','assistant') " +
            "ORDER BY createdAt ASC"
    )
    fun observeForLevel(level: String): Flow<List<ChatMessageEntity>>

    @Query(
        "SELECT * FROM chat_messages WHERE level = :level AND role IN ('user','assistant') " +
            "ORDER BY createdAt ASC"
    )
    suspend fun forLevel(level: String): List<ChatMessageEntity>

    /**
     * بحث في **كل** المحادثة، مش آخر ست رسائل.
     *
     * ده اللي بيخلّي الذاكرة كاملة من غير ما تتكلّف: مفيش حرف زيادة
     * بيتبعت في الطلب العادي، والوكيل بيجيب القديم لما يحتاجه بس.
     */
    @Query(
        "SELECT * FROM chat_messages WHERE level = :level AND role IN ('user','assistant') " +
            "AND content LIKE '%' || :query || '%' ORDER BY createdAt DESC LIMIT :limit"
    )
    suspend fun search(level: String, query: String, limit: Int): List<ChatMessageEntity>

    /** ملاحظات الوكيل المحفوظة — الأحدث الأول. */
    @Query(
        "SELECT * FROM chat_messages WHERE level = :level AND role = 'memory' " +
            "ORDER BY createdAt DESC LIMIT :limit"
    )
    suspend fun memory(level: String, limit: Int): List<ChatMessageEntity>

    /** ملاحظة بنفس المفتاح بتتشال قبل ما الجديدة تتكتب. */
    @Query("DELETE FROM chat_messages WHERE level = :level AND role = 'memory' AND content LIKE :prefix || '%'")
    suspend fun forgetByPrefix(level: String, prefix: String)

    /** عدد رسائل المحادثة — للعرض ولقرار الاختصار. */
    @Query("SELECT COUNT(*) FROM chat_messages WHERE level = :level AND role IN ('user','assistant')")
    suspend fun countForLevel(level: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages WHERE level = :level")
    suspend fun clearLevel(level: String)
}

// ═══════════════════════════════════════════════════════════════════
// إعادة تصميم الموديولات
// ═══════════════════════════════════════════════════════════════════

@Dao
interface FileMetaDao {
    @Query("SELECT * FROM file_meta")
    fun observeAll(): Flow<List<FileMetaEntity>>

    @Query("SELECT * FROM file_meta WHERE path = :path")
    suspend fun byPath(path: String): FileMetaEntity?

    @Query("SELECT * FROM file_meta WHERE favourite = 1")
    fun observeFavourites(): Flow<List<FileMetaEntity>>

    @Query("SELECT * FROM file_meta WHERE lastOpenedAt > 0 ORDER BY lastOpenedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 30): Flow<List<FileMetaEntity>>

    /**
     * بحث في الاسم والوسوم **والنص المستخرج**. الجزء الأخير هو اللي بيخلّي
     * البحث مفيد: "W12" بتلاقي صفحة الجدول اللي جواها الكود، مش بس اسم ملف
     * صادف إن فيه W12.
     */
    @Query(
        "SELECT * FROM file_meta WHERE " +
            "path LIKE '%' || :q || '%' OR tags LIKE '%' || :q || '%' OR ocrText LIKE '%' || :q || '%' " +
            "LIMIT :limit"
    )
    suspend fun search(q: String, limit: Int = 100): List<FileMetaEntity>

    @Query("SELECT * FROM file_meta WHERE ocrStatus = :status LIMIT :limit")
    suspend fun withOcrStatus(status: String, limit: Int = 20): List<FileMetaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FileMetaEntity)

    @Query("DELETE FROM file_meta WHERE path = :path")
    suspend fun delete(path: String)
}

@Dao
interface ChatThreadDao {
    /** المثبّت الأول، وبعدين الأحدث تعديلاً. */
    @Query("SELECT * FROM chat_threads WHERE level = :level ORDER BY pinned DESC, updatedAt DESC")
    fun observeForLevel(level: String): Flow<List<ChatThreadEntity>>

    @Query("SELECT * FROM chat_threads WHERE id = :id")
    suspend fun byId(id: Long): ChatThreadEntity?

    @Query("SELECT * FROM chat_threads WHERE level = :level AND title LIKE '%' || :q || '%'")
    suspend fun search(level: String, q: String): List<ChatThreadEntity>

    @Query("SELECT DISTINCT folder FROM chat_threads WHERE level = :level AND folder != ''")
    fun observeFolders(level: String): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ChatThreadEntity): Long

    @Query("DELETE FROM chat_threads WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface LinkDao {
    @Query("SELECT * FROM links WHERE fromType = :type AND fromId = :id")
    fun observeFrom(type: String, id: String): Flow<List<LinkEntity>>

    @Query("SELECT * FROM links WHERE toType = :type AND toId = :id")
    fun observeTo(type: String, id: String): Flow<List<LinkEntity>>

    /** كل اللي متربط بكيان معيّن في الاتجاهين — الربط علاقة مش اتجاه. */
    @Query(
        "SELECT * FROM links WHERE (fromType = :type AND fromId = :id) " +
            "OR (toType = :type AND toId = :id)"
    )
    suspend fun allFor(type: String, id: String): List<LinkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LinkEntity): Long

    @Query("DELETE FROM links WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM links WHERE (fromType = :type AND fromId = :id) OR (toType = :type AND toId = :id)")
    suspend fun deleteAllFor(type: String, id: String)
}

@Dao
interface NoteRevisionDao {
    @Query("SELECT * FROM note_revisions WHERE noteId = :noteId ORDER BY savedAt DESC")
    fun observeForNote(noteId: Long): Flow<List<NoteRevisionEntity>>

    @Query("SELECT * FROM note_revisions WHERE noteId = :noteId ORDER BY savedAt DESC LIMIT 1")
    suspend fun latest(noteId: Long): NoteRevisionEntity?

    @Insert
    suspend fun insert(entity: NoteRevisionEntity): Long

    @Query("DELETE FROM note_revisions WHERE noteId = :noteId")
    suspend fun deleteForNote(noteId: Long)
}

@Dao
interface PromptDao {
    /** الأكتر استخداماً الأول — اللي بتستخدمه كل يوم مايستهلكش تمرير. */
    @Query("SELECT * FROM prompts ORDER BY usageCount DESC, updatedAt DESC")
    fun observeAll(): Flow<List<PromptEntity>>

    @Query("SELECT * FROM prompts WHERE id = :id")
    suspend fun byId(id: Long): PromptEntity?

    @Query("SELECT * FROM prompts WHERE name = :name LIMIT 1")
    suspend fun byName(name: String): PromptEntity?

    @Query("SELECT * FROM prompts ORDER BY usageCount DESC, updatedAt DESC")
    suspend fun getAll(): List<PromptEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PromptEntity): Long

    @Query("UPDATE prompts SET usageCount = usageCount + 1, lastUsedAt = :at WHERE id = :id")
    suspend fun markUsed(id: Long, at: Long)

    @Query("DELETE FROM prompts WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ImportedMarkDao {
    @Query("SELECT * FROM imported_marks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ImportedMarkEntity>>

    @Query("SELECT * FROM imported_marks ORDER BY createdAt DESC")
    suspend fun getAll(): List<ImportedMarkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ImportedMarkEntity>)

    @Query("DELETE FROM imported_marks WHERE mark = :mark")
    suspend fun delete(mark: String)

    @Query("DELETE FROM imported_marks")
    suspend fun deleteAll()
}

/**
 * حصر الكميات.
 *
 * كل الاستعلامات مفلترة بالأب (قسم/رسمة/صفحة) في SQL مش في Kotlin —
 * قسم فيه ٥٠ رسمة مايستاهلش يقرا بنود كل الرسمات عشان يعرض واحدة.
 */
@Dao
interface TakeoffDao {

    // ── الأقسام
    @Query("SELECT * FROM takeoff_projects ORDER BY updatedAt DESC")
    fun observeProjects(): Flow<List<TakeoffProjectEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProject(entity: TakeoffProjectEntity): Long

    @Query("DELETE FROM takeoff_projects WHERE id = :id")
    suspend fun deleteProject(id: Long)

    // ── الرسمات
    @Query("SELECT * FROM takeoff_drawings WHERE projectId = :projectId ORDER BY createdAt")
    fun observeDrawings(projectId: Long): Flow<List<TakeoffDrawingEntity>>

    @Query("SELECT * FROM takeoff_drawings WHERE projectId = :projectId")
    suspend fun drawingsOf(projectId: Long): List<TakeoffDrawingEntity>

    @Query("SELECT * FROM takeoff_drawings WHERE id = :id")
    suspend fun drawing(id: Long): TakeoffDrawingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDrawing(entity: TakeoffDrawingEntity): Long

    @Query("DELETE FROM takeoff_drawings WHERE id = :id")
    suspend fun deleteDrawing(id: Long)

    // ── الفئات والمجموعات
    @Query("SELECT * FROM takeoff_categories WHERE projectId = :projectId ORDER BY sortOrder, id")
    fun observeCategories(projectId: Long): Flow<List<TakeoffCategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategory(entity: TakeoffCategoryEntity): Long

    @Query("DELETE FROM takeoff_categories WHERE id = :id")
    suspend fun deleteCategory(id: Long)

    @Query("UPDATE takeoff_items SET categoryId = NULL, groupId = NULL WHERE categoryId = :categoryId")
    suspend fun clearCategoryFromItems(categoryId: Long)

    @Query("SELECT * FROM takeoff_groups WHERE categoryId IN (SELECT id FROM takeoff_categories WHERE projectId = :projectId) ORDER BY sortOrder, id")
    fun observeGroups(projectId: Long): Flow<List<TakeoffGroupEntity>>

    @Query("SELECT * FROM takeoff_groups WHERE categoryId = :categoryId")
    suspend fun groupsOfCategory(categoryId: Long): List<TakeoffGroupEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGroup(entity: TakeoffGroupEntity): Long

    @Query("DELETE FROM takeoff_groups WHERE id = :id")
    suspend fun deleteGroup(id: Long)

    // ── المعايرة
    @Query("SELECT * FROM takeoff_scales WHERE drawingId = :drawingId")
    fun observeScales(drawingId: Long): Flow<List<TakeoffScaleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertScale(entity: TakeoffScaleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertScales(entities: List<TakeoffScaleEntity>)

    @Query("DELETE FROM takeoff_scales WHERE drawingId = :drawingId")
    suspend fun clearScales(drawingId: Long)

    // ── البنود
    @Query("SELECT * FROM takeoff_items WHERE drawingId = :drawingId ORDER BY id")
    fun observeItems(drawingId: Long): Flow<List<TakeoffItemEntity>>

    @Query("SELECT * FROM takeoff_items WHERE drawingId IN (SELECT id FROM takeoff_drawings WHERE projectId = :projectId)")
    fun observeProjectItems(projectId: Long): Flow<List<TakeoffItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItem(entity: TakeoffItemEntity): Long

    @Query("SELECT * FROM takeoff_items WHERE id = :id")
    suspend fun item(id: Long): TakeoffItemEntity?

    /** خصومات بند بعينه — للتراجع عن الحذف: لازم نسترجعهم مع الأب. */
    @Query("SELECT * FROM takeoff_items WHERE parentId = :id")
    suspend fun childrenOf(id: Long): List<TakeoffItemEntity>

    /** حذف بند **وكل خصوماته** — عشان مايفضلش خصم معلّق على أب ميت. */
    @Query("DELETE FROM takeoff_items WHERE id = :id OR parentId = :id")
    suspend fun deleteItemCascade(id: Long)

    @Query("DELETE FROM takeoff_items WHERE drawingId = :drawingId")
    suspend fun clearDrawingItems(drawingId: Long)

    /**
     * بنود اتسمّت واتحجز لها ID بس محدش رسمها.
     *
     * التسمية بتحصل **قبل** الرسم عشان الـID يبقى جاهز للصيغ، فالصف
     * بيتكتب بهندسة فاضية. لو المستخدم خرج من الرسمة قبل ما يرسم، الصف
     * بيفضل شبح: مالوش شكل على الشاشة، كميته صفر، وبيظهر في شجرة
     * القياسات كبند مالوش معنى. بيتنضّفوا مرة عند فتح الرسمة.
     */
    @Query(
        "DELETE FROM takeoff_items WHERE drawingId = :drawingId " +
            "AND pointsJson IN ('[]', '') " +
            "AND extraRingsJson IN ('[]', '') " +
            "AND extraSegmentsJson IN ('[]', '')"
    )
    suspend fun purgeEmptyItems(drawingId: Long)

    // ── الصيغ
    @Query("SELECT * FROM takeoff_formulas WHERE drawingId = :drawingId ORDER BY id")
    fun observeFormulas(drawingId: Long): Flow<List<TakeoffFormulaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFormula(entity: TakeoffFormulaEntity): Long

    @Query("DELETE FROM takeoff_formulas WHERE id = :id")
    suspend fun deleteFormula(id: Long)

    @Query("DELETE FROM takeoff_formulas WHERE drawingId = :drawingId")
    suspend fun clearDrawingFormulas(drawingId: Long)

    // ── التعليقات
    @Query("SELECT * FROM takeoff_annotations WHERE drawingId = :drawingId ORDER BY id")
    fun observeAnnotations(drawingId: Long): Flow<List<TakeoffAnnotationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAnnotation(entity: TakeoffAnnotationEntity): Long

    @Query("SELECT * FROM takeoff_annotations WHERE id = :id")
    suspend fun annotation(id: Long): TakeoffAnnotationEntity?

    @Query("DELETE FROM takeoff_annotations WHERE id = :id")
    suspend fun deleteAnnotation(id: Long)

    @Query("DELETE FROM takeoff_annotations WHERE drawingId = :drawingId")
    suspend fun clearDrawingAnnotations(drawingId: Long)
}

/**
 * طلبات الفحص وصفحاتها.
 *
 * الطلب بيتخزّن بالدور، بس القايمة بتتقري كلها والفلترة بتحصل فوق —
 * نفس نمط باقي الشاشات المربوطة بالدور الشغّال، وعدد الطلبات في مشروع
 * واحد بالمئات مش بالآلاف.
 */
@Dao
interface WirDao {
    @Query("SELECT * FROM wirs ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<WirEntity>>

    @Query("SELECT * FROM wirs ORDER BY updatedAt DESC")
    suspend fun getAll(): List<WirEntity>

    @Query("SELECT * FROM wirs WHERE id = :id")
    suspend fun byId(id: Long): WirEntity?

    /** الاسم مفتاح المستخدم: "أرسل لـWIR" باسم موجود بيضيف على نفس الملف. */
    @Query("SELECT * FROM wirs WHERE level = :level AND name = :name LIMIT 1")
    suspend fun byName(level: String, name: String): WirEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WirEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<WirEntity>)

    @Query("DELETE FROM wirs WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM wir_items WHERE wirId = :wirId ORDER BY page")
    fun observeItems(wirId: Long): Flow<List<WirItemEntity>>

    @Query("SELECT * FROM wir_items ORDER BY wirId, page")
    suspend fun allItems(): List<WirItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItem(item: WirItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItems(items: List<WirItemEntity>)

    @Query("DELETE FROM wir_items WHERE wirId = :wirId")
    suspend fun clearItems(wirId: Long)
}

/** شيت الحضور بالأسماء — الصفوف والخلايا. */
@Dao
interface AttendanceSheetDao {
    @Query("SELECT * FROM attendance_roster WHERE fileId = :fileId ORDER BY ordinal, id")
    fun observeRoster(fileId: Long): Flow<List<AttendanceRosterEntity>>

    @Query("SELECT * FROM attendance_roster ORDER BY fileId, ordinal, id")
    suspend fun allRoster(): List<AttendanceRosterEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRow(row: AttendanceRosterEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRows(rows: List<AttendanceRosterEntity>)

    @Query("DELETE FROM attendance_roster WHERE id = :id")
    suspend fun deleteRow(id: Long)

    @Query("DELETE FROM attendance_roster WHERE fileId = :fileId")
    suspend fun clearRoster(fileId: Long)

    @Query("SELECT * FROM attendance_marks WHERE fileId = :fileId")
    fun observeMarks(fileId: Long): Flow<List<AttendanceMarkEntity>>

    @Query("SELECT * FROM attendance_marks")
    suspend fun allMarks(): List<AttendanceMarkEntity>

    /**
     * الفهرس على (`rosterId`,`day`) فريد، فالاستبدال بيحدّث الخلية
     * الموجودة بدل ما يزوّد صف تاني لنفس اليوم.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMark(mark: AttendanceMarkEntity)

    @Query("DELETE FROM attendance_marks WHERE rosterId = :rosterId AND day = :day")
    suspend fun clearMark(rosterId: Long, day: Int)

    @Query("DELETE FROM attendance_marks WHERE fileId = :fileId")
    suspend fun clearMarks(fileId: Long)
}
