package com.fanson.ime.keyboard

import org.junit.Assert.*
import org.junit.Test

class HapticFeedbackTest {
    @Test fun silenceAndMalformedPreferencesCannotProduceInvalidOneShot() {
        assertEquals(50, HapticFeedback.strength(null))
        assertEquals(50, HapticFeedback.strength("invalid"))
        assertEquals(0, HapticFeedback.strength("-20"))
        assertEquals(100, HapticFeedback.strength("500"))
        for (supported in listOf(false, true)) {
            assertNull(HapticFeedback.pulse(0, supported))
            assertNull(HapticFeedback.pulse(-1, supported))
        }
    }
    @Test fun allPositiveLevelsAreLegalAndMonotonicForBothHardwareCapabilities() {
        val amplitudes = (1..100).map { HapticFeedback.pulse(it, true)!! }
        assertTrue(amplitudes.all { it.milliseconds == 10L && it.amplitude in 1..255 })
        assertTrue(amplitudes.zipWithNext().all { (a, b) -> a.amplitude <= b.amplitude })
        val durations = (1..100).map { HapticFeedback.pulse(it, false)!! }
        assertTrue(durations.all { it.milliseconds in 1..20 && it.amplitude == -1 })
        assertTrue(durations.zipWithNext().all { (a, b) -> a.milliseconds <= b.milliseconds })
        assertEquals(10L, HapticFeedback.pulse(50, false)!!.milliseconds)
        assertEquals(255, HapticFeedback.pulse(100, true)!!.amplitude)
    }
}
