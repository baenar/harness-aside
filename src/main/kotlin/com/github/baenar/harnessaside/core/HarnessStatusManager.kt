package com.github.baenar.harnessaside.core

import com.intellij.ide.SaveAndSyncHandler
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager

/**
 * Project service holding the single-session mode's status, which the tool window button shows, and the
 * gear-menu options. In multi-session mode each tab has a [SessionStatus] of its own and the button stays still.
 */
@Service(Service.Level.PROJECT)
class HarnessStatusManager(private val project: Project) : Disposable {

    private val toolWindow get() = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)

    val single = SessionStatus(
        project,
        showIcon = { status, frame -> toolWindow?.setIcon(StatusIcons.forStatus(status, frame)) },
        isSeen = { toolWindow?.isVisible == true },
    ).also { Disposer.register(this, it) }

    fun describeState(): String {
        val server = project.service<HarnessStatusServer>()
        return buildString {
            appendLine("Server running: ${server.isRunning} (port ${server.port})")
            appendLine("Mode: ${if (isMultiSession(project)) "multiple sessions" else "single session"}")
            appendLine(single.describe())
        }
    }

    override fun dispose() {}

    companion object {
        const val NOTIFICATION_GROUP = "Harness Aside"
        private const val SOUND_ON_INPUT_KEY = "harness.aside.soundOnInput"
        private const val SOUND_WHEN_DONE_KEY = "harness.aside.soundWhenDone"
        private const val MULTI_SESSION_KEY = "harness.aside.multiSession"

        // The gear-menu options. The sounds apply to every project; the session mode to this one. Each change is
        // saved right away rather than left to the IDE's next save, so it survives an IDE restart.
        var soundOnInput: Boolean
            get() = PropertiesComponent.getInstance().getBoolean(SOUND_ON_INPUT_KEY)
            set(value) {
                PropertiesComponent.getInstance().setValue(SOUND_ON_INPUT_KEY, value)
                SaveAndSyncHandler.getInstance().scheduleSave(SaveAndSyncHandler.SaveTask())
            }

        var soundWhenDone: Boolean
            get() = PropertiesComponent.getInstance().getBoolean(SOUND_WHEN_DONE_KEY)
            set(value) {
                PropertiesComponent.getInstance().setValue(SOUND_WHEN_DONE_KEY, value)
                SaveAndSyncHandler.getInstance().scheduleSave(SaveAndSyncHandler.SaveTask())
            }

        fun isMultiSession(project: Project): Boolean = PropertiesComponent.getInstance(project).getBoolean(MULTI_SESSION_KEY)

        fun setMultiSession(project: Project, value: Boolean) {
            PropertiesComponent.getInstance(project).setValue(MULTI_SESSION_KEY, value)
            SaveAndSyncHandler.getInstance().scheduleProjectSave(project)
        }
    }
}
