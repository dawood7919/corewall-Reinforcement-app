package com.corewall.qaqc.data

import android.content.Context
import com.corewall.qaqc.data.db.AppDatabase
import com.corewall.qaqc.data.db.AttendanceFileEntity
import com.corewall.qaqc.data.db.BarCountEntity
import com.corewall.qaqc.data.db.CommentEntity
import com.corewall.qaqc.data.db.DailyAttendanceEntity
import com.corewall.qaqc.data.db.ElementAttachmentEntity
import com.corewall.qaqc.data.db.ElementNameEntity
import com.corewall.qaqc.data.db.ImportedMarkEntity
import com.corewall.qaqc.data.db.InspectionEntity
import com.corewall.qaqc.data.db.NoteEntity
import com.corewall.qaqc.data.db.NoteLabelEntity
import com.corewall.qaqc.data.db.NoteLabelLinkEntity
import com.corewall.qaqc.data.db.PromptEntity
import com.corewall.qaqc.data.db.PdfAnnotationEntity
import com.corewall.qaqc.data.db.PdfBookmarkEntity
import com.corewall.qaqc.data.db.PdfMeasurementEntity
import com.corewall.qaqc.data.db.PdfScaleEntity
import com.corewall.qaqc.data.db.RangeEditEntity
import com.corewall.qaqc.data.db.SitePhotoEntity
import com.corewall.qaqc.data.db.TaskEntity
import com.corewall.qaqc.data.db.WirEntity
import com.corewall.qaqc.data.db.WirItemEntity
import com.corewall.qaqc.data.model.BeamRange
import com.corewall.qaqc.data.model.PlanData
import com.corewall.qaqc.data.model.ScheduleData
import com.corewall.qaqc.data.model.WallRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * بيانات الجدول المرجعية بتتقري read-only من الأصول (assets)،
 * وكل تعديلات المستخدم (أسماء/حالات/كومنتات/تعديلات قيم) في Room —
 * بتتحفظ تلقائي وتفضل موجودة بعد قفل التطبيق.
 */
