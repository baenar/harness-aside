# claude-aside

Private IntelliJ/PyCharm plugin: Claude Code in its own terminal tool window, docked on the right.

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
and uploads the installable zip as a workflow artifact.

To install a zip manually: <kbd>Settings</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk…</kbd>.
