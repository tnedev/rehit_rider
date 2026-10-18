package com.tisho.ride.bike

import android.content.Context
import java.io.DataInputStream
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Power and resistance model for this bike.
 *
 * power_table.bin holds 256x256 big-endian floats, index innate*256 + rpm, already corrected with
 * the bike's calibration factors (validated against 68k logged samples from the original app).
 */
class PowerModel(context: Context) {
    private val table = FloatArray(256 * 256).also { t ->
        DataInputStream(context.assets.open("power_table.bin").buffered()).use { input ->
            for (i in t.indices) t[i] = input.readFloat()
        }
    }

    fun watts(rpm: Int, innate: Int): Double {
        if (rpm <= 0) return 0.0
        return table[(innate.coerceIn(0, 255) shl 8) or rpm.coerceIn(0, 255)].toDouble()
    }

    /** Lowest innate resistance that produces at least [targetWatts] at [rpm]. */
    fun innateFor(targetWatts: Double, rpm: Int): Int {
        if (rpm <= 0) return 0
        for (innate in 0..MAX_INNATE) if (watts(rpm, innate) >= targetWatts) return innate
        return MAX_INNATE
    }

    companion object {
        const val MAX_INNATE = 220
        const val MAX_LEVEL = 100

        /** User-facing level 0..100, the same scale the original app showed. */
        fun innateForLevel(level: Int): Int = if (level <= 0) 0 else (6 + (2.14 * level.coerceAtMost(MAX_LEVEL)).roundToInt()).coerceAtMost(MAX_INNATE)
        fun levelForInnate(innate: Int): Int = if (innate <= 0) 0 else ((innate - 6) / 2.14).roundToInt().coerceIn(0, MAX_LEVEL)

        /** User-facing difficulty 0..20: one step per − / + tap, 5 levels of the 0..100 scale behind it. */
        const val MAX_DIFFICULTY = 20
        fun difficultyForLevel(level: Int): Int = Math.round(level / 5f).coerceIn(0, MAX_DIFFICULTY)

        /** Display resistance used by the original app's charts. */
        fun standardResistance(innate: Int): Int = when {
            innate <= 0 -> 0
            innate > 220 -> 220
            else -> (6.40628568 + innate.toDouble().pow(1.045523623) * 0.772093591).roundToInt()
        }
    }
}
