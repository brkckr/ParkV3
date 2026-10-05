package com.brkckr.parkv3.data.remote

import com.brkckr.parkv3.data.remote.parse.DetailParseResult
import com.brkckr.parkv3.data.remote.parse.ListParseResult
import com.brkckr.parkv3.domain.model.RefreshError
import com.brkckr.parkv3.testutil.Fixtures
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Exercises the real Retrofit/OkHttp stack against a local server. */
class IsparkRemoteDataSourceTest {

    private val server = MockWebServer()
    private lateinit var dataSource: IsparkRemoteDataSource

    @Before
    fun setUp() {
        server.start()
        val client = OkHttpClient.Builder().readTimeout(2, TimeUnit.SECONDS).build()
        dataSource = IsparkRemoteDataSource(IsparkApiFactory.create(server.url("/ispark/").toString(), client))
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun respond(code: Int, body: String, contentType: String = "application/json; charset=utf-8") =
        server.enqueue(MockResponse.Builder().code(code).setHeader("Content-Type", contentType).body(body).build())

    @Test
    fun `list response is fetched from the Park path and parsed`() = runBlocking<Unit> {
        respond(200, Fixtures.parkList)

        val result = dataSource.fetchParks()

        val parsed = (result as RemoteResult.Success).value as ListParseResult.Parsed
        assertThat(parsed.parks).hasSize(7)
        assertThat(server.takeRequest().url.encodedPath).isEqualTo("/ispark/Park")
    }

    @Test
    fun `detail request sends the park id as query parameter`() = runBlocking<Unit> {
        respond(200, Fixtures.parkDetail)

        val result = dataSource.fetchDetail(parkId = 101, fetchedAtMillis = 5L)

        assertThat((result as RemoteResult.Success).value).isInstanceOf(DetailParseResult.Parsed::class.java)
        val request = server.takeRequest()
        assertThat(request.url.encodedPath).isEqualTo("/ispark/ParkDetay")
        assertThat(request.url.queryParameter("id")).isEqualTo("101")
    }

    @Test
    fun `HTTP errors keep only the status code even with hostile bodies`() = runBlocking<Unit> {
        respond(503, "<html>%s %d %1\$s ${"x".repeat(10_000)}</html>", contentType = "text/html")

        assertThat(dataSource.fetchParks()).isEqualTo(RemoteResult.Failure(RefreshError.Http(503)))
    }

    @Test
    fun `a 200 response with an HTML page never yields park records`() = runBlocking<Unit> {
        // kotlinx reads an unquoted token as a JSON literal; the list parser then reports it as
        // not an array, which ListSyncPolicy rejects as Malformed.
        respond(200, "<html>bakımdayız</html>", contentType = "text/html")
        assertThat(dataSource.fetchParks()).isEqualTo(RemoteResult.Success(ListParseResult.NotAnArray))

        respond(200, "<html><body>Bakım çalışması</body></html>", contentType = "text/html")
        assertThat(dataSource.fetchDetail(1, 0L)).isEqualTo(RemoteResult.Failure(RefreshError.Malformed))
    }

    @Test
    fun `an empty 200 body is malformed`() = runBlocking<Unit> {
        respond(200, "")

        assertThat(dataSource.fetchParks()).isEqualTo(RemoteResult.Failure(RefreshError.Malformed))
    }

    @Test
    fun `a dropped connection is a network error`() = runBlocking<Unit> {
        server.enqueue(MockResponse.Builder().onRequestStart(SocketEffect.CloseSocket()).build())

        assertThat(dataSource.fetchParks()).isEqualTo(RemoteResult.Failure(RefreshError.Network))
    }

    @Test
    fun `an unreachable server is a network error`() = runBlocking<Unit> {
        server.close()

        assertThat(dataSource.fetchParks()).isEqualTo(RemoteResult.Failure(RefreshError.Network))
    }

    @Test
    fun `cancellation propagates instead of becoming a network error`() = runBlocking<Unit> {
        server.enqueue(MockResponse.Builder().headersDelay(10, TimeUnit.SECONDS).body("[]").build())

        val call = async(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) { dataSource.fetchParks() }
        withTimeout(5_000) { server.takeRequest() }
        call.cancel()

        val outcome = runCatching { call.await() }
        assertThat(outcome.exceptionOrNull()).isInstanceOf(CancellationException::class.java)
        assertThat(call.isCancelled).isTrue()
    }
}
