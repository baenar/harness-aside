package com.github.baenar.harnessaside

import com.github.baenar.harnessaside.core.HarnessTerminalService
import com.github.baenar.harnessaside.core.TOOL_WINDOW_ID
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager

/**
 * Editor action: inserts a reference to the current file (and selected line range) into
 * the active harness terminal as `@path/to/file#Lx-Ly`, without submitting it.
 */
class SendSelectionToHarnessAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val reference = buildReference(project, file, e.getData(CommonDataKeys.EDITOR))
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID) ?: return

        // activate() creates the tool window's content (via the factory) if it doesn't exist yet.
        toolWindow.activate { project.service<HarnessTerminalService>().sendText(toolWindow, "$reference ") }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null && e.getData(CommonDataKeys.VIRTUAL_FILE) != null
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    private fun buildReference(project: Project, file: VirtualFile, editor: Editor?): String {
        val path = relativePath(project, file)
        val selection = editor?.selectionModel?.takeIf { it.hasSelection() } ?: return "@$path"
        val start = editor.document.getLineNumber(selection.selectionStart) + 1
        val end = editor.document.getLineNumber(selection.selectionEnd) + 1
        return if (start == end) "@$path#L$start" else "@$path#L$start-$end"
    }

    private fun relativePath(project: Project, file: VirtualFile): String {
        val base = project.basePath
        return if (base != null && file.path.startsWith("$base/")) file.path.removePrefix("$base/") else file.name
    }
}
