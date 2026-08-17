# claude-aside

Private IntelliJ/PyCharm plugin: Claude Code in its own terminal tool window, docked on the right.

## Install

[**Download the latest plugin zip**](https://github.com/baenar/claude-aside/releases/latest/download/claude-aside.zip)
— then <kbd>Settings</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk…</kbd>.

All versions live under [Releases](https://github.com/baenar/claude-aside/releases).

## Dev

```bash
./gradlew runIde        # sandbox IDE with the plugin
./gradlew check         # tests
./gradlew verifyPlugin  # plugin structure + IntelliJ Plugin Verifier
./gradlew buildPlugin   # -> build/distributions/claude-aside-<version>.zip
```

From a WSL shell, point Gradle at a JDK: `JAVA_HOME=~/.jdks/openjdk-26.0.2 ./gradlew …`
(not needed when running from the IDE). The build toolchain is JDK 21; Gradle provisions it itself.

CI (`.github/workflows/build.yml`) runs the same three tasks — `buildPlugin`, `check`, `verifyPlugin` —
and uploads the installable zip as a workflow artifact (per commit, 90-day expiry, login required).

## Releasing

`.github/workflows/release.yml` turns a tag into a public GitHub Release with the zip attached:

```bash
git tag v0.1.0 && git push origin v0.1.0
```

The tag sets the version (`-Pversion=0.1.0`), so `gradle.properties` only holds the dev default.
Each release carries `claude-aside-<version>.zip` plus a copy named `claude-aside.zip`, which is
what the stable "latest" download link above points at.
