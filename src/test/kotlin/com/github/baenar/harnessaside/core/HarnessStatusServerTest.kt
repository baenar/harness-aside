package com.github.baenar.harnessaside.core

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.CopyOnWriteArrayList

class HarnessStatusServerTest {

    private lateinit var server: HarnessStatusServer
    private val client = HttpClient.newHttpClient()
    private val receivedStatuses = CopyOnWriteArrayList<HarnessStatus>()

    @Before
    fun setUp() {
        server = HarnessStatusServer()
        server.statusListener = { receivedStatuses.add(it) }
        server.start()
    }

    @After
    fun tearDown() {
        server.dispose()
    }

    @Test
    fun `server starts on ephemeral loopback port`() {
        assertTrue(server.isRunning)
        assertTrue(server.port > 0)
    }

    @Test
    fun `health endpoint returns 200 OK json`() {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:${server.port}/health"))
            .GET()
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, response.statusCode())
        assertTrue(response.body().contains("\"status\":\"ok\""))
    }

    @Test
    fun `status endpoint dispatches thinking state`() {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:${server.port}/status?state=thinking&token=${server.token}"))
            .POST(HttpRequest.BodyPublishers.noBody())
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, response.statusCode())
        assertEquals(listOf(HarnessStatus.THINKING), receivedStatuses)
    }

    @Test
    fun `status endpoint dispatches tool execution state`() {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:${server.port}/status?state=tool&token=${server.token}"))
            .POST(HttpRequest.BodyPublishers.noBody())
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, response.statusCode())
        assertEquals(listOf(HarnessStatus.EXECUTING_TOOL), receivedStatuses)
    }

    @Test
    fun `status endpoint accepts token in X-Harness-Token header`() {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:${server.port}/status?state=waiting"))
            .header("X-Harness-Token", server.token)
            .POST(HttpRequest.BodyPublishers.noBody())
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, response.statusCode())
        assertEquals(listOf(HarnessStatus.WAITING), receivedStatuses)
    }

    @Test
    fun `status endpoint rejects incorrect token`() {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:${server.port}/status?state=thinking&token=invalid-secret"))
            .POST(HttpRequest.BodyPublishers.noBody())
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        assertEquals(401, response.statusCode())
        assertTrue(receivedStatuses.isEmpty())
    }

    @Test
    fun `dispose stops the server`() {
        assertTrue(server.isRunning)
        server.dispose()
        assertFalse(server.isRunning)
    }
}
