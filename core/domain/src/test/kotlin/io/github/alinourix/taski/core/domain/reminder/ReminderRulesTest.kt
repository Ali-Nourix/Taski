package io.github.alinourix.taski.core.domain.reminder

import io.github.alinourix.taski.core.domain.Fixtures.item
import io.github.alinourix.taski.core.domain.Fixtures.today
import io.github.alinourix.taski.core.domain.model.Priority
import io.github.alinourix.taski.core.domain.model.TaskStatus
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReminderRulesTest {
    private val zone = ZoneId.of("Asia/Tehran")
    private fun at(value: String) = LocalDateTime.parse(value).atZone(zone).toInstant()

    @Test
    fun aTaskRemindsBeforeItsDueMoment() {
        val task = item("t", due = today).task.copy(dueTime = LocalTime.of(14, 30), reminderOffsetMinutes = 15)
        assertEquals(at("2026-09-29T14:15"), ReminderRules.taskReminderAt(task, zone))
        assertEquals(at("2026-09-29T09:00"), ReminderRules.taskReminderAt(task.copy(dueTime = null, reminderOffsetMinutes = 0), zone))
        assertNull(ReminderRules.taskReminderAt(task.copy(status = TaskStatus.Done), zone))
        assertNull(ReminderRules.taskReminderAt(task.copy(reminderOffsetMinutes = null), zone))
    }

    @Test
    fun quietHoursWrapPastMidnight() {
        val settings = DigestSettings(quietHoursEnabled = true, quietStart = LocalTime.of(23, 0), quietEnd = LocalTime.of(7, 0))
        assertTrue(ReminderRules.isQuietTime(LocalTime.of(23, 30), settings))
        assertTrue(ReminderRules.isQuietTime(LocalTime.of(6, 59), settings))
        assertTrue(!ReminderRules.isQuietTime(LocalTime.of(7, 0), settings))
    }

    @Test
    fun nextDigestIsTheSoonestSlot() {
        val settings = DigestSettings(times = listOf(LocalTime.of(9, 0), LocalTime.of(18, 0)))
        assertEquals(at("2026-09-29T18:00"), ReminderRules.nextDigestAt(at("2026-09-29T10:00"), zone, settings, null))
        assertEquals(at("2026-09-30T09:00"), ReminderRules.nextDigestAt(at("2026-09-29T18:00"), zone, settings, null))
        assertNull(ReminderRules.nextDigestAt(at("2026-09-29T10:00"), zone, settings.copy(enabled = false), null))
    }

    @Test
    fun repeatIntervalsSkipQuietHours() {
        val settings = DigestSettings(
            times = emptyList(), repeatEnabled = true, repeatIntervalMinutes = 120,
            quietHoursEnabled = true, quietStart = LocalTime.of(22, 0), quietEnd = LocalTime.of(7, 0),
        )
        assertEquals(at("2026-09-29T12:00"), ReminderRules.nextDigestAt(at("2026-09-29T10:30"), zone, settings, at("2026-09-29T10:00")))
        assertEquals(at("2026-09-30T07:00"), ReminderRules.nextDigestAt(at("2026-09-29T21:00"), zone, settings, at("2026-09-29T21:00")))
    }

    @Test
    fun digestListsOnlyWhatPassesTheFilters() {
        val now = at("2026-09-29T09:00")
        val items = listOf(
            item("overdue", priority = Priority.High, due = today.minusDays(3)),
            item("today", priority = Priority.Highest, due = today),
            item("later", priority = Priority.High, due = today.plusDays(3)),
            item("done", priority = Priority.High, due = today, status = TaskStatus.Done),
            item("low", priority = Priority.Low, due = today),
            item("none", due = today),
            item("undated", priority = Priority.High),
        )
        val defaults = DigestSettings()
        assertEquals(listOf("today", "overdue"), ReminderRules.digestTasks(items, defaults, today, now).map { it.id })
        assertEquals(
            listOf("today", "overdue", "later", "undated"),
            ReminderRules.digestTasks(items, defaults.copy(dueWithinDays = 7, includeWithoutDeadline = true), today, now).map { it.id },
        )
    }

    @Test
    fun theAgeFilterCombinesAsConfigured() {
        val now = at("2026-09-29T09:00")
        val fresh = now.minus(Duration.ofDays(1)).toEpochMilli()
        val items = listOf(
            item("fresh-undated", priority = Priority.High, createdAt = fresh),
            item("old-today", priority = Priority.High, due = today),
        )
        val base = DigestSettings(createdWithinDays = 3)
        assertEquals(listOf("fresh-undated"), ReminderRules.digestTasks(items, base, today, now).map { it.id })
        assertEquals(setOf("fresh-undated", "old-today"), ReminderRules.digestTasks(items, base.copy(ageFilterMode = AgeFilterMode.Or), today, now).map { it.id }.toSet())
        assertEquals(emptyList(), ReminderRules.digestTasks(items, base.copy(ageFilterMode = AgeFilterMode.And), today, now).map { it.id })
    }
}
