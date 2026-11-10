// Installed by the Harness Aside IntelliJ plugin, which rewrites it on every OpenCode launch.
// Reports OpenCode's state to the IDE's status icon; outside a Harness Aside terminal it does nothing.
const port = process.env.HARNESS_ASIDE_IDE_PORT
const token = encodeURIComponent(process.env.HARNESS_ASIDE_IDE_TOKEN ?? "")

export const HarnessAsideStatus = async () => {
  if (!port) return {}

  // Fire and forget, but in order; a dead IDE must never slow OpenCode down.
  let queue = Promise.resolve()
  const send = (state) => {
    queue = queue
      .then(() => fetch(`http://127.0.0.1:${port}/status?state=${state}&token=${token}`, {
        method: "POST",
        signal: AbortSignal.timeout(2000),
      }))
      .catch(() => {})
  }

  // Subagents run in child sessions; the turn is over only when none of them is busy.
  const busy = new Set()
  // `session.idle` repeats `session.status`, so only the first report for a busy session is a finished turn.
  const idle = (sessionID) => {
    const wasBusy = busy.delete(sessionID)
    if (busy.size === 0) send(wasBusy ? "done" : "idle")
  }

  return {
    event: async ({ event }) => {
      const p = event.properties ?? {}
      switch (event.type) {
        case "session.status":
          if (p.status?.type === "idle") idle(p.sessionID)
          else {
            busy.add(p.sessionID)
            send("thinking")
          }
          break
        case "session.idle":
          idle(p.sessionID)
          break
        // `permission.updated` is the pre-v2 name of `permission.asked`.
        case "permission.asked":
        case "permission.updated":
        case "question.asked":
          send("waiting")
          break
        case "permission.replied":
        case "question.replied":
        case "question.rejected":
          send(busy.size ? "thinking" : "idle")
          break
      }
    },
    "tool.execute.before": async () => send("tool"),
    "tool.execute.after": async () => send("thinking"),
  }
}