class AppRepository(context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val db = AppDatabase.get(context)

    private val assets = context.applicationContext.assets

    /**
     * بيانات المسقط والجدول — **بتتقري عند أول استخدام مش عند الإنشاء**.
     *
     * قبل كده الاتنين كانوا `val` عادي، والمستودع بيتعمل جوّه
     * `Application.onCreate()`. يعني قراية ملفين JSON وفكّهم كانوا بيحصلوا
     * **قبل أول إطار يترسم** — التطبيق بيفضل شاشة بيضا لحد ما يخلّصوا.
     *
     * `by lazy` بيأجّلهم لأول قراية فعلية، و[warmUp] بيسخّنهم في الخلفية
     * وقت التشغيل. النتيجة إن النافذة بتفتح فوراً والبيانات بتبقى جاهزة
     * قبل ما أي شاشة تسأل عليها.
     */
    val planData: PlanData by lazy {
        assets.open("plan-elements.json")
            .bufferedReader().use { it.readText() }
            .let { json.decodeFromString<PlanData>(it) }
    }

    /** الجدول الأصلي زي ما جه من المكتب — من غير أي تعديلات. */
    val baseSchedule: ScheduleData by lazy {
        assets.open("schedule-data.json")
            .bufferedReader().use { it.readText() }
            .let { json.decodeFromString<ScheduleData>(it) }
    }

    /**
     * بيجهّز الأصول في الخلفية.
     *
     * `by lazy` لوحده بينقل التكلفة لأول قراية — واللي غالباً بيبقى على
     * خيط الواجهة وقت بناء أول شاشة. النداء ده من التشغيل بيخلّي القراية
     * تحصل بالتوازي مع فتح النافذة.
     */
    fun warmUp() {
        planData
        baseSchedule
    }

    val names: Flow<Map<String, String>> =
        db.elementNameDao().observeAll().map { list -> list.associate { it.elementId to it.mark } }

    val inspections: Flow<Map<Pair<String, String>, String>> =
        db.inspectionDao().observeAll().map { list ->
            list.associate { (it.elementId to it.level) to it.status }
        }

    val comments: Flow<List<CommentEntity>> = db.commentDao().observeAll()

    val rangeEdits: Flow<List<RangeEditEntity>> = db.rangeEditDao().observeAll()

    suspend fun setName(elementId: String, mark: String) {
        if (mark.isBlank()) db.elementNameDao().delete(elementId)
        else db.elementNameDao().upsert(ElementNameEntity(elementId, mark.trim()))
    }

    suspend fun setInspection(elementId: String, level: String, status: String) =
        db.inspectionDao().upsert(InspectionEntity(elementId, level, status))

    suspend fun addComment(elementId: String, level: String, text: String) =
        db.commentDao().upsert(
            CommentEntity(elementId = elementId, level = level, text = text, timestamp = System.currentTimeMillis())
        )

    suspend fun deleteComment(id: Long) = db.commentDao().delete(id)

    suspend fun saveRangeEdit(mark: String, rowIndex: Int, patch: Map<String, String>) {
        if (patch.isEmpty()) db.rangeEditDao().delete(mark, rowIndex)
        else db.rangeEditDao().upsert(RangeEditEntity(mark, rowIndex, json.encodeToString(patch)))
    }

    suspend fun clearRangeEdit(mark: String, rowIndex: Int) = db.rangeEditDao().delete(mark, rowIndex)

    // ---------- عدّاد الأسياخ (Corewall Counting) ----------

    val barCounts: Flow<List<BarCountEntity>> = db.barCountDao().observeAll()

    /** استبدال صفوف العدّ لعنصر في دور معيّن — أدوار تانية مش بتتأثر. */
    suspend fun replaceBarCounts(elementId: String, level: String, entries: List<BarCountEntity>) {
        db.barCountDao().deleteForElement(elementId, level)
        val cleaned = entries
            .filter { it.count > 0 && it.diameter > 0 }
            .map { it.copy(id = 0, elementId = elementId, level = level) }
        if (cleaned.isNotEmpty()) db.barCountDao().upsertAll(cleaned)
    }

    // ---------- تطبيق التعديلات فوق الجدول المرجعي ----------

    fun parsePatch(patchJson: String): Map<String, String> =
        runCatching { json.decodeFromString<Map<String, String>>(patchJson) }.getOrDefault(emptyMap())

    /**
     * الجدول المعروض = جدول المكتب + تعديلات المستخدم + الأكواد المستوردة.
     *
     * الترتيب مقصود: التعديلات بتتحط على صفوف المكتب بمكانها (rowIndex)،
     * والمستورد بيتضاف بعد كده. لو كود مستورد بنفس اسم كود مكتبي، المستورد
     * بيكسب — المستخدم استورده عن قصد، وشاشة الاستيراد بتحذّره إن ده هيغطّي.
     */
    fun applyEdits(
        edits: List<RangeEditEntity>,
        imported: List<ImportedMarkEntity> = emptyList()
    ): ScheduleData {
        if (edits.isEmpty() && imported.isEmpty()) return baseSchedule

        // ١) المستورد الأول — بيحلّ محل كود المكتب لو نفس الاسم.
        val walls = LinkedHashMap(baseSchedule.walls)
        val beams = LinkedHashMap(baseSchedule.beams)
        imported.forEach { m ->
            when (m.kind) {
                ImportedMarkEntity.BEAM ->
                    decodeRows<BeamRange>(m.rowsJson)?.let { beams[m.mark] = it }
                ImportedMarkEntity.WALL ->
                    decodeRows<WallRange>(m.rowsJson)?.let { walls[m.mark] = it }
            }
        }
        if (edits.isEmpty()) return ScheduleData(baseSchedule.levels, walls, beams)

        // ٢) تعديلات المستخدم فوق النتيجة.
        //
        // الترتيب ده مش تفصيلة: قبل كده التعديلات كانت بتتطبّق الأول
        // والمستورد بيتكتب فوقها. يعني لو عدّلت قيمة في كمرة مستوردة،
        // التعديل بيتحفظ في القاعدة و**ما بيظهرش أبداً** — أسوأ نوع من
        // الأعطال، لأن المستخدم مش شايف إن حاجة اتكسرت.
        val byKey = edits.associateBy { it.mark to it.rowIndex }
        return ScheduleData(
            levels = baseSchedule.levels,
            walls = walls.mapValues { (mark, rows) ->
                rows.mapIndexed { i, row ->
                    byKey[mark to i]?.let { applyWallPatch(row, parsePatch(it.patchJson)) } ?: row
                }
            },
            beams = beams.mapValues { (mark, rows) ->
                rows.mapIndexed { i, row ->
                    byKey[mark to i]?.let { applyBeamPatch(row, parsePatch(it.patchJson)) } ?: row
                }
            }
        )
    }

    /**
     * الجدول قبل أي تعديل يدوي — المكتب + المستورد وبس.
     *
     * محرّر الصف بيحتاجه عشان يقارن ويخزّن **الفرق** بس. لو قارن بجدول
     * المكتب لوحده، أي كمرة مستوردة مالهاش أصل هناك فكل حقولها بتتخزّن
     * كتعديل حتى لو ماتغيّرش فيها حاجة.
     */
    fun originalSchedule(imported: List<ImportedMarkEntity>): ScheduleData =
        applyEdits(emptyList(), imported)

    /** صف مكسور مايوقّعش الجدول كله — الكود بيتتشال وخلاص. */
    private inline fun <reified T> decodeRows(raw: String): List<T>? =
        runCatching { json.decodeFromString<List<T>>(raw) }.getOrNull()?.takeIf { it.isNotEmpty() }

    // ---------- الأكواد المستوردة من المستخدم ----------

    val importedMarks: Flow<List<ImportedMarkEntity>> = db.importedMarkDao().observeAll()

    /**
     * بيقرا ملف مستورد ويحفظ اللي صحّ منه.
     * بيرجّع النتيجة كاملة — عدد اللي نجح واللي اترفض وليه — مش "تم" وخلاص.
     */
    suspend fun importMarks(content: String, source: String): ScheduleImport.Outcome {
        val outcome = ScheduleImport.parse(
            content = content,
            source = source,
            knownLevels = baseSchedule.levels,
            existingMarks = baseSchedule.allMarks.toSet()
        )
        if (outcome.marks.isNotEmpty()) db.importedMarkDao().upsertAll(outcome.marks)
        return outcome
    }

    suspend fun deleteImportedMark(mark: String) = db.importedMarkDao().delete(mark)

    suspend fun deleteAllImportedMarks() = db.importedMarkDao().deleteAll()

    fun importTemplate(): String = ScheduleImport.beamTemplate(baseSchedule.levels)

    // ---------- مكتبة البرومبت ----------

    val prompts: Flow<List<PromptEntity>> = db.promptDao().observeAll()

    suspend fun savePrompt(prompt: PromptEntity): Long = db.promptDao().upsert(prompt)
    suspend fun deletePrompt(id: Long) = db.promptDao().delete(id)
    suspend fun promptById(id: Long): PromptEntity? = db.promptDao().byId(id)
    suspend fun promptByName(name: String): PromptEntity? = db.promptDao().byName(name)
    suspend fun markPromptUsed(id: Long) = db.promptDao().markUsed(id, System.currentTimeMillis())

    private fun applyWallPatch(row: WallRange, patch: Map<String, String>): WallRange {
        if (patch.isEmpty()) return row
        return row.copy(
            w = patch["w"]?.toIntOrNull() ?: row.w,
            v = patch["v"] ?: row.v,
            h = patch["h"] ?: row.h,
            t = patch["t"] ?: row.t,
            edited = true
        )
    }

    private fun applyBeamPatch(row: BeamRange, patch: Map<String, String>): BeamRange {
        if (patch.isEmpty()) return row
        fun layers(prefix: String, base: List<String>): List<String> =
            List(maxOf(base.size, 3)) { i -> patch["$prefix$i"] ?: base.getOrElse(i) { "-" } }
        return row.copy(
            w = patch["w"]?.toIntOrNull() ?: row.w,
            d = patch["d"]?.toIntOrNull() ?: row.d,
            bottom = layers("B", row.bottom),
            top = layers("T", row.top),
            side = patch["side"] ?: row.side,
            links = patch["links"] ?: row.links,
            edited = true
        )
    }

    // ---------- أداة Data: مرفقات العناصر + المهام + تعليقات PDF ----------

    val attachments: Flow<List<ElementAttachmentEntity>> = db.elementAttachmentDao().observeAll()

    suspend fun addAttachment(entity: ElementAttachmentEntity) =
        db.elementAttachmentDao().upsert(entity)

    suspend fun deleteAttachment(entity: ElementAttachmentEntity) {
        db.elementAttachmentDao().delete(entity.id)
        entity.filePath?.let { runCatching { java.io.File(it).delete() } }
    }

    val tasks: Flow<List<TaskEntity>> = db.taskDao().observeAll()

    suspend fun upsertTask(task: TaskEntity) = db.taskDao().upsert(task)
    suspend fun deleteTask(id: Long) = db.taskDao().delete(id)
    suspend fun deleteCompletedTasks() = db.taskDao().deleteCompleted()

    val notes: Flow<List<NoteEntity>> = db.noteDao().observeAll()
    val noteLabels: Flow<List<NoteLabelEntity>> = db.noteLabelDao().observeAll()
    val noteLabelLinks: Flow<List<NoteLabelLinkEntity>> = db.noteLabelLinkDao().observeAll()

    suspend fun upsertNoteLabel(entity: NoteLabelEntity): Long =
        db.noteLabelDao().upsert(entity)

    /** حذف التصنيف بيشيل روابطه — الرابط اليتيم بيخلّي الفلترة تكدب. */
    suspend fun deleteNoteLabel(id: Long) {
        db.noteLabelLinkDao().unlinkLabel(id)
        db.noteLabelDao().delete(id)
    }

    suspend fun setNoteLabel(noteId: Long, labelId: Long, attached: Boolean) {
        if (attached) db.noteLabelLinkDao().link(NoteLabelLinkEntity(noteId, labelId))
        else db.noteLabelLinkDao().unlink(noteId, labelId)
    }

    suspend fun emptyNoteTrash() = db.noteDao().emptyTrash()

    /**
     * تنظيف المهملات القديمة.
     *
     * بيتنادى مرة عند التشغيل. الملاحظة اللي قعدت شهر في المهملات محدش
     * هيرجّعها، وسيبانها بيخلّي القاعدة تكبر للأبد.
     */
    suspend fun purgeOldNoteTrash(): Int {
        val cutoff = System.currentTimeMillis() -
            NoteEntity.TRASH_RETENTION_DAYS * 24L * 60L * 60L * 1000L
        return db.noteDao().purgeTrashOlderThan(cutoff)
    }

    suspend fun saveNote(note: NoteEntity): Long = db.noteDao().upsert(note)
    /** حذف نهائي — بيشيل روابط التصنيفات معاه. */
    suspend fun deleteNote(note: NoteEntity) {
        db.noteLabelLinkDao().unlinkNote(note.id)
        db.noteDao().delete(note.id)
        runCatching {
            json.decodeFromString<List<String>>(note.imagePathsJson).forEach { java.io.File(it).delete() }
        }
    }

    // ---------- Site Photos (صور الموقع لكل دور) ----------

    val sitePhotos: Flow<List<SitePhotoEntity>> = db.sitePhotoDao().observeAll()

    suspend fun saveSitePhoto(photo: SitePhotoEntity): Long = db.sitePhotoDao().upsert(photo)

    suspend fun deleteSitePhoto(photo: SitePhotoEntity) {
        db.sitePhotoDao().delete(photo.id)
        runCatching { java.io.File(photo.filePath).delete() }
    }

    // ---------- WIR (طلبات فحص الأعمال) ----------

    val wirs: Flow<List<WirEntity>> = db.wirDao().observeAll()

    fun wirItems(wirId: Long): Flow<List<WirItemEntity>> = db.wirDao().observeItems(wirId)

    suspend fun wirByName(level: String, name: String): WirEntity? =
        db.wirDao().byName(level, name)

    suspend fun saveWir(wir: WirEntity): Long = db.wirDao().upsert(wir)

    suspend fun addWirItem(item: WirItemEntity): Long = db.wirDao().upsertItem(item)

    /** تأشير صفحة واحدة — بيتكتب جوّه الـPDF وقت الإرسال لطلب فحص. */
    suspend fun annotationsForPage(path: String, page: Int): List<PdfAnnotationEntity> =
        db.pdfAnnotationDao().forPage(path, page)

    /**
     * بينقل القياسات ومعايرة المقياس مع الصفحة.
     *
     * التعليقات **مش** هنا: دي بتتكتب جوّه الـPDF نفسه عشان الملف اللي
     * بيسيب التطبيق يبقى شايلها. القياسات طبقة تطبيق (رقم ووحدة محسوبين
     * من المقياس) فبتفضل صفوف، والنقط منسّبة (٠..١) فالنقل مايحتاجش أي
     * تحويل — نفس النسب على نفس الصفحة = نفس المكان.
     *
     * والمقياس بينتقل معاها: من غيره القياسات المنقولة بتعرض أرقام غلط،
     * وده أسوأ من إنها ماتظهرش.
     */
    suspend fun copyPageMeasurements(
        fromPath: String,
        fromPage: Int,
        toPath: String,
        toPage: Int
    ): Int {
        val measurements = db.pdfMeasurementDao().forPage(fromPath, fromPage)
        db.pdfMeasurementDao().upsertAll(
            measurements.map { it.copy(id = 0, filePath = toPath, page = toPage) }
        )
        val scale = db.pdfScaleDao().forPage(fromPath, fromPage)
            ?: db.pdfScaleDao().forPage(fromPath, PdfScaleEntity.WHOLE_DOCUMENT)
        if (scale != null) {
            db.pdfScaleDao().upsert(scale.copy(filePath = toPath, page = toPage))
        }
        return measurements.size
    }

    // ---------- Manpower (ملفات الحضور + السجلات اليومية) ----------

    val attendanceFiles: Flow<List<AttendanceFileEntity>> = db.attendanceFileDao().observeAll()
    val dailyAttendance: Flow<List<DailyAttendanceEntity>> = db.dailyAttendanceDao().observeAll()

    suspend fun saveAttendanceFile(file: AttendanceFileEntity): Long = db.attendanceFileDao().upsert(file)
    suspend fun deleteAttendanceFile(id: Long) {
        db.dailyAttendanceDao().deleteForFile(id)
        db.attendanceFileDao().delete(id)
    }
    suspend fun saveDaily(day: DailyAttendanceEntity): Long = db.dailyAttendanceDao().upsert(day)
    suspend fun deleteDaily(id: Long) = db.dailyAttendanceDao().delete(id)

    val pdfAnnotations: Flow<List<PdfAnnotationEntity>> = db.pdfAnnotationDao().observeAll()

    /**
     * بيانات رسمة واحدة.
     *
     * العارض بيفتح ملف واحد، فمالوش لازمة يشترك في تعليقات وعلامات
     * وقياسات كل الملفات. الاشتراك بيبدأ مع فتح الملف وبيقف مع قفله.
     */
    /** لقطة واحدة لتعليقات ملف — للتصدير والمشاركة. */
    suspend fun pdfAnnotationsForFileOnce(filePath: String): List<PdfAnnotationEntity> =
        db.pdfAnnotationDao().getAll().filter { it.filePath == filePath }

    fun pdfAnnotationsFor(filePath: String): Flow<List<PdfAnnotationEntity>> =
        db.pdfAnnotationDao().observeForFile(filePath)

    suspend fun addPdfAnnotation(entity: PdfAnnotationEntity) = db.pdfAnnotationDao().upsert(entity)
    suspend fun undoLastPdfAnnotation(filePath: String, page: Int) =
        db.pdfAnnotationDao().deleteLast(filePath, page)

    suspend fun deletePdfAnnotation(id: Long) = db.pdfAnnotationDao().delete(id)

    suspend fun deletePdfAnnotations(ids: List<Long>) = db.pdfAnnotationDao().deleteAll(ids)
    suspend fun clearPdfPage(filePath: String, page: Int) =
        db.pdfAnnotationDao().clearPage(filePath, page)

    val pdfBookmarks: Flow<List<PdfBookmarkEntity>> = db.pdfBookmarkDao().observeAll()

    fun pdfBookmarksFor(filePath: String): Flow<List<PdfBookmarkEntity>> =
        db.pdfBookmarkDao().observeForFile(filePath)

    suspend fun addPdfBookmark(entity: PdfBookmarkEntity) = db.pdfBookmarkDao().upsert(entity)
    suspend fun deletePdfBookmark(id: Long) = db.pdfBookmarkDao().delete(id)

    val pdfMeasurements: Flow<List<PdfMeasurementEntity>> = db.pdfMeasurementDao().observeAll()
    val pdfScales: Flow<List<PdfScaleEntity>> = db.pdfScaleDao().observeAll()

    fun pdfMeasurementsFor(filePath: String): Flow<List<PdfMeasurementEntity>> =
        db.pdfMeasurementDao().observeForFile(filePath)

    fun pdfScalesFor(filePath: String): Flow<List<PdfScaleEntity>> =
        db.pdfScaleDao().observeForFile(filePath)

    suspend fun addPdfMeasurement(entity: PdfMeasurementEntity) =
        db.pdfMeasurementDao().upsert(entity)
    suspend fun deletePdfMeasurement(id: Long) = db.pdfMeasurementDao().delete(id)
    suspend fun clearPdfMeasurements(filePath: String, page: Int) =
        db.pdfMeasurementDao().clearPage(filePath, page)
    suspend fun setPdfScale(entity: PdfScaleEntity) = db.pdfScaleDao().upsert(entity)
    suspend fun clearPdfScale(filePath: String, page: Int) =
        db.pdfScaleDao().delete(filePath, page)

    // ---------- نسخة احتياطية ----------

    @Serializable
    data class Backup(
        val version: Int = 2,
        val exportedAt: Long,
        val names: List<ElementNameEntity>,
        val inspections: List<InspectionEntity>,
        val comments: List<CommentEntity>,
        val rangeEdits: List<RangeEditEntity>,
        val barCounts: List<BarCountEntity> = emptyList(),
        val tasks: List<TaskEntity> = emptyList(),
        val attachments: List<ElementAttachmentEntity> = emptyList(),
        val pdfAnnotations: List<PdfAnnotationEntity> = emptyList(),
        val notes: List<NoteEntity> = emptyList(),
        val attendanceFiles: List<AttendanceFileEntity> = emptyList(),
        val dailyAttendance: List<DailyAttendanceEntity> = emptyList(),
        val sitePhotos: List<SitePhotoEntity> = emptyList(),
        // القيم الافتراضية بتخلّي النسخ القديمة تتقري عادي — من غيرها
        // كل نسخة اتصدّرت قبل النهاردة كانت هتبقى غير قابلة للاستيراد.
        val prompts: List<PromptEntity> = emptyList(),
        val importedMarks: List<ImportedMarkEntity> = emptyList(),
        val pdfBookmarks: List<PdfBookmarkEntity> = emptyList(),
        val pdfMeasurements: List<PdfMeasurementEntity> = emptyList(),
        val pdfScales: List<PdfScaleEntity> = emptyList(),
        val noteLabels: List<NoteLabelEntity> = emptyList(),
        val noteLabelLinks: List<NoteLabelLinkEntity> = emptyList()
    )

    suspend fun exportBackupJson(): String = json.encodeToString(
        Backup(
            exportedAt = System.currentTimeMillis(),
            names = db.elementNameDao().getAll(),
            inspections = db.inspectionDao().getAll(),
            comments = db.commentDao().getAll(),
            rangeEdits = db.rangeEditDao().getAll(),
            barCounts = db.barCountDao().getAll(),
            tasks = db.taskDao().getAll(),
            attachments = db.elementAttachmentDao().getAll(),
            pdfAnnotations = db.pdfAnnotationDao().getAll(),
            notes = db.noteDao().getAll(),
            attendanceFiles = db.attendanceFileDao().getAll(),
            dailyAttendance = db.dailyAttendanceDao().getAll(),
            sitePhotos = db.sitePhotoDao().getAll(),
            prompts = db.promptDao().getAll(),
            importedMarks = db.importedMarkDao().getAll(),
            pdfBookmarks = db.pdfBookmarkDao().getAll(),
            pdfMeasurements = db.pdfMeasurementDao().getAll(),
            pdfScales = db.pdfScaleDao().getAll(),
            noteLabels = db.noteLabelDao().getAll(),
            noteLabelLinks = db.noteLabelLinkDao().getAll()
        )
    )

    /** بيرجع رسالة نجاح/فشل مختصرة. */
    suspend fun importBackupJson(content: String): Result<String> = runCatching {
        val backup = json.decodeFromString<Backup>(content)
        db.elementNameDao().upsertAll(backup.names)
        db.inspectionDao().upsertAll(backup.inspections)
        db.commentDao().upsertAll(backup.comments)
        db.rangeEditDao().upsertAll(backup.rangeEdits)
        db.barCountDao().upsertAll(backup.barCounts.map { it.copy(id = 0) })
        db.taskDao().upsertAll(backup.tasks.map { it.copy(id = 0) })
        db.elementAttachmentDao().upsertAll(backup.attachments.map { it.copy(id = 0) })
        db.pdfAnnotationDao().upsertAll(backup.pdfAnnotations.map { it.copy(id = 0) })
        db.noteDao().upsertAll(backup.notes.map { it.copy(id = 0) })
        db.attendanceFileDao().upsertAll(backup.attendanceFiles.map { it.copy(id = 0) })
        db.dailyAttendanceDao().upsertAll(backup.dailyAttendance.map { it.copy(id = 0) })
        db.sitePhotoDao().upsertAll(backup.sitePhotos.map { it.copy(id = 0) })
        backup.prompts.forEach { db.promptDao().upsert(it.copy(id = 0)) }
        db.importedMarkDao().upsertAll(backup.importedMarks)
        db.pdfBookmarkDao().upsertAll(backup.pdfBookmarks.map { it.copy(id = 0) })
        db.pdfMeasurementDao().upsertAll(backup.pdfMeasurements.map { it.copy(id = 0) })
        db.pdfScaleDao().upsertAll(backup.pdfScales)
        db.noteLabelDao().upsertAll(backup.noteLabels)
        db.noteLabelLinkDao().linkAll(backup.noteLabelLinks)
        "تم استيراد ${backup.names.size} اسم و${backup.inspections.size} حالة فحص " +
            "و${backup.comments.size} كومنت و${backup.barCounts.size} صف عدّ و${backup.tasks.size} مهمة " +
            "و${backup.sitePhotos.size} صورة موقع و${backup.prompts.size} برومبت " +
            "و${backup.importedMarks.size} كود مستورد"
    }
}
