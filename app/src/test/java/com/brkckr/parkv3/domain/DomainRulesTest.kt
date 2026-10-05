package com.brkckr.parkv3.domain

import com.brkckr.parkv3.domain.model.Availability
import com.brkckr.parkv3.domain.model.FreshnessPolicy
import com.brkckr.parkv3.domain.model.GeoPoint
import com.brkckr.parkv3.domain.model.Occupancy
import com.brkckr.parkv3.domain.model.RefreshError
import com.brkckr.parkv3.domain.model.SyncInfo
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DomainRulesTest {

    @Test
    fun `occupancy distinguishes known, missing and inconsistent values`() {
        assertThat(Occupancy.of(100, 0)).isEqualTo(Occupancy.Known(100, 0))
        assertThat(Occupancy.of(100, 100)).isEqualTo(Occupancy.Known(100, 100))
        assertThat(Occupancy.of(null, null)).isEqualTo(Occupancy.Missing)
        assertThat(Occupancy.of(100, null)).isEqualTo(Occupancy.Missing)
        assertThat(Occupancy.of(null, 5)).isEqualTo(Occupancy.Missing)
        assertThat(Occupancy.of(0, 0)).isEqualTo(Occupancy.Inconsistent(0, 0))
        assertThat(Occupancy.of(null, -1)).isEqualTo(Occupancy.Inconsistent(null, -1))
        assertThat(Occupancy.of(10, 11)).isEqualTo(Occupancy.Inconsistent(10, 11))
    }

    @Test
    fun `availability comes from occupancy alone and unknown occupancy is never free or full`() {
        assertThat(Availability.of(Occupancy.Known(10, 5))).isEqualTo(Availability.AVAILABLE)
        assertThat(Availability.of(Occupancy.Known(10, 0))).isEqualTo(Availability.FULL)
        assertThat(Availability.of(Occupancy.Missing)).isEqualTo(Availability.UNKNOWN)
        assertThat(Availability.of(Occupancy.Inconsistent(10, 11))).isEqualTo(Availability.UNKNOWN)
    }

    @Test
    fun `coordinates outside Istanbul or at null island are rejected`() {
        assertThat(GeoPoint.validOrNull(41.0, 29.0)).isNotNull()
        assertThat(GeoPoint.validOrNull(0.0, 0.0)).isNull()
        assertThat(GeoPoint.validOrNull(29.0, 41.0)).isNull()
        assertThat(GeoPoint.validOrNull(39.9, 32.8)).isNull() // Ankara
        assertThat(GeoPoint.validOrNull(Double.NaN, 29.0)).isNull()
        assertThat(GeoPoint.validOrNull(null, 29.0)).isNull()
    }

    @Test
    fun `freshness treats missing, old and future timestamps as stale`() {
        val now = 10_000_000L
        val fiveMin = FreshnessPolicy.LIST_AUTO_REFRESH_AFTER_MS
        assertThat(FreshnessPolicy.isOlderThan(null, now, fiveMin)).isTrue()
        assertThat(FreshnessPolicy.isOlderThan(now - fiveMin - 1, now, fiveMin)).isTrue()
        assertThat(FreshnessPolicy.isOlderThan(now - fiveMin, now, fiveMin)).isFalse()
        assertThat(FreshnessPolicy.isOlderThan(now + 60_000, now, fiveMin)).isTrue()
    }

    @Test
    fun `list is stale after the warning threshold or when the last attempt failed`() {
        val now = 100_000_000L
        val fresh = SyncInfo(lastSuccessAtMillis = now - 60_000)
        assertThat(FreshnessPolicy.isListStale(fresh, now)).isFalse()
        assertThat(FreshnessPolicy.isListStale(fresh.copy(lastError = RefreshError.Network), now)).isTrue()
        assertThat(FreshnessPolicy.isListStale(SyncInfo(lastSuccessAtMillis = now - 16 * 60_000), now)).isTrue()
        assertThat(FreshnessPolicy.isListStale(SyncInfo(), now)).isTrue()
    }

    @Test
    fun `reconnecting retries network failures and due refreshes but not server errors`() {
        val now = 100_000_000L
        val fresh = SyncInfo(lastSuccessAtMillis = now - 60_000)
        assertThat(FreshnessPolicy.shouldRefreshListOnReconnect(fresh, now)).isFalse()
        assertThat(FreshnessPolicy.shouldRefreshListOnReconnect(fresh.copy(lastError = RefreshError.Network), now)).isTrue()
        assertThat(FreshnessPolicy.shouldRefreshListOnReconnect(fresh.copy(lastError = RefreshError.Http(503)), now)).isFalse()
        assertThat(FreshnessPolicy.shouldRefreshListOnReconnect(fresh.copy(lastError = RefreshError.Malformed), now)).isFalse()
        assertThat(FreshnessPolicy.shouldRefreshListOnReconnect(SyncInfo(lastSuccessAtMillis = now - 6 * 60_000), now)).isTrue()
        assertThat(FreshnessPolicy.shouldRefreshListOnReconnect(SyncInfo(lastError = RefreshError.Http(503)), now)).isTrue()
    }
}
