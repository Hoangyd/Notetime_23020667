package com.example.notes.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.notes.domain.Category
import com.example.notes.domain.Note
import com.example.notes.domain.Priority
import com.example.notes.domain.TaskFilter
import com.example.notes.domain.TimeUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlannerRepositoryTest {
    private lateinit var db: NotesDatabase
    private lateinit var tasks: TaskRepository
    private lateinit var calendar: CalendarRepository
    private lateinit var habits: HabitRepository
    private lateinit var insights: InsightsRepository
    private lateinit var notes: NoteRepository

    private val hour = 3_600_000L
    private val today = TimeUtils.today()
    private val dayStart = TimeUtils.dayStart(today)

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, NotesDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        notes = NoteRepository(db.noteDao())
        tasks = TaskRepository(db.taskDao(), db.noteDao())
        calendar = CalendarRepository(db.eventDao(), db.taskDao())
        habits = HabitRepository(db.habitDao())
        insights = InsightsRepository(db.insightsDao(), db.taskDao(), db.eventDao(), db.habitDao())
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun taskFilters_splitTodayUpcomingOverdueCompleted() = runBlocking {
        val now = dayStart + 12 * hour
        tasks.saveTask(TaskEntity(title = "due today", deadline = dayStart + 18 * hour))
        tasks.saveTask(TaskEntity(title = "next week", deadline = dayStart + 7 * 24 * hour))
        tasks.saveTask(TaskEntity(title = "late", deadline = dayStart - 5 * hour))
        val doneId = tasks.saveTask(TaskEntity(title = "finished", deadline = dayStart + 20 * hour))
        tasks.setDone(doneId, true)

        fun titles(filter: TaskFilter) =
            runBlocking { tasks.observeTasks(filter, epochDay = today, now = now).first() }
                .map { it.task.title }

        assertEquals(listOf("due today"), titles(TaskFilter.TODAY))
        assertEquals(listOf("next week"), titles(TaskFilter.UPCOMING))
        assertEquals(listOf("late"), titles(TaskFilter.OVERDUE))
        assertEquals(listOf("finished"), titles(TaskFilter.COMPLETED))
        assertEquals(4, titles(TaskFilter.ALL).size)
    }

    @Test
    fun taskSearchCategoryAndPriority_narrowResults() = runBlocking {
        tasks.saveTask(TaskEntity(title = "Read chapter 3", category = Category.STUDY.name, priority = Priority.HIGH.level))
        tasks.saveTask(TaskEntity(title = "Gym", category = Category.HEALTH.name, priority = Priority.LOW.level))

        assertEquals(1, tasks.observeTasks(search = "chapter").first().size)
        assertEquals("Gym", tasks.observeTasks(category = Category.HEALTH).first().single().task.title)
        assertEquals("Read chapter 3", tasks.observeTasks(priority = Priority.HIGH).first().single().task.title)
    }

    @Test
    fun subtasks_driveParentCompletionAndProgress() = runBlocking {
        val id = tasks.saveTask(TaskEntity(title = "Project"))
        val a = tasks.addSubtask(id, "Outline")
        val b = tasks.addSubtask(id, "Draft")

        tasks.toggleSubtask(a)
        var loaded = tasks.observeTask(id).first()!!
        assertEquals(0.5f, loaded.progress, 0.001f)
        assertFalse(loaded.task.isDone)

        tasks.toggleSubtask(b)
        loaded = tasks.observeTask(id).first()!!
        assertTrue(loaded.task.isDone)
        assertNotNull(loaded.task.completedAt)

        // Adding a new open subtask re-opens the parent.
        tasks.addSubtask(id, "Review")
        assertFalse(tasks.observeTask(id).first()!!.task.isDone)
    }

    @Test
    fun completingTask_checksAllSubtasks() = runBlocking {
        val id = tasks.saveTask(TaskEntity(title = "Project"))
        tasks.addSubtask(id, "One")
        tasks.addSubtask(id, "Two")

        tasks.setDone(id, true)

        assertEquals(1f, tasks.observeTask(id).first()!!.progress, 0.001f)
    }

    @Test
    fun todayOverview_reportsProgressOverdueAndSuggestion() = runBlocking {
        val doneId = tasks.saveTask(TaskEntity(title = "a", deadline = dayStart + 9 * hour))
        tasks.saveTask(TaskEntity(title = "b", deadline = dayStart + 15 * hour, priority = Priority.HIGH.level))
        val lateId = tasks.saveTask(TaskEntity(title = "late", deadline = dayStart - 3 * hour))
        tasks.setDone(doneId, true)

        val overview = tasks.observeToday(today, now = dayStart + 10 * hour).first()

        assertEquals(2, overview.totalCount)
        assertEquals(1, overview.doneCount)
        assertEquals(0.5f, overview.progress, 0.001f)
        assertEquals(listOf(lateId), overview.overdue.map { it.task.id })
        assertEquals("late", overview.suggestions.first().title)
    }

    @Test
    fun deletingTask_removesSubtasksAndUnlinksNotes() = runBlocking {
        val taskId = tasks.saveTask(TaskEntity(title = "Linked"))
        tasks.addSubtask(taskId, "x")
        val noteId = notes.save(Note(title = "Meeting notes", linkedTaskId = taskId))

        tasks.deleteTask(taskId)

        assertNull(tasks.observeTask(taskId).first())
        assertTrue(db.taskDao().getSubtasks(taskId).isEmpty())
        assertNull(notes.observeNote(noteId).first()!!.linkedTaskId)
    }

    @Test
    fun saveTask_rejectsBadInput() = runBlocking<Unit> {
        assertTrue(runCatching { tasks.saveTask(TaskEntity(title = "  ")) }.isFailure)
        assertTrue(
            runCatching {
                tasks.saveTask(TaskEntity(title = "x", scheduledStart = 10L, scheduledEnd = 5L))
            }.isFailure
        )
    }

    @Test
    fun calendar_flagsOverlapsAndSupportsMoving() = runBlocking {
        val taskId = tasks.saveTask(TaskEntity(title = "Study", category = Category.STUDY.name))
        val first = calendar.scheduleTask(taskId, dayStart + 9 * hour, dayStart + 10 * hour)!!
        assertFalse(first.hasConflict)

        val clash = calendar.saveEvent(
            EventEntity(title = "Dentist", startAt = dayStart + 9 * hour + 30 * 60_000L, endAt = dayStart + 10 * hour + 30 * 60_000L)
        )
        assertEquals(1, clash.conflicts.size)
        assertEquals(BlockKind.TASK, clash.conflicts.single().kind)

        val blocks = calendar.observeBlocks(dayStart, dayStart + 24 * hour).first()
        assertEquals(2, blocks.size)
        assertEquals(2, Scheduling.conflictKeys(blocks).size)

        // Moving the event later resolves the clash and keeps its duration.
        val moved = calendar.moveEvent(clash.id, dayStart + 11 * hour)!!
        assertFalse(moved.hasConflict)
        assertEquals(hour, db.eventDao().getById(clash.id)!!.let { it.endAt - it.startAt })
    }

    @Test
    fun weeklyClass_appearsOnEachMatchingDay() = runBlocking {
        val monday = TimeUtils.weekStart(today)
        calendar.saveEvent(
            EventEntity(
                title = "Physics",
                category = "STUDY",
                startAt = TimeUtils.dayStart(monday) + 8 * hour,
                endAt = TimeUtils.dayStart(monday) + 9 * hour,
                repeatDaysMask = 0b0010101 // Mon, Wed, Fri
            )
        )

        val week = calendar.observeBlocks(TimeUtils.dayStart(monday), TimeUtils.dayStart(monday + 7)).first()

        assertEquals(listOf(monday, monday + 2, monday + 4), week.map { it.occurrenceDay })
    }

    @Test
    fun habits_toggleBuildsStreak() = runBlocking {
        val id = habits.saveHabit(HabitEntity(name = "Read", createdAt = dayStart - 10 * 24 * hour))
        habits.toggle(id, today - 2)
        habits.toggle(id, today - 1)
        assertTrue(habits.toggle(id, today))

        var stats = habits.observeHabits(today).first().single()
        assertEquals(3, stats.currentStreak)
        assertTrue(stats.doneToday)

        assertFalse(habits.toggle(id, today)) // un-check today
        stats = habits.observeHabits(today).first().single()
        assertEquals(2, stats.currentStreak) // pending today keeps yesterday's streak
        assertFalse(stats.doneToday)
    }

    @Test
    fun deletingHabit_removesItsLogs() = runBlocking {
        val id = habits.saveHabit(HabitEntity(name = "Run"))
        habits.toggle(id, today)

        habits.deleteHabit(id)

        assertEquals(0, db.habitDao().countLogs())
    }

    @Test
    fun insights_weeklyCompletionHeatmapAndPlanVsActual() = runBlocking {
        val monday = TimeUtils.weekStart(today)
        val mondayStart = TimeUtils.dayStart(monday)
        val a = tasks.saveTask(TaskEntity(title = "a", deadline = mondayStart + 10 * hour))
        tasks.saveTask(TaskEntity(title = "b", deadline = mondayStart + 12 * hour))
        tasks.setDone(a, true)

        val week = insights.observeWeeklyCompletion(monday).first()
        assertEquals(7, week.size)
        assertEquals(2, week[0].due)
        assertEquals(1, week[0].done)
        assertEquals(0.5f, week[0].rate, 0.001f)

        val habitId = habits.saveHabit(HabitEntity(name = "Walk"))
        habits.toggle(habitId, today)
        val heat = insights.observeHeatmap(monday, monday + 6).first()
        assertTrue((heat[today] ?: 0) >= 1)

        // Plan: 2h study block. Actual: 1.5h logged.
        val studyTask = tasks.saveTask(TaskEntity(title = "study", category = Category.STUDY.name))
        calendar.scheduleTask(studyTask, mondayStart + 14 * hour, mondayStart + 16 * hour)
        insights.logTime(Category.STUDY, mondayStart + 14 * hour, mondayStart + 15 * hour + 30 * 60_000L, studyTask)

        val plan = insights.observePlannedVsActual(mondayStart, mondayStart + 24 * hour).first()
            .first { it.category == Category.STUDY }
        assertEquals(2 * hour, plan.plannedMs)
        assertEquals(90 * 60_000L, plan.actualMs)
    }

    @Test
    fun notes_draftsAreHiddenUntilPublished_andChecklistRoundTrips() = runBlocking {
        val draftId = notes.saveDraft(Note(title = "WIP", color = 0xFF2196F3.toInt(), labels = listOf("a", "b")))
        assertTrue(notes.observeNotes("", false, null).first().isEmpty())
        assertEquals(draftId, notes.observeLatestDraft().first()!!.id)

        notes.save(notes.observeLatestDraft().first()!!.copy(isDraft = false, isChecklist = true))
        val published = notes.observeNotes("", false, null).first().single()
        assertEquals(listOf("a", "b"), published.labels)
        assertEquals(0xFF2196F3.toInt(), published.color)

        notes.replaceItems(
            published.id,
            listOf(NoteItemEntity(noteId = 0, text = "milk"), NoteItemEntity(noteId = 0, text = ""), NoteItemEntity(noteId = 0, text = "eggs"))
        )
        val items = notes.getItems(published.id)
        assertEquals(listOf("milk", "eggs"), items.map { it.text })

        // Labels are searchable and the checklist survives export/import.
        assertEquals(1, notes.observeNotes("b", false, null).first().size)
        val json = notes.exportAsJson()
        notes.deleteNote(published.id)
        assertEquals(1, notes.importFromJson(json))
        val restored = notes.observeNotes("", false, null).first().single()
        assertEquals(2, notes.getItems(restored.id).size)
    }

    @Test
    fun notes_canBeLinkedToDate() = runBlocking {
        notes.save(Note(title = "Day plan", linkedDate = today))
        notes.save(Note(title = "Other", linkedDate = today + 1))

        assertEquals("Day plan", notes.observeNotesForDate(today).first().single().title)
    }

    @Test
    fun editingTask_keepsItsSubtasks() = runBlocking {
        val id = tasks.saveTask(TaskEntity(title = "Project"))
        tasks.addSubtask(id, "Outline")
        tasks.addSubtask(id, "Draft")

        tasks.saveTask(db.taskDao().getById(id)!!.copy(title = "Project v2"))

        assertEquals(listOf("Outline", "Draft"), db.taskDao().getSubtasks(id).map { it.title })
    }

    @Test
    fun editingHabit_keepsItsCheckIns() = runBlocking {
        val id = habits.saveHabit(HabitEntity(name = "Run"))
        habits.toggle(id, today)

        habits.saveHabit(db.habitDao().getById(id)!!.copy(name = "Run daily"))

        assertTrue(db.habitDao().hasLog(id, today))
    }
}
