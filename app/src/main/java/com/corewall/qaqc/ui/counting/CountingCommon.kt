package com.corewall.qaqc.ui.counting

import com.corewall.qaqc.data.db.BarCountEntity

/** الأقطار القياسية المتاحة في مؤشر الاختيار. */
val STANDARD_DIAMETERS = listOf(8, 10, 12, 14, 16, 18, 20, 22, 25, 28, 32, 40)

/** "22Ø12" — وأكتر من صف بيتوصلوا بـ"+": "22Ø12+4Ø16". */
fun formatEntries(entries: List<BarCountEntity>): String =
    entries
        .filter { it.count > 0 }
        .sortedBy { it.diameter }
        .joinToString("+") { "${it.count}Ø${it.diameter}" }

/** إجمالي العدد لكل قطر. */
fun totalsByDiameter(entries: List<BarCountEntity>): Map<Int, Int> =
    entries
        .filter { it.count > 0 }
        .groupBy { it.diameter }
        .mapValues { (_, rows) -> rows.sumOf { it.count } }
        .toSortedMap()

fun siteOf(entries: List<BarCountEntity>) =
    entries.filter { it.source == BarCountEntity.SOURCE_SITE }

fun drawingOf(entries: List<BarCountEntity>) =
    entries.filter { it.source == BarCountEntity.SOURCE_DRAWING }
