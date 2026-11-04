package com.github.baenar.harnessaside.opencode

import com.github.baenar.harnessaside.claude.ClaudeSessions
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Installs the OpenCode plugin that pushes status to [com.github.baenar.harnessaside.core.HarnessStatusServer].
 * OpenCode has no shell hooks, so this is a JS file in its global plugin directory, run inside OpenCode itself.
 */
object OpenCodeStatusPlugin {
    private val LOG = logger<OpenCodeStatusPlugin>()

    const val FILE = "harness-aside-status.js"
    val SOURCE: String by lazy { OpenCodeStatusPlugin::class.java.getResource("/opencode/$FILE")!!.readText() }

    /** `~/.config/opencode/plugins`, on Windows too. */
    fun pluginDir(home: String): Path = Paths.get(home, ".config", "opencode", "plugins")

    /**
     * Every home an `opencode` started from [project] may read: the IDE's, and for a project inside WSL also
     * the distro's. Inside WSL `opencode` is often the Windows `opencode.exe` reached through PATH interop.
     */
    fun homes(project: Project, userHome: String = System.getProperty("user.home")): Set<String> {
        val projectPath = project.basePath ?: return setOf(userHome)
        return setOf(userHome, ClaudeSessions.storage(userHome, projectPath).userHome)
    }

    fun install(home: String) {
        try {
            val target = pluginDir(home).resolve(FILE)
            if (Files.exists(target) && Files.readString(target) == SOURCE) return
            Files.createDirectories(target.parent)
            Files.writeString(target, SOURCE)
        } catch (e: Exception) {
            LOG.warn("Could not install the OpenCode status plugin into $home", e)
        }
    }
}
