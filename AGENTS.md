# Harness Aside — Project Architecture & Agent Guidelines

## 1. Vision & Goals

**Harness Aside** is an IntelliJ Platform plugin designed to integrate AI coding CLI harnesses into a dedicated, distraction-free environment.

* **Right-Docked Dedicated Workspace**: Embeds AI harnesses directly in a dedicated right-side tool window dock (alongside Gradle/Maven/Database), keeping the primary bottom terminal dock completely free for standard build commands, git workflows, and general shell operations.
* **Single-Session Discipline**: Enforces a strict one-active-tab invariant to eliminate tab clutter. When no harness session is active, a launcher panel is shown.
* **Frictionless Context Sharing**: Provides editor and project view actions to send current file paths and selections directly into the active harness terminal.
* **Minimal, Non-Intrusive Feedback**: Displays status through animated and static tool window icons (custom Manul pixel art) that convey harness activity without stealing focus or interrupting flow.

---

## 2. Current Functionalities

### Harness Launcher & Tab Lifecycle
* **Launcher Panel (`HarnessLauncherPanel`)**: An empty-state Swing UI displayed when no harness is active. Lists registered harnesses from `HarnessRegistry` with one-click launch buttons.
* **Single-Tab Management (`HarnessToolWindowUi`)**: Enforces that the tool window contains either the launcher panel or exactly one active interactive terminal session. Switching harnesses safely terminates the current process and returns to the launcher. The header shows plain text (`running <harness>`), not a tab.
* **Session Actions**: One title-bar action (circular arrow) ends the session and returns to the launcher.

### Claude Code Harness Integration
* **CLI Execution (`ClaudeHarness`)**: Launches or resumes Claude Code CLI sessions (`claude` or `claude -c`, depending on whether the project has a session to continue) inside the embedded JetBrains terminal widget.
* **Session Storage Discovery (`ClaudeSessions`)**: Automatically resolves Claude session paths and transcripts across Linux, native Windows, and WSL environments (translating Windows UNC paths such as `\\wsl$\` and `\\wsl.localhost\`).
* **Interrupt Detection (`ClaudeInterruptKeys`)**: Detects interrupt signals (e.g., Ctrl+C) to reset the status icon back to idle.

### Antigravity CLI Harness Integration
* **CLI Execution (`AntigravityHarness`)**: Launches Antigravity CLI sessions (`agy`) inside the dedicated terminal tool window using the Antigravity brand icon.
* **Push-Based Hook Configuration (`AntigravityStatusHooks`)**: Generates and merges lifecycle hooks into global `~/.gemini/config/hooks.json` (`SessionStart`, `PreInvocation`, `PreToolUse`, `PostToolUse`, `Stop`) with guarded curl status webhooks. The commands are written per target: POSIX `sh` syntax for Linux/macOS/WSL, and a quote-free `cmd.exe` variant (`curl.exe`, `%VAR%`, always `exit 0`) for native Windows, where `PreToolUse`/`PostToolUse` forward their stdin to `POST /status?event=` and the server classifies the tool/error (`AntigravityHookEvents`).
* **Interrupt Detection**: Wires terminal interrupt signals (Ctrl+C) via `HarnessStatusManager` to reset status icons back to idle.

### Push-Based Status Server (`HarnessStatusServer`)
* **Localhost Loopback Server**: An embedded HTTP server built on the standard JDK `com.sun.net.httpserver.HttpServer`, bound to `127.0.0.1` on an ephemeral port.
* **Environment Injection**: Injects `HARNESS_ASIDE_IDE_PORT` and an ephemeral `HARNESS_ASIDE_IDE_TOKEN` into the terminal shell environment at startup.
* **Automatic Hook Configuration (`ClaudeStatusHooks`)**: Merges lightweight HTTP webhook hooks into global `~/.claude/settings.json`, triggering on events (`SessionStart`, `UserPromptSubmit`, `PreToolUse`, `PostToolUse`, `Stop`, `Notification`, etc.).
* **Endpoint (`POST /status?state=...`)**: Receives state notifications and updates the IDE tool window header and stripe button on the Event Dispatch Thread (EDT).

### Status Visuals & Animations (`StatusIcons`)
* **States Supported**: `IDLE`, `THINKING`, `EXECUTING_TOOL`, and `WAITING`.
* **Adaptive Theme SVGs**: Separate color palettes for Light (`#6C707E`) and Dark (`#CED0D6`) IDE themes that integrate with IntelliJ's `SvgAttributePatcher`.
* **Animated Feedback**: Custom animated SVG pixel art frames for working and waiting states.

### Editor Context Sharing (`SendSelectionToHarnessAction`)
* **Context Menus**: Appears in the Editor context menu and Project View popup menu.
* **Payload Injection**: Inserts formatted file references and text selections into the active harness terminal.

---

## 3. Coding Practices & Architecture Rules (Zero Bloat Policy)

All code modifications must strictly adhere to the following principles:

### 1. Push-Based Event IPC (Zero CPU at Idle)
* **Never scrape terminal buffers**: Do not poll or read the terminal output stream or parse ANSI escape codes with regular expressions. Dynamic TUI rendering libraries (e.g., Ink, Bubbletea, Curses) continuously rewrite lines, making buffer scraping CPU-intensive, prone to regex deadlocks, and unstable.
* **Zero CPU overhead when idle**: Status reporting must always rely on push-based HTTP webhooks to the embedded loopback server. When the agent is waiting or idle, CPU consumption must remain at **0.00%**.
* **Fail-safe hook commands**: Hook invocations must be non-blocking and fail-safe (`>/dev/null 2>&1 || true`). A failure to connect to the IDE server must never cause the CLI harness process to fail.

### 2. Dependency & Resource Hygiene
* **No unnecessary dependencies**: Rely on the JetBrains Platform SDK and the standard JDK (`java.*`, `com.sun.net.httpserver.*`). Do not introduce heavyweight external networking, JSON, or reactive libraries.
* **Thread safety and EDT rules**: Any code updating Swing components, tool window titles, or icons must execute strictly on the Event Dispatch Thread using `ApplicationManager.getApplication().invokeLater`. Network listening and I/O must remain on background threads.

### 3. Strict Single-Tab Invariant
* Never allow multiple concurrent terminal tabs to accumulate in the tool window.
* The tool window must always hold exactly one content element: either the launcher panel or the active harness terminal.

### 4. Cross-Platform Path Handling
* Never assume a standard POSIX or Windows-only filesystem.
* When interacting with paths, always account for WSL environments, Windows drive letters, and UNC network paths (`\\wsl$\`, `\\wsl.localhost\`).
