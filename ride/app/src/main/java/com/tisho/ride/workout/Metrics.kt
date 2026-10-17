package com.tisho.ride.workout

import com.tisho.ride.data.Profile
import com.tisho.ride.data.Ride
import java.util.Calendar
import kotlin.math.exp
import kotlin.math.pow

object Metrics {
    /** Best average power over [window] seconds of a 1 Hz series. */
    fun peak(watts: List<Float>, window: Int): Double {
        if (watts.size < window || window <= 0) return 0.0
        var sum = 0.0
        for (i in 0 until window) sum += watts[i]
        var best = sum
        for (i in window until watts.size) {
            sum += watts[i] - watts[i - window]
            if (sum > best) best = sum
        }
        return best / window
    }

    /** Coggan normalized power: 30 s rolling mean, 4th-power average. */
    fun normalized(watts: List<Float>): Double {
        if (watts.size < 30) return watts.average().takeIf { !it.isNaN() } ?: 0.0
        var sum = 0.0
        for (i in 0 until 30) sum += watts[i]
        var acc = 0.0
        var n = 0
        for (i in 30..watts.size) {
            acc += (sum / 30).pow(4); n++
            if (i < watts.size) sum += watts[i] - watts[i - 30]
        }
        return (acc / n).pow(0.25)
    }

    fun tss(seconds: Int, np: Double, ftp: Int): Double {
        if (ftp <= 0) return 0.0
        val ifactor = np / ftp
        return seconds * np * ifactor / (ftp * 3600.0) * 100
    }

    /** HR zone 1..5 by % of max HR (50/60/70/80/90). 0 = below zone 1 or no HR. */
    fun hrZone(hr: Int, maxHr: Int): Int {
        if (hr <= 0 || maxHr <= 0) return 0
        val p = hr.toDouble() / maxHr
        return when {
            p >= 0.9 -> 5; p >= 0.8 -> 4; p >= 0.7 -> 3; p >= 0.6 -> 2; p >= 0.5 -> 1; else -> 0
        }
    }

    val hrZoneNames = listOf("Rest", "Warm-up", "Fat burn", "Aerobic", "Threshold", "Max")

    /** Coggan power zone 1..7 by % of FTP. */
    fun powerZone(watts: Double, ftp: Int): Int {
        if (ftp <= 0 || watts <= 0) return 0
        val p = watts / ftp
        return when {
            p > 1.5 -> 7; p > 1.2 -> 6; p > 1.05 -> 5; p > 0.9 -> 4; p > 0.75 -> 3; p > 0.55 -> 2; else -> 1
        }
    }

    val powerZoneNames = listOf("—", "Recovery", "Endurance", "Tempo", "Threshold", "VO₂max", "Anaerobic", "Sprint")

    /** ACSM leg-cycling equation applied to peak aerobic power, ml/kg/min. */
    fun vo2max(peakAerobicWatts: Double, weightKg: Double): Double =
        if (weightKg <= 0) 0.0 else 10.8 * peakAerobicWatts / weightKg + 7

    /** Fitness percentile label from VO₂max, roughly following ACSM/Cooper norms. */
    fun vo2Rating(vo2: Double, age: Int, female: Boolean): String {
        val base = if (female) 30.0 else 36.0
        val adj = base - (age - 30).coerceAtLeast(0) * 0.3
        return when {
            vo2 <= 0 -> "—"
            vo2 >= adj + 14 -> "Superior"
            vo2 >= adj + 8 -> "Excellent"
            vo2 >= adj + 3 -> "Good"
            vo2 >= adj - 3 -> "Fair"
            else -> "Poor"
        }
    }

    data class Load(val fitness: Double, val fatigue: Double) {
        val form: Double get() = fitness - fatigue
    }

    /** Banister/Coggan chronic (42 d) and acute (7 d) training load from daily TSS. */
    fun trainingLoad(rides: List<Ride>, now: Long = System.currentTimeMillis()): List<Pair<Long, Load>> {
        if (rides.isEmpty()) return emptyList()
        val day = 86_400_000L
        val first = startOfDay(rides.minOf { it.startedAt })
        val last = startOfDay(now)
        val byDay = rides.groupBy { startOfDay(it.startedAt) }.mapValues { (_, r) -> r.sumOf { it.tss } }
        var ctl = 0.0
        var atl = 0.0
        val kc = 1 - exp(-1.0 / 42)
        val ka = 1 - exp(-1.0 / 7)
        val out = ArrayList<Pair<Long, Load>>()
        var d = first
        while (d <= last) {
            val t = byDay[d] ?: 0.0
            ctl += (t - ctl) * kc
            atl += (t - atl) * ka
            out += d to Load(ctl, atl)
            d += day
        }
        return out
    }

