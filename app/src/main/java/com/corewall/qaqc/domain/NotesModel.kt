package com.corewall.qaqc.domain

import com.corewall.qaqc.data.db.NoteEntity
import com.corewall.qaqc.data.db.NoteLabelEntity

/**
 * منطق الملاحظات — الفلترة والبحث وقوايم المهام.
 *
 * كل ده هنا مش في الشاشة عن قصد: الشاشة بتعرض، والقرار "أنهي ملاحظة
 * تظهر" و"هل دي بتطابق البحث" قرار منطقي بيتّاخد مرة واحدة ويتّست لوحده.
 */

/** الجزء اللي المستخدم شايفه من الملاحظات. */
enum class NotesView(val label: String) {
    ACTIVE("الملاحظات"),
    ARCHIVE("الأرشيف"),
    TRASH("المهملات")
}

/** شكل العرض — قايمة أو شبكة. */
enum class NotesLayout { LIST, GRID }

/**
 * بند في قايمة مهام.
 *
 * البنود متخزّنة **جوّه نصّ الملاحظة** كسطور ماركداون (`- [ ] بند`) مش في
 * جدول لوحدها. القرار ده مقصود:
 *
 * • البحث بيشتغل عليها مجاناً — هي أصلاً جزء من النص.
 * • التحويل بين ملاحظة نصّية وقايمة مهام مافيهوش أي فقدان.
 * • العارض الموجود بيرسم الـ`- [ ]` أصلاً، فالمعاينة في الكارت مجانية.
 * • مفيش جدول تالت ولا ترحيل ولا مزامنة بين نصّين لنفس الحاجة.
 *
 * الثمن الوحيد إن إعادة الترتيب بتتعمل على النص — وده أرخص من الثمن التاني.
 */
data class ChecklistItem(
    val text: String,
    val done: Boolean,
    /** رقم السطر في النص الأصلي — عشان التعديل يرجع مكانه بالظبط. */
    val line: Int
)

object NotesLogic {

    private val CHECK_LINE = Regex("""^\s*[-*]\s*\[( |x|X)]\s?(.*)$""")

    /** بيقرا بنود قايمة المهام من نصّ الملاحظة. */
    fun checklist(body: String): List<ChecklistItem> =
        body.lines().mapIndexedNotNull { index, raw ->
            CHECK_LINE.matchEntire(raw)?.let { m ->
                ChecklistItem(
                    text = m.groupValues[2].trim(),
                    done = !m.groupValues[1].equals(" ", ignoreCase = false),
                    line = index
                )
            }
        }

    /** بيبدّل حالة بند واحد ويرجّع النص كامل. */
    fun toggleItem(body: String, line: Int): String {
        val lines = body.lines().toMutableList()
        if (line !in lines.indices) return body
        val m = CHECK_LINE.matchEntire(lines[line]) ?: return body
        val done = !m.groupValues[1].equals(" ", ignoreCase = false)
        lines[line] = "- [${if (done) " " else "x"}] ${m.groupValues[2].trim()}"
        return lines.joinToString("\n")
    }

    fun setItemText(body: String, line: Int, text: String): String {
        val lines = body.lines().toMutableList()
        if (line !in lines.indices) return body
        val m = CHECK_LINE.matchEntire(lines[line]) ?: return body
        val mark = if (m.groupValues[1].equals(" ", false)) " " else "x"
        lines[line] = "- [$mark] $text"
        return lines.joinToString("\n")
    }

    fun removeItem(body: String, line: Int): String {
        val lines = body.lines().toMutableList()
        if (line !in lines.indices) return body
        lines.removeAt(line)
        return lines.joinToString("\n")
    }

    /** بيضيف بند جديد في الآخر ويرجّع (النص، رقم السطر الجديد). */
    fun addItem(body: String, text: String = ""): Pair<String, Int> {
        val lines = body.lines().toMutableList()
        // سطر فاضي في الآخر مش بند — بنستبدله بدل ما نسيب فراغ.
        if (lines.isNotEmpty() && lines.last().isBlank()) lines.removeAt(lines.size - 1)
        lines += "- [ ] $text"
        return lines.joinToString("\n") to lines.lastIndex
    }

    /**
     * بينقل بند لمكان تاني.
     *
     * البنود مش لازم تكون سطور متتالية (ممكن يكون بينهم نص)، فالنقل
     * بيشتغل على **مواضع البنود** مش على أرقام السطور مباشرة.
     */
    fun moveItem(body: String, from: Int, to: Int): String {
        val lines = body.lines().toMutableList()
        val positions = lines.indices.filter { CHECK_LINE.matches(lines[it]) }
        val fromPos = positions.indexOf(from)
        val toPos = positions.indexOf(to)
        if (fromPos < 0 || toPos < 0 || fromPos == toPos) return body

        // بنرتّب نصوص البنود بالترتيب الجديد وبنعيد توزيعها على نفس
        // المواضع — فالنص اللي بين البنود بيفضل مكانه.
        val texts = positions.map { lines[it] }.toMutableList()
        texts.add(toPos, texts.removeAt(fromPos))
        positions.forEachIndexed { i, lineIndex -> lines[lineIndex] = texts[i] }
        return lines.joinToString("\n")
    }

