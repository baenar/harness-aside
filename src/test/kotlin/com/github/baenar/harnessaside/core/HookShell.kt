package com.github.baenar.harnessaside.core

import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Runs a hook command string the way the harness CLI does, so tests execute the real generated command.
 *
 * POSIX: `/bin/sh -c <command>`. Windows: `agy` (a Go binary) runs `cmd.exe /c <command>`, and Go quotes that
 * argument with `syscall.EscapeArg`, which turns every `"` into `\"` before cmd sees it. Java's ProcessBuilder
 * doesn't, so the argument is pre-escaped here; Java passes an already-quoted argument through verbatim.
 */
object HookShell {
    val isWindows: Boolean = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)

    /** Fail rather than wait: a slow hook stalls every tool call (agy's own limit is 30s). */
    private const val TIMEOUT_SECONDS = 5L

    data class Result(val exitCode: Int, val stdout: String, val stderr: String)

    /** [env] entries with a null value are removed from the inherited environment. */
    fun run(command: String, cwd: Path, env: Map<String, String?> = emptyMap(), stdin: String = ""): Result {
        val argv = if (isWindows) listOf("cmd.exe", "/c", goEscapeArg(command)) else listOf("/bin/sh", "-c", command)
        val builder = ProcessBuilder(argv).directory(cwd.toFile())
        for ((name, value) in env) {
            if (value == null) builder.environment().remove(name) else builder.environment()[name] = value
        }
        val process = builder.start()
        val stdout = CompletableFuture.supplyAsync { process.inputStream.readAllBytes().toString(Charsets.UTF_8) }
        val stderr = CompletableFuture.supplyAsync { process.errorStream.readAllBytes().toString(Charsets.UTF_8) }
        // A hook that never reads stdin may already have exited and closed the pipe.
        runCatching { process.outputStream.use { it.write(stdin.toByteArray()) } }
        if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw AssertionError("hook ran longer than ${TIMEOUT_SECONDS}s: $command")
        }
        return Result(
            process.exitValue(),
            stdout.get(TIMEOUT_SECONDS, TimeUnit.SECONDS),
            stderr.get(TIMEOUT_SECONDS, TimeUnit.SECONDS),
        )
    }

    /** Port of Go's `syscall.EscapeArg`: quote when there is whitespace, backslash-escape every `"`. */
    fun goEscapeArg(s: String): String {
        if (s.isEmpty()) return "\"\""
        val hasSpace = s.any { it == ' ' || it == '\t' }
        if (s.none { it == '"' || it == '\\' }) return if (hasSpace) "\"$s\"" else s
        val b = StringBuilder()
        if (hasSpace) b.append('"')
        var slashes = 0
        for (c in s) {
            when (c) {
                '\\' -> slashes++
                '"' -> {
                    repeat(slashes + 1) { b.append('\\') }
                    slashes = 0
                }
                else -> slashes = 0
            }
            b.append(c)
        }
        if (hasSpace) {
            repeat(slashes) { b.append('\\') }
            b.append('"')
        }
        return b.toString()
    }
}
