package com.brkckr.parkv3.data.sync

import com.brkckr.parkv3.data.remote.parse.ListParseResult
import com.brkckr.parkv3.domain.model.Park
import com.brkckr.parkv3.domain.model.RefreshError

sealed interface ListSyncDecision {
    /**
     * Write [parks]. When [markMissing] is false (shrink guard), records absent from the
     * response are kept as they are instead of being marked missing.
     */
    data class Apply(val parks: List<Park>, val markMissing: Boolean) : ListSyncDecision

    /** Leave the cache untouched and record [error]. */
    data class Reject(val error: RefreshError) : ListSyncDecision
}

/** Decides how a parsed list response may change the cache. See docs/adr/0004. */
object ListSyncPolicy {

    const val SHRINK_GUARD_MIN_CACHED = 20
    const val SHRINK_GUARD_RATIO = 0.5

    fun decide(result: ListParseResult, cachedActiveCount: Int): ListSyncDecision {
        val parsed = when (result) {
            ListParseResult.NotAnArray -> return ListSyncDecision.Reject(RefreshError.Malformed)
            is ListParseResult.Parsed -> result
        }
        if (parsed.totalCount == 0) return ListSyncDecision.Reject(RefreshError.EmptyResponse)
        if (parsed.parks.isEmpty() || parsed.skippedCount * 2 > parsed.totalCount) {
            // Mostly unusable records usually means the schema changed: do not write.
            return ListSyncDecision.Reject(RefreshError.Malformed)
        }
        val suspiciousShrink = cachedActiveCount >= SHRINK_GUARD_MIN_CACHED &&
            parsed.parks.size < cachedActiveCount * SHRINK_GUARD_RATIO
        return ListSyncDecision.Apply(parsed.parks, markMissing = !suspiciousShrink)
    }
}
