# Claude Aside — Development Plan

## Problem

Claude Code runs in a regular Terminal tool window tab. Mixed with compile/`gh`/misc
tabs it becomes hard to navigate, especially with a narrow terminal panel.

## Solution

Separate the two: regular terminals stay at the bottom (default), Claude Code gets its
own tool window docked **right** (next to Gradle/Database). One click on the stripe
button opens a terminal already running Claude with the previous session resumed.

---

## Facts the design relies on (verified)

### Claude Code CLI & session storage

Verified against `claude` 2.1.228 on this machine.

| Fact | Detail |
|---|---|
| Session files | `~/.claude/projects/<encoded-path>/*.jsonl`, one file per session (the dir may also hold non-session entries such as `memory/` — filter on `*.jsonl`, not "dir is non-empty") |
| Path encoding | every non-alphanumeric char becomes `-`, including `/`, `.` **and `_`**: `/home/x/dot.test_dir` → `-home-x-dot-test-dir` |
| `claude` | starts a fresh session in cwd |
| `claude -c` / `--continue` | resumes the most recent session for cwd; **falls back to a fresh session when there is none** — verified in an empty dir both with `-p` and interactively under a pty |
| `claude -r` / `--resume` | resumes by session id, or opens the built-in interactive picker |
| `claude --fork-session` | on resume, branches into a new session id |

Launch command on tool window open: **always `claude -c`**. Since `-c` degrades to a
fresh session on its own, the plugin needs no session lookup and no path encoding in
Phase 1. Path encoding is only needed in Phase 3, to map a session to its state file.

> Note: the first launch in a directory the user has not opened before shows Claude's
> "Is this a project you trust?" prompt instead of the session. Expected, not a bug —
> but it will show up on every fresh `runIde` sandbox.

### IntelliJ Platform APIs (verified with `javap` against IC 2025.2.6 jars)

| API | Use |
|---|---|
| `toolWindow` extension point, `anchor="right"` | registers the stripe button + panel |
| `LocalTerminalDirectRunner.createTerminalRunner(project)` | terminal runner honoring the IDE shell settings (incl. WSL shells) |
| `runner.startShellTerminalWidget(disposable, ShellStartupOptions, deferUntilShown)` → `com.intellij.terminal.ui.TerminalWidget` | creates an embeddable terminal session; **not** `@Deprecated`, **not** `@ApiStatus.Internal` — unlike the whole older `createTerminalWidget` family on the same class |
| `ShellStartupOptions.Builder().workingDirectory(path).build()` | start in project root |
| `TerminalWidget.sendCommandToExecute("claude -c")` | inject the command into the shell (survives Claude exit — user lands back in shell) |
| `TerminalWidget.getComponent()` | Swing component to put into a `Content` |
| `ToolWindow.setIcon(...)`, `ContentManager` | dynamic status icon, multiple tabs |

Why `sendCommandToExecute` instead of `ShellStartupOptions.shellCommand(["claude","-c"])`:
with `shellCommand` the terminal dies when Claude exits; injecting into the default
shell keeps the tab usable and works identically for local and WSL shells. Trade-off:
the command lands in shell history — acceptable.

---

## Architecture

```mermaid
graph LR
    subgraph plugin [claude-aside plugin]
        F[ClaudeToolWindowFactory<br/>creates content on first open]
        S[ClaudeTerminalService<br/>@Service Level.PROJECT<br/>owns TerminalWidgets, spawns tabs]
    end
    TW[Tool window right<br/>stripe button Claude Aside] --> F
    F --> S
    S --> R[LocalTerminalDirectRunner<br/>terminal plugin API]
```

- **`ClaudeToolWindowFactory`** — registered in `plugin.xml`; on first open asks the
  service for a terminal tab. Nothing runs until the user clicks the button
  (no `postStartupActivity`).
- **`ClaudeTerminalService`** (project service) — creates `TerminalWidget`s with
  `sendCommandToExecute("claude -c")`, registers them as `Content`s, disposes on
  project close. Single place that future features (tabs, states) extend.

That is the whole MVP: two files, no session logic (see the `-c` fallback above).

Later phases add: `ClaudeSessions` (path encoding + session lookup, needed only to
locate hook state files), `ClaudeStateWatcher` and title-bar actions — all hanging off
the same service.

