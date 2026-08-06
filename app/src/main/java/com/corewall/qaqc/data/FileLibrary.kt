package com.corewall.qaqc.data

import com.corewall.qaqc.data.db.FileMetaDao
import com.corewall.qaqc.data.db.FileMetaEntity
import com.corewall.qaqc.data.db.LinkDao
import com.corewall.qaqc.data.db.LinkEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

/**
 * مكتبة الملفات — الطبقة اللي بتدّي الملفات معنى.
 *
 * الملفات نفسها بتفضل على القرص زي ما هي. الكلاس ده بيعلّق عليها وسوم
 * ومفضّلة ونصّ مستخرج، وبيدّي البحث والفلاتر.
 *
 * القاعدة: المسار هو الهوية. لو الملف اتمسح من برّه التطبيق، السطر بتاعه
 * بيبقى يتيم — وده مقبول ورخيص، أرخص بكتير من إننا نحاول نراقب كل تغيير
 * على نظام الملفات.
 */
class FileLibrary(
    private val metaDao: FileMetaDao,
    private val linkDao: LinkDao
) {

    val allMeta: Flow<Map<String, FileMetaEntity>> =
        metaDao.observeAll().map { list -> list.associateBy { it.path } }

    val favourites: Flow<List<FileMetaEntity>> = metaDao.observeFavourites()

    val recent: Flow<List<FileMetaEntity>> = metaDao.observeRecent()

    /** كل الوسوم المستخدمة فعلاً — للفلاتر. */
    val allTags: Flow<List<String>> = metaDao.observeAll().map { list ->
        list.flatMap { it.tagList }.distinct().sorted()
    }

    suspend fun meta(path: String): FileMetaEntity? = metaDao.byPath(path)

    private suspend fun mutate(path: String, block: (FileMetaEntity) -> FileMetaEntity) {
        val current = metaDao.byPath(path) ?: FileMetaEntity(path = path)
        metaDao.upsert(block(current).copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun toggleFavourite(path: String) =
        mutate(path) { it.copy(favourite = !it.favourite) }

    suspend fun setTags(path: String, tags: List<String>) =
        mutate(path) { it.copy(tags = tags.map(String::trim).filter(String::isNotEmpty).joinToString(",")) }

    suspend fun addTag(path: String, tag: String) {
        val t = tag.trim()
        if (t.isEmpty()) return
        mutate(path) { m -> if (t in m.tagList) m else m.copy(tags = (m.tagList + t).joinToString(",")) }
    }

    suspend fun removeTag(path: String, tag: String) =
        mutate(path) { m -> m.copy(tags = m.tagList.filterNot { it == tag }.joinToString(",")) }

    /** بيتنده لما ملف يتفتح — بيغذّي قايمة "الأخيرة". */
    suspend fun markOpened(path: String) =
        mutate(path) { it.copy(lastOpenedAt = System.currentTimeMillis()) }

    suspend fun setOcr(path: String, text: String, status: String) =
        mutate(path) { it.copy(ocrText = text, ocrStatus = status) }

    suspend fun markOcrPending(path: String) =
        mutate(path) { it.copy(ocrStatus = FileMetaEntity.OCR_PENDING) }

    suspend fun pendingOcr(limit: Int = 20): List<FileMetaEntity> =
        metaDao.withOcrStatus(FileMetaEntity.OCR_PENDING, limit)

    /**
     * بحث في الاسم والوسوم والنصّ المستخرج.
     *
     * الترتيب مقصود: مطابقة الاسم أولاً لأن اللي بيدوّر بالاسم عارف بيدوّر
     * على إيه؛ ومطابقة النصّ المستخرج بعدها لأنها اكتشاف مش استرجاع.
     */
    suspend fun search(query: String, limit: Int = 100): List<FileSearchHit> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        return metaDao.search(q, limit).map { meta ->
            val name = File(meta.path).name
            val where = when {
                name.contains(q, ignoreCase = true) -> FileSearchHit.Where.NAME
                meta.tagList.any { it.contains(q, ignoreCase = true) } -> FileSearchHit.Where.TAG
                else -> FileSearchHit.Where.CONTENT
            }
            FileSearchHit(
                path = meta.path,
                name = name,
                where = where,
                snippet = if (where == FileSearchHit.Where.CONTENT) snippet(meta.ocrText, q) else "",
                meta = meta
            )
        }.sortedBy { it.where.ordinal }
    }

    /** مقتطف حوالين موضع المطابقة — عشان تعرف ليه الملف ده ظهر. */
    private fun snippet(text: String, q: String, radius: Int = 60): String {
        val i = text.indexOf(q, ignoreCase = true)
        if (i < 0) return ""
        val start = (i - radius).coerceAtLeast(0)
        val end = (i + q.length + radius).coerceAtMost(text.length)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < text.length) "…" else ""
        return prefix + text.substring(start, end).replace('\n', ' ').trim() + suffix
    }

    // ─────────────────────────────────────────────── الروابط

    suspend fun link(fromType: String, fromId: String, toType: String, toId: String, level: String) {
        linkDao.upsert(
            LinkEntity(
                fromType = fromType, fromId = fromId,
                toType = toType, toId = toId,
                level = level, createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun linksFor(type: String, id: String): List<LinkEntity> = linkDao.allFor(type, id)

    suspend fun unlinkAll(type: String, id: String) = linkDao.deleteAllFor(type, id)

    /** بيتنده بعد حذف ملف — بينضّف البيانات المعلّقة. */
    suspend fun forget(path: String) {
        metaDao.delete(path)
        linkDao.deleteAllFor(LinkEntity.FILE, path)
    }
}

/** نتيجة بحث واحدة، ومعاها **ليه** ظهرت. */
data class FileSearchHit(
    val path: String,
    val name: String,
    val where: Where,
    val snippet: String,
    val meta: FileMetaEntity
) {
    enum class Where(val label: String) {
        NAME("في الاسم"),
        TAG("في الوسوم"),
        CONTENT("جوّه الملف")
    }
}
