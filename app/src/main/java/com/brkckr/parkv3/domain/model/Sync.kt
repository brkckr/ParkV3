package com.brkckr.parkv3.domain.model

/** Why a refresh did not produce new data. UI maps these to localized text only. */
sealed interface RefreshError {
    data object Network : RefreshError
    data class Http(val code: Int) : RefreshError
    data object Malformed : RefreshError
    data object EmptyResponse : RefreshError
    data object NotFound : RefreshError
    data object Unexpected : RefreshError
}

sealed interface RefreshResult {
    /** [partial] is true when the shrink guard kept records that were missing (ADR-0004). */
    data class Success(val partial: Boolean = false) : RefreshResult
    data class Failure(val error: RefreshError) : RefreshResult
}

/**
 * Persisted list sync bookkeeping. [lastSuccessAtMillis] only moves on a successful refresh;
 * [lastError] describes the most recent attempt and is cleared by the next success.
 */
data class SyncInfo(
    val lastSuccessAtMillis: Long? = null,
    val lastAttemptAtMillis: Long? = null,
    val lastError: RefreshError? = null,
)

/** A favorite whose park is no longer listed by the source. */
data class OrphanFavorite(val parkId: Int, val name: String?, val district: String?)

fun interface Clock {
    fun nowMillis(): Long
}

object FreshnessPolicy {
    const val LIST_AUTO_REFRESH_AFTER_MS = 5 * 60_000L
    const val LIST_STALE_WARNING_AFTER_MS = 15 * 60_000L
    const val DETAIL_REFRESH_AFTER_MS = 5 * 60_000L

    /** A timestamp in the future (clock moved back) is treated as stale. */
    fun isOlderThan(timestampMillis: Long?, nowMillis: Long, thresholdMillis: Long): Boolean =
        timestampMillis == null ||
            timestampMillis > nowMillis ||
            nowMillis - timestampMillis > thresholdMillis

    fun isListStale(info: SyncInfo, nowMillis: Long): Boolean =
        info.lastError != null ||
            isOlderThan(info.lastSuccessAtMillis, nowMillis, LIST_STALE_WARNING_AFTER_MS)

    /**
     * When the connection comes back: retry a refresh that failed for lack of network, or one
     * that is due anyway. Server-side failures (HTTP, malformed) are not retried just because
     * the device reconnected.
     */
    fun shouldRefreshListOnReconnect(info: SyncInfo, nowMillis: Long): Boolean =
        info.lastError == RefreshError.Network ||
            isOlderThan(info.lastSuccessAtMillis, nowMillis, LIST_AUTO_REFRESH_AFTER_MS)
}