---

## Feasibility of requested features

| # | Feature | Feasibility | Notes |
|---|---|---|---|
| 0 | Right-docked terminal auto-running Claude with resume | **Easy** | everything verified above |
| 1 | Multiple Claude terminals as tabs | **Easy** | `ContentManager.addContent` per widget + a `+` title action |
| 2 | Dropdown of open Claude sessions | **Easy–Medium** | multiple contents already give the built-in tab list; a custom dropdown = `ToolWindow.setTitleActions` with a popup action group |
| 3 | Status icons (thinking / finished / awaiting approval) | **Medium–Hard** | cheap 80% first: `TerminalWidget.isCommandRunning()` + `addTerminationCallback()` give running/idle for free, no hooks. Full states need more — the terminal is a dumb pty, state is not observable from output reliably. Robust path: **Claude Code hooks** (`Stop`, `Notification`, `PreToolUse`) writing a tiny state file per session; plugin watches it (NIO `WatchService` or VFS listener) and swaps the tool window icon (`ToolWindow.setIcon`) / tab icons. Requires the plugin to install hook entries into `.claude/settings.json` (ask first). Parsing terminal output = fragile, rejected. |
| 4 | JetBrains MCP | **Medium** | 2025.2+ ships a built-in MCP server (Settings → Tools → MCP Server). Plugin's job is only config glue: detect the server port, run `claude mcp add` (or write `.mcp.json`) so Claude gets IDE tools. Also note: the official *Claude Code [Beta]* JetBrains plugin provides `/ide` integration (diffs, selection context) and coexists with ours — ours owns *where the terminal lives*, theirs owns editor integration. Verify interop early. |

### Additional ideas (5)

- **"New session" / "Resume…" title actions** — `+` runs `claude`, clock icon runs `claude -r`.
- **Send selection to Claude** — editor context menu action that pastes
  `@file#Lx-Ly`-style reference (or the selected text) into the active Claude terminal
  via `sendCommandToExecute` without newline. Cheap, high value.