    fun startOfDay(t: Long): Long = Calendar.getInstance().apply {
        timeInMillis = t
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun startOfWeek(t: Long): Long = Calendar.getInstance().apply {
        timeInMillis = startOfDay(t)
        firstDayOfWeek = Calendar.MONDAY
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        if (timeInMillis > t) add(Calendar.DAY_OF_YEAR, -7)
    }.timeInMillis

    /** Consecutive weeks (ending this week or last) where the rider rode at least once. */
    fun weekStreak(rides: List<Ride>, now: Long = System.currentTimeMillis()): Int {
        val weeks = rides.map { startOfWeek(it.startedAt) }.toSet()
        var w = startOfWeek(now)
        if (w !in weeks) w -= 7 * 86_400_000L
        var n = 0
        while (w in weeks) { n++; w -= 7 * 86_400_000L }
        return n
    }

    /** WHO-style active minutes: vigorous time counts double. */
    fun activeMinutes(r: Ride): Int {
        val vigorous = r.intensityFactor >= 0.85 || r.workoutId.startsWith("rehit") || r.workoutId == "tabata"
        return (r.durationSec / 60) * if (vigorous) 2 else 1
    }

    data class RiderScore(
        val profile: Profile,
        val total: Int,
        val fitness: Int, val consistency: Int, val sprint: Int, val improvement: Int,
        val vo2: Double, val vo2Estimated: Boolean,
        val sprintWkg: Double, val weekMinutes: Int, val streak: Int, val improvementPct: Double?,
    )

    /**
     * Leaderboard score out of 1000, fair across ages and body sizes:
     * fitness 400 (VO₂max vs age/sex norm), consistency 300 (weekly goal + streak),
     * sprint 150 (best 5-s W/kg, 30 days), improvement 150 (best 20-s power, 4 weeks vs previous 4).
     */
    fun riderScore(p: Profile, rides: List<Ride>, now: Long = System.currentTimeMillis()): RiderScore {
        val day = 86_400_000L
        val ramp = rides.filter { it.workoutId == "ramp" && it.startedAt > now - 90 * day }.maxOfOrNull { it.peak1m }
        val vo2 = vo2max(ramp ?: (p.ftp / 0.75), p.weightKg)
        val norm = (if (p.sex == "F") 30.0 else 36.0) - (p.age - 30).coerceAtLeast(0) * 0.3
        val fitness = (((vo2 - norm + 10) / 30).coerceIn(0.0, 1.0) * 400).toInt()

        val week = rides.filter { it.startedAt >= startOfWeek(now) }.sumOf { activeMinutes(it) }
        val streak = weekStreak(rides, now)
        val consistency = ((week.toDouble() / p.weeklyGoalMinutes.coerceAtLeast(1)).coerceAtMost(1.0) * 200 + streak.coerceAtMost(8) / 8.0 * 100).toInt()

        val sprintWkg = (rides.filter { it.startedAt > now - 30 * day }.maxOfOrNull { it.peak5s } ?: 0.0) / p.weightKg
        val sprint = (((sprintWkg - 4) / 10).coerceIn(0.0, 1.0) * 150).toInt()

        val recent = rides.filter { it.startedAt > now - 28 * day }.maxOfOrNull { it.peak20s }
        val before = rides.filter { it.startedAt in (now - 56 * day)..(now - 28 * day) }.maxOfOrNull { it.peak20s }
        val pct = if (recent != null && before != null && before > 0) (recent - before) / before * 100 else null
        val improvement = if (pct == null) 50 else (((pct + 10) / 30).coerceIn(0.0, 1.0) * 150).toInt()

        return RiderScore(p, fitness + consistency + sprint + improvement, fitness, consistency, sprint, improvement,
            vo2, ramp == null, sprintWkg, week, streak, pct)
    }

    /**
     * Initial sprint resistance: the research load of 7.5% of body mass (5% for riders 60+), i.e. the
     * Wingate-equivalent power at 110 rpm (0.075 * m * g * 6 m/rev * rpm / 60).
     */
    fun defaultSprintInnate(p: Profile, wattsAt: (rpm: Int, innate: Int) -> Double): Int {
        val fraction = if (p.age >= 60) 0.05 else 0.075
        val target = fraction * p.weightKg * 9.81 * 6 * 110 / 60
        for (innate in 40..220) if (wattsAt(110, innate) >= target) return innate
        return 220
    }
}
