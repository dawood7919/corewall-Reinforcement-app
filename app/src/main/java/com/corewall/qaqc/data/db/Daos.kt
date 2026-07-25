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
