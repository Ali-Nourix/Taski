package io.github.alinourix.taski.feature.board

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.alinourix.taski.core.domain.model.GroupBy
import io.github.alinourix.taski.core.domain.model.Priority
import io.github.alinourix.taski.core.domain.model.Project
import io.github.alinourix.taski.core.domain.model.SortKey
import io.github.alinourix.taski.core.domain.model.Tag
import io.github.alinourix.taski.core.domain.model.TaskStatus
import io.github.alinourix.taski.core.domain.query.TaskGroup
import io.github.alinourix.taski.core.ui.format.priorityLabel
import io.github.alinourix.taski.core.ui.format.statusLabel
import io.github.alinourix.taski.core.ui.R as UiR

@Composable
internal fun groupByLabel(groupBy: GroupBy): String = stringResource(
    when (groupBy) {
        GroupBy.None -> R.string.group_none
        GroupBy.Status -> R.string.group_status
        GroupBy.Tag -> R.string.group_tag
        GroupBy.Priority -> R.string.group_priority
        GroupBy.Deadline -> R.string.group_deadline
        GroupBy.Project -> R.string.group_project
    },
)

@Composable
internal fun sortLabel(key: SortKey): String = stringResource(
    when (key) {
        SortKey.Manual -> R.string.sort_manual
        SortKey.Title -> R.string.sort_title
        SortKey.Status -> R.string.sort_status
        SortKey.Priority -> R.string.sort_priority
        SortKey.Deadline -> R.string.sort_deadline
        SortKey.Progress -> R.string.sort_progress
        SortKey.Project -> R.string.sort_project
        SortKey.Created -> R.string.sort_created
    },
)

internal val SortIcon = Icons.AutoMirrored.Rounded.Sort

@Composable
internal fun groupTitle(group: TaskGroup, tags: List<Tag>, projects: List<Project>): String = when (group.groupBy) {
    GroupBy.None -> stringResource(R.string.group_all)
    GroupBy.Status -> statusLabel(TaskStatus.fromCode(group.value))
    GroupBy.Priority -> priorityLabel(Priority.fromCode(group.value))
    GroupBy.Tag -> tags.firstOrNull { it.id == group.value }?.name ?: stringResource(UiR.string.no_tag)
    GroupBy.Project -> projects.firstOrNull { it.id == group.value }?.name ?: stringResource(UiR.string.inbox)
    GroupBy.Deadline -> stringResource(
        when (group.value) {
            "overdue" -> R.string.bucket_overdue
            "today" -> R.string.bucket_today
            "week" -> R.string.bucket_week
            "later" -> R.string.bucket_later
            "past" -> R.string.bucket_past
            else -> R.string.bucket_none
        },
    )
}
