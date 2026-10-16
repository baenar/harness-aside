package com.github.baenar.harnessaside.core

import com.intellij.openapi.util.Key

/** Identifier of the right-docked tool window in plugin.xml. */
const val TOOL_WINDOW_ID = "Harness Aside"

/** User data key on terminal content storing the active harness ID. */
val HARNESS_ID_KEY: Key<String> = Key.create<String>("harness.aside.harnessId")
