import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import java.util.Base64

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    intellijPlatform {
        intellijIdeaCommunity("2025.2.6")
        bundledPlugin("org.jetbrains.plugins.terminal")
        testFramework(TestFrameworkType.Platform)
    }
}

intellijPlatform {
    instrumentCode = false
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "252"
            untilBuild = provider { null }
        }
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}

tasks.register("deployToIde") {
    group = "intellij platform"
    description = "Installs the built plugin into the running JetBrains IDE (Windows) and restarts it."
    dependsOn(tasks.buildPlugin)
    val zip = tasks.buildPlugin.flatMap { it.archiveFile }
    val ideProcess = providers.gradleProperty("ideProcess").orElse("idea64")
    val script = layout.projectDirectory.file("tools/deploy-to-ide.ps1")

    doLast {
        fun exec(vararg command: String): String {
            val process = ProcessBuilder(*command).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText().trim()
            if (process.waitFor() != 0) throw GradleException("${command.first()} failed: $output")
            return output
        }
        fun encoded(ps: String) = Base64.getEncoder().encodeToString(ps.toByteArray(Charsets.UTF_16LE))
        fun quoted(s: String) = "'" + s.replace("'", "''") + "'"

        val zipPath = zip.get().asFile.absolutePath
        val windowsZip = when {
            System.getenv("WSL_DISTRO_NAME") != null -> exec("wslpath", "-w", zipPath)
            System.getProperty("os.name").startsWith("Windows") -> zipPath
            else -> throw GradleException("deployToIde only supports an IDE on Windows (Gradle on Windows or in WSL)")
        }
        val swap = "\$Zip = ${quoted(windowsZip)}\n\$IdeProcess = ${quoted(ideProcess.get())}\n" + script.asFile.readText()
        // Created through WMI so it is a child of neither this build nor the IDE it is about to close.
        val launch = "\$r = Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{ CommandLine = " +
            "'powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -EncodedCommand ${encoded(swap)}' }; exit \$r.ReturnValue"
        exec("powershell.exe", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded(launch))
        logger.lifecycle("${ideProcess.get()} will close, get ${File(zipPath).name} and start again. Log: %TEMP%\\harness-aside-deploy.log")
    }
}
