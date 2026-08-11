package com.corewall.qaqc.pdfengine

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.conflate
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.corewall.qaqc.stylus.StylusInkController
import com.corewall.qaqc.stylus.stylusInk
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * سطح الرسم — المربّعات والإيماءات والاندفاع.
 *
 * **طبقتان دايماً:**
 *
 * 1. **معاينة** — الصفحة كلها في مربّع أو أربعة عند تكبير منخفض، ممدودة
 *    على مقاس العرض الحالي. رخيصة، وبتفضل في الكاش.
 * 2. **حادّة** — مربّعات المستوى الحالي، بتترسم فوق المعاينة.
 *
 * ليه الطبقتين: لما تكبّر، المربّعات الحادّة بتاخد جزء من ثانية. من غير
 * طبقة المعاينة تحتها، المستخدم بيبصّ على **مربّعات بيضا فاضية** بتتملي
 * واحد ورا التاني — وده بيحسّ إنه بطء حتى لو الرندر أسرع من الأول. مع
 * المعاينة، هو بيشوف الرسمة فوراً (طرية شوية) وبتنشف قدام عينه.
 *
 * ودي كمان السبب إن المعاينة **مابتتخلصش** من الكاش زي المربّعات العادية.
 */
@Composable
fun PdfCanvas(
    state: PdfViewerState,
    engine: TileEngine,
    session: PdfDocumentSession,
    modifier: Modifier = Modifier,
    /**
     * أداة نشطة تمنع التمرير وتاخد اللمس للرسم بدل التنقّل.
     *
     * في وضع "الرسم بالقلم بس" ده بيبقى `false` دايماً: القلم بياخد الحبر
     * من طبقة منفصلة، والصباع بيفضل بيتنقّل عادي — يعني تقدر تكبّر وتمرّر
     * وأنت ماسك القلم من غير ما تبدّل أداة.
     */
    drawingActive: Boolean = false,
    onDrawStart: (Offset) -> Unit = {},
    onDrawMove: (Offset) -> Unit = {},
    onDrawEnd: () -> Unit = {},
    /** الخط اتلغى (كف أو إلغاء من النظام) — يترمي، مايتحفظش. */
    onDrawCancel: () -> Unit = {},
    /**
     * موجّه لمس القلم. `null` = الوضع مقفول، وساعتها الشاشة بتشتغل
     * بالسلوك القديم بالظبط.
     */
    stylus: StylusInkController? = null,
    onTap: (Offset) -> Unit = {},
    /** ضغطة مطوّلة — بيبدأ بيها تحديد النص. */
    onLongPress: (Offset) -> Unit = {},
    /** بيترسم فوق الصفحات — التعليقات والقياسات. */
    overlay: DrawScope.(PdfViewerState) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val flingJob = remember { arrayOfNulls<Job>(1) }
    val measured by session.measuredCount.collectAsState()

    /**
     * إعادة حساب المربّعات المطلوبة مع كل حركة.
     *
     * `revision` بيتغيّر مع كل إطار وقت التمرير أو التكبير. لو ربطناه
     * كمفتاح لـ`LaunchedEffect`، كل إطار كان بيلغي كوروتين ويطلق واحد
     * جديد — ستّين إلغاء وإطلاق في الثانية وسط الإيماءة بالظبط، يعني
     * ضغط تخصيص وGC في أسوأ لحظة ممكنة.
     *
     * `snapshotFlow` بيخلّي كوروتين **واحد** يعيش طول عمر الشاشة ويستقبل
     * التغيّرات، و`conflate` بيرمي الإطارات اللي فاتت لو المزامنة اتأخّرت
     * — واللي إحنا عايزينه فعلاً هو آخر وضع للشاشة مش تاريخها.
     *
     * `measured` لسه مفتاح لأنه بيتغيّر نادراً (لما صفحة تتقاس) ووقتها
     * لازم نعيد الحساب من الأول بمقاسات حقيقية.
     */
    LaunchedEffect(engine, session, measured) {
        snapshotFlow { state.revision to state.mode }
            .conflate()
            .collect {
                // المعاينة الأول في الطابور: صفحة طرية أحسن من صفحة بيضا
                val previews = previewTilesFor(state, session)
                val required = state.requiredTiles(session)
                val all = ArrayList<TileKey>(previews.size + required.size)
                all.addAll(previews)
                all.addAll(required)
                engine.sync(all)
            }
    }

    // بعد ما التكبير يستقر، نرمي المستويات البعيدة
    LaunchedEffect(state.interacting) {
        if (!state.interacting) {
            kotlinx.coroutines.delay(400)
            engine.dropDistantLevels(ZoomLadder.levelFor(state.zoom))
        }
    }

    Canvas(
        modifier
            .fillMaxSize()
            .onSizeChanged { state.updateViewport(it) }
            // طبقة الحبر **قبل** طبقة الإيماءات في السلسلة: هي اللي بتشوف
            // الحدث الأول وبتقرّر تبلعه (قلم) ولا تسيبه يعدّي (صباع).
            .then(if (stylus != null) Modifier.stylusInk(stylus) else Modifier)
            .pointerInput(drawingActive) {
                if (drawingActive) {
                    detectDragGestures(
                        onDragStart = { onDrawStart(it) },
                        onDrag = { change, _ -> onDrawMove(change.position); change.consume() },
                        onDragEnd = { onDrawEnd() },
                        // الإلغاء مش إنهاء: الخط اللي النظام لغاه مايتحفظش.
                        onDragCancel = { onDrawCancel() }
                    )
                } else {
                    detectPdfGestures(
                        onStart = {
                            flingJob[0]?.cancel()
                            state.interacting = true
                        },
                        onGesture = { centroid, pan, zoomChange ->
                            if (zoomChange != 1f) state.zoomBy(zoomChange, centroid)
                            if (pan != Offset.Zero) state.panBy(pan.x, pan.y)
                        },
                        onEnd = { velocity ->
                            state.interacting = false
                            flingJob[0] = scope.launch { fling(state, velocity.x, velocity.y) }
                        }
                    )
                }
            }
            .pointerInput(drawingActive) {
                if (!drawingActive) {
                    detectTapGestures(
                        onTap = { onTap(it) },
                        onLongPress = { onLongPress(it) },
                        onDoubleTap = { point -> state.cycleZoom(point) }
                    )
                }
            }
    ) {
        drawPages(state, engine, session)
        overlay(state)
    }
}

