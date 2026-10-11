package com.github.baenar.harnessaside

/**
 * Which Claude session the tool-window button follows, and what it is doing.
 *
 * Deliberately free of IDE types so the rules below can be tested directly:
 *
 * - the **first open tab** owns the button; other tabs run untracked
 * - when that tab closes, the **next-oldest** takes over
 * - an interrupt counts only once, and only when it is new since the tab opened
 *
 * Every method is synchronized: status arrives on watcher and poller threads while tabs
 * open and close on the EDT.
 */
internal class ClaudeStatusState {

    private val openSessions = LinkedHashSet<String>()
    private val statuses = HashMap<String, ClaudeStatus>()
    private val tabNames = HashMap<String, String>()

    /** Newest interrupt uuid already seen per session — the baseline for [observeInterrupt]. */
    private val seenInterrupts = HashMap<String, String?>()

    /** The session the button follows: the oldest tab still open. */
    @Synchronized
    fun primarySession(): String? = openSessions.firstOrNull()

    /** The state the button should show, or null when the primary tab has reported nothing. */
    @Synchronized
    fun visibleStatus(): ClaudeStatus? = openSessions.firstOrNull()?.let { statuses[it] }

    @Synchronized
    fun tabName(sessionId: String): String? = tabNames[sessionId]

    @Synchronized
    fun openTabCount(): Int = openSessions.size

    @Synchronized
    fun openTab(sessionId: String, tabName: String?) {
        openSessions.add(sessionId)
        tabName?.let { tabNames[sessionId] = it }
    }

    @Synchronized
    fun closeTab(sessionId: String) {
        openSessions.remove(sessionId)
        statuses.remove(sessionId)
        tabNames.remove(sessionId)
        seenInterrupts.remove(sessionId)
    }

    /**
     * Records [status] for [sessionId].
     *
     * @return true when this is a fresh [ClaudeStatus.WAITING] for the session the button
     *   follows — the one case worth a notification balloon.
     */
    @Synchronized
    fun setStatus(sessionId: String, status: ClaudeStatus): Boolean {
        val previous = statuses.put(sessionId, status)
        return status == ClaudeStatus.WAITING &&
            previous != ClaudeStatus.WAITING &&
            sessionId == openSessions.firstOrNull()
    }

    @Synchronized
    fun clearStatus(sessionId: String) {
        statuses.remove(sessionId)
    }

    /**
     * Feeds the newest interrupt uuid found in a session's transcript.
     *
     * The first call for a session only records a baseline and reports nothing: a resumed
     * transcript is usually full of old interrupts, and none of them mean "Claude just
     * stopped". Only a *change* afterwards counts.
     *
     * @return true when the caller should reset [sessionId] to [ClaudeStatus.IDLE].
     */
    /**
     * Accepts [uuid] as "already accounted for" without reporting it.
     *
     * Called whenever a session starts being watched, so that records left over from
     * earlier — including an interrupt the user has just been told about by other means —
     * cannot fire again the next time Claude gets busy.
     */
    @Synchronized
    fun baselineInterrupt(sessionId: String, uuid: String?) {
        seenInterrupts[sessionId] = uuid
    }

    @Synchronized
    fun observeInterrupt(sessionId: String, uuid: String?): Boolean {
        val hadBaseline = seenInterrupts.containsKey(sessionId)
        val previous = seenInterrupts.put(sessionId, uuid)
        return hadBaseline && uuid != null && uuid != previous
    }
}
