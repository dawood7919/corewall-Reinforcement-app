package com.corewall.qaqc.takeoff

import android.content.Context
import android.net.Uri
import com.corewall.qaqc.data.db.AppDatabase
import com.corewall.qaqc.data.db.TakeoffDrawingEntity
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
            runCatching { File(drawing.filePath).delete() }
            dao.deleteDrawing(drawing.id)
        }
        dao.deleteProject(id)
    }

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

    // ═══════════════════════════════════════════════ التحويل

    /** الصف المتخزّن → نموذج الحساب الخالص. */
    fun toModel(row: TakeoffItemEntity): TakeoffItem = TakeoffItem(
        id = row.id.toString(),
        drawingPath = row.drawingId.toString(),
        page = row.page,
        tool = runCatching { TakeoffTool.valueOf(row.tool) }.getOrDefault(TakeoffTool.AREA),
        name = row.name,
        categoryId = row.category,
        colorArgb = row.colorArgb,
        visible = row.visible,
        verts = decodeRing(row.pointsJson),
        extraRings = decodeRings(row.extraRingsJson),
        extraSegments = decodeRings(row.extraSegmentsJson),
        parentId = row.parentId?.toString(),
        zone = row.zone
    )

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