/**
 * الاندفاع بعد رفع الإصبع.
 *
 * محورين مستقلين بنفس معامل الاحتكاك. الفصل مقصود: على رسمة عريضة، الاندفاع
 * القُطري بيوقف في المحورين مع بعض ويحسّ إنه ملزوق؛ لما كل محور يهدى لوحده
 * الحركة بتحسّ طبيعية.
 */
private suspend fun fling(state: PdfViewerState, vx: Float, vy: Float) = coroutineScope {
    if (abs(vx) < MIN_FLING && abs(vy) < MIN_FLING) return@coroutineScope
    val decay = exponentialDecay<Float>(frictionMultiplier = 1.1f)

    // المحورين **مع بعض** مش واحد ورا التاني. لو تسلسلوا، الاندفاع القطري
    // بيتحرك أفقي لحد ما يقف وبعدين يبدأ ينزل — حركة غريبة وواضحة.
    if (abs(vx) >= MIN_FLING) launch {
        var last = 0f
        AnimationState(initialValue = 0f, initialVelocity = vx).animateDecay(decay) {
            state.panBy(value - last, 0f)
            last = value
        }
    }
    if (abs(vy) >= MIN_FLING) launch {
        var last = 0f
        AnimationState(initialValue = 0f, initialVelocity = vy).animateDecay(decay) {
            state.panBy(0f, value - last)
            last = value
        }
    }
}

private const val MIN_FLING = 80f

/**
 * نقرتين = دورة تكبير: ملء العرض ← ١٠٠٪ ← ٢٠٠٪ ← ملء العرض.
 *
 * دورة مش تبديل بين حالتين: مهندس بيقرا رسمة محتاج "قرّبني تاني" من غير ما
 * يقرص، والدورة بتدّيه ده بنقرة.
 */
private fun PdfViewerState.cycleZoom(focus: Offset) {
    val fitW = if (layout.contentWidth > 0f) viewport.width / layout.contentWidth else 1f
    val target = when {
        zoom < fitW * 1.05f -> 1f
        zoom < 1.9f -> 2f
        else -> fitW
    }
    zoomBy(target / zoom, focus)
}

