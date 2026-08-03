package com.corewall.qaqc.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ElementNameEntity::class,
        InspectionEntity::class,
        CommentEntity::class,
        RangeEditEntity::class,
        BarCountEntity::class,
        ElementAttachmentEntity::class,
        TaskEntity::class,
        PdfAnnotationEntity::class,
        NoteEntity::class,
        AttendanceFileEntity::class,
        DailyAttendanceEntity::class,
        SitePhotoEntity::class,
        AiAnalysisEntity::class,
        DocumentEntity::class,
        DocFactEntity::class,
        ChatMessageEntity::class
    ],
    version = 11,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun elementNameDao(): ElementNameDao
    abstract fun inspectionDao(): InspectionDao
    abstract fun commentDao(): CommentDao
    abstract fun rangeEditDao(): RangeEditDao
    abstract fun barCountDao(): BarCountDao
    abstract fun elementAttachmentDao(): ElementAttachmentDao
    abstract fun taskDao(): TaskDao
    abstract fun pdfAnnotationDao(): PdfAnnotationDao
    abstract fun noteDao(): NoteDao
    abstract fun attendanceFileDao(): AttendanceFileDao
    abstract fun dailyAttendanceDao(): DailyAttendanceDao
    abstract fun sitePhotoDao(): SitePhotoDao
    abstract fun aiAnalysisDao(): AiAnalysisDao
    abstract fun documentDao(): DocumentDao
    abstract fun docFactDao(): DocFactDao
    abstract fun chatMessageDao(): ChatMessageDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `bar_counts` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`elementId` TEXT NOT NULL, " +
                        "`source` TEXT NOT NULL, " +
                        "`diameter` INTEGER NOT NULL, " +
                        "`count` INTEGER NOT NULL)"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `element_attachments` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`elementId` TEXT NOT NULL, " +
                        "`level` TEXT NOT NULL, " +
                        "`type` TEXT NOT NULL, " +
                        "`text` TEXT NOT NULL, " +
                        "`filePath` TEXT, " +
                        "`timestamp` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tasks` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`notes` TEXT NOT NULL, " +
                        "`done` INTEGER NOT NULL, " +
                        "`priority` INTEGER NOT NULL, " +
                        "`dueDate` INTEGER, " +
                        "`level` TEXT, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`completedAt` INTEGER)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pdf_annotations` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`filePath` TEXT NOT NULL, " +
                        "`page` INTEGER NOT NULL, " +
                        "`tool` TEXT NOT NULL, " +
                        "`color` INTEGER NOT NULL, " +
                        "`pointsJson` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `bar_counts` ADD COLUMN `level` TEXT NOT NULL DEFAULT 'GROUND'")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tasks_new` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`notes` TEXT NOT NULL, " +
                        "`done` INTEGER NOT NULL, " +
                        "`priority` INTEGER NOT NULL, " +
                        "`dueDate` INTEGER, " +
                        "`level` TEXT NOT NULL DEFAULT 'GROUND', " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`completedAt` INTEGER)"
                )
                db.execSQL(
                    "INSERT INTO `tasks_new` (id, title, notes, done, priority, dueDate, level, createdAt, completedAt) " +
                        "SELECT id, title, notes, done, priority, dueDate, COALESCE(level, 'GROUND'), createdAt, completedAt FROM `tasks`"
                )
                db.execSQL("DROP TABLE `tasks`")
                db.execSQL("ALTER TABLE `tasks_new` RENAME TO `tasks`")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `notes` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`elementId` TEXT NOT NULL, " +
                        "`level` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL DEFAULT '', " +
                        "`body` TEXT NOT NULL DEFAULT '', " +
                        "`imagePathsJson` TEXT NOT NULL DEFAULT '[]', " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL)"
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `attendance_files` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`level` TEXT NOT NULL, " +
                        "`company` TEXT NOT NULL, " +
                        "`trade` TEXT NOT NULL, " +
                        "`startDate` INTEGER NOT NULL, " +
                        "`notes` TEXT NOT NULL, " +
                        "`colorTag` INTEGER NOT NULL, " +
                        "`logoPath` TEXT, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `daily_attendance` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`fileId` INTEGER NOT NULL, " +
                        "`date` INTEGER NOT NULL, " +
                        "`workers` INTEGER NOT NULL, " +
                        "`foremen` INTEGER NOT NULL, " +
                        "`engineers` INTEGER NOT NULL, " +
                        "`supervisors` INTEGER NOT NULL, " +
                        "`overtimeHours` REAL NOT NULL, " +
                        "`weather` TEXT NOT NULL, " +
                        "`remarks` TEXT NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL)"
                )
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `site_photos` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`level` TEXT NOT NULL, " +
                        "`filePath` TEXT NOT NULL, " +
                        "`comment` TEXT NOT NULL, " +
                        "`timestamp` INTEGER NOT NULL)"
                )
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `site_photos` ADD COLUMN `folder` TEXT NOT NULL DEFAULT ''")
            }
        }

        // كاش تحليل الـ AI لكل دور. من غير DEFAULT هنا عشان يطابق الـEntity بالظبط
        // (Room بيتأكد من تطابق المخطط وبيقع لو فيه اختلاف).
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ai_analysis` (" +
                        "`level` TEXT NOT NULL, " +
                        "`json` TEXT NOT NULL, " +
                        "`model` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`level`))"
                )
            }
        }


        // طبقة المعرفة: مستندات محلّلة + حقائق مستخرجة (knowledge graph) + محادثة.
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `documents` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`filePath` TEXT NOT NULL, `fileName` TEXT NOT NULL, `level` TEXT NOT NULL, " +
                        "`docType` TEXT NOT NULL, `title` TEXT NOT NULL, `drawingNumber` TEXT NOT NULL, " +
                        "`revision` TEXT NOT NULL, `discipline` TEXT NOT NULL, `company` TEXT NOT NULL, " +
                        "`engineer` TEXT NOT NULL, `docDate` TEXT NOT NULL, `summary` TEXT NOT NULL, " +
                        "`status` TEXT NOT NULL, `error` TEXT NOT NULL, " +
                        "`analyzedAt` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `doc_facts` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`documentId` INTEGER NOT NULL, `level` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                        "`key` TEXT NOT NULL, `value` TEXT NOT NULL, `unit` TEXT NOT NULL, " +
                        "`numericValue` REAL NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `chat_messages` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`level` TEXT NOT NULL, `role` TEXT NOT NULL, `content` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_documents_level` ON `documents` (`level`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_facts_level` ON `doc_facts` (`level`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_facts_key` ON `doc_facts` (`key`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_chat_level` ON `chat_messages` (`level`)")
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "corewall.db"
                ).addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
                    MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
                    MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11
                ).build().also { instance = it }
            }
    }
}
