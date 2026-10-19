package com.example.nightwatchprotocol.data.learning

/** Islands on the map: the six topics plus the "Prueba Final" (the exam). Figma: "N de 7 islas completadas". */
const val TOTAL_ISLANDS = 7

enum class IslandState { Locked, Available, Completed }

/**
 * One island of the map. [lessonViewed] and [labCompleted] are what is saved; [state] follows the group's rules.
 * The lesson and the lab of an island can be opened when the island is unlocked ([isUnlocked]): the lab does NOT need the
 * lesson to be seen first (decision Q4).
 */
data class Island(
    val topic: LearningTopic,
    val lessonViewed: Boolean,
    val labCompleted: Boolean,
    val state: IslandState,
) {
    val isCompleted: Boolean get() = state == IslandState.Completed
    val isUnlocked: Boolean get() = state != IslandState.Locked
}

/** Whether the exam can be opened: only after the six islands are completed (decision P3). It can be retaken without limit. */
enum class ExamGate { Locked, Available }

/**
 * What the map, the labs list and Inicio show, derived from what is saved. Nothing here is stored (migration 0007 has no
 * "completed island" column) and the database does not enforce it: the order and the exam gate are the app's rules.
 */
data class LearningProgress(
    val islands: List<Island>,
    val examGate: ExamGate,
    val examPassed: Boolean,
    /** The six islands completed (counted by their data) plus the "Prueba Final" when an attempt passed. */
    val completedCount: Int,
    /** The topic of the first unlocked island whose lesson has not been seen; null if there is none (decision Q3). */
    val nextPendingLesson: LearningTopic?,
) {
    val totalCount: Int get() = TOTAL_ISLANDS

    fun island(topic: LearningTopic): Island = islands.first { it.topic == topic }

    fun canOpenLesson(topic: LearningTopic): Boolean = island(topic).isUnlocked

    fun canOpenLab(topic: LearningTopic): Boolean = island(topic).isUnlocked
}

/**
 * Derives the progress with the group's rules (spec, decisions P3, P1, Q3, Q4, Q5):
 *  - an island is completed with its lesson seen AND its lab completed;
 *  - the first island is always unlocked; each other one unlocks when the previous island is completed;
 *  - the exam opens when the six islands are completed;
 *  - the "Prueba Final" counts as completed when any attempt passed.
 * The islands follow the [LearningTopic] order.
 */
fun deriveLearningProgress(snapshot: LearningSnapshot): LearningProgress {
    val islands = mutableListOf<Island>()
    var previousCompleted = true
    for (topic in LearningTopic.entries) {
        val lessonViewed = topic in snapshot.viewedLessons
        val labCompleted = topic in snapshot.completedLabs
        val completed = lessonViewed && labCompleted
        val state = when {
            completed -> IslandState.Completed
            previousCompleted -> IslandState.Available
            else -> IslandState.Locked
        }
        islands += Island(topic, lessonViewed, labCompleted, state)
        previousCompleted = completed
    }

    val completedIslands = islands.count { it.isCompleted }
    return LearningProgress(
        islands = islands,
        examGate = if (completedIslands == LearningTopic.entries.size) ExamGate.Available else ExamGate.Locked,
        examPassed = snapshot.exam.hasPassed,
        completedCount = completedIslands + if (snapshot.exam.hasPassed) 1 else 0,
        nextPendingLesson = islands.firstOrNull { it.isUnlocked && !it.lessonViewed }?.topic,
    )
}
