package com.github.baenar.harnessaside

import com.github.baenar.harnessaside.ui.AlertOptionAction
import com.github.baenar.harnessaside.ui.DiagnoseStatusAction
import com.github.baenar.harnessaside.ui.EndSessionAction
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
            SendSelectionToHarnessAction::class,
            EndSessionAction::class,
            DiagnoseStatusAction::class,
            AlertOptionAction::class,
        )
        for (cls in classes) {
            assertTrue("${cls.simpleName} must implement DumbAware", DumbAware::class.java.isAssignableFrom(cls.java))
        }
    }
}
