package com.corewall.qaqc.takeoff

import android.content.Context
import android.net.Uri
import com.corewall.qaqc.data.db.AppDatabase
import com.corewall.qaqc.data.db.TakeoffCategoryEntity
import com.corewall.qaqc.data.db.TakeoffDrawingEntity
import com.corewall.qaqc.data.db.TakeoffFormulaEntity
import com.corewall.qaqc.data.db.TakeoffGroupEntity
import com.corewall.qaqc.data.db.TakeoffItemEntity
import com.corewall.qaqc.data.db.TakeoffProjectEntity
import com.corewall.qaqc.data.db.TakeoffScaleEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * حالة قسم الحصر.
 *
 * القسم ده **مستقل تماماً** عن باقي التطبيق: مالوش علاقة بالدور الشغّال
 * ولا بشجرة ملفات المشروع. المستخدم بيعمل قسم، بيرفع فيه رسمات، وبيحصر.
 *
 * الماسك بيتبني `by lazy` من الـViewModel، فاللي مش بيستخدم الحصر مش
 * دافع تمن أي استعلام ولا اشتراك.
 */
class TakeoffStore(
    private val appContext: Context,
    private val scope: CoroutineScope
) {
    private val dao by lazy { AppDatabase.get(appContext).takeoffDao() }
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * مجلد رسمات الحصر.
     *
     * جوّه مساحة التطبيق عن قصد: الـURI اللي جاي من منتقي الملفات صلاحيته
     * مؤقتة، ولو خزّناه بدل ما ننسخ الملف، الرسمة بتختفي بعد إعادة التشغيل
     * والحصر اللي عليها يبقى معلّق على ملف مش موجود.
     */
    private val drawingsDir: File
        get() = File(appContext.getExternalFilesDir(null) ?: appContext.filesDir, "takeoff")
            .apply { mkdirs() }

    val projects: StateFlow<List<TakeoffProjectEntity>> =
        dao.observeProjects().stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun drawings(projectId: Long): Flow<List<TakeoffDrawingEntity>> = dao.observeDrawings(projectId)
    suspend fun drawingById(id: Long): TakeoffDrawingEntity? = withContext(Dispatchers.IO) { dao.drawing(id) }
    fun items(drawingId: Long): Flow<List<TakeoffItemEntity>> = dao.observeItems(drawingId)
    fun projectItems(projectId: Long): Flow<List<TakeoffItemEntity>> = dao.observeProjectItems(projectId)
    fun scales(drawingId: Long): Flow<List<TakeoffScaleEntity>> = dao.observeScales(drawingId)

    // ═══════════════════════════════════════════════ الأقسام

    suspend fun createProject(name: String, note: String = ""): Long =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            dao.upsertProject(
                TakeoffProjectEntity(
                    name = name.trim().ifBlank { "قسم بدون اسم" },
                    note = note.trim(), createdAt = now, updatedAt = now
                )
            )
        }

    suspend fun renameProject(project: TakeoffProjectEntity, name: String) =
        withContext(Dispatchers.IO) {
            val clean = name.trim()
            if (clean.isNotEmpty()) {
                dao.upsertProject(
                    project.copy(name = clean, updatedAt = System.currentTimeMillis())
                )
            }
            Unit
        }

    /**
     * حذف قسم — **وكل ملفاته من القرص**.
     *
     * الصفوف بتروح بالتتالي (رسمات ← بنود ← معايرة)، بس ملفات الـPDF
     * نفسها مش في القاعدة. لو مامسحناهاش، كل قسم بيتحذف بيسيب ورا ملفات
     * محدش يقدر يوصلها ولا يمسحها.
     */
    suspend fun deleteProject(id: Long) = withContext(Dispatchers.IO) {
        dao.drawingsOf(id).forEach { drawing ->
            dao.clearDrawingItems(drawing.id)
            dao.clearScales(drawing.id)
            dao.clearDrawingFormulas(drawing.id)
            runCatching { File(drawing.filePath).delete() }
            dao.deleteDrawing(drawing.id)
        }
        dao.deleteProject(id)
    }

    // ═══════════════════════════════════════════════ الفئات والمجموعات

    fun categories(projectId: Long): Flow<List<TakeoffCategoryEntity>> = dao.observeCategories(projectId)
    fun groups(projectId: Long): Flow<List<TakeoffGroupEntity>> = dao.observeGroups(projectId)

    /** بيرجّع الفئة الجديدة كاملة — الشاشة محتاجة الـid فورًا تحدّده كنشطة. */
    suspend fun createCategory(
        projectId: Long,
        name: String,
        colorArgb: Long,
        rate: Double = 0.0,
        wastePct: Double = 0.0
    ): TakeoffCategoryEntity = withContext(Dispatchers.IO) {
        val entity = TakeoffCategoryEntity(
            projectId = projectId,
            name = name.trim().ifBlank { "بلا اسم" },
            colorArgb = colorArgb,
            rate = rate,
            wastePct = wastePct,
            createdAt = System.currentTimeMillis()
        )
        entity.copy(id = dao.upsertCategory(entity))
    }

    suspend fun updateCategory(category: TakeoffCategoryEntity) =
        withContext(Dispatchers.IO) { dao.upsertCategory(category); Unit }

    /** حذف فئة **بيفكّ ربط بنودها** — الحصر بتاعها فاضل، بس من غير تصنيف. */
    suspend fun deleteCategory(id: Long) = withContext(Dispatchers.IO) {
        dao.clearCategoryFromItems(id)
        dao.groupsOfCategory(id).forEach { dao.deleteGroup(it.id) }
        dao.deleteCategory(id)
    }

    suspend fun createGroup(categoryId: Long, name: String): TakeoffGroupEntity =
        withContext(Dispatchers.IO) {
            val entity = TakeoffGroupEntity(categoryId = categoryId, name = name.trim().ifBlank { "مجموعة" })
            entity.copy(id = dao.upsertGroup(entity))
        }

    suspend fun deleteGroup(id: Long) = withContext(Dispatchers.IO) { dao.deleteGroup(id) }

    // ═══════════════════════════════════════════════ الرسمات

    /**
     * بينسخ ملف PDF جوّه التطبيق ويسجّله في القسم.
     *
     * بيرجّع `null` لو النسخ فشل — والشاشة بتقول للمستخدم بدل ما تسجّل
     * رسمة بمسار فاضي.
     */
    suspend fun addDrawing(projectId: Long, uri: Uri, displayName: String): Long? =
        withContext(Dispatchers.IO) {
            val safeName = displayName.replace(Regex("""[^\p{L}\p{N}._\- ]"""), "_")
                .ifBlank { "رسمة" }
            val target = uniqueFile(drawingsDir, safeName.ifEndsWithPdf())
            val copied = runCatching {
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                } != null
            }.getOrDefault(false)
            if (!copied || !target.exists() || target.length() == 0L) {
                runCatching { target.delete() }
                return@withContext null
            }
            dao.upsertDrawing(
                TakeoffDrawingEntity(
                    projectId = projectId,
                    name = safeName.removeSuffix(".pdf"),
                    filePath = target.absolutePath,
                    pageCount = 1,
                    createdAt = System.currentTimeMillis()
                )
            )
        }

    suspend fun setPageCount(drawing: TakeoffDrawingEntity, pages: Int) =
        withContext(Dispatchers.IO) {
            if (pages > 0 && pages != drawing.pageCount) {
                dao.upsertDrawing(drawing.copy(pageCount = pages))
            }
            Unit
        }

    suspend fun deleteDrawing(drawing: TakeoffDrawingEntity) = withContext(Dispatchers.IO) {
        dao.clearDrawingItems(drawing.id)
        dao.clearScales(drawing.id)
        dao.clearDrawingFormulas(drawing.id)
        runCatching { File(drawing.filePath).delete() }
        dao.deleteDrawing(drawing.id)
    }

    // ═══════════════════════════════════════════════ المعايرة

    suspend fun setScale(drawingId: Long, page: Int, metresPerPoint: Double, note: String) =
        withContext(Dispatchers.IO) {
            dao.upsertScale(TakeoffScaleEntity(drawingId, page, metresPerPoint, note))
        }

    /**
     * نسخ معايرة صفحة لكل صفحات الرسمة.
     *
     * مجموعة رسمات متطبوعة بمقياس واحد = معايرة واحدة تكفي. من غير ده
     * المستخدم بيعاير كل صفحة بإيده، وده أكتر مكان بيحصل فيه غلط بشري.
     */
    suspend fun copyScaleToAllPages(drawingId: Long, from: TakeoffScaleEntity, pageCount: Int) =
        withContext(Dispatchers.IO) {
            dao.upsertScales(
                (0 until pageCount).map { page ->
                    from.copy(drawingId = drawingId, page = page)
                }
            )
        }

    // ═══════════════════════════════════════════════ البنود

    suspend fun saveItem(item: TakeoffItemEntity): Long =
        withContext(Dispatchers.IO) { dao.upsertItem(item) }

    /** حذف بند وكل خصوماته — مفيش خصم بيفضل معلّق على أب ميت. */
    suspend fun deleteItem(id: Long) = withContext(Dispatchers.IO) { dao.deleteItemCascade(id) }

    suspend fun itemById(id: Long): TakeoffItemEntity? =
        withContext(Dispatchers.IO) { dao.item(id) }

    // ═══════════════════════════════════════════════ الصيغ

    fun formulas(drawingId: Long): Flow<List<TakeoffFormulaEntity>> = dao.observeFormulas(drawingId)

    suspend fun saveFormula(entity: TakeoffFormulaEntity): Long =
        withContext(Dispatchers.IO) { dao.upsertFormula(entity) }

    suspend fun deleteFormula(id: Long) = withContext(Dispatchers.IO) { dao.deleteFormula(id) }

    // ═══════════════════════════════════════════════ التحويل

    /** الصف المتخزّن → نموذج الحساب الخالص. */
    fun toModel(row: TakeoffItemEntity): TakeoffItem = TakeoffItem(
        id = row.id.toString(),
        drawingPath = row.drawingId.toString(),
        page = row.page,
        tool = runCatching { TakeoffTool.valueOf(row.tool) }.getOrDefault(TakeoffTool.AREA),
        name = row.name,
        categoryId = row.categoryId?.toString(),
        colorArgb = row.colorArgb,
        visible = row.visible,
        verts = decodeRing(row.pointsJson),
        extraRings = decodeRings(row.extraRingsJson),
        extraSegments = decodeRings(row.extraSegmentsJson),
        parentId = row.parentId?.toString(),
        groupId = row.groupId?.toString(),
        zone = row.zone,
        progressPercent = row.progressPercent,
        rateOverride = row.rateOverride,
        thickness = row.thickness,
        colLength = row.colLength,
        colWidth = row.colWidth,
        colHeight = row.colHeight
    )

    fun categoryToModel(row: TakeoffCategoryEntity): TakeoffCategory = TakeoffCategory(
        id = row.id.toString(), name = row.name, colorArgb = row.colorArgb,
        rate = row.rate, wastePct = row.wastePct
    )

    fun groupToModel(row: TakeoffGroupEntity): TakeoffGroup = TakeoffGroup(
        id = row.id.toString(), categoryId = row.categoryId.toString(), name = row.name
    )

    fun formulaToModel(row: TakeoffFormulaEntity): TakeoffFormula = TakeoffFormula(
        id = row.id.toString(), name = row.name, expr = row.expr, unit = row.unit,
        roundTo = row.roundTo, colorArgb = row.colorArgb,
        refs = decodeRefs(row.refsJson).mapValues { it.value.toString() }
    )

    fun encodeRefs(refs: Map<String, Long>): String = json.encodeToString(refs)

    private fun decodeRefs(raw: String): Map<String, Long> = runCatching {
        json.decodeFromString<Map<String, Long>>(raw)
    }.getOrDefault(emptyMap())

    fun encodeRing(points: List<TakeoffPoint>): String =
        json.encodeToString(points.map { listOf(it.x, it.y) })

    fun encodeRings(rings: List<List<TakeoffPoint>>): String =
        json.encodeToString(rings.map { ring -> ring.map { listOf(it.x, it.y) } })

    private fun decodeRing(raw: String): List<TakeoffPoint> = runCatching {
        json.decodeFromString<List<List<Double>>>(raw)
            .mapNotNull { if (it.size >= 2) TakeoffPoint(it[0], it[1]) else null }
    }.getOrDefault(emptyList())

    private fun decodeRings(raw: String): List<List<TakeoffPoint>> = runCatching {
        json.decodeFromString<List<List<List<Double>>>>(raw)
            .map { ring -> ring.mapNotNull { if (it.size >= 2) TakeoffPoint(it[0], it[1]) else null } }
    }.getOrDefault(emptyList())

    private fun String.ifEndsWithPdf(): String =
        if (endsWith(".pdf", ignoreCase = true)) this else "$this.pdf"

    private fun uniqueFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        var i = 2
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        while (candidate.exists()) {
            candidate = File(dir, if (ext.isEmpty()) "$base ($i)" else "$base ($i).$ext")
            i++
        }
        return candidate
    }
}
