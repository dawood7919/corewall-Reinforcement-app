package com.corewall.qaqc.ai.agent

import com.corewall.qaqc.data.db.BarCountEntity
import com.corewall.qaqc.data.db.CommentEntity
import com.corewall.qaqc.data.db.DailyAttendanceEntity
import com.corewall.qaqc.data.db.NoteEntity
import com.corewall.qaqc.data.db.SitePhotoEntity
import com.corewall.qaqc.data.db.TaskEntity
import com.corewall.qaqc.data.model.PlanData
import com.corewall.qaqc.data.model.ScheduleData
import com.corewall.qaqc.domain.ScheduleLogic

/**
 * الشباك اللي الوكيل بيبصّ منه على التطبيق.
 *
 * كل حاجة هنا **قراءة لحظية** من حالة التطبيق الحيّة، فالوكيل بيشوف
 * نفس اللي المستخدم شايفه — مش نسخة قديمة. الإجراءات المحدودة
 * (تنقّل/تغيير دور/فتح ملف) موجودة هنا كمان عشان تفضل في مكان واحد
 * ينفع تتراجع.
 *
 * [MainViewModel] بينفّذ الواجهة دي؛ طبقة الـai مابتعرفش عنه حاجة.
 */
interface AgentHost {

    // ---------------------------------------------- حالة حيّة
    val levels: List<String>
    val currentLevel: String
    val schedule: ScheduleData
    val logic: ScheduleLogic
    val planData: PlanData

    /** elementId → mark. العناصر اللي متسمّتش مش بتظهر هنا. */
    val names: Map<String, String>

    /** (elementId, level) → اسم حالة الفحص. */
    val inspections: Map<Pair<String, String>, String>

    val comments: List<CommentEntity>
    val barCounts: List<BarCountEntity>
    val tasks: List<TaskEntity>
    val notes: List<NoteEntity>
    val sitePhotos: List<SitePhotoEntity>
    val dailyAttendance: List<DailyAttendanceEntity>

    /** أسماء ملفات الحضور بالـid — للسياق. */
    fun attendanceFileLabels(): Map<Long, String>

    // ---------------------------------------------- إجراءات آمنة
    fun setLevel(level: String): Boolean
    fun openScreen(screen: String): Boolean
    fun openFile(path: String): Boolean

    // ---------------------------------------------- إجراءات محتاجة موافقة
    suspend fun addTask(title: String, level: String): Boolean
    suspend fun addComment(elementId: String, text: String): Boolean
    suspend fun setInspection(elementId: String, status: String): Boolean
    suspend fun deleteTask(id: Long): Boolean

    /** elementId المقابل لكود مرجعي في الدور الحالي، أو null. */
    fun elementIdForMark(mark: String): String?
}
