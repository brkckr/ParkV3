package com.brkckr.parkv3.data.sync

import com.brkckr.parkv3.data.remote.parse.ListParseResult
import com.brkckr.parkv3.domain.model.RefreshError
import com.brkckr.parkv3.testutil.park
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ListSyncPolicyTest {

    private fun parsed(validCount: Int, skipped: Int = 0) =
        ListParseResult.Parsed((1..validCount).map { park(it) }, skippedCount = skipped, duplicateCount = 0)

    @Test
    fun `an empty array never clears the cache`() {
        assertThat(ListSyncPolicy.decide(parsed(0), cachedActiveCount = 250))
            .isEqualTo(ListSyncDecision.Reject(RefreshError.EmptyResponse))
    }

    @Test
    fun `a non array body is rejected`() {
        assertThat(ListSyncPolicy.decide(ListParseResult.NotAnArray, cachedActiveCount = 0))
            .isEqualTo(ListSyncDecision.Reject(RefreshError.Malformed))
    }

    @Test
    fun `mostly unusable records are rejected as a probable schema change`() {
        assertThat(ListSyncPolicy.decide(parsed(validCount = 4, skipped = 5), cachedActiveCount = 0))
            .isEqualTo(ListSyncDecision.Reject(RefreshError.Malformed))
        assertThat(ListSyncPolicy.decide(parsed(validCount = 0, skipped = 3), cachedActiveCount = 0))
            .isEqualTo(ListSyncDecision.Reject(RefreshError.Malformed))
    }

    @Test
    fun `a complete response marks absent records missing`() {
        val decision = ListSyncPolicy.decide(parsed(validCount = 240, skipped = 3), cachedActiveCount = 250)
        assertThat(decision).isInstanceOf(ListSyncDecision.Apply::class.java)
        decision as ListSyncDecision.Apply
        assertThat(decision.markMissing).isTrue()
        assertThat(decision.parks).hasSize(240)
    }

    @Test
    fun `a sharp shrink keeps absent records`() {
        val decision = ListSyncPolicy.decide(parsed(validCount = 100), cachedActiveCount = 250)
        assertThat(decision).isEqualTo(ListSyncDecision.Apply(parsed(100).parks, markMissing = false))
    }

    @Test
    fun `shrink guard does not apply to a small cache`() {
        val decision = ListSyncPolicy.decide(parsed(validCount = 2), cachedActiveCount = 10)
        assertThat((decision as ListSyncDecision.Apply).markMissing).isTrue()
    }
}
