package com.github.baenar.harnessaside.core

import com.github.baenar.harnessaside.antigravity.AntigravityHookEvents
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Embedded HTTP server on the 127.0.0.1 loopback, on an ephemeral port. Receives status pushes from running
 * harnesses (Claude Code and Antigravity hooks, the OpenCode plugin) with zero CPU overhead while idle.
 *
 * Requests are handled on the server's own dispatcher thread: there is one client and handling one takes
 * next to nothing, so a separate executor would just be a thread to leak.
 */
@Service(Service.Level.PROJECT)
class HarnessStatusServer(private val project: Project?) : Disposable {

    constructor() : this(null)

    private var server: HttpServer? = null
    val token: String = UUID.randomUUID().toString()

    @Volatile
    var statusListener: ((HarnessStatus) -> Unit)? = null

    val port: Int
        get() = server?.address?.port ?: 0

    val isRunning: Boolean
        get() = server != null

    /** Starts the server unless it is already running, and returns its port. */
    @Synchronized
    fun ensureStarted(): Int {
        if (server == null) {
            try {
                server = HttpServer.create(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0).apply {
                    createContext("/status", ::handleStatus)
                    start()
                }
                LOG.info("HarnessStatusServer started on port $port")
            } catch (e: Exception) {
                LOG.error("Failed to start HarnessStatusServer", e)
            }
        }
        return port
    }

    @Synchronized
    override fun dispose() {
        server?.stop(0)
        server = null
    }

    private fun handleStatus(exchange: HttpExchange) {
        try {
            val query = parseQuery(exchange.requestURI.query)
            if ((query["token"] ?: exchange.requestHeaders.getFirst("X-Harness-Token")) != token) {
                sendResponse(exchange, 401, "Unauthorized\n")
                return
            }

            // `event=` carries a harness hook payload to classify (Windows hooks can't); `state=` is pre-classified.
            val event = query["event"]
            val state = if (event == null) query["state"] else {
                val body = String(exchange.requestBody.readNBytes(MAX_EVENT_BODY), StandardCharsets.UTF_8)
                AntigravityHookEvents.state(event, body) ?: run {
                    sendResponse(exchange, 400, "Unknown event\n")
                    return
                }
            }

            val status = HarnessStatus.fromId(state) ?: HarnessStatus.IDLE
            statusListener?.invoke(status)
            if (project != null) {
                ApplicationManager.getApplication().invokeLater(
                    { project.service<HarnessStatusManager>().onStatusChanged(status) },
                    project.disposed,
                )
            }

            sendResponse(exchange, 200, "OK\n")
        } catch (e: Exception) {
            LOG.warn("Error handling /status request", e)
            sendResponse(exchange, 500, "Error: ${e.message}\n")
        }
    }

    private fun sendResponse(exchange: HttpExchange, statusCode: Int, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        exchange.sendResponseHeaders(statusCode, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun parseQuery(query: String?): Map<String, String> =
        query.orEmpty().split("&").associate { it.substringBefore('=') to it.substringAfter('=', "") }

    private companion object {
        private val LOG = logger<HarnessStatusServer>()

        /** Cap on a hook payload; a truncated one fails to parse and falls back to the default state. */
        private const val MAX_EVENT_BODY = 1 shl 20
    }
}
