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

Launch command on tool window open: **`claude -c`** through Phase 3 — since `-c` degrades
to a fresh session on its own, Phase 1 needs no session lookup and no path encoding.
Phase 3.1 replaces it with `-r <id>` / `--session-id <uuid>`: `-c` never reveals *which*
session it resumed, and per-tab status needs that. Path encoding is what maps a session to
its state and transcript files.

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

> On a native Windows shell the POSIX hook command is a no-op, so status stays on the
> default glyph there; targets WSL/macOS/Linux/Git-bash.

Manual testing then surfaced three problems, all fixed in Phase 3.1: one status file per
*project* meant two tabs stomped on the same dot; Ctrl+C left the indicator stuck on
`thinking` because `Stop` only fires on a normal end-of-turn; and denying a permission
prompt left it stuck on `waiting` because no hook fires on the *answer*.

### Phase 3.1 — Status correctness fixes

**More facts (verified against `claude` 2.1.229 and the hooks reference):**

| Fact | Detail |
|---|---|
| `claude --session-id <uuid>` | starts a session under an id the caller picks — removes all session-id guessing |
| Hook stdin payload | every event carries `session_id`, `cwd`, `transcript_path`, `hook_event_name` as JSON on stdin |
| `PermissionRequest` hook | fires exactly when a tool call needs a permission decision (a plain command hook can't alter the decision — exit 2 is not honoured there) |
| `Pre`/`PostToolUse` hooks | bracket each tool call; `PostToolUse` is what makes an *approved* tool return the tab to `thinking` |
| Interrupts fire **no** hook | Ctrl+C and Esc-denying a permission prompt are invisible to hooks — confirmed in the reference and by testing |
| …but they are recorded | Claude appends a `type:"user"` transcript entry reading `[Request interrupted by user]` (or `…for tool use]` for a denied tool) — verified in real `~/.claude/projects/*/*.jsonl` files on this machine |

- [x] Per-tab status: every tab is launched with a **known** session id — `--session-id
      <uuid>` for a new session, `-r <id>` for a resumed one — instead of bare `claude -c`,
      which picks a session without saying which. Hooks write
      `.claude/aside-status/<session-id>`, tabs carry their own dot icon, and the stripe
      button shows the most urgent state across tabs (waiting > thinking > idle)
- [x] Reset to idle on interrupt: the plugin tails each open tab's transcript and treats a
      trailing `[Request interrupted by user…]` record as "back to idle", applying each
      record once (keyed by its entry uuid). Rejected the alternatives — a stale-status
      timeout guesses wrong during long thinking, and `addTerminationCallback` only fires
      when the whole pty dies, not when a turn is cut short
- [x] Reset to idle when the user denies a permission prompt: same mechanism — a denial is
      recorded as `[Request interrupted by user for tool use]`. Added `PermissionRequest` →
      `waiting` and `Post`/`PreToolUse` → `thinking` so an *approved* tool also recovers,
      which the `Notification`→`Stop` pair alone never did
- [ ] Manual test: two tabs open side by side, each showing its own correct status;
      Ctrl+C during a tool call returns to idle; answering "no" to a permission prompt
      returns to idle

> Upgrading needs one click: **⚙ → Reinstall Status Indicator Hooks**. Install now writes a
> generated `.claude/aside-status.sh` (POSIX, `sed` not `jq`, so no extra dependency on
> macOS or Git-bash) and *replaces* entries from older plugin versions instead of appending
> to them. The script is exercised by a unit test that runs it through a real `sh`.

**Was a limitation, fixed in Phase 3.5:** the transcript tail is read from
`~/.claude/projects/<encoded>/`, which under WSL is neither the IDE's `$HOME` nor the
IDE's project path.

### Phase 3.2 — One animated icon instead of per-tab dots

Per-tab dots were a dead end. `Content.setIcon` alone paints nothing on a tool-window tab:
the platform needs the opt-in `ToolWindow.SHOW_CONTENT_ICON` (`Key<Boolean>`, verified in
`app-client.jar` of IC-2025.2.6). Even opting in, a single tab shows no tab strip at all,
so the common case was still one dot. Dropped the idea rather than fight it.

- [x] **One button, one state — the first open tab's.** Extra tabs still open and run, they
      just don't drive the icon; when the first tab closes the next-oldest takes over
- [x] **Three looks, no compositing.** No glyph-plus-dot layering; each state is its own
      artwork, all generated from the one glyph by `tools/generate-icons.sh`:
      | State | Look |
      |---|---|
      | idle / unknown | glyph centred, still |
      | thinking | glyph walks — slides off the right edge, re-enters from the left (painted twice a width apart so the button is never empty) |
      | waiting | glyph upside down, rocking side to side so its legs kick |
- [x] **Always white.** The glyph no longer uses Claude orange (`#D97757`), and the
      `_dark` variant is gone — one file, one colour, both themes
- [x] Manual test: walking loop is smooth and never blank; kicking reads as kicking;
      icon returns to still-and-centred when Claude finishes

> **Frames must be real icon files.** The first attempt kept one SVG and derived each frame
> at paint time from a custom `javax.swing.Icon` applying `Graphics2D` transforms. On a
> tool-window button that renders at the wrong size and throws — the icon visibly shrank to
> a frozen, clipped frame and the IDE raised an error balloon. `tools/generate-icons.sh`
> now bakes 12 walk + 8 kick frames into SVGs loaded through `IconLoader`, the same path as
> every icon that has ever worked here. `ClaudeStatusService` also stops the loop and falls
> back to the still glyph if a frame ever fails to paint, so a decoration can't keep
> throwing at the user.
>
> Frames are pushed with `ToolWindow.setIcon` on a ~14 fps Swing timer. The tidier-looking
> `com.intellij.ui.AnimatedIcon` was rejected: it repaints through the component it is
> painted on (unverified for stripe buttons) and is `@ApiStatus.Experimental`, whereas
> `setIcon` is already proven to repaint here.
>
> White-on-white is a real risk in light themes — revisit if the button ever disappears.

### Phase 3.3 — Interrupt detection actually fires, and a test suite

The Ctrl+C reset never worked, and the reason was an assumption, not a race: the code
asked "is the newest transcript line an interrupt?". Claude appends bookkeeping records
(`system`, `last-prompt`, `total_tokens_reminder`) within *milliseconds* of the interrupt,
so the interrupt is almost never last. Verified by timestamping real interrupts in this
project's own transcript — the record itself lands promptly, but is immediately buried.

- [x] Scan the whole read tail for the newest interrupt (`ClaudeSessions.lastInterruptUuid`)
      instead of testing only the final line
- [x] Baseline on tab open: the first read of a session records its existing interrupts
      without acting, so resuming a conversation full of old ones doesn't read as "Claude
      just stopped". Only a change afterwards resets the icon
- [x] **Poll** the transcript every 700 ms while the icon animates, instead of watching the
      directory. An interrupt is only possible while Claude is busy, so the cost is one
      tail read a second and only then; it also sidesteps the question of whether a
      directory watch reports appends to a file another process holds open
- [x] Test suite — 55 tests, no skips:
      | Suite | Covers |
      |---|---|
      | `ClaudeStatusStateTest` | first tab owns the button, promotion on close, per-session interrupt baselines, when a balloon is due |
      | `ClaudeSessionsTest` | path encoding, session lookup, interrupt parsing incl. the bug above |
      | `ClaudeStatusHooksTest` | settings.json merge, upgrade from old entries, foreign hooks preserved, plus the reporter script executed through a real `sh` |
      | `ClaudeStatusIconsTest` | frame naming and wrap-around, every frame file present, frames are white |
      | `ClaudeLaunchTest` | launch commands always name a session id |
      | `ClaudeStatusTest` | status words round-trip with what the script writes |

> Testability drove three small refactors: `ClaudeStatusHooks` takes a directory rather than
> a `Project`, the button's rules moved into a plain `ClaudeStatusState`, and launch
> commands became a `ClaudeTerminalService.Launch` value. What remains untested is the raw
> IDE wiring — creating terminal widgets, setting the tool-window icon — which needs a
> running IDE rather than a unit test.

### Phase 3.4 — The keystroke, not the transcript

Ctrl+C during a turn still left the icon walking. Measuring the transcripts settled why —
two independent faults, both found by counting real records rather than reasoning:

| Measured | Consequence |
|---|---|
| A test session where Ctrl+C was pressed ends with a normal `stop_hook_summary` and **no interrupt record at all**. Of five genuine interrupts in this project's main transcript, the recent ones are all `…for tool use` | Interrupting a *tool call* is recorded; interrupting plain thinking often is not. The transcript cannot be the only signal |
| Matching the bare phrase found **27** interrupts where **5** were real — the rest were the assistant's own tool calls grepping for it and test files containing it | The detector was firing on records that merely *mention* an interrupt |

**There is no interrupt hook — measured, not assumed.** Each interrupt's nearest following
`stop_hook_summary` is minutes later and belongs to the *next* turn (22:47:58 → 22:50:33;
23:20:21 → 23:24:35), so `Stop` does not fire when a turn is cut short. The documented
event list, down to `TeammateIdle` and `WorktreeCreate`, has nothing for interrupt or
cancel either. The keystroke is the only immediate evidence there is.

- [x] **Watch the keystroke.** The plugin owns the terminal, so Ctrl+C and Esc pressed in a
      Claude tab put it back to idle at once. No hook, no transcript, no polling delay.
      Optimistic by design: if Claude was in fact still working, its next
      `Pre`/`PostToolUse` hook puts the animation back within moments.
      Listens via `Toolkit.addAWTEventListener` rather than an `IdeEventQueue` dispatcher —
      the lowest level the key can be seen at, before the IDE routes it — and accepts the
      key either when it comes from inside the tab's component tree *or* while the Claude
      tool window is the active one, since the terminal can host its editor outside that
      tree
- [x] **Gear menu → Diagnose Status Indicator.** The indicator spans a shell script, a file
      watcher, a transcript and a key listener; when one goes quiet nothing is visible.
      The action reports tracked tabs, the first tab's session, the status on disk, whether
      hooks are installed, and when an interrupt key was last seen — turning "the icon is
      wrong" into a named broken link
- [x] **Anchor the marker to its field.** `"text":"[Request interrupted by user` — real
      records carry it unescaped, records *about* one nest the quotes a level deeper
      (`\"text\":\"…`). That single anchor is the difference between 27 matches and 5
- [x] **Re-baseline when a busy stretch starts**, so the record Claude writes for an
      interrupt the keyboard watcher already handled can't blank the icon during the *next*
      turn
- [x] 62 tests, no skips (`ClaudeInterruptKeysTest` plus regressions for both faults above)

> Esc doubles as "interrupt" and as the terminal's focus-editor key; treating it as an
> interrupt is deliberate, since denying a permission prompt is an Esc. The cost of a false
> positive is a briefly still icon, which the next hook corrects.

### Phase 3.5 — WSL projects

The Risks table had flagged this as Phase 3 work, and Phase 3 shipped without it. Under WSL
*two* things disagree, and translating one without the other still finds nothing:

| The IDE says | Claude, inside the distro, uses |
|---|---|
| project `//wsl$/Debian/home/me/proj` | `/home/me/proj`, encoded `-home-me-proj` |
| `$HOME` = `C:\Users\me` | the distro's `/home/me`, i.e. `//wsl$/Debian/home/me/.claude` |

- [x] `ClaudeSessions.storage()` returns the pair Claude would use, and `sessionsDir` runs
      everything through it — so `latestSessionId` and the transcript tail were both fixed
      by one change. Handles the `wsl$` and `wsl.localhost` spellings, either slash, any casing
- [x] Falls back to the IDE's own values when the home cannot be derived from the path
      (anything outside `/home/<user>` and `/root`). Guessing a home would be worse than
      today's "finds nothing"
- [x] 9 tests; 71 total, no skips
- [x] Confirmed against the real thing rather than reasoned about: `pwd` in a WSL tab reads
      `/home/mbejnar/thesis`, and `\\wsl$\Debian\home\mbejnar\.claude\projects\`
      holds `-home-mbejnar-thesis` with 5 transcripts — exactly the path this builds

> What was *not* broken under WSL: the hook-driven states. `.claude/aside-status/` lives
> inside the project, so it is reached over the UNC path like any other file. Only the two
> features reading `~/.claude/projects` — resume-on-open and interrupt detection — were
> silently dead.

### Phase 4 — MCP & polish
- [ ] Auto-configure JetBrains MCP server for Claude
- [ ] Focus shortcuts, settings page (claude binary path, default flags)

Each phase ships independently; stop whenever it's good enough.

---

## Risks & open questions

| Risk | Mitigation |
|---|---|
| Terminal APIs churn (Block/gen2 terminal rework ongoing; `AbstractTerminalRunner` may deprecate) | narrow in practice: `startShellTerminalWidget` is the *only* non-deprecated widget-creation path left on that class, i.e. the one JetBrains migrated to itself. Pin `sinceBuild=252`; revisit on IDE upgrade |
| WSL: IDE-visible project path (`\\wsl$\...`) vs Linux path used in `~/.claude/projects` encoding | **done in Phase 3.5** — `ClaudeSessions.storage` translates both the path and the home; a no-op if the IDE runs inside WSL. Residual: a distro project outside `/home/<user>` or `/root` still cannot be located |
| `claude` not on PATH of the shell the IDE spawns | Phase 1: rely on shell rc files (login shell); Phase 4: settings field for binary path |
| Hook-based state detection depends on Claude Code hook config format staying stable | keep the hook payload trivial (touch a file); feature degrades gracefully to "no status icons" |
| Official Claude Code JetBrains plugin may add its own tool window later | ours is still the only one giving *terminal-in-right-dock*; re-evaluate overlap when it happens |

---

## References

- [Tool Windows — IntelliJ Platform SDK](https://plugins.jetbrains.com/docs/intellij/tool-windows.html)
- [Claude Code CLI reference](https://docs.anthropic.com/en/docs/claude-code/cli-reference) (`-c`, `-r`, hooks)
- [Claude Code hooks](https://docs.anthropic.com/en/docs/claude-code/hooks)
- Terminal plugin sources: [intellij-community/plugins/terminal](https://github.com/JetBrains/intellij-community/tree/master/plugins/terminal)
