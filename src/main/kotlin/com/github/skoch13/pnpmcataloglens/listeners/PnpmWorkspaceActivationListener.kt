package com.github.skoch13.pnpmcataloglens.listeners

import com.github.skoch13.pnpmcataloglens.services.PnpmWorkspaceService
import com.intellij.openapi.application.ApplicationActivationListener
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFrame

/**
 * Listener for application activation events.
 * Refreshes PNPM workspace data when the application is activated.
 */
internal class PnpmWorkspaceActivationListener : ApplicationActivationListener {
    private val LOG = logger<PnpmWorkspaceActivationListener>()

    override fun applicationActivated(ideFrame: IdeFrame) {
        // Get the active project
        val project = ideFrame.project ?: return

        // Refresh PNPM workspace data
        refreshPnpmWorkspaceData(project)
    }

    /**
     * Refreshes PNPM workspace data for the given project.
     */
    private fun refreshPnpmWorkspaceData(project: Project) {
        try {
            val workspaceService = project.service<PnpmWorkspaceService>()

            // Only refresh if the project has a pnpm-workspace.yaml file
            if (workspaceService.hasPnpmWorkspace()) {
                workspaceService.refresh()
                LOG.info("Refreshed PNPM workspace data for project: ${project.name}")
            }
        } catch (e: Exception) {
            LOG.warn("Failed to refresh PNPM workspace data", e)
        }
    }
}
