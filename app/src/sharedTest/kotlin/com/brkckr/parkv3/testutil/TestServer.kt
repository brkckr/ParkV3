package com.brkckr.parkv3.testutil

import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Process-wide fake İSPARK server for Hilt UI tests. Responses are keyed by endpoint
 * ("Park", "ParkDetay"); queued responses are served first, then the fallback.
 */
object TestServer {
    private val queued = ConcurrentHashMap<String, ConcurrentLinkedQueue<MockResponse>>()
    private val fallback = ConcurrentHashMap<String, MockResponse>()

    private val server: MockWebServer by lazy {
        MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val endpoint = request.url.pathSegments.lastOrNull().orEmpty()
                    return queued[endpoint]?.poll() ?: fallback[endpoint] ?: MockResponse.Builder().code(404).build()
                }
            }
            start()
        }
    }

    val baseUrl: String get() = server.url("/ispark/").toString()

    fun reset() {
        queued.clear()
        fallback.clear()
    }

    fun enqueue(endpoint: String, vararg responses: MockResponse) {
        queued.getOrPut(endpoint) { ConcurrentLinkedQueue() }.addAll(responses)
    }

    fun always(endpoint: String, response: MockResponse) {
        fallback[endpoint] = response
    }

    fun json(body: String, code: Int = 200): MockResponse =
        MockResponse.Builder().code(code).setHeader("Content-Type", "application/json").body(body).build()

    fun disconnect(): MockResponse = MockResponse.Builder().onRequestStart(SocketEffect.CloseSocket()).build()
}