/**
 * مستوى المعاينة لصفحة: أكبر مستوى بيخلّي الصفحة كلها ≤ ~١٠٢٤ بكسل.
 * كده صفحة A4 بتبقى مربّع واحد، وA0 بتبقى أربعة — رخيصة في الحالتين.
 */
private fun previewLevelFor(size: SizePt): Int {
    val longest = maxOf(size.width, size.height)
    for (level in ZoomLadder.MAX_LEVEL downTo ZoomLadder.MIN_LEVEL) {
        if (longest * ZoomLadder.scaleOf(level) <= PREVIEW_MAX_PX) return level
    }
    return ZoomLadder.MIN_LEVEL
}

private const val PREVIEW_MAX_PX = 1024f

private fun previewTilesFor(state: PdfViewerState, session: PdfDocumentSession): List<TileKey> {
    val rect = state.visibleDocRect()
    if (rect.width <= 0f) return emptyList()
    val out = ArrayList<TileKey>(8)
    for (slot in state.layout.visible(rect.left, rect.top, rect.right, rect.bottom)) {
        val size = session.knownSize(slot.index) ?: continue
        val level = previewLevelFor(size)
        val grid = TileGrid(slot.index, level, size)
        for (r in 0 until grid.rows) for (c in 0 until grid.cols) {
            out += TileKey.of(slot.index, level, r, c)
        }
    }
    return out
}

/** بيرسم كل الصفحات المرئية: خلفية ← معاينة ← مربّعات حادّة. */
private fun DrawScope.drawPages(
    state: PdfViewerState,
    engine: TileEngine,
    session: PdfDocumentSession
) {
    val rect = state.visibleDocRect()
    if (rect.width <= 0f) return
    val sharpLevel = ZoomLadder.levelFor(state.zoom)

    for (slot in state.layout.visible(rect.left, rect.top, rect.right, rect.bottom)) {
        val pageLeft = state.docToScreenX(slot.left)
        val pageTop = state.docToScreenY(slot.top)
        val pageW = slot.size.width * state.zoom
        val pageH = slot.size.height * state.zoom

        // ورقة بيضا تحت كل حاجة — بتدّي إحساس إن الصفحة "موجودة" فوراً
        drawRect(
            color = Color.White,
            topLeft = Offset(pageLeft, pageTop),
            size = Size(pageW, pageH)
        )

        val size = session.knownSize(slot.index)
        if (size == null) {
            drawPagePlaceholder(pageLeft, pageTop, pageW, pageH)
            continue
        }

        // ١) المعاينة
        val previewLevel = previewLevelFor(size)
        drawTileLayer(
            engine, slot.index, previewLevel, size,
            pageLeft, pageTop, state.zoom, FilterQuality.Low
        )

        // ٢) الحادّة فوقها (لو مستوى مختلف)
        if (sharpLevel != previewLevel) {
            drawTileLayer(
                engine, slot.index, sharpLevel, size,
                pageLeft, pageTop, state.zoom, FilterQuality.None
            )
        }

        // حدّ رفيع يفصل الصفحة عن الخلفية
        drawRect(
            color = Color(0x22000000),
            topLeft = Offset(pageLeft, pageTop),
            size = Size(pageW, pageH),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f)
        )
    }
}

/**
 * بيرسم مربّعات مستوى واحد.
 *
 * التقريب هنا مش تفصيلة: بنقرّب **الحافتين** (البداية والنهاية) مش البداية
 * والعرض. لو قرّبنا العرض، مربّعين متجاورين ممكن يبقى بينهم بكسل فاضي —
 * وعلى رسمة فيها خطوط رفيعة، الشبكة الرمادية دي بتبان بشكل فاضح.
 *
 * **الحلقة بتلفّ على المربّعات المرئية بس.** ده مش تجميل: صفحة A1 عند ٦٤×
 * شبكتها ٥٣ عمود × ٧٥ صف = قرابة أربعة آلاف مربّع. الحلقة القديمة كانت
 * بتلفّ عليهم كلهم **في كل إطار ولكل طبقة**، وكل لفّة فيها بحث في
 * `SnapshotStateMap` — يعني تمانية آلاف قراءة مراقَبة في الإطار الواحد
 * وقت التكبير. اللي بيتقاطع مع الشاشة فعلاً عشرات قليلة.
 *
 * القصّ ده **مابيغيّرش اللي بيترسم**: الفحص الدقيق للحدود لسه مكانه جوّه،
 * والحدود المحسوبة هنا أوسع منه بمربّع. اللي بيتغيّر هو عدد اللفّات بس.
 */
