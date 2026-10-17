package com.tisho.ride.workout

import com.tisho.ride.data.Profile

/**
 * Per-rider energy expenditure, integrated once a second.
 *
 * - Work-based: measured mechanical power divided by gross efficiency. Gross efficiency on a
 *   cycle ergometer is ~19% for untrained and ~23% for trained riders (Coyle 1992; Hopker 2009),
 *   so it is scaled with the rider's FTP per kg.
 * - Heart-rate based: Keytel et al. 2005 (J Sports Sci) prediction from HR, weight, age and sex,
 *   valid during exercise (HR >= 90). It captures the elevated cost of recovery after sprints that
 *   pure work misses.
 * - Resting: Mifflin-St Jeor BMR, used as the floor and to split total into active calories.
 */
class EnergyModel(private val p: Profile) {
    private val efficiency = (0.185 + 0.012 * (p.ftp / p.weightKg)).coerceIn(0.19, 0.235)

    /** Resting kcal per second. */
    val bmrPerSecond: Double = run {
        val base = 10 * p.weightKg + 6.25 * p.heightCm - 5 * p.age
        (if (p.sex == "F") base - 161 else base + 5) / 86_400.0
    }

    /** Total (gross) kcal burned in one second at [watts] and heart rate [hr] (0 = unknown). */
    fun kcalPerSecond(watts: Double, hr: Int): Double {
        val work = watts / efficiency / 4184.0
        val heart = if (hr in 90..210) keytelPerMinute(hr) / 60.0 else 0.0
        val blended = when {
            heart > 0 && watts >= 20 -> 0.65 * work + 0.35 * heart
            heart > 0 -> heart
            else -> work
        }
        return maxOf(blended, bmrPerSecond)
    }

    private fun keytelPerMinute(hr: Int): Double {
        val a = p.age.toDouble()
        val w = p.weightKg
        val kj = if (p.sex == "F") -20.4022 + 0.4472 * hr - 0.1263 * w + 0.074 * a
        else -55.0969 + 0.6309 * hr + 0.1988 * w + 0.2017 * a
        return (kj / 4.184).coerceAtLeast(0.0)
    }
}
