package com.mustafakoceerr.tilepdf.feature.reader

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class JumpBackTimerTest {

    private var expired = 0

    private fun TestScope.timer() = JumpBackTimer(backgroundScope, timeoutMs = 8_000)

    @Test
    fun theWayBackIsForgottenOnceTheTimeIsUp() = runTest {
        val timer = timer()
        timer.restart { expired++ }

        advanceTimeBy(7_999)
        runCurrent()
        assertEquals(0, expired)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, expired)
    }

    @Test
    fun anotherJumpStartsTheTimeOver() = runTest {
        val timer = timer()
        timer.restart { expired++ }
        advanceTimeBy(5_000)

        timer.restart { expired++ }
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(0, expired)

        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(1, expired)
    }

    @Test
    fun goingBackStopsIt() = runTest {
        val timer = timer()
        timer.restart { expired++ }

        timer.cancel()
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(0, expired)
    }
}
