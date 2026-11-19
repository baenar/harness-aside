package com.github.baenar.harnessaside.ui

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros

/** The multi-session tabs, kept in the per-user workspace file so they come back after an IDE restart. */
@Service(Service.Level.PROJECT)
@State(name = "HarnessAsideTabs", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class HarnessTabsState : PersistentStateComponent<HarnessTabsState.Saved> {

    /** [seed] picks the tab's manul. [harness] is null while the tab shows the launcher. */
    class SavedTab {
        var seed: Int = 0
        var harness: String? = null
        var session: String? = null
    }

    class Saved {
        var tabs: MutableList<SavedTab> = mutableListOf()
        var selected: Int = 0
    }

    var saved = Saved()
        private set

    override fun getState(): Saved = saved

    override fun loadState(state: Saved) {
        saved = state
    }
}
