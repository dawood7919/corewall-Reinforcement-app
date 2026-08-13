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
    /** أقل عدد نقاط صالح لإنهاء أداة قياس؛ يمنع واجهة المحرر من مسح قياس ناقص. */
    fun canCommitMeasurement(tool: TakeoffTool, pointCount: Int): Boolean = when (tool) {
        TakeoffTool.COUNT, TakeoffTool.COLUMN -> pointCount >= 1
        TakeoffTool.LENGTH -> pointCount >= 2
        TakeoffTool.DIMENSION -> pointCount == 3
        TakeoffTool.AREA, TakeoffTool.DEDUCT, TakeoffTool.VOLUME -> pointCount >= 3
    }


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

        // المساحة زي AREA بالظبط، بس مضروبة في سمك المستخدم — والسمك
        // مُدخَل مباشرة بالمتر، مش نقط PDF، فمالوش علاقة بالمعايرة.
        TakeoffTool.VOLUME ->
            (area(item.verts, page) + item.extraRings.sumOf { area(it, page) }) * (item.thickness ?: 0.0)

        // زي العدّ، بس كل علامة بتساوي حجم عمود واحد مش واحد صحيح.
        // العدد نفسه مالوش علاقة بالمعايرة زي COUNT بالظبط.
        TakeoffTool.COLUMN ->
            item.verts.size.toDouble() *
                (item.colLength ?: 0.0) * (item.colWidth ?: 0.0) * (item.colHeight ?: 0.0)

        // أول نقطتين بس — التالتة إزاحة خط القياس بصريًا، مالهاش دخل
        // بالمسافة الحقيقية.
        TakeoffTool.DIMENSION ->
            if (item.verts.size >= 2) length(item.verts.take(2), page) else 0.0
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
        val holes = all.asSequence()
            .filter { it.tool == TakeoffTool.DEDUCT && it.parentId == item.id && it.visible }
        return when (item.tool) {
            TakeoffTool.AREA -> gross - holes.sumOf { grossQuantity(it, page) }

            // الخصم على حجم مش خصم مساحة مباشرة — الفتحة نفسها مضلّع
            // مسطّح (زي أي خصم تاني)، وسمكها هو سمك الأب اللي هي فيه،
            // مش سمك خاص بيها (الخصومات معندهاش سمك أصلاً).
            TakeoffTool.VOLUME -> {
                val thickness = item.thickness ?: 0.0
                gross - holes.sumOf { area(it.verts, page) + it.extraRings.sumOf { r -> area(r, page) } } * thickness
            }

            else -> gross
        }
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
        // العدّ والعمود مبنيين على "عدد علامات" — الهالك مالوش معنى على
        // بند مقاسه بالحبّة، حتى لو كميته النهائية طلعت متر مكعّب.
        if (item.tool == TakeoffTool.COUNT || item.tool == TakeoffTool.COLUMN) return net
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
     * أقرب رأس في **أي جزء** من البند. النسخة القديمة من تحرير الرؤوس كانت
     * تفحص الشكل الأساسي فقط؛ لذلك كانت الحلقات والقطاعات المضافة تظهر
     * كمحتوى قابل للتحديد لكن غير قابل للتحرير. هذا الهدف يحتفظ بمكان الرأس
     * بدقة حتى يُعاد حفظ الجزء الصحيح فقط.
     */
    fun nearestVertexTarget(
        item: TakeoffItem,
        p: TakeoffPoint,
        page: PageGeometry,
        tapRadiusPt: Double
    ): TakeoffVertexTarget? {
        var best: TakeoffVertexTarget? = null
        var bestDist = Double.MAX_VALUE
        editableParts(item).forEach { part ->
            part.points.forEachIndexed { index, vertex ->
                val d = hypot((p.x - vertex.x) * page.widthPt, (p.y - vertex.y) * page.heightPt)
                if (d < bestDist) {
                    bestDist = d
                    best = TakeoffVertexTarget(part.kind, part.index, index)
                }
            }
        }
        return best?.takeIf { bestDist <= tapRadiusPt }
    }

    /**
     * أقرب ضلع قابل لإضافة رأس في أي جزء قابل للقياس. النتيجة تحمل موضع
     * الإدراج داخل الحلقة أو القطعة المناسبة، لا داخل الشكل الأساسي دائماً.
     */
    fun nearestEdgeInsertTarget(
        item: TakeoffItem,
        p: TakeoffPoint,
        page: PageGeometry,
        radiusPt: Double
    ): TakeoffVertexTarget? {
        if (!item.tool.isAreaLike && item.tool != TakeoffTool.LENGTH) return null
        val closed = item.tool.isAreaLike
        var best: TakeoffVertexTarget? = null
        var bestDist = Double.MAX_VALUE
        editableParts(item).forEach { part ->
            val insertAt = nearestEdgeInsertIndex(part.points, p, page, closed, radiusPt) ?: return@forEach
            val px = p.x * page.widthPt
            val py = p.y * page.heightPt
            val previousIndex = if (insertAt == 0) part.points.lastIndex else insertAt - 1
            val nextIndex = insertAt % part.points.size
            val a = part.points[previousIndex]
            val b = part.points[nextIndex]
            val distance = distanceToSegment(
                px, py, a.x * page.widthPt, a.y * page.heightPt,
                b.x * page.widthPt, b.y * page.heightPt
            )
            if (distance < bestDist) {
                bestDist = distance
                best = TakeoffVertexTarget(part.kind, part.index, insertAt)
            }
        }
        return best
    }

    /** الرؤوس التابعة لهدف تحرير معين؛ القائمة الفارغة تعني هدفاً غير صالح. */
    fun verticesFor(item: TakeoffItem, target: TakeoffVertexTarget): List<TakeoffPoint> = when (target.part) {
        TakeoffGeometryPart.PRIMARY -> item.verts
        TakeoffGeometryPart.EXTRA_RING -> item.extraRings.getOrNull(target.partIndex).orEmpty()
        TakeoffGeometryPart.EXTRA_SEGMENT -> item.extraSegments.getOrNull(target.partIndex).orEmpty()
    }

    /** يبدّل رؤوس جزء واحد فقط، ويحافظ على باقي هندسة البند دون تغيير. */
    fun withVertices(
        item: TakeoffItem,
        target: TakeoffVertexTarget,
        vertices: List<TakeoffPoint>
    ): TakeoffItem = when (target.part) {
        TakeoffGeometryPart.PRIMARY -> item.copy(verts = vertices)
        TakeoffGeometryPart.EXTRA_RING -> {
            if (target.partIndex !in item.extraRings.indices) item
            else item.copy(extraRings = item.extraRings.mapIndexed { index, ring ->
                if (index == target.partIndex) vertices else ring
            })
        }
        TakeoffGeometryPart.EXTRA_SEGMENT -> {
            if (target.partIndex !in item.extraSegments.indices) item
            else item.copy(extraSegments = item.extraSegments.mapIndexed { index, segment ->
                if (index == target.partIndex) vertices else segment
            })
        }
    }

    /**
     * فهرس الإدراج لرأس جديد على أقرب ضلع للنقطة — الرأس الجديد بيتحط
     * **قبل** الفهرس المرجوع. `closed=true` بتضيف ضلع الإغلاق (آخر رأس
     * لأول رأس) زي أي مضلّع؛ `false` (الطول) بتوقف عند آخر رأس.
     *
     * دي اللي بتعمل "إضافة رأس" في تعديل الرؤوس: سحب من نقطة على ضلع
     * (مش على رأس موجود) بيدرج رأس جديد هناك بدل ما محصلش حاجة.
     */
    fun nearestEdgeInsertIndex(
        verts: List<TakeoffPoint>,
        p: TakeoffPoint,
        page: PageGeometry,
        closed: Boolean,
        radiusPt: Double
    ): Int? {
        if (verts.size < 2) return null
        val px = p.x * page.widthPt
        val py = p.y * page.heightPt
        var bestIdx = -1
        var bestDist = Double.MAX_VALUE
        val lastEdge = if (closed) verts.size - 1 else verts.size - 2
        for (i in 0..lastEdge) {
            val a = verts[i]
            val b = verts[(i + 1) % verts.size]
            val d = distanceToSegment(
                px, py, a.x * page.widthPt, a.y * page.heightPt, b.x * page.widthPt, b.y * page.heightPt
            )
            if (d < bestDist) { bestDist = d; bestIdx = i + 1 }
        }
        return bestIdx.takeIf { bestDist <= radiusPt }
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

    /**
     * البند بيلمس مستطيل تحديد؟ — نافذة عبور بأسلوب AutoCAD: يكفي رأس
     * واحد جوّه المستطيل، أو ضلع بيعدّي من جوّاه حتى لو كل رؤوسه برّه
     * (شكل كبير عدّى فوق مستطيل صغير). أوسع بكتير من [fullyInside] —
     * ده اللي بيخلّي التحديد بمستطيل مفيد فعلاً على رسمة مزدحمة.
     *
     * الإحداثيات منسّبة على الاتنين زي [fullyInside] بالظبط.
     */
    fun crossesBox(item: TakeoffItem, min: TakeoffPoint, max: TakeoffPoint): Boolean {
        val rings = buildList {
            if (item.verts.isNotEmpty()) add(item.verts)
            addAll(item.extraRings)
            addAll(item.extraSegments)
        }
        if (rings.isEmpty()) return false
        fun inBox(p: TakeoffPoint) = p.x in min.x..max.x && p.y in min.y..max.y
        val closed = item.tool.isAreaLike
        for (ring in rings) {
            if (ring.any(::inBox)) return true
            val edgeCount = if (closed) ring.size else ring.size - 1
            for (i in 0 until edgeCount.coerceAtLeast(0)) {
                if (segmentCrossesBox(ring[i], ring[(i + 1) % ring.size], min, max)) return true
            }
        }
        return false
    }

    private fun segmentCrossesBox(
        a: TakeoffPoint, b: TakeoffPoint, min: TakeoffPoint, max: TakeoffPoint
    ): Boolean {
        val corners = listOf(
            TakeoffPoint(min.x, min.y), TakeoffPoint(max.x, min.y),
            TakeoffPoint(max.x, max.y), TakeoffPoint(min.x, max.y)
        )
        for (i in corners.indices) {
            if (segmentsIntersect(a, b, corners[i], corners[(i + 1) % corners.size])) return true
        }
        return false
    }

    /** تقاطع خطّين — بالاتجاه (orientation)، مش بالمعادلة الخطية. */
    private fun segmentsIntersect(
        p1: TakeoffPoint, p2: TakeoffPoint, p3: TakeoffPoint, p4: TakeoffPoint
    ): Boolean {
        fun cross(o: TakeoffPoint, a: TakeoffPoint, b: TakeoffPoint) =
            (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
        val d1 = cross(p3, p4, p1)
        val d2 = cross(p3, p4, p2)
        val d3 = cross(p1, p2, p3)
        val d4 = cross(p1, p2, p4)
        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) &&
            ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))
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

    private data class EditablePart(
        val kind: TakeoffGeometryPart,
        val index: Int,
        val points: List<TakeoffPoint>
    )

    private fun editableParts(item: TakeoffItem): List<EditablePart> = buildList {
        if (item.verts.isNotEmpty()) add(EditablePart(TakeoffGeometryPart.PRIMARY, 0, item.verts))
        item.extraRings.forEachIndexed { index, ring ->
            if (ring.isNotEmpty()) add(EditablePart(TakeoffGeometryPart.EXTRA_RING, index, ring))
        }
        item.extraSegments.forEachIndexed { index, segment ->
            if (segment.isNotEmpty()) add(EditablePart(TakeoffGeometryPart.EXTRA_SEGMENT, index, segment))
        }
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
        TakeoffTool.AREA, TakeoffTool.DEDUCT, TakeoffTool.VOLUME -> {
            fun hitsRing(ring: List<TakeoffPoint>): Boolean = ring.size >= 3 && (
                pointInRing(p, ring) ||
                    distanceToPolylinePt(p, ring + ring.first(), page) <= tapRadiusPt
                )
            hitsRing(item.verts) || item.extraRings.any(::hitsRing)
        }

        TakeoffTool.LENGTH ->
            distanceToPolylinePt(p, item.verts, page) <= tapRadiusPt ||
                item.extraSegments.any { distanceToPolylinePt(p, it, page) <= tapRadiusPt }

        TakeoffTool.COUNT, TakeoffTool.COLUMN ->
            item.verts.any { marker ->
                hypot(
                    (p.x - marker.x) * page.widthPt,
                    (p.y - marker.y) * page.heightPt
                ) <= tapRadiusPt
            }

        TakeoffTool.DIMENSION ->
            item.verts.size >= 2 && distanceToPolylinePt(p, item.verts.take(2), page) <= tapRadiusPt
    }
}
