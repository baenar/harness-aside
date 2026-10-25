package com.github.baenar.harnessaside.claude

import com.github.baenar.harnessaside.core.HarnessStatusManager
import com.github.baenar.harnessaside.core.HarnessStatusServer
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import java.awt.Component

/**
 * Service providing Claude status tracking, bridging to [HarnessStatusServer] and [HarnessStatusManager].
 *
 * Replaces the former file-polling and transcript-tailing mechanism with push-based HTTP events.
 */
@Service(Service.Level.PROJECT)
class ClaudeStatusService(private val project: Project) : Disposable {

    private val statusManager get() = project.service<HarnessStatusManager>()
    private val statusServer get() = project.service<HarnessStatusServer>()

    fun ensureStarted() {
        statusServer.ensureStarted()
        statusManager.ensureStarted()
    }

    fun watchInterruptKeys(component: Component, sessionId: String, parent: Disposable) {
        statusManager.watchInterruptKeys(component, parent)
    }

    fun describeState(): String = statusManager.describeState()

    override fun dispose() {
    }

    companion object {
        const val NOTIFICATION_GROUP = HarnessStatusManager.NOTIFICATION_GROUP
    }
}