- **Notification balloon when Claude awaits approval while the tool window is hidden**
  (builds on the same hook/state mechanism as #3).
- **Focus shortcut** — an action + editable keybinding to jump editor ↔ Claude terminal.
- **Per-worktree working dir** — if the project has git worktrees, let a new tab pick one.

---

## Development phases

### Phase 1 — MVP (the whole original problem)
- [x] Tool window `Claude Aside`, `anchor="right"`, 13×13 SVG icon (mono, per IJ guidelines)
- [x] `ClaudeTerminalService`: one terminal tab, working dir = project root, `sendCommandToExecute("claude -c")`
- [x] Manual test in `runIde` sandbox + real install into PyCharm/IntelliJ (WSL shell)

No unit tests in this phase — after dropping the session lookup there is no pure logic
left to test; everything is IDE wiring that only a manual run exercises.

**Done when:** one click → Claude with the last session, bottom terminals untouched.

### Phase 2 — Tabs & actions
- [x] `+` (new session), `Resume…` (bare `claude -r` — its built-in picker beats
      re-implementing a session list in Swing), restart-tab action
- [x] Multiple contents with sensible tab titles; dispose kills the pty
- [x] Send-selection-to-Claude editor action

### Phase 3 — Status
- [x] `ClaudeSessions`: path encoding (non-alphanumeric → `-`) + `*.jsonl` lookup
      (unit-tested — the only pure logic in the plugin)
- [x] Hook installer (writes `UserPromptSubmit`/`Notification`/`Stop` entries into project
      `.claude/settings.json`, with user consent). Uses `UserPromptSubmit`→thinking rather
      than `PreToolUse` — same "busy" signal, no tool-name matcher needed. Command is a
      portable POSIX write to `$CLAUDE_PROJECT_DIR/.claude/aside-status`.
- [x] State file watcher (NIO `WatchService`) → tool-window icon variants + tab icons
      (coloured circles: amber=thinking, orange=waiting, green=idle)
- [x] Balloon on "awaiting approval" when window hidden

> Status is **project-level** for now: one indicator for the whole tool window, driven by
> a single `.claude/aside-status` file. Per-tab status needs tab→session-id resolution
> (the `claude -c` launch doesn't tell the plugin which session it picked) — deferred.
> On a native Windows shell the POSIX hook command is a no-op, so status stays on the
> default glyph there; targets WSL/macOS/Linux/Git-bash.

**Known limitations (found in manual testing, not yet fixed):**
- **Only correct with a single open tab.** One status file / one dot means two Claude
  tabs stomp on the same indicator — whichever hook fired last wins, with no way to tell
  which tab it refers to. Same root cause as the per-tab deferral above; a real fix needs
  the tab→session mapping.
- **Doesn't reset on Ctrl+C.** The `Stop` hook only fires on Claude's normal end-of-turn;
  killing the running turn with Ctrl+C bypasses it, so the indicator sticks on
  `thinking`/`waiting` even though Claude is idle again. No hook exists for "interrupted."
- **Doesn't reset when the user answers "no" to a permission prompt.** `Notification`
  fires when Claude asks for approval (→ `waiting`), but there's no hook for the *answer*
  — a denial leaves the indicator stuck on `waiting` instead of returning to `idle`/`thinking`.

Needs a real fix before Phase 3 is "done": candidates are a `PreToolUse`/`PostToolUse`
pair to bracket tool calls more precisely, and/or a stale-status timeout in the watcher
(e.g. treat a `waiting`/`thinking` status older than N seconds with no active pty as idle).

### Phase 3.1 — Status correctness fixes
- [ ] Per-tab status: resolve each tab to its session id (`ClaudeSessions.latestSessionId`
      at launch time, or parse the session id Claude prints on the `claude`/`claude -c`
      startup banner) and key the state file / indicator by session instead of by project,
      so multiple open tabs no longer share one dot
- [ ] Reset to idle on interrupt: detect Ctrl+C — either via
      `TerminalWidget.addTerminationCallback` firing without a preceding `Stop`-hook write,
      or a `PreToolUse`/`PostToolUse` hook pair bracketing tool calls narrowly enough that
      an interrupt can't leave the indicator stuck mid-tool
- [ ] Reset to idle when the user denies a permission prompt: no hook currently fires on
      the *answer* to a `Notification`; needs either a stale-status timeout in the watcher
      (treat `waiting`/`thinking` older than N seconds with no active pty as idle) or a
      hook-format change once Claude Code exposes one
- [ ] Manual test: two tabs open side by side, each showing its own correct status;
      Ctrl+C during a tool call returns to idle; answering "no" to a permission prompt
      returns to idle

### Phase 4 — MCP & polish
- [ ] Auto-configure JetBrains MCP server for Claude
- [ ] Focus shortcuts, settings page (claude binary path, default flags)

Each phase ships independently; stop whenever it's good enough.

---

## Risks & open questions

| Risk | Mitigation |
|---|---|
| Terminal APIs churn (Block/gen2 terminal rework ongoing; `AbstractTerminalRunner` may deprecate) | narrow in practice: `startShellTerminalWidget` is the *only* non-deprecated widget-creation path left on that class, i.e. the one JetBrains migrated to itself. Pin `sinceBuild=252`; revisit on IDE upgrade |
| WSL: IDE-visible project path (`\\wsl$\...`) vs Linux path used in `~/.claude/projects` encoding | **not a Phase 1 problem** — nothing reads the session dir until Phase 3. `ClaudeSessions` must then normalize UNC → Linux path; a no-op if the IDE runs inside WSL |
| `claude` not on PATH of the shell the IDE spawns | Phase 1: rely on shell rc files (login shell); Phase 4: settings field for binary path |
| Hook-based state detection depends on Claude Code hook config format staying stable | keep the hook payload trivial (touch a file); feature degrades gracefully to "no status icons" |
| Official Claude Code JetBrains plugin may add its own tool window later | ours is still the only one giving *terminal-in-right-dock*; re-evaluate overlap when it happens |

---

## References

- [Tool Windows — IntelliJ Platform SDK](https://plugins.jetbrains.com/docs/intellij/tool-windows.html)
- [Claude Code CLI reference](https://docs.anthropic.com/en/docs/claude-code/cli-reference) (`-c`, `-r`, hooks)
- [Claude Code hooks](https://docs.anthropic.com/en/docs/claude-code/hooks)
- Terminal plugin sources: [intellij-community/plugins/terminal](https://github.com/JetBrains/intellij-community/tree/master/plugins/terminal)
