package io.github.alinourix.taski

import io.github.alinourix.taski.core.domain.CalendarSystem
import io.github.alinourix.taski.core.domain.model.ColorToken
import io.github.alinourix.taski.core.domain.model.NewTask
import io.github.alinourix.taski.core.domain.model.Priority
import io.github.alinourix.taski.core.domain.model.RepeatRule
import io.github.alinourix.taski.core.domain.model.RepeatUnit
import io.github.alinourix.taski.core.domain.model.StepProgress
import io.github.alinourix.taski.core.domain.model.TaskEdit
import io.github.alinourix.taski.core.domain.model.TaskStatus
import io.github.alinourix.taski.core.domain.repository.PreferencesRepository
import io.github.alinourix.taski.core.domain.repository.ProjectRepository
import io.github.alinourix.taski.core.domain.repository.TagRepository
import io.github.alinourix.taski.core.domain.repository.TaskRepository
import java.time.LocalDate
import java.time.LocalTime

/** A believable week of tasks, in English or Persian, for the screenshots. */
object Sample {
    suspend fun seed(
        tasks: TaskRepository,
        tags: TagRepository,
        projects: ProjectRepository,
        prefs: PreferencesRepository,
        persian: Boolean,
        today: LocalDate,
    ): Map<String, String> {
        prefs.update { it.copy(materialYou = false, calendar = if (persian) CalendarSystem.Jalali else CalendarSystem.Gregorian, markOverdueNotDone = false) }
        fun t(en: String, fa: String) = if (persian) fa else en

        val work = tags.create(t("Work", "کار"), ColorToken.Palette.Blue)
        val client = tags.create(t("Client", "مشتری"), ColorToken.Palette.Purple)
        val deep = tags.create(t("Deep work", "کار عمیق"), ColorToken.Palette.Green)
        val home = tags.create(t("Home", "خانه"), ColorToken.Palette.Orange)

        val launch = projects.create(t("Product launch", "راه‌اندازی محصول"), ColorToken.Palette.Blue)
        val thesis = projects.create(t("Thesis", "پایان‌نامه"), ColorToken.Palette.Purple)
        val house = projects.create(t("House", "خانه"), ColorToken.Palette.Orange)

        val brief = tasks.create(
            NewTask(
                t("Draft the launch brief", "نوشتن پیش‌نویس معرفی محصول"), projectId = launch, priority = Priority.High,
                startDate = today, startTime = LocalTime.of(9, 0), dueDate = today, dueTime = LocalTime.of(14, 30), tagIds = listOf(work, deep), timerMinutes = 25,
            ),
        )
        tasks.setStatus(brief, TaskStatus.InProgress)
        tasks.create(NewTask(t("Collect screenshots", "جمع کردن اسکرین‌شات‌ها"), parentId = brief))
        val outline = tasks.create(NewTask(t("Outline the sections", "طرح کلی بخش‌ها"), parentId = brief))
        tasks.setStatus(outline, TaskStatus.Done)

        tasks.create(NewTask(t("Reply to the client about pricing", "پاسخ به مشتری درباره قیمت"), projectId = launch, priority = Priority.Highest, dueDate = today.minusDays(2), tagIds = listOf(client)))
        tasks.create(NewTask(t("Weekly review", "مرور هفتگی"), priority = Priority.Medium, startDate = today, startTime = LocalTime.of(16, 0), dueDate = today, dueTime = LocalTime.of(17, 0), repeat = RepeatRule(1, RepeatUnit.Week), tagIds = listOf(work)))
        val chapter = tasks.create(NewTask(t("Write chapter 3", "نوشتن فصل ۳"), projectId = thesis, priority = Priority.High, startDate = today.minusDays(1), dueDate = today.plusDays(3), tagIds = listOf(deep)))
        tasks.edit(chapter, listOf(TaskEdit.Progress(StepProgress(3, 8))))
        tasks.create(NewTask(t("Read two papers on HLCs", "خواندن دو مقاله درباره HLC"), projectId = thesis, priority = Priority.Low, dueDate = today.plusDays(1)))
        tasks.create(NewTask(t("Water the plants", "آب دادن به گیاه‌ها"), projectId = house, dueDate = today.plusDays(1), repeat = RepeatRule(3, RepeatUnit.Day), tagIds = listOf(home)))
        tasks.create(NewTask(t("Book the plumber", "رزرو لوله‌کش"), projectId = house, priority = Priority.Medium, startDate = today.plusDays(2), dueDate = today.plusDays(5), tagIds = listOf(home)))
        tasks.create(NewTask(t("Call with the client", "تماس با مشتری"), projectId = launch, priority = Priority.Medium, startDate = today, startTime = LocalTime.of(10, 0), dueDate = today, dueTime = LocalTime.of(11, 0), tagIds = listOf(client)))
        val done = tasks.create(NewTask(t("Pay the internet bill", "پرداخت قبض اینترنت"), dueDate = today, tagIds = listOf(home)))
        tasks.setStatus(done, TaskStatus.Done)
        tasks.create(NewTask(t("Plan the team offsite", "برنامه‌ریزی دورهمی تیم"), projectId = launch, priority = Priority.Lowest, tagIds = listOf(work)))
        val blocked = tasks.create(NewTask(t("Publish the release notes", "انتشار یادداشت‌های نسخه"), projectId = launch, priority = Priority.High, dueDate = today.plusDays(2), tagIds = listOf(work)))
        tasks.addDependency(blocked, brief)
        return mapOf("brief" to brief, "launch" to launch)
    }
}
