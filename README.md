<h1 align="center">harness-aside</h1>

<p align="center">
  <img src="src/main/resources/icons/manul-working.svg" alt="Manul working" width="96" height="96" />
</p>

<p align="center">
  <a href="https://github.com/baenar/harness-aside/actions/workflows/build.yml"><img src="https://github.com/baenar/harness-aside/actions/workflows/build.yml/badge.svg" alt="Build" /></a>
</p>

Personal IntelliJ/PyCharm plugin: AI harnesses (Claude Code, Antigravity CLI, OpenCode) in a dedicated terminal tool window, docked on the right.
Almost entirely vibecoded - use at your own risk:))

Verified on **Windows**, **WSL**, and **Linux**.

## Install

[**Download the latest plugin zip**](https://github.com/baenar/harness-aside/releases/latest/download/harness-aside.zip)
— then <kbd>Settings</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk…</kbd>.

All versions live under [Releases](https://github.com/baenar/harness-aside/releases).

## Platform support

*Verified* means exercised by hand end to end: the tab opens and resumes its session, the
icon animates while Claude works, waiting-for-approval shows, and Ctrl+C returns it to the
still glyph.

| Platform | Status                                                                          |
|---|---------------------------------------------------------------------------------|
| Windows  | **Verified** — IDE and Claude both on the Windows side                          |
| WSL      | **Verified** — IDE on Windows, Claude inside the distro, project under `/home`  |
| Linux    | **Verified** — exercised end to end natively on Linux                           |
| macOS    | **Unverified** — prolly safe to run, not tested                                 |

Status hooks last verified by hand against **Antigravity CLI 1.4.6** (WSL) and **1.4.4** (native Windows),
and **Claude Code 2.1.309** (WSL). CI runs every generated Antigravity hook command through `/bin/sh` (Linux)
and `cmd.exe` (Windows) against a live status server, but it can't notice a CLI release that changes how
hooks are launched, so re-check by hand after upgrading either CLI.

IDEs: developed and run against IntelliJ IDEA Community. PyCharm should load it — the
plugin depends only on the platform and the terminal — but that is untested too.

## Dev

```bash
./gradlew runIde        # sandbox IDE with the plugin
./gradlew check         # tests
./gradlew verifyPlugin  # plugin structure + IntelliJ Plugin Verifier
./gradlew buildPlugin   # -> build/distributions/harness-aside-<version>.zip
./gradlew deployToIde   # build, swap into the running IntelliJ and restart it
```

Gradle job `deployToIde` closes the running `idea64` (never forcibly — a killed IDE reindexes the
whole project — so answer its exit prompts; it gives up after 5 minutes), replaces `%APPDATA%\JetBrains\<IDE>\plugins\harness-aside` and starts it
again. Use `-PideProcess=pycharm64` for PyCharm. The swap runs detached, so it survives the IDE
closing even when started from the IDE's own terminal; check `%TEMP%\harness-aside-deploy.log` if
the IDE doesn't come back.

From a WSL shell, point Gradle at a JDK: `JAVA_HOME=~/.jdks/openjdk-26.0.2 ./gradlew …`
(not needed when running from the IDE). The build toolchain is JDK 21; Gradle provisions it itself.
`gradle.properties` sets `kotlin.compiler.execution.strategy=in-process` to prevent Kotlin compiler daemon loopback timeouts under WSL.

CI (`.github/workflows/build.yml`) runs the same three tasks — `buildPlugin`, `check`, `verifyPlugin` —
and uploads the installable zip as a workflow artifact (per commit, 90-day expiry, login required).
It runs on every push to `main`, but on a pull request only when the PR gets the **`test ready`**
label; pushing more commits runs nothing, so remove the label and add it again to test the latest head.

## Releasing

`.github/workflows/release.yml` turns a tag into a public GitHub Release with the zip attached:

```bash
git tag v0.1.0 && git push origin v0.1.0
```

The tag sets the version (`-Pversion=0.1.0`), so `gradle.properties` only holds the dev default.
Each release carries `harness-aside-<version>.zip` plus a copy named `harness-aside.zip`, which is
what the stable "latest" download link above points at.

## License

This project is licensed under the [MIT License](LICENSE).
