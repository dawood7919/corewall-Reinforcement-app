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
}

@Dao
interface PdfAnnotationDao {
    @Query("SELECT * FROM pdf_annotations")
    fun observeAll(): Flow<List<PdfAnnotationEntity>>

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
}

@Dao
interface PdfBookmarkDao {
    @Query("SELECT * FROM pdf_bookmarks ORDER BY filePath, page")
    fun observeAll(): Flow<List<PdfBookmarkEntity>>

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
}

@Dao
interface PdfScaleDao {
    @Query("SELECT * FROM pdf_scales")
    fun observeAll(): Flow<List<PdfScaleEntity>>

    @Query("SELECT * FROM pdf_scales")
    suspend fun getAll(): List<PdfScaleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PdfScaleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<PdfScaleEntity>)

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

    @Query("SELECT * FROM doc_facts")
    suspend fun getAll(): List<DocFactEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<DocFactEntity>)

    @Query("DELETE FROM doc_facts WHERE documentId = :docId")
    suspend fun deleteForDocument(docId: Long)
}

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE level = :level ORDER BY createdAt ASC")
    fun observeForLevel(level: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE level = :level ORDER BY createdAt ASC")
    suspend fun forLevel(level: String): List<ChatMessageEntity>

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