    /** بيحوّل ملاحظة نصّية لقايمة مهام: كل سطر فيه كلام بيبقى بند. */
    fun toChecklist(body: String): String =
        body.lines()
            .filter { it.isNotBlank() }
            .joinToString("\n") { line ->
                if (CHECK_LINE.matches(line)) line else "- [ ] ${line.trim()}"
            }
            .ifBlank { "- [ ] " }

    /** بيرجّع النص عادي — بيشيل علامات البنود بس. */
    fun toPlainText(body: String): String =
        body.lines().joinToString("\n") { line ->
            CHECK_LINE.matchEntire(line)?.groupValues?.get(2)?.trim() ?: line
        }

    // ────────────────────────────────────────────────────────── البحث

    /**
     * مطابقة بحث.
     *
     * بيدوّر في العنوان والنص (وده شامل بنود قايمة المهام) وأسماء
     * التصنيفات ونوع الملاحظة والدور. كل كلمة في الاستعلام لازم تتطابق —
     * يعني "حيطة تفتيش" بترجّع اللي فيه الاتنين مش اللي فيه أي واحد،
     * وده اللي المستخدم بيتوقّعه لما يضيف كلمة تانية.
     */
    fun matches(
        note: NoteEntity,
        query: String,
        labelNames: List<String>
    ): Boolean {
        val terms = query.trim().split(WHITESPACE).filter { it.isNotBlank() }
        if (terms.isEmpty()) return true
        val haystack = buildString {
            append(note.title).append(' ')
            append(note.body).append(' ')
            append(note.level).append(' ')
            append(note.noteType).append(' ')
            labelNames.forEach { append(it).append(' ') }
        }
        return terms.all { haystack.contains(it, ignoreCase = true) }
    }

    private val WHITESPACE = Regex("\\s+")

    /**
     * ترتيب العرض: المثبّت أولاً وبعدين الأحدث تعديلاً.
     *
     * التثبيت بيكسر ترتيب الوقت عن قصد — ده معنى التثبيت. جوّه كل مجموعة،
     * الأحدث فوق لأن الملاحظة اللي لسه بتتكتب هي اللي المستخدم راجعلها.
     */
    fun sorted(notes: List<NoteEntity>): List<NoteEntity> =
        notes.sortedWith(
            compareByDescending<NoteEntity> { it.pinned }.thenByDescending { it.updatedAt }
        )

    /** بيفلتر حسب الجزء المعروض + التصنيف المختار + البحث. */
    fun visible(
        notes: List<NoteEntity>,
        view: NotesView,
        labelFilter: Long?,
        query: String,
        labelsOf: (Long) -> List<NoteLabelEntity>
    ): List<NoteEntity> {
        val filtered = notes.asSequence()
            .filter { note ->
                when (view) {
                    NotesView.ACTIVE -> note.isActive
                    NotesView.ARCHIVE -> note.archived && !note.isTrashed
                    NotesView.TRASH -> note.isTrashed
                }
            }
            .filter { note ->
                labelFilter == null || labelsOf(note.id).any { it.id == labelFilter }
            }
            .filter { note -> matches(note, query, labelsOf(note.id).map { it.name }) }
            .toList()
        return sorted(filtered)
    }
}

/**
 * ألوان الملاحظات.
 *
 * لوحة قصيرة ومهدّية عن قصد. الغرض إن المستخدم يفرّق بين مجموعات بلمحة،
 * مش إن الشاشة تبقى ملوّنة. الألوان الفاقعة بتشدّ العين للون بدل المحتوى،
 * وفي تطبيق بيتفتح في الشمس ده بيأذي القراءة.
 */
object NoteColors {
    /** ٠ = لون السطح من الثيم (يشتغل في الفاتح والغامق لوحده). */
    const val DEFAULT = 0L

    val PALETTE = listOf(
        DEFAULT,
        0xFFFFF3C4, // رملي
        0xFFFFE0DB, // طوبي فاتح
        0xFFE4F0D8, // زيتي فاتح
        0xFFD9EAF7, // سماوي
        0xFFE8E1F5, // بنفسجي هادي
        0xFFF5E1EC, // وردي هادي
        0xFFE3E6E8  // رمادي
    )

    /**
     * لون النص فوق لون الملاحظة.
     *
     * الألوان كلها فاتحة، فالنص الغامق بيحقّق تباين كافي عليها في الوضعين.
     * بنرجّع `null` للون الافتراضي عشان الثيم يتصرّف هو.
     */
    fun contentOn(argb: Long): Long? = if (argb == DEFAULT) null else 0xFF1B1B1F
}
