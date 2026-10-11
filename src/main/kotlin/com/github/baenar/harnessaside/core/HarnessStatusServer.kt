package com.github.baenar.harnessaside.core

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Embedded lightweight HTTP micro-server running on 127.0.0.1 loopback on an ephemeral port.
 *
 * Receives status push events from running AI harnesses (Claude Code hooks, Antigravity hooks,
 * OpenCode plugin fetch requests) with zero CPU overhead while idle.
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

    @Synchronized
    fun ensureStarted(): Int {
        if (server == null) {
            start()
        }
        return port
    }

    @Synchronized
    fun start() {
        if (server != null) return
        try {
            val address = InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0)
            val http = HttpServer.create(address, 0)
            http.createContext("/status", StatusHandler())
            http.createContext("/health", HealthHandler())
            val executor = Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "harness-aside-http").apply { isDaemon = true }
            }
            http.executor = executor
            http.start()
            server = http
            LOG.info("HarnessStatusServer started on port ${http.address.port}")
        } catch (e: Exception) {
            LOG.error("Failed to start HarnessStatusServer", e)
        }
    }

    override fun dispose() {
        synchronized(this) {
            try {
                server?.stop(0)
            } catch (e: Exception) {
                LOG.warn("Error stopping HarnessStatusServer", e)
            } finally {
                server = null
            }
        }
    }

    private inner class StatusHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            try {
                val query = parseQuery(exchange.requestURI.query)
                val stateParam = query["state"]
                val tokenParam = query["token"] ?: exchange.requestHeaders.getFirst("X-Harness-Token")

                if (tokenParam != null && tokenParam.isNotEmpty() && tokenParam != token) {
                    sendResponse(exchange, 401, "Unauthorized\n")
                    return
                }

                val status = when (stateParam?.lowercase()?.trim()) {
                    "thinking" -> HarnessStatus.THINKING
                    "tool" -> HarnessStatus.EXECUTING_TOOL
                    "waiting" -> HarnessStatus.WAITING
                    "idle", "end", "stop" -> HarnessStatus.IDLE
                    "error" -> HarnessStatus.ERROR
                    else -> HarnessStatus.fromId(stateParam.orEmpty()) ?: HarnessStatus.IDLE
                }

                statusListener?.invoke(status)

                val app = ApplicationManager.getApplication()
                if (app != null && project != null) {
                    app.invokeLater {
                        if (!project.isDisposed) {
                            project.service<HarnessStatusManager>().onStatusChanged(status)
                        }
                    }
                }

                sendResponse(exchange, 200, "OK\n")
            } catch (e: Exception) {
                LOG.warn("Error handling /status request", e)
                sendResponse(exchange, 500, "Error: ${e.message}\n")
            }
        }
    }

    private inner class HealthHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            exchange.responseHeaders.set("Content-Type", "application/json")
            sendResponse(exchange, 200, "{\"status\":\"ok\"}\n")
        }
    }

    private fun sendResponse(exchange: HttpExchange, statusCode: Int, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        exchange.sendResponseHeaders(statusCode, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun parseQuery(query: String?): Map<String, String> {
        if (query.isNullOrEmpty()) return emptyMap()
        return query.split("&").mapNotNull { param ->
            val parts = param.split("=", limit = 2)
            if (parts.isNotEmpty()) {
                val key = parts[0]
                val value = if (parts.size > 1) parts[1] else ""
                key to value
            } else null
        }.toMap()
    }

    companion object {
        private val LOG = logger<HarnessStatusServer>()

        fun getInstance(project: Project): HarnessStatusServer = project.service()
    }
}
