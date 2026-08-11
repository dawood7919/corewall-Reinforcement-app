package com.corewall.qaqc.takeoff

import kotlin.math.abs
import kotlin.math.hypot

/**
 * حساب كميات الحصر — **كوتلن خالص، بيتّست على الـJVM**.
 *
 * ## القاعدة اللي كل الملف مبني عليها
 *
 * **مفيش دالة كمية واحدة هنا بتاخد [ViewTransform].**
 *
 * ده مش تنظيم، ده الضمانة نفسها. المواصفة بتقول إن أخطر باج في نسخة
 * الويب كان إن المساحة بتتغيّر لما المستخدم يكبّر. الباج ده بيحصل لما
 * التكبير يدخل في حساب الكمية. لو التكبير **مش بارامتر أصلاً** في أي
 * دالة حساب، الباج ده مستحيل يتكتب — الكومبايلر بيمنعه.
 *
 * التكبير موجود في دالتين بس: [pageToScreen] و[screenToPage]، ودول
 * وظيفتهم العرض واللمس، مش الحساب.
 */
object TakeoffMath {

    // ═══════════════════════════════════════════════ التحويل (وبس)

    /** نقطة منسّبة → بكسل على الشاشة. للرسم. */
    fun pageToScreen(p: TakeoffPoint, page: PageGeometry, view: ViewTransform): Pair<Double, Double> =
        Pair(
            p.x * page.widthPt * view.zoom + view.offsetX,
            p.y * page.heightPt * view.zoom + view.offsetY
        )

    /**
     * بكسل على الشاشة → نقطة منسّبة. **للّمس**.
     *
     * كل لمسة بتعدّي من هنا **فوراً** قبل ما تتخزّن في أي مسوّدة. دي
     * النقطة اللي المواصفة بتصرّ عليها: "converted immediately to
     * page-space before it is ever stored".
     */
    fun screenToPage(sx: Double, sy: Double, page: PageGeometry, view: ViewTransform): TakeoffPoint {
        val w = page.widthPt * view.zoom
        val h = page.heightPt * view.zoom
        if (w == 0.0 || h == 0.0) return TakeoffPoint(0.0, 0.0)
        return TakeoffPoint((sx - view.offsetX) / w, (sy - view.offsetY) / h)
    }

    // ═══════════════════════════════════════════════ الهندسة

    /**
     * مساحة مضلّع بالمتر المربّع — صيغة الشوليس (Shoelace).
     *
     * النقط منسّبة، فبنحوّلها لنقط PDF الأول (× مقاس الصفحة) وبعدين
     * للمتر (× المعايرة تربيع). ترتيب الضرب ده مقصود: التحويل للنقط
     * بيخلّي الرقم الوسيط مفهوم لو حد وقف يـdebug فيه.
     *
     * المضلّع بيتقفل لوحده (آخر نقطة ↔ أول نقطة)، فمش لازم المستخدم
     * يكرّر أول نقطة في الآخر.
     */
    fun area(ring: List<TakeoffPoint>, page: PageGeometry): Double {
        if (ring.size < 3 || !page.calibrated) return 0.0
        var sum = 0.0
        for (i in ring.indices) {
            val a = ring[i]
            val b = ring[(i + 1) % ring.size]
            val ax = a.x * page.widthPt
            val ay = a.y * page.heightPt
            val bx = b.x * page.widthPt
            val by = b.y * page.heightPt
            sum += ax * by - bx * ay
        }
        val pointsSquared = abs(sum) / 2.0
        return pointsSquared * page.metresPerPoint * page.metresPerPoint
    }

    /** طول خط مكسّر بالمتر — مجموع أضلاعه. المسار **مش** بيتقفل. */
    fun length(line: List<TakeoffPoint>, page: PageGeometry): Double {
        if (line.size < 2 || !page.calibrated) return 0.0
        var points = 0.0
        for (i in 0 until line.size - 1) {
            val a = line[i]
            val b = line[i + 1]
            points += hypot(
                (b.x - a.x) * page.widthPt,
                (b.y - a.y) * page.heightPt
            )
        }
        return points * page.metresPerPoint
    }

    /** محيط مضلّع مقفول بالمتر. */
    fun perimeter(ring: List<TakeoffPoint>, page: PageGeometry): Double {
        if (ring.size < 3) return 0.0
        return length(ring + ring.first(), page)
    }

    // ═══════════════════════════════════════════════ الكميات

