package io.github.alinourix.taski.core.domain.sync

object OrphanRules {
    /**
     * Tasks whose project no longer exists (deleted on another device, or never
     * arrived). They move to the Inbox (`project_id = null`) rather than vanish.
     */
    fun tasksToMoveToInbox(taskProjects: Map<String, String?>, liveProjectIds: Set<String>): Set<String> =
        taskProjects.filterValues { it != null && it !in liveProjectIds }.keys
}
