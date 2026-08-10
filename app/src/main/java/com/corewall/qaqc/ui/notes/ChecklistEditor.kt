package com.corewall.qaqc.ui.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import com.corewall.qaqc.domain.NotesLogic
import com.corewall.qaqc.ui.design.CwIconButton
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Sizes
import com.corewall.qaqc.ui.design.Space

/**
 * محرّر قايمة المهام.
 *
 * كل بند سطر: مربّع + نص + شيلة. البنود متخزّنة جوّه نص الملاحظة كسطور
 * `- [ ]` (شوف [NotesLogic])، فالمحرّر ده مش بيمسك حالة خاصة بيه — بيقرا
 * النص، وكل تعديل بيرجّع نص جديد كامل. مصدر واحد للحقيقة، والحفظ التلقائي
 * الموجود أصلاً بيشتغل عليه من غير أي ربط زيادة.
 *
 * البنود المخلّصة **ما بتتحرّكش** وأنت بتعدّل. تحريكها لتحت لحظة ما تعلّم
 * عليها بيخلّي السطر اللي تحت إيدك يقفز — والمعاينة في الكارت أصلاً
 * بتأخّرها، فالمعلومة مش ضايعة.
 */
@Composable
fun ChecklistEditor(
    body: String,
    onBodyChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalCwColors.current
    val items = remember(body) { NotesLogic.checklist(body) }

    // السطر اللي المؤشّر المفروض يروح له بعد إضافة بند جديد.
    var focusLine by remember { mutableIntStateOf(-1) }
    val focus = remember { FocusRequester() }

    fun addItem() {
        val (next, line) = NotesLogic.addItem(body)
        focusLine = line
        onBodyChange(next)
    }

    LaunchedEffect(focusLine, items.size) {
        if (focusLine >= 0 && items.any { it.line == focusLine }) {
            runCatching { focus.requestFocus() }
        }
    }

    Column(modifier.fillMaxWidth()) {
        items.forEach { item ->
            key(item.line) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = Sizes.touch),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CwIconButton(
                        if (item.done) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                        if (item.done) "شيل العلامة" else "علّم كمنتهي",
                        { onBodyChange(NotesLogic.toggleItem(body, item.line)) },
                        tint = if (item.done) c.accent else c.textSecondary
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .padding(vertical = Space.sm),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (item.text.isEmpty()) {
                            Text(
                                "بند",
                                style = MaterialTheme.typography.bodyLarge,
                                color = c.textTertiary
                            )
                        }
                        BasicTextField(
                            value = item.text,
                            onValueChange = {
                                // سطر واحد لكل بند — لصق نص فيه أسطر بيبوّظ
                                // بنية القايمة، فبنطبّقها هنا.
                                onBodyChange(
                                    NotesLogic.setItemText(
                                        body, item.line, it.replace('\n', ' ')
                                    )
                                )
                            },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = if (item.done) c.textTertiary else c.textPrimary,
                                textDecoration = if (item.done) TextDecoration.LineThrough else null
                            ),
                            cursorBrush = SolidColor(c.accent),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { addItem() }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (item.line == focusLine) Modifier.focusRequester(focus)
                                    else Modifier
                                )
                        )
                    }
                    CwIconButton(
                        Icons.Filled.Close,
                        "شيل البند",
                        {
                            if (item.line == focusLine) focusLine = -1
                            onBodyChange(NotesLogic.removeItem(body, item.line))
                        },
                        tint = c.textTertiary
                    )
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Sizes.touch)
                .clickable { addItem() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.xs)
        ) {
            CwIconButton(Icons.Filled.Add, "ضيف بند", { addItem() }, tint = c.accent)
            Text(
                "ضيف بند",
                style = MaterialTheme.typography.bodyLarge,
                color = c.textSecondary
            )
        }

        if (items.isNotEmpty()) {
            val done = items.count { it.done }
            Text(
                "$done من ${items.size} خلصوا",
                style = MaterialTheme.typography.labelSmall,
                color = c.textTertiary,
                modifier = Modifier.padding(top = Space.sm, start = Space.sm)
            )
        }
    }
}