    /**
     * الكمية الإجمالية لبند — **قبل** أي خصم.
     *
     * بتشمل الأشكال المتجمّعة ([TakeoffItem.extraRings] و
     * [TakeoffItem.extraSegments]) لأنها جزء من نفس البند مش بنود تانية.
     */
    fun grossQuantity(item: TakeoffItem, page: PageGeometry): Double = when (item.tool) {
        TakeoffTool.AREA, TakeoffTool.DEDUCT ->
            area(item.verts, page) + item.extraRings.sumOf { area(it, page) }

        TakeoffTool.LENGTH ->
            length(item.verts, page) + item.extraSegments.sumOf { length(it, page) }

        // العدّ مالوش علاقة بالمعايرة — علامة = واحد، حتى لو الصفحة
        // مش معايرة أصلاً.
        TakeoffTool.COUNT -> item.verts.size.toDouble()
    }

    /**
     * الكمية الصافية — الإجمالي ناقص الخصومات المربوطة بالبند ده.
     *
     * الخصم **مش** كمية سالبة في المجموع العام. لو كان كده، خصم باب
     * كان هيقلّل مساحة البلاط في أوضة تانية خالص. الربط بـ[TakeoffItem.parentId]
     * بيخلّي الخصم يتقطع من الحيطة بتاعته هي بس.
     *
     * الخصومات نفسها مالهاش خصومات (خصم جوّه خصم مالوش معنى)، فبنحسب
     * إجماليها مباشرة من غير تكرار (recursion).
     *
     * الخصم المخفي مابيتحسبش: إخفاء الخصم معناه "وريني المساحة من غير
     * الفتحة دي".
     */
    fun netQuantity(
        item: TakeoffItem,
        all: List<TakeoffItem>,
        page: PageGeometry
    ): Double {
        val gross = grossQuantity(item, page)
        if (item.tool != TakeoffTool.AREA) return gross
        val deducted = all.asSequence()
            .filter { it.tool == TakeoffTool.DEDUCT && it.parentId == item.id && it.visible }
            .sumOf { grossQuantity(it, page) }
        return gross - deducted
    }

    /** كل الخصومات المربوطة ببند — للرسم (الفتحة) وللحذف المتتالي. */
    fun deductionsOf(item: TakeoffItem, all: List<TakeoffItem>): List<TakeoffItem> =
        all.filter { it.tool == TakeoffTool.DEDUCT && it.parentId == item.id }

    // ═══════════════════════════════════════════════ التكلفة

    /**
     * الكمية الصافية **بعد** إضافة نسبة الهالك.
     *
     * العدّ مالوش هالك — علامة اتحطّت مرة مش بتزيد بنسبة. لو الأداة
     * تعتبر عدّ ([TakeoffTool.COUNT]) بترجع الكمية الصافية زي ما هي.
     */
    fun quantityWithWaste(
        item: TakeoffItem,
        all: List<TakeoffItem>,
        page: PageGeometry,
        categories: List<TakeoffCategory>
    ): Double {
        val net = netQuantity(item, all, page)
        if (item.tool == TakeoffTool.COUNT) return net
        val waste = wastePctFor(item, categories)
        return if (waste > 0.0) net * (1.0 + waste / 100.0) else net
    }

    /** نسبة هالك الفئة — الفئة الوحيدة اللي عندها هالك، مفيش تجاوز على البند. */
    fun wastePctFor(item: TakeoffItem, categories: List<TakeoffCategory>): Double =
        categories.firstOrNull { it.id == item.categoryId }?.wastePct ?: 0.0

    /** سعر الوحدة — تجاوز البند لو موجود، وإلا افتراضي فئته. */
    fun rateFor(item: TakeoffItem, categories: List<TakeoffCategory>): Double =
        item.rateOverride ?: categories.firstOrNull { it.id == item.categoryId }?.rate ?: 0.0

    /** التكلفة التقديرية — الكمية بعد الهالك × السعر. */
    fun costOf(
        item: TakeoffItem,
        all: List<TakeoffItem>,
        page: PageGeometry,
        categories: List<TakeoffCategory>
    ): Double = quantityWithWaste(item, all, page, categories) * rateFor(item, categories)

    // ═══════════════════════════════════════════════ اختبار اللمس

    /**
     * نقطة جوّه مضلّع؟ — خوارزمية ray casting.
     *
     * بتشتغل على الإحداثيات المنسّبة مباشرة: التحويل لنقط ضرب في ثابت
     * موجب على المحورين، وده مابيغيّرش نتيجة "جوّه ولا برّه".
     */
    fun pointInRing(p: TakeoffPoint, ring: List<TakeoffPoint>): Boolean {
        if (ring.size < 3) return false
        var inside = false
        var j = ring.size - 1
        for (i in ring.indices) {
            val a = ring[i]
            val b = ring[j]
            if ((a.y > p.y) != (b.y > p.y) &&
                p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x
            ) inside = !inside
            j = i
        }
        return inside
    }

