package com.corewall.qaqc.ui.manpower

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.data.db.AttendanceMarkEntity
import com.corewall.qaqc.data.db.AttendanceRosterEntity
import com.corewall.qaqc.ui.design.CwButton
import com.corewall.qaqc.ui.design.CwButtonStyle
import com.corewall.qaqc.ui.design.CwEmptyState
import com.corewall.qaqc.ui.design.CwIconButton
import com.corewall.qaqc.ui.design.CwText
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Radius
import com.corewall.qaqc.ui.design.Space
import java.util.Calendar

/**
 * شيت الحضور — أسماء في الصفوف، أيام الشهر في الأعمدة.
 *
 * ## التلات حالات
 *
 * - **حاضر** أخضر · **غايب** أحمر · **فاضي** أزرق فاتح شفّاف.
 *
 * الفاضي مش حالة تالتة للعامل — هو "لسه محدش راجع اليوم ده". الفرق ده هو
 * كل الفايدة: خانة زرقا معناها مراجعة ناقصة، وخانة حمرا معناها غياب
 * متسجّل. لو الاتنين شكلهم واحد، الشيت مابيقولش مين فات على مين.
 *
 * ## عمود الأسماء مثبّت
 *
 * الشهر تلاتين عمود، والاسم لازم يفضل باين وانت بتمرّر لآخر الشهر — غير
 * كده بتأشّر على صف ومش عارف هو مين. العمودين بيتشاركوا نفس حالة التمرير
 * الأفقي عشان الترويسة تفضل مظبوطة فوق الخانات.
 */
@Composable
fun AttendanceSheetView(vm: MainViewModel, fileId: Long, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val roster by remember(fileId) { vm.attendanceRoster(fileId) }
        .collectAsStateWithLifecycle(emptyList())
    val marks by remember(fileId) { vm.attendanceMarks(fileId) }
        .collectAsStateWithLifecycle(emptyList())

    val today = remember { Calendar.getInstance() }
    var year by remember { mutableIntStateOf(today.get(Calendar.YEAR)) }
    var month by remember { mutableIntStateOf(today.get(Calendar.MONTH)) }
    var addRow by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            vm.importRoster(fileId, uri) { message ->
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    val days = remember(year, month) { daysOf(year, month) }
    val marksByCell = remember(marks) { marks.associateBy { it.rosterId to it.day } }

    Column(modifier.fillMaxSize()) {
        MonthBar(
            year = year,
            month = month,
            onPrevious = {
                if (month == 0) { month = 11; year-- } else month--
            },
            onNext = {
                if (month == 11) { month = 0; year++ } else month++
            },
            onImport = {
                // الأنواع دي بس: نافذة النظام بتعرض الباقي رمادي بدل ما
                // المستخدم يختار ملف ونقوله بعدين إنه مش مدعوم.
                picker.launch(
                    arrayOf(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "text/csv",
                        "text/comma-separated-values",
                        "application/vnd.ms-excel",
                        "text/plain"
                    )
                )
            },
            onAddRow = { addRow = true }
        )

        if (roster.isEmpty()) {
            CwEmptyState(
                icon = Icons.Filled.UploadFile,
                title = "مفيش شيت لسه",
                detail = "ارفع شيت المقاول (xlsx أو csv) وهيتقرا منه عمود الأسماء، " +
                    "أو ضيف الأسماء واحد واحد. بعدها دوس على أي خانة عشان تقلبها " +
                    "حاضر أو غايب."
            )
        } else {
            Sheet(
                roster = roster,
                days = days,
                markAt = { rowId, day -> marksByCell[rowId to day]?.state },
                onToggle = { row, day, current ->
                    vm.setAttendanceMark(fileId, row.id, day, next(current))
                },
                onRemoveRow = { vm.deleteRosterRow(it.id) }
            )
        }
    }

    if (addRow) {
        AddRosterRowDialog(
            onConfirm = { name, code, trade ->
                vm.addRosterRow(fileId, name, code, trade, roster.size)
                addRow = false
            },
            onDismiss = { addRow = false }
        )
    }
}

/** فاضي ← حاضر ← غايب ← فاضي. الدورة بتخلّي التصحيح ضغطة مش قايمة. */
private fun next(current: String?): String? = when (current) {
    null -> AttendanceMarkEntity.PRESENT
    AttendanceMarkEntity.PRESENT -> AttendanceMarkEntity.ABSENT
    else -> null
}

@Composable
private fun MonthBar(
    year: Int,
    month: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onImport: () -> Unit,
    onAddRow: () -> Unit
) {
    val c = LocalCwColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.xs)
    ) {
        CwIconButton(Icons.AutoMirrored.Filled.ArrowForward, "الشهر اللي فات", onPrevious)
        Text(
            "${MONTHS[month]} $year",
            style = MaterialTheme.typography.titleSmall,
            color = c.textPrimary,
            modifier = Modifier.width(120.dp),
            maxLines = 1
        )
        CwIconButton(Icons.AutoMirrored.Filled.ArrowBack, "الشهر الجاي", onNext)
        Spacer(Modifier.weight(1f))
        CwIconButton(Icons.Filled.PersonAdd, "ضيف اسم", onAddRow)
        CwButton("ارفع شيت", onImport, style = CwButtonStyle.Secondary, icon = Icons.Filled.UploadFile)
    }
}

