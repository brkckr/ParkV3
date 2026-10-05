package com.brkckr.parkv3.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.brkckr.parkv3.R
import com.brkckr.parkv3.domain.model.Clock
import com.brkckr.parkv3.domain.model.Occupancy
import com.brkckr.parkv3.domain.model.RefreshError
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Wall clock for relative times; tests provide a fixed one. */
val LocalClock = staticCompositionLocalOf { Clock { System.currentTimeMillis() } }

/** Current time, re-read every 30 s so "3 minutes ago" labels stay correct. */
@Composable
fun rememberNow(): Long {
    val clock = LocalClock.current
    val now by produceState(clock.nowMillis(), clock) {
        while (true) {
            delay(30_000)
            value = clock.nowMillis()
        }
    }
    return now
}

/** The UI locale, read observably so a language change recomposes formatted text. */
@Composable
@ReadOnlyComposable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

/** Server text is never shown; only typed, localized reasons (and an HTTP status code). */
@Composable
@ReadOnlyComposable
fun refreshErrorText(error: RefreshError): String = when (error) {
    RefreshError.Network -> stringResource(R.string.error_network)
    is RefreshError.Http -> stringResource(R.string.error_http, error.code)
    RefreshError.Malformed -> stringResource(R.string.error_malformed)
    RefreshError.EmptyResponse -> stringResource(R.string.error_empty)
    RefreshError.NotFound -> stringResource(R.string.error_not_found)
    RefreshError.Unexpected -> stringResource(R.string.error_unexpected)
}

@Composable
@ReadOnlyComposable
fun parkName(name: String?, id: Int): String = name ?: stringResource(R.string.park_unnamed, id)

@Composable
@ReadOnlyComposable
fun distanceText(meters: Double): String = if (meters < 1_000) {
    stringResource(R.string.distance_m, ((meters / 10).toInt() * 10).coerceAtLeast(10))
} else {
    stringResource(R.string.distance_km, String.format(currentLocale(), "%.1f", meters / 1_000))
}

@Composable
@ReadOnlyComposable
fun occupancyText(occupancy: Occupancy, capacity: Int?): String = when (occupancy) {
    is Occupancy.Known -> stringResource(R.string.occupancy_known, occupancy.empty, occupancy.capacity)
    Occupancy.Missing -> if (capacity != null && capacity > 0) {
        stringResource(R.string.occupancy_capacity_only, capacity)
    } else {
        stringResource(R.string.occupancy_missing)
    }
    is Occupancy.Inconsistent -> stringResource(R.string.occupancy_inconsistent)
}

/** "14:05 (3 minutes ago)"; older than a day shows the date as well. */
@Composable
fun timeWithRelative(timestampMillis: Long, nowMillis: Long): String {
    val locale = currentLocale()
    val zone = ZoneId.systemDefault()
    val instant = Instant.ofEpochMilli(timestampMillis)
    val ageMillis = nowMillis - timestampMillis
    val absolute = if (ageMillis in 0 until DAY_MS) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).withZone(zone).format(instant)
    } else {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale).withZone(zone).format(instant)
    }
    val minutes = (ageMillis / 60_000).toInt()
    val relative = when {
        ageMillis < 0 || ageMillis >= DAY_MS -> return absolute
        minutes < 1 -> stringResource(R.string.relative_just_now)
        minutes < 60 -> pluralStringResource(R.plurals.relative_minutes, minutes, minutes)
        else -> (minutes / 60).let { hours -> pluralStringResource(R.plurals.relative_hours, hours, hours) }
    }
    return stringResource(R.string.time_with_relative, absolute, relative)
}

/** Source timestamps are shown in Istanbul time, as published. */
@Composable
@ReadOnlyComposable
fun sourceTimestampText(epochMillis: Long?, raw: String): String {
    if (epochMillis == null) return raw
    return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(currentLocale())
        .withZone(ZoneId.of("Europe/Istanbul"))
        .format(Instant.ofEpochMilli(epochMillis))
}

@Composable
@ReadOnlyComposable
fun amountText(amount: Double): String =
    NumberFormat.getNumberInstance(currentLocale()).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }.format(amount)

private const val DAY_MS = 24 * 60 * 60 * 1000L
