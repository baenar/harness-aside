package com.github.baenar.harnessaside

import com.github.baenar.harnessaside.core.HarnessTerminalService
import com.github.baenar.harnessaside.core.TOOL_WINDOW_ID
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager

/**
 * Editor action: inserts a reference to the current file (and selected line range) into
 * the active harness terminal as `@path/to/file#Lx-Ly`, without submitting it.
 */
open class SendSelectionToHarnessAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val editor = e.getData(CommonDataKeys.EDITOR)

        val reference = buildReference(project, file, editor)
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID) ?: return

        // activate() creates the first tab (via the factory) if none exists yet, then sends.
        toolWindow.activate { project.service<HarnessTerminalService>().sendText(toolWindow, "$reference ") }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null && e.getData(CommonDataKeys.VIRTUAL_FILE) != null
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    private fun buildReference(project: Project, file: VirtualFile, editor: Editor?): String {
        val ref = StringBuilder("@").append(relativePath(project, file))
        val selection = editor?.selectionModel
        if (selection != null && selection.hasSelection()) {
            val doc = editor.document
            val start = doc.getLineNumber(selection.selectionStart) + 1
            val end = doc.getLineNumber(selection.selectionEnd) + 1
            if (start == end) ref.append("#L").append(start)
            else ref.append("#L").append(start).append('-').append(end)
        }
        return ref.toString()
    }

    private fun relativePath(project: Project, file: VirtualFile): String {
        val base = project.basePath
        val path = file.path
        return if (base != null && path.startsWith(base)) path.substring(base.length).trimStart('/') else file.name
    }
}

/** Legacy alias retained for plugin.xml backward compatibility. */
class SendSelectionToClaudeAction : SendSelectionToHarnessAction()