@Composable
private fun Sheet(
    roster: List<AttendanceRosterEntity>,
    days: List<Int>,
    markAt: (Long, Int) -> String?,
    onToggle: (AttendanceRosterEntity, Int, String?) -> Unit,
    onRemoveRow: (AttendanceRosterEntity) -> Unit
) {
    val c = LocalCwColors.current
    val horizontal = rememberScrollState()
    val vertical = rememberScrollState()

    Column(Modifier.fillMaxSize()) {
        // ترويسة الأيام — بتتمرّر مع الخانات بنفس الحالة.
        Row(Modifier.fillMaxWidth().background(c.surfaceAlt)) {
            HeaderCell("الاسم", NAME_WIDTH)
            Row(Modifier.horizontalScroll(horizontal)) {
                days.forEach { day -> HeaderCell("${day % 100}", CELL_SIZE) }
            }
        }

        Row(Modifier.fillMaxWidth().verticalScroll(vertical)) {
            Column {
                roster.forEach { row ->
                    NameCell(row = row, onLongPress = { onRemoveRow(row) })
                }
            }
            Column(Modifier.horizontalScroll(horizontal)) {
                roster.forEach { row ->
                    Row {
                        days.forEach { day ->
                            val state = markAt(row.id, day)
                            MarkCell(state) { onToggle(row, day, state) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderCell(text: String, width: androidx.compose.ui.unit.Dp) {
    val c = LocalCwColors.current
    Box(
        Modifier.width(width).height(CELL_SIZE),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = CwText.codeSmall, color = c.textSecondary, maxLines = 1)
    }
}

@Composable
private fun NameCell(row: AttendanceRosterEntity, onLongPress: () -> Unit) {
    val c = LocalCwColors.current
    Column(
        Modifier
            .width(NAME_WIDTH)
            .height(CELL_SIZE)
            .padding(horizontal = Space.xs),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            row.name,
            style = MaterialTheme.typography.labelLarge,
            color = c.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (row.code.isNotBlank() || row.trade.isNotBlank()) {
            Text(
                listOf(row.code, row.trade).filter { it.isNotBlank() }.joinToString(" · "),
                style = CwText.codeSmall,
                color = c.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MarkCell(state: String?, onClick: () -> Unit) {
    val c = LocalCwColors.current
    val fill = when (state) {
        AttendanceMarkEntity.PRESENT -> c.success.solid
        AttendanceMarkEntity.ABSENT -> c.danger.solid
        // الفاضي أزرق فاتح شفّاف: باين إنه خانة، وواضح إنه لسه ما اتملاش.
        else -> c.info.solid.copy(alpha = 0.12f)
    }
    val label = when (state) {
        AttendanceMarkEntity.PRESENT -> "P"
        AttendanceMarkEntity.ABSENT -> "A"
        else -> ""
    }
    Box(
        Modifier
            .size(CELL_SIZE)
            .padding(1.dp)
            .clip(Radius.shapeSm)
            .background(fill)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (label.isNotEmpty()) {
            Text(label, style = CwText.codeSmall, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

/** أيام الشهر كأرقام `yyyyMMdd`. */
private fun daysOf(year: Int, month: Int): List<Int> {
    val calendar = Calendar.getInstance().apply {
        clear(); set(Calendar.YEAR, year); set(Calendar.MONTH, month)
    }
    val count = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    return (1..count).map { day -> year * 10_000 + (month + 1) * 100 + day }
}

private val MONTHS = listOf(
    "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
    "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
)

/** الخانة مربّعة وبمساحة لمس معقولة — الشيت بيتأشّر عليه بالصباع في الموقع. */
private val CELL_SIZE = 44.dp
private val NAME_WIDTH = 132.dp

@Composable
private fun AddRosterRowDialog(
    onConfirm: (String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var trade by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ضيف اسم للشيت") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                com.corewall.qaqc.ui.design.CwField(
                    value = name, onValueChange = { name = it }, label = "الاسم"
                )
                com.corewall.qaqc.ui.design.CwField(
                    value = code, onValueChange = { code = it }, label = "الكود"
                )
                com.corewall.qaqc.ui.design.CwField(
                    value = trade, onValueChange = { trade = it }, label = "التخصص"
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name, code, trade) }
            ) { Text("ضيف") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
