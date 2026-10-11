# harness-aside

[![Build](https://github.com/baenar/harness-aside/actions/workflows/build.yml/badge.svg)](https://github.com/baenar/harness-aside/actions/workflows/build.yml)

Private IntelliJ/PyCharm plugin: AI harnesses (Claude Code, Antigravity CLI, OpenCode) in a dedicated terminal tool window, docked on the right.
Almost entirely vibecoded - use at your own risk:))

## Install

[**Download the latest plugin zip**](https://github.com/baenar/harness-aside/releases/latest/download/harness-aside.zip)
— then <kbd>Settings</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk…</kbd>.

All versions live under [Releases](https://github.com/baenar/harness-aside/releases).

## Platform support

*Confirmed* means exercised by hand end to end: the tab opens and resumes its session, the
icon animates while Claude works, waiting-for-approval shows, and Ctrl+C returns it to the
still glyph.

| Platform | Status                                                                                                         |
|---|----------------------------------------------------------------------------------------------------------------|
| Windows | **Confirmed** — IDE and Claude both on the Windows side                                                        |
| WSL | **Confirmed** — IDE on Windows, Claude inside the distro, project under `/home`                                |
| Linux | **Untested** — no known blocker, and CI runs the test suite on Ubuntu, but the plugin has never been run there |
| macOS | **Unverified** — prolly safe to run, not tested                                                                |

IDEs: developed and run against IntelliJ IDEA Community. PyCharm should load it — the
plugin depends only on the platform and the terminal — but that is untested too.

## Dev

```bash
./gradlew runIde        # sandbox IDE with the plugin
./gradlew check         # tests
./gradlew verifyPlugin  # plugin structure + IntelliJ Plugin Verifier
./gradlew buildPlugin   # -> build/distributions/harness-aside-<version>.zip
```

From a WSL shell, point Gradle at a JDK: `JAVA_HOME=~/.jdks/openjdk-26.0.2 ./gradlew …`
(not needed when running from the IDE). The build toolchain is JDK 21; Gradle provisions it itself.
`gradle.properties` sets `kotlin.compiler.execution.strategy=in-process` to prevent Kotlin compiler daemon loopback timeouts under WSL.

CI (`.github/workflows/build.yml`) runs the same three tasks — `buildPlugin`, `check`, `verifyPlugin` —
and uploads the installable zip as a workflow artifact (per commit, 90-day expiry, login required).

## Releasing

`.github/workflows/release.yml` turns a tag into a public GitHub Release with the zip attached:

```bash
git tag v0.1.0 && git push origin v0.1.0
```

The tag sets the version (`-Pversion=0.1.0`), so `gradle.properties` only holds the dev default.
Each release carries `harness-aside-<version>.zip` plus a copy named `harness-aside.zip`, which is
what the stable "latest" download link above points at.