private fun DrawScope.drawTileLayer(
    engine: TileEngine,
    page: Int,
    level: Int,
    pageSize: SizePt,
    pageLeft: Float,
    pageTop: Float,
    zoom: Float,
    quality: FilterQuality
) {
    val grid = TileGrid(page, level, pageSize)
    // بكسل المربّع → بكسل الشاشة
    val factor = zoom / grid.scale

    // خطوة المربّع على الشاشة. موضع المربّع دايماً `index × step` بالظبط
    // (العرض هو اللي بيقلّ على الحافة، مش الموضع) — فالحساب ده مضبوط.
    val step = TILE_SIZE * factor
    if (step <= 0f) return

    val c0 = floor(-pageLeft / step).toInt().coerceIn(0, grid.cols - 1)
    val c1 = floor((size.width - pageLeft) / step).toInt().coerceIn(0, grid.cols - 1)
    val r0 = floor(-pageTop / step).toInt().coerceIn(0, grid.rows - 1)
    val r1 = floor((size.height - pageTop) / step).toInt().coerceIn(0, grid.rows - 1)

    for (row in r0..r1) {
        for (col in c0..c1) {
            val image = engine.tiles[TileKey.of(page, level, row, col).packed] ?: continue

            val x0 = pageLeft + col * TILE_SIZE * factor
            val y0 = pageTop + row * TILE_SIZE * factor
            val x1 = x0 + grid.tileWidth(col) * factor
            val y1 = y0 + grid.tileHeight(row) * factor

            // خارج الشاشة؟ متتعبش الرسّام
            if (x1 < 0 || y1 < 0 || x0 > this.size.width || y0 > this.size.height) continue

            val ix0 = x0.roundToInt()
            val iy0 = y0.roundToInt()
            val iw = x1.roundToInt() - ix0
            val ih = y1.roundToInt() - iy0
            if (iw <= 0 || ih <= 0) continue

            drawImage(
                image = image,
                dstOffset = IntOffset(ix0, iy0),
                dstSize = IntSize(iw, ih),
                filterQuality = quality
            )
        }
    }
}

/** صفحة لسه ما اتقاستش — خطوط خفيفة بدل فراغ. */
private fun DrawScope.drawPagePlaceholder(x: Float, y: Float, w: Float, h: Float) {
    val lineColor = Color(0x11000000)
    val step = (h / 14f).coerceAtLeast(8f)
    var cy = y + step
    while (cy < y + h - step) {
        drawRect(
            color = lineColor,
            topLeft = Offset(x + w * 0.12f, cy),
            size = Size(w * 0.76f, 2f)
        )
        cy += step
    }
}

/** بيحوّل نقطة على الشاشة لإحداثي منسّب (٠..١) جوّه صفحة. */
fun PdfViewerState.pageHit(screen: Offset): PageHit? {
    val doc = screenToDoc(screen)
    val slot = layout.slots.firstOrNull {
        doc.x >= it.left && doc.x <= it.right && doc.y >= it.top && doc.y <= it.bottom
    } ?: return null
    return PageHit(
        page = slot.index,
        nx = ((doc.x - slot.left) / slot.size.width).coerceIn(0f, 1f),
        ny = ((doc.y - slot.top) / slot.size.height).coerceIn(0f, 1f)
    )
}

data class PageHit(val page: Int, val nx: Float, val ny: Float)

/** العكس: إحداثي منسّب جوّه صفحة → نقطة على الشاشة. */
fun PdfViewerState.pagePointToScreen(page: Int, nx: Float, ny: Float): Offset? {
    val slot = layout.slotAt(page) ?: return null
    return Offset(
        docToScreenX(slot.left + nx * slot.size.width),
        docToScreenY(slot.top + ny * slot.size.height)
    )
}

/** مستطيل الصفحة على الشاشة — للقصّ وللمصغّرات. */
fun PdfViewerState.pageScreenRect(page: Int): Rect? {
    val slot = layout.slotAt(page) ?: return null
    return Rect(
        docToScreenX(slot.left),
        docToScreenY(slot.top),
        docToScreenX(slot.right),
        docToScreenY(slot.bottom)
    )
}
