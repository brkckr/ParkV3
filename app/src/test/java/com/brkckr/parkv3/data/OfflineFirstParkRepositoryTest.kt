package com.brkckr.parkv3.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.brkckr.parkv3.data.local.ParkDatabase
import com.brkckr.parkv3.data.remote.IsparkApiFactory
import com.brkckr.parkv3.data.remote.IsparkRemoteDataSource
import com.brkckr.parkv3.domain.model.FreshnessPolicy
import com.brkckr.parkv3.domain.model.OrphanFavorite
import com.brkckr.parkv3.domain.model.RefreshError
import com.brkckr.parkv3.domain.model.RefreshResult
import com.brkckr.parkv3.domain.model.SyncInfo
import com.brkckr.parkv3.testutil.Fixtures
import com.brkckr.parkv3.testutil.MutableClock
import com.brkckr.parkv3.testutil.parkListJson
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/** Real repository + in-memory Room + real Retrofit/OkHttp against a local server. */
@RunWith(AndroidJUnit4::class)
class OfflineFirstParkRepositoryTest {

    private val server = MockWebServer()
    private val clock = MutableClock(now = 1_800_000_000_000L)
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var database: ParkDatabase
    private lateinit var repository: OfflineFirstParkRepository

    @Before
    fun setUp() {
        server.start()
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ParkDatabase::class.java).build()
        val api = IsparkApiFactory.create(server.url("/ispark/").toString(), OkHttpClient())
        repository = OfflineFirstParkRepository(IsparkRemoteDataSource(api), database.parkDao(), clock, appScope)
    }

    @After
    fun tearDown() {
        appScope.cancel()
        database.close()
        server.close()
    }

    private fun respond(code: Int = 200, body: String, delayMillis: Long = 0) = server.enqueue(
        MockResponse.Builder().code(code).body(body).headersDelay(delayMillis, TimeUnit.MILLISECONDS).build(),
    )

    private suspend fun parkIds() = repository.observeParks().first().map { it.id }.toSet()
    private suspend fun syncInfo() = repository.observeSyncInfo().first()
    private suspend fun awaitRequest() = withContext(Dispatchers.IO) { server.takeRequest(5, TimeUnit.SECONDS) }

    @Test
    fun `without cache a network failure leaves no content and a retry succeeds`() = runBlocking<Unit> {
        respond(code = 503, body = "<html>down</html>")

        assertThat(repository.refreshParks()).isEqualTo(RefreshResult.Failure(RefreshError.Http(503)))
        assertThat(parkIds()).isEmpty()
        assertThat(syncInfo()).isEqualTo(SyncInfo(lastSuccessAtMillis = null, lastAttemptAtMillis = clock.now, lastError = RefreshError.Http(503)))

        clock.advanceBy(10_000)
        respond(body = Fixtures.parkList)

        assertThat(repository.refreshParks()).isEqualTo(RefreshResult.Success())
        assertThat(parkIds()).containsExactly(101, 102, 103, 104, 105, 106, 107)
        assertThat(syncInfo()).isEqualTo(SyncInfo(lastSuccessAtMillis = clock.now, lastAttemptAtMillis = clock.now, lastError = null))
    }

    @Test
    fun `with cache a failed refresh keeps content and the last success time and marks it stale`() = runBlocking<Unit> {
        respond(body = Fixtures.parkList)
        repository.refreshParks()
        val successAt = clock.now

        clock.advanceBy(60_000)
        server.close() // connection refused

        assertThat(repository.refreshParks()).isEqualTo(RefreshResult.Failure(RefreshError.Network))
        assertThat(parkIds()).hasSize(7)
        val info = syncInfo()
        assertThat(info.lastSuccessAtMillis).isEqualTo(successAt)
        assertThat(info.lastAttemptAtMillis).isEqualTo(clock.now)
        assertThat(info.lastError).isEqualTo(RefreshError.Network)
        assertThat(FreshnessPolicy.isListStale(info, clock.now)).isTrue()
    }

    @Test
    fun `favorites changed while a refresh is in flight are kept`() = runBlocking<Unit> {
        respond(body = Fixtures.parkList)
        repository.refreshParks()
        repository.setFavorite(103, true)

        respond(body = Fixtures.parkList, delayMillis = 500)
        val refresh = async { repository.refreshParks() }
        awaitRequest()
        repository.setFavorite(101, true)
        repository.setFavorite(103, false)

        assertThat(refresh.await()).isEqualTo(RefreshResult.Success())
        assertThat(repository.observeFavoriteIds().first()).containsExactly(101)
    }

    @Test
    fun `an empty or malformed response never clears the cache`() = runBlocking<Unit> {
        respond(body = Fixtures.parkList)
        repository.refreshParks()

        respond(body = "[]")
        assertThat(repository.refreshParks()).isEqualTo(RefreshResult.Failure(RefreshError.EmptyResponse))
        respond(body = """{"error": "x"}""")
        assertThat(repository.refreshParks()).isEqualTo(RefreshResult.Failure(RefreshError.Malformed))
        respond(body = "<html>bakım</html>")
        assertThat(repository.refreshParks()).isEqualTo(RefreshResult.Failure(RefreshError.Malformed))

        assertThat(parkIds()).hasSize(7)
    }

    @Test
    fun `a complete response hides vanished parks and keeps their favorites as orphans`() = runBlocking<Unit> {
        respond(body = Fixtures.parkList)
        repository.refreshParks()
        repository.setFavorite(103, true)

        respond(body = parkListJson(listOf(101, 102, 104, 105, 106, 107)))
        repository.refreshParks()

        assertThat(parkIds()).doesNotContain(103)
        assertThat(repository.observeFavoriteIds().first()).containsExactly(103)
        assertThat(repository.observeOrphanFavorites().first())
            .containsExactly(OrphanFavorite(103, "Şişli Merkez Katlı", "ŞİŞLİ"))
        assertThat(repository.observePark(103).first()).isNotNull()

        respond(body = parkListJson(listOf(101, 103)))
        repository.refreshParks()

        assertThat(parkIds()).containsExactly(101, 103)
        assertThat(repository.observeOrphanFavorites().first()).isEmpty()
    }

    @Test
    fun `a sharp shrink keeps records that are missing from the response`() = runBlocking<Unit> {
        respond(body = parkListJson(1..30))
        repository.refreshParks()

        respond(body = parkListJson(1..10))

        assertThat(repository.refreshParks()).isEqualTo(RefreshResult.Success(partial = true))
        assertThat(parkIds()).hasSize(30)
    }

    @Test
    fun `parks missing for more than 30 days are purged with their details but favorites remain`() = runBlocking<Unit> {
        respond(body = parkListJson(listOf(1, 2, 3)))
        repository.refreshParks()
        repository.setFavorite(3, true)
        respond(body = """[{"parkID": 3, "parkName": "Otopark 3", "capacity": 10, "emptyCapacity": 1}]""")
        repository.refreshDetail(3)

        respond(body = parkListJson(listOf(1, 2)))
        repository.refreshParks()
        assertThat(repository.observeDetail(3).first()).isNotNull()

        clock.advanceBy(OfflineFirstParkRepository.PURGE_MISSING_AFTER_MS + 1)
        respond(body = parkListJson(listOf(1, 2)))
        repository.refreshParks()

        assertThat(repository.observePark(3).first()).isNull()
        assertThat(repository.observeDetail(3).first()).isNull()
        assertThat(repository.observeFavoriteIds().first()).containsExactly(3)
        assertThat(repository.observeOrphanFavorites().first().single().name).isEqualTo("Otopark 3")
    }

    @Test
    fun `concurrent refreshes share a single request`() = runBlocking<Unit> {
        respond(body = Fixtures.parkList, delayMillis = 300)

        val results = (1..3).map { async { repository.refreshParks() } }.awaitAll()

        assertThat(results).containsExactly(RefreshResult.Success(), RefreshResult.Success(), RefreshResult.Success())
        assertThat(server.requestCount).isEqualTo(1)
        assertThat(repository.isRefreshingList.value).isFalse()
    }

    @Test
    fun `cancelling one waiting caller neither aborts the shared refresh nor records an error`() = runBlocking<Unit> {
        respond(body = Fixtures.parkList, delayMillis = 300)

        val first = async { repository.refreshParks() }
        val second = async { repository.refreshParks() }
        awaitRequest()
        first.cancel()

        assertThat(second.await()).isEqualTo(RefreshResult.Success())
        assertThat(syncInfo().lastError).isNull()
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun `cancellation of the refresh itself is not recorded as a network error`() = runBlocking<Unit> {
        respond(body = Fixtures.parkList, delayMillis = 2_000)

        val refresh = async { runCatching { repository.refreshParks() } }
        awaitRequest()
        appScope.cancel()

        assertThat(refresh.await().exceptionOrNull()).isInstanceOf(CancellationException::class.java)
        assertThat(syncInfo()).isEqualTo(SyncInfo())
        assertThat(parkIds()).isEmpty()
    }

    @Test
    fun `refresh if older than skips fresh data`() = runBlocking<Unit> {
        respond(body = Fixtures.parkList)
        repository.refreshParks()

        clock.advanceBy(60_000)
        assertThat(repository.refreshParksIfOlderThan(FreshnessPolicy.LIST_AUTO_REFRESH_AFTER_MS)).isNull()
        assertThat(server.requestCount).isEqualTo(1)

        clock.advanceBy(FreshnessPolicy.LIST_AUTO_REFRESH_AFTER_MS)
        respond(body = Fixtures.parkList)
        assertThat(repository.refreshParksIfOlderThan(FreshnessPolicy.LIST_AUTO_REFRESH_AFTER_MS)).isEqualTo(RefreshResult.Success())
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test
    fun `detail first failure then retry succeeds`() = runBlocking<Unit> {
        respond(code = 500, body = "error")

        assertThat(repository.refreshDetail(101)).isEqualTo(RefreshResult.Failure(RefreshError.Http(500)))
        assertThat(repository.observeDetail(101).first()).isNull()

        respond(body = Fixtures.parkDetail)

        assertThat(repository.refreshDetail(101)).isEqualTo(RefreshResult.Success())
        val detail = repository.observeDetail(101).first()!!
        assertThat(detail.address).isEqualTo("Rıhtım Cad. No:1 Kadıköy/İstanbul")
        assertThat(detail.fetchedAtMillis).isEqualTo(clock.now)
        assertThat(detail.tariffLines).hasSize(4)
        assertThat(repository.observeRefreshingDetails().first()).isEmpty()
    }

    @Test
    fun `placeholder detail for an unknown id is never cached`() = runBlocking<Unit> {
        respond(body = Fixtures.parkDetailUnknownId)

        assertThat(repository.refreshDetail(999)).isEqualTo(RefreshResult.Failure(RefreshError.NotFound))
        assertThat(repository.observeDetail(999).first()).isNull()
    }

    @Test
    fun `a failed detail refresh keeps the cached detail`() = runBlocking<Unit> {
        respond(body = Fixtures.parkDetail)
        repository.refreshDetail(101)
        val cached = repository.observeDetail(101).first()

        clock.advanceBy(60_000)
        respond(code = 502, body = "")

        assertThat(repository.refreshDetail(101)).isEqualTo(RefreshResult.Failure(RefreshError.Http(502)))
        assertThat(repository.observeDetail(101).first()).isEqualTo(cached)
    }

    @Test
    fun `concurrent detail refreshes for the same park share a request`() = runBlocking<Unit> {
        respond(body = Fixtures.parkDetail, delayMillis = 300)

        val results = (1..2).map { async { repository.refreshDetail(101) } }.awaitAll()

        assertThat(results).containsExactly(RefreshResult.Success(), RefreshResult.Success())
        assertThat(server.requestCount).isEqualTo(1)
    }
}
