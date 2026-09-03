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
        AgentExecutionPlanEntity::class,
        AgentExecutionStepEntity::class,
        AgentActionAuditEntity::class,
        CreativeDocumentEntity::class,
        CreativeDocumentExportEntity::class,
        CadDrawingSettingsEntity::class,
        CadMeasurementEntity::class,
        DocumentEntity::class,
        DocFactEntity::class,
        DocChunkEntity::class,
        DocChunkFtsEntity::class,
        ChatMessageEntity::class,
        FileMetaEntity::class,
        ChatThreadEntity::class,
        LinkEntity::class,
        NoteRevisionEntity::class,
        PromptEntity::class,
        ImportedMarkEntity::class,
        PdfBookmarkEntity::class,
        PdfMeasurementEntity::class,
        PdfScaleEntity::class,
        NoteLabelEntity::class,
        NoteLabelLinkEntity::class,
        TakeoffProjectEntity::class,
        TakeoffDrawingEntity::class,
        TakeoffScaleEntity::class,
        TakeoffItemEntity::class,
        TakeoffCategoryEntity::class,
        TakeoffGroupEntity::class,
        TakeoffFormulaEntity::class,
        TakeoffAnnotationEntity::class,
        WirEntity::class,
        WirItemEntity::class,
        AttendanceRosterEntity::class,
        AttendanceMarkEntity::class
    ],
    version = 30,
    // بيتصدّر لـ`app/schemas` عشان الفحص الآلي في الـCI يقدر يقراه.
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun elementNameDao(): ElementNameDao
    abstract fun takeoffDao(): TakeoffDao
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
    abstract fun agentExecutionDao(): AgentExecutionDao
    abstract fun creativeDocumentDao(): CreativeDocumentDao
    abstract fun cadMeasurementDao(): CadMeasurementDao
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
    abstract fun pdfMeasurementDao(): PdfMeasurementDao
    abstract fun pdfScaleDao(): PdfScaleDao
    abstract fun noteLabelDao(): NoteLabelDao
    abstract fun noteLabelLinkDao(): NoteLabelLinkDao
    abstract fun wirDao(): WirDao
    abstract fun attendanceSheetDao(): AttendanceSheetDao

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

        /**
         * القياس: قياسات مرسومة + معايرة المقياس.
         *
         * `pdf_scales` مفتاحها الأساسي (filePath, page) — يعني مافيش
         * عمود `id`، وRoom بيتوقّع الجدول كده بالظبط. والفهرس على
         * `pdf_measurements.filePath` اسمه لازم يبقى `index_pdf_measurements_filePath`
         * زي ما Room بيولّده؛ `tools/check_room_schema.py` بيقارن الاتنين.
         */
        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pdf_measurements` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`filePath` TEXT NOT NULL, `page` INTEGER NOT NULL, " +
                        "`kind` TEXT NOT NULL, `pointsJson` TEXT NOT NULL, " +
                        "`label` TEXT NOT NULL, `colorArgb` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pdf_measurements_filePath` ON `pdf_measurements` (`filePath`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pdf_scales` (" +
                        "`filePath` TEXT NOT NULL, `page` INTEGER NOT NULL, " +
                        "`unitsPerPoint` REAL NOT NULL, `unit` TEXT NOT NULL, " +
                        "`note` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`filePath`, `page`))"
                )
            }
        }

        /**
         * نظام الملاحظات: تثبيت وأرشفة ومهملات وألوان وتصنيفات وتذكير.
         *
         * كل الأعمدة الجديدة بقيم افتراضية، فالملاحظات الموجودة بتفضل زي
         * ما هي بالظبط: مش مثبّتة، مش مؤرشفة، مش محذوفة، لون الثيم.
         *
         * أسماء الفهارس هنا لازم تطابق اللي Room بيولّده بالحرف —
         * `tools/check_room_schema.py` بيقارنهم في الـCI، والاختلاف معناه
         * تطبيق بيقفل أول ما يفتح عند اللي بيحدّث.
         */
        /**
         * فهرس على `pdf_annotations.filePath`.
         *
         * الاستعلام بقى بيفلتر بالملف بدل ما يقرا الجدول كله ويفلتر في
         * Kotlin — ومن غير فهرس ده مسح كامل للجدول مع كل فتحة رسمة.
         * مفيش تغيير في أي بيانات.
         */
        /**
         * قسم حصر الكميات — أربع جداول جديدة، صفر لمس لأي بيانات قديمة.
         *
         * الحصر قسم مستقل: أقسامه ورسماته وبنوده مالهاش أي علاقة بجداول
         * المشروع (الأدوار، الجدول، الملفات). الترحيل ده **بيضيف بس**.
         */
        private val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `takeoff_projects` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `note` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `takeoff_drawings` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`projectId` INTEGER NOT NULL, `name` TEXT NOT NULL, " +
                        "`filePath` TEXT NOT NULL, `pageCount` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_takeoff_drawings_projectId` ON `takeoff_drawings` (`projectId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `takeoff_scales` (" +
                        "`drawingId` INTEGER NOT NULL, `page` INTEGER NOT NULL, " +
                        "`metresPerPoint` REAL NOT NULL, `note` TEXT NOT NULL, " +
                        "PRIMARY KEY(`drawingId`, `page`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `takeoff_items` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`drawingId` INTEGER NOT NULL, `page` INTEGER NOT NULL, " +
                        "`tool` TEXT NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL, " +
                        "`colorArgb` INTEGER NOT NULL, `visible` INTEGER NOT NULL, " +
                        "`pointsJson` TEXT NOT NULL, `extraRingsJson` TEXT NOT NULL, " +
                        "`extraSegmentsJson` TEXT NOT NULL, `parentId` INTEGER, " +
                        "`zone` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_takeoff_items_drawingId_page` ON `takeoff_items` (`drawingId`, `page`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_takeoff_items_parentId` ON `takeoff_items` (`parentId`)")
            }
        }

        /**
         * فئات ومجموعات الحصر + أعمدة جديدة على البنود — **بيضيف بس**.
         *
         * الأعمدة الجديدة كلها Nullable أو ليها قيمة افتراضية، فالصفوف
         * القديمة بتقرا سليمة من غير أي تحويل بيانات.
         */
        private val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `takeoff_categories` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`projectId` INTEGER NOT NULL, `name` TEXT NOT NULL, " +
                        "`colorArgb` INTEGER NOT NULL, `rate` REAL NOT NULL, " +
                        "`wastePct` REAL NOT NULL, `sortOrder` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_takeoff_categories_projectId` ON `takeoff_categories` (`projectId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `takeoff_groups` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`categoryId` INTEGER NOT NULL, `name` TEXT NOT NULL, " +
                        "`sortOrder` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_takeoff_groups_categoryId` ON `takeoff_groups` (`categoryId`)")
                db.execSQL("ALTER TABLE `takeoff_items` ADD COLUMN `categoryId` INTEGER")
                db.execSQL("ALTER TABLE `takeoff_items` ADD COLUMN `groupId` INTEGER")
                db.execSQL("ALTER TABLE `takeoff_items` ADD COLUMN `progressPercent` REAL")
                db.execSQL("ALTER TABLE `takeoff_items` ADD COLUMN `rateOverride` REAL")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_takeoff_items_categoryId` ON `takeoff_items` (`categoryId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_takeoff_items_groupId` ON `takeoff_items` (`groupId`)")
            }
        }

        /** الحجم والعمود — ٤ أعمدة اختيارية جديدة على البنود، بيضيف بس. */
        private val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `takeoff_items` ADD COLUMN `thickness` REAL")
                db.execSQL("ALTER TABLE `takeoff_items` ADD COLUMN `colLength` REAL")
                db.execSQL("ALTER TABLE `takeoff_items` ADD COLUMN `colWidth` REAL")
                db.execSQL("ALTER TABLE `takeoff_items` ADD COLUMN `colHeight` REAL")
            }
        }

        /** جدول الصيغ — بيضيف بس. */
        private val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `takeoff_formulas` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`drawingId` INTEGER NOT NULL, `name` TEXT NOT NULL, " +
                        "`expr` TEXT NOT NULL, `unit` TEXT NOT NULL, " +
                        "`roundTo` INTEGER NOT NULL, `colorArgb` INTEGER NOT NULL, " +
                        "`refsJson` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_takeoff_formulas_drawingId` ON `takeoff_formulas` (`drawingId`)")
            }
        }

        /** التعليقات — جدول جديد، بيضيف بس. */
        private val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `takeoff_annotations` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`drawingId` INTEGER NOT NULL, `page` INTEGER NOT NULL, " +
                        "`type` TEXT NOT NULL, `pointsJson` TEXT NOT NULL, " +
                        "`colorArgb` INTEGER NOT NULL, `text` TEXT NOT NULL, " +
                        "`visible` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_takeoff_annotations_drawingId_page` ON `takeoff_annotations` (`drawingId`, `page`)")
            }
        }

        /** مصدر محتوى الملاحظات الجديد: وثيقة متعددة الكتل قابلة للترحيل كسولاً. */
        private val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `notes` ADD COLUMN `documentJson` TEXT NOT NULL DEFAULT ''")
            }
        }

        /** خطط وكيل الذكاء وإيصالات التنفيذ الدائمة؛ جداول جديدة لا تمس بيانات المستخدم. */
        private val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `agent_execution_plans` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`level` TEXT NOT NULL, `title` TEXT NOT NULL, `sourceQuestion` TEXT NOT NULL, " +
                        "`status` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_execution_plans_level` ON `agent_execution_plans` (`level`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_execution_plans_status` ON `agent_execution_plans` (`status`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `agent_execution_steps` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `planId` INTEGER NOT NULL, " +
                        "`ordinal` INTEGER NOT NULL, `tool` TEXT NOT NULL, `argsJson` TEXT NOT NULL, " +
                        "`label` TEXT NOT NULL, `risk` TEXT NOT NULL, `requiresApproval` INTEGER NOT NULL, " +
                        "`status` TEXT NOT NULL, `result` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_execution_steps_planId` ON `agent_execution_steps` (`planId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_execution_steps_status` ON `agent_execution_steps` (`status`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `agent_action_audit` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `planId` INTEGER, `stepId` INTEGER, " +
                        "`level` TEXT NOT NULL, `tool` TEXT NOT NULL, `detail` TEXT NOT NULL, `result` TEXT NOT NULL, " +
                        "`ok` INTEGER NOT NULL, `auto` INTEGER NOT NULL, `at` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_action_audit_level` ON `agent_action_audit` (`level`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_action_audit_planId` ON `agent_action_audit` (`planId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_action_audit_at` ON `agent_action_audit` (`at`)")
            }
        }

        /** مسودات استوديو الإنشاء وإصداراتها؛ تحفظ المصدر والتصدير منفصلين. */
        private val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `creative_documents` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `level` TEXT NOT NULL, " +
                        "`templateKey` TEXT NOT NULL, `title` TEXT NOT NULL, `contentJson` TEXT NOT NULL, " +
                        "`sourceJson` TEXT NOT NULL, `status` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_creative_documents_level` ON `creative_documents` (`level`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_creative_documents_updatedAt` ON `creative_documents` (`updatedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_creative_documents_status` ON `creative_documents` (`status`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `creative_document_exports` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `documentId` INTEGER NOT NULL, " +
                        "`format` TEXT NOT NULL, `path` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_creative_document_exports_documentId` ON `creative_document_exports` (`documentId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_creative_document_exports_createdAt` ON `creative_document_exports` (`createdAt`)")
            }
        }

        /** معايرة ورسومات قياس CAD؛ تبقى الإحداثيات الأصلية مستقلة عن الزوم. */
        private val MIGRATION_26_27 = object : Migration(26, 27) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `cad_drawing_settings` (" +
                        "`filePath` TEXT NOT NULL, `unitsPerMeter` REAL NOT NULL, `displayUnit` TEXT NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`filePath`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `cad_measurements` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `filePath` TEXT NOT NULL, " +
                        "`kind` TEXT NOT NULL, `pointsJson` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cad_measurements_filePath` ON `cad_measurements` (`filePath`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cad_measurements_createdAt` ON `cad_measurements` (`createdAt`)")
            }
        }

        /**
         * النص الأصلي للمستندات + فهرس بحث عليه.
         *
         * جملة `CREATE VIRTUAL TABLE` لازم تطابق اللي Room بيولّده بالحرف —
         * Room بيقارن مخطط الكيانات بالقاعدة بعد كل ترحيل، وأي اختلاف بيرمي
         * استثناء والتطبيق بيقفل بعد ثانيتين من الفتح. `tools/check_room_schema.py`
         * بيقارن الاتنين في الـCI قبل نشر الـAPK.
         *
         * الجدولين فاضيين بعد الترحيل: المستندات القديمة اتحللت قبل ما المخزن
         * ده يوجد، ونصّها مش متسجّل. مابنعيدش تحليلها — بتفضل شغّالة بحقايقها
         * زي ما هي، والنص بيتسجّل لأي مستند بيتحلّل من دلوقتي.
         */
        private val MIGRATION_27_28 = object : Migration(27, 28) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `doc_chunks` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`documentId` INTEGER NOT NULL, `level` TEXT NOT NULL, " +
                        "`page` INTEGER NOT NULL, `ordinal` INTEGER NOT NULL, " +
                        "`text` TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_doc_chunks_documentId` " +
                        "ON `doc_chunks` (`documentId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_doc_chunks_level` " +
                        "ON `doc_chunks` (`level`)"
                )
                db.execSQL(
                    "CREATE VIRTUAL TABLE IF NOT EXISTS `doc_chunks_fts` " +
                        "USING FTS4(`text` TEXT NOT NULL, tokenize=unicode61)"
                )
            }
        }

        /**
         * طلبات الفحص (WIR).
         *
         * الجملتين دول لازم يطابقوا اللي Room بيولّده حرف بحرف — أي فرق
         * (فهرس ناقص، ترتيب عمود، `NOT NULL` زيادة) بيخلّي Room يرمي عند
         * أول فتح والتطبيق يقفل. `tools/check_room_schema.py` بيقارنهم
         * بالمخطط المصدَّر في الـCI قبل نشر الـAPK.
         */
        /**
         * شيت الحضور بالأسماء.
         *
         * الفهرس الفريد على (`rosterId`,`day`) هو اللي بيخلّي تسجيل نفس
         * اليوم مرتين **يستبدل** بدل ما يضيف صف تاني — من غيره الخلية
         * الواحدة بتبقى ليها تاريخين متناقضين ومحدش يعرف أنهي واحد الصح.
         */
        private val MIGRATION_29_30 = object : Migration(29, 30) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `attendance_roster` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`fileId` INTEGER NOT NULL, `name` TEXT NOT NULL, " +
                        "`code` TEXT NOT NULL, `trade` TEXT NOT NULL, " +
                        "`ordinal` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_attendance_roster_fileId` " +
                        "ON `attendance_roster` (`fileId`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `attendance_marks` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`fileId` INTEGER NOT NULL, `rosterId` INTEGER NOT NULL, " +
                        "`day` INTEGER NOT NULL, `state` TEXT NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_attendance_marks_rosterId_day` " +
                        "ON `attendance_marks` (`rosterId`, `day`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_attendance_marks_fileId_day` " +
                        "ON `attendance_marks` (`fileId`, `day`)"
                )
                db.execSQL(
                    "ALTER TABLE `attendance_files` ADD COLUMN `sheetPath` TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        private val MIGRATION_28_29 = object : Migration(28, 29) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `wirs` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `level` TEXT NOT NULL, " +
                        "`filePath` TEXT NOT NULL, `pageCount` INTEGER NOT NULL, " +
                        "`status` TEXT NOT NULL, `note` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_wirs_level` ON `wirs` (`level`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `wir_items` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`wirId` INTEGER NOT NULL, `sourcePath` TEXT NOT NULL, " +
                        "`sourceName` TEXT NOT NULL, `sourcePage` INTEGER NOT NULL, " +
                        "`page` INTEGER NOT NULL, `addedAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_wir_items_wirId` " +
                        "ON `wir_items` (`wirId`)"
                )
            }
        }

        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pdf_annotations_filePath` ON `pdf_annotations` (`filePath`)")
            }
        }

        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `notes` ADD COLUMN `pinned` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `notes` ADD COLUMN `archived` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `notes` ADD COLUMN `deletedAt` INTEGER")
                db.execSQL("ALTER TABLE `notes` ADD COLUMN `colorArgb` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `notes` ADD COLUMN `kind` TEXT NOT NULL DEFAULT 'TEXT'")
                db.execSQL("ALTER TABLE `notes` ADD COLUMN `reminderAt` INTEGER")
                db.execSQL("ALTER TABLE `notes` ADD COLUMN `noteType` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `notes` ADD COLUMN `priority` INTEGER NOT NULL DEFAULT 0")

                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_archived_deletedAt` ON `notes` (`archived`, `deletedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_pinned` ON `notes` (`pinned`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_level` ON `notes` (`level`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `note_labels` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `colorArgb` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_note_labels_name` ON `note_labels` (`name`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `note_label_links` (" +
                        "`noteId` INTEGER NOT NULL, `labelId` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`noteId`, `labelId`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_label_links_labelId` ON `note_label_links` (`labelId`)")
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
                    MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17,
                    MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21,
                    MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27,
                    MIGRATION_27_28, MIGRATION_28_29, MIGRATION_29_30
                ).build().also { instance = it }
            }
    }
}
