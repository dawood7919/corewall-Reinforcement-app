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
        ChatMessageEntity::class,
        FileMetaEntity::class,
        ChatThreadEntity::class,
        LinkEntity::class,
        NoteRevisionEntity::class,
        PromptEntity::class,
        ImportedMarkEntity::class,
        PdfBookmarkEntity::class
    ],
    version = 15,
    // بيتصدّر لـ`app/schemas` عشان الفحص الآلي في الـCI يقدر يقراه.
    exportSchema = true
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
    abstract fun fileMetaDao(): FileMetaDao
    abstract fun chatThreadDao(): ChatThreadDao
    abstract fun linkDao(): LinkDao
    abstract fun noteRevisionDao(): NoteRevisionDao
    abstract fun promptDao(): PromptDao
    abstract fun importedMarkDao(): ImportedMarkDao
    abstract fun pdfBookmarkDao(): PdfBookmarkDao

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



        /**
         * إعادة تصميم الموديولات: بيانات الملفات (وسوم/مفضّلة/OCR)، المحادثات
         * كوحدات ليها عنوان وتثبيت، الروابط العامة بين الكيانات، ونُسخ الملاحظات.
         *
         * كله جداول جديدة — مفيش عمود اتغيّر ولا اتشال، فالبيانات القديمة
         * ما بتتلمسش خالص.
         */
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `file_meta` (" +
                        "`path` TEXT NOT NULL, `favourite` INTEGER NOT NULL, " +
                        "`tags` TEXT NOT NULL, `ocrText` TEXT NOT NULL, " +
                        "`ocrStatus` TEXT NOT NULL, `lastOpenedAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`path`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `chat_threads` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`level` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                        "`pinned` INTEGER NOT NULL, `folder` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `links` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`fromType` TEXT NOT NULL, `fromId` TEXT NOT NULL, " +
                        "`toType` TEXT NOT NULL, `toId` TEXT NOT NULL, " +
                        "`level` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `note_revisions` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`noteId` INTEGER NOT NULL, `title` TEXT NOT NULL, " +
                        "`body` TEXT NOT NULL, `savedAt` INTEGER NOT NULL)"
                )
                // الرسايل القديمة بتفضل زي ما هي؛ threadId = 0 يعني "قبل المحادثات".
                db.execSQL("ALTER TABLE `chat_messages` ADD COLUMN `threadId` INTEGER NOT NULL DEFAULT 0")
                // الأسماء دي مش اختيارية: لازم تطابق اللي Room بيولّده من
                // `@Index` على الكيان بالحرف، وإلا التحقّق بعد الترحيل بيرمي
                // والتطبيق بيقفل أول ما يفتح. الصيغة هي
                // `index_<جدول>_<عمود>_<عمود>`.
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_links_fromType_fromId` ON `links` (`fromType`, `fromId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_links_toType_toId` ON `links` (`toType`, `toId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_messages_threadId` ON `chat_messages` (`threadId`)")
            }
        }

        /**
         * مكتبة البرومبت + الأكواد المستوردة من المستخدم.
         *
         * كله إضافة: جدولين جداد وعمود واحد على `documents`. مفيش عمود
         * اتشال ولا اتغيّر نوعه، فالبيانات القديمة ما بتتلمسش.
         *
         * ملاحظة مقصودة: **مفيش `CREATE INDEX` هنا**. الجدولين صغيرين
         * (عشرات الصفوف)، والفهرس اللي مش معرّف على الكيان بيوقّع التطبيق
         * أول ما يفتح — وده حصل مرتين قبل كده.
         */
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `prompts` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `body` TEXT NOT NULL, " +
                        "`usageCount` INTEGER NOT NULL, `lastUsedAt` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `imported_marks` (" +
                        "`mark` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                        "`rowsJson` TEXT NOT NULL, `source` TEXT NOT NULL, " +
                        "`rowCount` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`mark`))"
                )
                db.execSQL("ALTER TABLE `documents` ADD COLUMN `promptName` TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * سُمك الخط والشفافية للعلامات.
         *
         * عمودين بقيم افتراضية بس — العلامات القديمة بتاخد السُمك اللي كانت
         * بترسم بيه فعلاً (٢.٥) وشفافية كاملة، فشكلها مابيتغيّرش.
         *
         * ومفيش `@ColumnInfo(defaultValue = …)` على الكيان عن قصد: طالما
         * الكيان مش معرّف قيمة افتراضية، Room مابيقارنهاش أصلاً. ده نفس
         * اللي عملناه مع `threadId` في ترحيل ١١→١٢ واشتغل.
         */
        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `pdf_annotations` ADD COLUMN `strokeWidth` REAL NOT NULL DEFAULT 2.5")
                db.execSQL("ALTER TABLE `pdf_annotations` ADD COLUMN `opacity` REAL NOT NULL DEFAULT 1.0")
            }
        }

        /**
         * العلامات المرجعية على ملفات الـPDF.
         *
         * اسم الفهرس هنا مش اختياري — لازم يبقى بالظبط `index_pdf_bookmarks_filePath`،
         * وده الاسم اللي Room بيولّده لـ`@Index(value = ["filePath"])`. أي اسم
         * تاني وRoom بيقارن المخطط وقت التشغيل، بيلاقي اختلاف، ويرمي — والتطبيق
         * بيقفل أول ما يلمس القاعدة. حصلت قبل كده مرتين، وعلشان كده فيه سكربت
         * `tools/check_room_schema.py` بيقارن الاتنين في الـCI.
         */
        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pdf_bookmarks` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`filePath` TEXT NOT NULL, `page` INTEGER NOT NULL, " +
                        "`label` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pdf_bookmarks_filePath` ON `pdf_bookmarks` (`filePath`)")
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
                // ملاحظة: ممنوع نعمل فهارس هنا طالما الـEntity مش معرّفها —
                // Room بيقارن المخطط وبيقع لو فيه أي اختلاف (ده اللي كان بيقفل التطبيق).
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
                    MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11,
                    MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14,
                    MIGRATION_14_15
                ).build().also { instance = it }
            }
    }
}
