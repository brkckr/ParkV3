package com.brkckr.parkv3.testutil

import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
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

    @Volatile
    private var startedBaseUrl: String? = null

    /**
     * Read by the Hilt module when the app first needs the API, which happens on the main
     * thread; the socket work therefore happens earlier, in [reset] on the test thread.
     */
    val baseUrl: String
        get() = checkNotNull(startedBaseUrl) { "Call TestServer.reset() in @Before, before launching the app" }

    /** Starts the server if needed (off the main thread) and clears all scripted responses. */
    fun reset() {
        if (startedBaseUrl == null) startedBaseUrl = server.url("/ispark/").toString()
        queued.clear()
        fallback.clear()
        FakeConnectivity.offline = false
    }

    fun enqueue(endpoint: String, vararg responses: MockResponse) {
        queued.getOrPut(endpoint) { ConcurrentLinkedQueue() }.addAll(responses)
    }

    fun always(endpoint: String, response: MockResponse) {
        fallback[endpoint] = response
    }

    fun json(body: String, code: Int = 200): MockResponse =
        MockResponse.Builder().code(code).setHeader("Content-Type", "application/json").body(body).build()
}
