package com.github.baenar.harnessaside

import com.github.baenar.harnessaside.claude.DiagnoseStatusAction
import com.github.baenar.harnessaside.claude.EnableStatusHooksAction
import com.github.baenar.harnessaside.claude.RemoveClaudeTabAction
import com.github.baenar.harnessaside.claude.RestartClaudeTabAction
import com.intellij.openapi.project.DumbAware
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The plugin never reads indexes, so none of it may be locked away while the IDE indexes
 * ("not available until indexes are built"). Add any new tool window or action here.
 */
class DumbAwareTest {

    @Test
    fun `tool window and every action stay usable during indexing`() {
        val classes = listOf(
            HarnessToolWindowFactory::class,
            SendSelectionToClaudeAction::class,
            RestartClaudeTabAction::class,
            RemoveClaudeTabAction::class,
            DiagnoseStatusAction::class,
            EnableStatusHooksAction::class,
        )
        for (cls in classes) {
            assertTrue("${cls.simpleName} must implement DumbAware", DumbAware::class.java.isAssignableFrom(cls.java))
        }
    }
}
