package com.fanson.ime.keyboard

import android.os.VibrationEffect
import android.os.Vibrator

data class HapticPulse(val milliseconds: Long, val amplitude: Int)

/** Shared by the settings preview and keyboard; no timers or per-key preference reads. */
object HapticFeedback {
    const val DEFAULT_STRENGTH = 50
    fun strength(value: String?): Int = (value?.toIntOrNull() ?: DEFAULT_STRENGTH).coerceIn(0, 100)

    fun pulse(strength: Int, amplitudeControl: Boolean): HapticPulse? {
        val level = strength.coerceIn(0, 100)
        if (level == 0) return null // One-shot amplitude 0 is invalid; silence means no effect.
        return if (amplitudeControl) HapticPulse(10, (level * 255 + 50) / 100)
        else HapticPulse(((level * 20 + 50) / 100).coerceAtLeast(1).toLong(), VibrationEffect.DEFAULT_AMPLITUDE)
    }
    fun effect(vibrator: Vibrator?, strength: Int): VibrationEffect? {
        if (strength <= 0 || vibrator?.hasVibrator() != true) return null
        val pulse = pulse(strength, vibrator.hasAmplitudeControl()) ?: return null
        return VibrationEffect.createOneShot(pulse.milliseconds, pulse.amplitude)
    }
    fun preview(vibrator: Vibrator?, strength: Int) {
        effect(vibrator, strength)?.let { vibrator?.vibrate(it) }
    }
}
