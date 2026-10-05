package com.brkckr.parkv3.connectivity

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ReconnectionsTest {

    private suspend fun reconnections(vararg states: Boolean): Int =
        states.asList().asFlow().reconnections().toList().size

    @Test
    fun `being online from the start is not a reconnection`() = runTest {
        assertThat(reconnections(true)).isEqualTo(0)
        assertThat(reconnections(true, true)).isEqualTo(0)
    }

    @Test
    fun `each return after a loss counts once`() = runTest {
        assertThat(reconnections(false, true)).isEqualTo(1)
        assertThat(reconnections(true, false, true)).isEqualTo(1)
        assertThat(reconnections(true, false, true, false, true)).isEqualTo(2)
        assertThat(reconnections(false, false, true, true)).isEqualTo(1)
    }

    @Test
    fun `staying offline is not a reconnection`() = runTest {
        assertThat(reconnections(false)).isEqualTo(0)
        assertThat(reconnections(true, false, false)).isEqualTo(0)
    }
}