    /**
     * أقرب مسافة من نقطة لخط مكسّر، **بالنقط** (مش منسّبة).
     *
     * بالنقط عشان نصف قطر اللمس يبقى ثابت بصرياً: نصف قطر منسّب معناه
     * إن اللمس على صفحة A0 يبقى أسمح بكتير منه على A4، وده بيبان كعشوائية.
     */
    fun distanceToPolylinePt(p: TakeoffPoint, line: List<TakeoffPoint>, page: PageGeometry): Double {
        if (line.isEmpty()) return Double.MAX_VALUE
        val px = p.x * page.widthPt
        val py = p.y * page.heightPt
        if (line.size == 1) {
            return hypot(px - line[0].x * page.widthPt, py - line[0].y * page.heightPt)
        }
        var best = Double.MAX_VALUE
        for (i in 0 until line.size - 1) {
            val ax = line[i].x * page.widthPt
            val ay = line[i].y * page.heightPt
            val bx = line[i + 1].x * page.widthPt
            val by = line[i + 1].y * page.heightPt
            best = minOf(best, distanceToSegment(px, py, ax, ay, bx, by))
        }
        return best
    }

    /**
     * أقرب رأس من `item.verts` للنقطة، لو جوّه نصف قطر اللمس.
     *
     * مقصود إنها تفحص `verts` بس مش `extraRings`/`extraSegments` — تعديل
     * الرؤوس دلوقتي بيشتغل على الشكل الأساسي، والتجميع (رسم مستمر) لسه
     * مالوش واجهة تضيف له. لو حد ضاف الواجهة دي بعدين لازم يوسّع الفحص هنا.
     */
    fun nearestVertexIndex(
        item: TakeoffItem,
        p: TakeoffPoint,
        page: PageGeometry,
        tapRadiusPt: Double
    ): Int? {
        if (item.verts.isEmpty()) return null
        var bestIdx = -1
        var bestDist = Double.MAX_VALUE
        item.verts.forEachIndexed { i, v ->
            val d = hypot((p.x - v.x) * page.widthPt, (p.y - v.y) * page.heightPt)
            if (d < bestDist) { bestDist = d; bestIdx = i }
        }
        return bestIdx.takeIf { bestDist <= tapRadiusPt }
    }

    /**
     * البند بالكامل جوّه مستطيل تحديد؟ — لازم **كل** رأس جوّه، مش أي رأس.
     *
     * الإحداثيات منسّبة على الاتنين فمفيش داعي لـ[PageGeometry]: المقارنة
     * صحيحة زي ما هي من غير أي تحويل.
     */
    fun fullyInside(item: TakeoffItem, min: TakeoffPoint, max: TakeoffPoint): Boolean {
        val all = item.verts + item.extraRings.flatten() + item.extraSegments.flatten()
        if (all.isEmpty()) return false
        return all.all { it.x in min.x..max.x && it.y in min.y..max.y }
    }

    private fun distanceToSegment(
        px: Double, py: Double,
        ax: Double, ay: Double,
        bx: Double, by: Double
    ): Double {
        val dx = bx - ax
        val dy = by - ay
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared == 0.0) return hypot(px - ax, py - ay)
        // الإسقاط مقصوص على [٠،١] عشان يفضل على القطعة مش على امتدادها.
        val t = (((px - ax) * dx + (py - ay) * dy) / lengthSquared).coerceIn(0.0, 1.0)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }

    /**
     * البند ده متلمس؟ — بيفحص **كل** أجزاء البند مش الشكل الأساسي بس.
     *
     * الجزء ده مهم: شكل اتبنى بالتجميع (أربع مضلّعات في بند واحد) لازم
     * يتحدّد بلمس **أي** جزء منه. فحص `verts` لوحدها كان هيخلّي تلات
     * أرباع الشكل مش قابلة للتحديد — وده بيبان كعطل عشوائي.
     *
     * [tapRadiusPt] بالنقط. للأصابع خلّيه أوسع من الماوس بكتير.
     */
    fun hitTest(
        item: TakeoffItem,
        p: TakeoffPoint,
        page: PageGeometry,
        tapRadiusPt: Double
    ): Boolean = when (item.tool) {
        TakeoffTool.AREA, TakeoffTool.DEDUCT ->
            pointInRing(p, item.verts) || item.extraRings.any { pointInRing(p, it) }

        TakeoffTool.LENGTH ->
            distanceToPolylinePt(p, item.verts, page) <= tapRadiusPt ||
                item.extraSegments.any { distanceToPolylinePt(p, it, page) <= tapRadiusPt }

        TakeoffTool.COUNT ->
            item.verts.any { marker ->
                hypot(
                    (p.x - marker.x) * page.widthPt,
                    (p.y - marker.y) * page.heightPt
                ) <= tapRadiusPt
            }
    }
}
