package com.tisho.ride.workout

import com.tisho.ride.bike.BikeConnection
import com.tisho.ride.bike.PowerModel
import com.tisho.ride.data.Profile
import com.tisho.ride.data.Ride
import com.tisho.ride.data.Sample
import com.tisho.ride.hr.HeartRateMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

enum class Phase { COUNTDOWN, RUNNING, PAUSED, FINISHED }

/** − / + step sizes: 5 levels (of 100) on level steps, 10 W on ERG steps. */
const val LEVEL_STEP = 5
const val WATT_STEP = 10

data class RideState(
    val phase: Phase = Phase.COUNTDOWN,
    val countdown: Int = 3,
    val elapsed: Int = 0,
    val stepIndex: Int = 0,
    val stepElapsed: Int = 0,
    val rpm: Int = 0,
    val watts: Double = 0.0,
    val level: Int = 0,
    val hr: Int = 0,
    val hrFromStrap: Boolean = false,
    val targetWatts: Int? = null,
    val kj: Double = 0.0,
    val kcal: Double = 0.0,
    val activeKcal: Double = 0.0,
    val avgWatts: Double = 0.0,
    val maxWatts: Double = 0.0,
    val autoPaused: Boolean = false,
    /** Resistance level the rider/workout asked for (shown immediately). */
    val targetLevel: Int = 0,
    /** True while the brake motor is still travelling to [targetLevel]. */
    val moving: Boolean = false,
    val speedKmh: Double = 0.0,
    val distanceM: Double = 0.0,
    val wattsHistory: List<Float> = emptyList(),
    val hrHistory: List<Int> = emptyList(),
    val rpmHistory: List<Int> = emptyList(),
)

data class SessionResult(
    val ride: Ride,
    val samples: List<Sample>,
    /** Suggested FTP after a ramp test. */
    val newFtp: Int? = null,
    val vo2max: Double? = null,
    /** Updated adaptive sprint resistance after REHIT. */
    val newSprintInnate: Int? = null,
    val sprintPeaks: List<Int> = emptyList(),
    val sprintEndRpm: List<Int> = emptyList(),
    val sprintInnateUsed: Int = 0,
)

/** Runs one workout for one rider against the bike. */
class RideSession(
    private val scope: CoroutineScope,
    private val bike: BikeConnection,
    private val hrm: HeartRateMonitor,
    private val power: PowerModel,
    val profile: Profile,
    val workout: Workout,
    private val cues: Cues,
) {
    private val _state = MutableStateFlow(RideState())
    val state: StateFlow<RideState> = _state.asStateFlow()

    private val samples = ArrayList<Sample>()
    private val watts = ArrayList<Float>()
    private val startedAt = System.currentTimeMillis()
    private val sprintInnate = if (profile.sprintInnate > 0) profile.sprintInnate else Metrics.defaultSprintInnate(profile, power::watts)
    private var levelOffset = 0
    private var wattOffset = 0
    private var freeLevel = workout.steps.firstOrNull()?.takeIf { it.type == TargetType.FREE }?.value ?: 20
    private var manualPause = false
    private var stillSeconds = 0
    private var lowCadenceSeconds = 0
    private var smoothRpm = 0.0
    private var job: Job? = null
    private var hrZoneSeconds = IntArray(6)
    private var rpmSum = 0L
    private var hrSum = 0L
    private var hrCount = 0
    private var maxHr = 0
    private var maxRpm = 0
    private val sprintPeaks = ArrayList<Int>()
    private val sprintEndRpm = ArrayList<Int>()
    private val sprintRpms = ArrayList<Int>()
    private var lastHrWarning = 0
    private var sprintPeak = 0
    private var finishedResult: SessionResult? = null
    private val energy = EnergyModel(profile)
    private val virtualSpeed = VirtualSpeed(profile.weightKg)

    val steps: List<Step> get() = workout.steps
    val currentStep: Step get() = steps[_state.value.stepIndex.coerceIn(0, steps.lastIndex)]

    fun start() {
        job = scope.launch {
            for (c in 3 downTo 1) {
                _state.value = _state.value.copy(phase = Phase.COUNTDOWN, countdown = c)
                cues.beep(short = true)
                delay(1000)
            }
            cues.beep(short = false)
            cues.say(steps.first().cue.ifEmpty { steps.first().label })
            _state.value = _state.value.copy(phase = Phase.RUNNING)
            var lastTick = System.currentTimeMillis()
            while (isActive && _state.value.phase != Phase.FINISHED) {
                control()
                val now = System.currentTimeMillis()
                if (now - lastTick >= 1000) {
                    lastTick += 1000
                    tick()
                }
                delay(100)
            }
        }
    }

    /** − / + buttons: level steps on level/free steps, 5 W on ERG steps. */
    fun nudge(up: Boolean) {
        val d = if (up) 1 else -1
        when (currentStep.type) {
            TargetType.FREE -> freeLevel = (freeLevel + d * LEVEL_STEP).coerceIn(0, PowerModel.MAX_LEVEL)
            TargetType.LEVEL -> levelOffset = (levelOffset + d * LEVEL_STEP).coerceIn(-60, 60)
            TargetType.FTP_PCT, TargetType.WATTS -> wattOffset = (wattOffset + d * WATT_STEP).coerceIn(-300, 400)
            TargetType.SPRINT -> {}
        }
        control()
    }

    fun togglePause() {
        manualPause = !manualPause
        _state.value = _state.value.copy(phase = if (manualPause) Phase.PAUSED else Phase.RUNNING, autoPaused = false)
    }

    fun skipStep() {
        val s = _state.value
        if (s.stepIndex < steps.lastIndex) enterStep(s.stepIndex + 1) else finish()
    }

    private fun control() {
        val t = bike.telemetry.value
        smoothRpm += (t.rpm - smoothRpm) * 0.3
        val step = currentStep
        val strap = hrm.bpm.value
        val hr = if (strap > 0) strap else t.gripHr
        var target: Int? = null
        val innate = when (step.type) {
            TargetType.FREE -> PowerModel.innateForLevel(freeLevel)
            TargetType.LEVEL -> PowerModel.innateForLevel(step.value + levelOffset)
            TargetType.SPRINT -> sprintInnate
            TargetType.FTP_PCT, TargetType.WATTS -> {
                val w = max(10, (if (step.type == TargetType.WATTS) step.value else profile.ftp * step.value / 100) + wattOffset)
                target = w
                // While the pedals are (nearly) still, hold the current resistance instead of guessing.
                if (smoothRpm < 20 && bike.resistance > 0) bike.resistance
                else power.innateFor(w.toDouble(), if (smoothRpm < 20) 75 else max(50, smoothRpm.roundToInt()))
            }
        }
        // Resistance is kept through pauses (dropping to 0 made it lag on restart); only finishing releases it.
        val desired = innate
        val erg = step.type == TargetType.FTP_PCT || step.type == TargetType.WATTS
        if (!erg || abs(desired - bike.resistance) >= 3) bike.setResistance(desired)
        _state.value = _state.value.copy(
            rpm = t.rpm, watts = t.watts, level = PowerModel.levelForInnate(t.innate), hr = hr,
            hrFromStrap = strap > 0, targetWatts = target,
            targetLevel = PowerModel.levelForInnate(bike.resistance), moving = abs(t.innate - bike.resistance) > 3,
        )
    }

    private fun tick() {
        val s = _state.value
        if (s.phase == Phase.FINISHED || s.phase == Phase.COUNTDOWN) return
        // Auto-pause when the pedals stop, resume as soon as they turn.
        if (!manualPause) {
            stillSeconds = if (s.rpm == 0) stillSeconds + 1 else 0
            val auto = stillSeconds >= 3
            if (auto != s.autoPaused || (s.phase == Phase.PAUSED) != auto) {
                _state.value = s.copy(autoPaused = auto, phase = if (auto) Phase.PAUSED else Phase.RUNNING)
            }
        }
        if (_state.value.phase != Phase.RUNNING) return

        val st = _state.value
        val step = currentStep
        val w = st.watts.toFloat()
        watts += w
        samples += Sample(0, st.elapsed, w, st.rpm, st.level, st.hr)
        rpmSum += st.rpm
        maxRpm = max(maxRpm, st.rpm)
        if (st.hr > 0) { hrSum += st.hr; hrCount++; maxHr = max(maxHr, st.hr) }
        hrZoneSeconds[Metrics.hrZone(st.hr, profile.maxHr)]++
        if (step.type == TargetType.SPRINT) { sprintPeak = max(sprintPeak, st.rpm); sprintRpms += st.rpm }
        if (st.hr > 0 && st.hr >= profile.maxHr * 0.95 && st.elapsed - lastHrWarning > 60) {
            lastHrWarning = st.elapsed
            cues.say("Heart rate very high. Ease off if you feel unwell.")
        }

        val kj = st.kj + w / 1000.0
        val burned = energy.kcalPerSecond(st.watts, st.hr)
        val metres = virtualSpeed.step(st.watts)
        _state.value = st.copy(
            elapsed = st.elapsed + 1,
            stepElapsed = st.stepElapsed + 1,
            kj = kj,
            kcal = st.kcal + burned,
            activeKcal = st.activeKcal + (burned - energy.bmrPerSecond).coerceAtLeast(0.0),
            distanceM = st.distanceM + metres,
            speedKmh = virtualSpeed.speed * 3.6,
            avgWatts = kj * 1000 / (st.elapsed + 1),
            maxWatts = max(st.maxWatts, st.watts),
            wattsHistory = (st.wattsHistory + w).takeLast(600),
            hrHistory = (st.hrHistory + st.hr).takeLast(600),
            rpmHistory = (st.rpmHistory + st.rpm).takeLast(600),
        )

        val remaining = step.seconds - _state.value.stepElapsed
        if (remaining in 1..3) cues.beep(short = true)
        if (remaining == 10 && steps.getOrNull(st.stepIndex + 1)?.type == TargetType.SPRINT) cues.say("Sprint in 10 seconds")

        // Ramp test ends on its own when cadence collapses.
        if (workout.openEnded && step.type == TargetType.WATTS) {
            lowCadenceSeconds = if (st.rpm < 45) lowCadenceSeconds + 1 else 0
            if (lowCadenceSeconds >= 8) { finish(); return }
        }
        if (remaining <= 0) {
            if (st.stepIndex < steps.lastIndex) enterStep(st.stepIndex + 1) else finish()
        }
    }

    private fun enterStep(i: Int) {
        if (currentStep.type == TargetType.SPRINT) closeSprint()
        levelOffset = 0
        val next = steps[i]
        if (next.type != TargetType.FTP_PCT && next.type != TargetType.WATTS) wattOffset = 0
        _state.value = _state.value.copy(stepIndex = i, stepElapsed = 0)
        cues.beep(short = false)
        cues.say(next.cue.ifEmpty { next.label })
    }

    fun finish(): SessionResult {
        finishedResult?.let { return it }
        if (currentStep.type == TargetType.SPRINT && sprintPeak > 0) closeSprint()
        _state.value = _state.value.copy(phase = Phase.FINISHED)
        job?.cancel()
        bike.setResistance(0)
        cues.say("Workout complete. Great job!")
        return buildResult().also { finishedResult = it }
    }

    private fun closeSprint() {
        sprintPeaks += sprintPeak
        sprintEndRpm += sprintRpms.takeLast(3).takeIf { it.isNotEmpty() }?.average()?.roundToInt() ?: 0
        sprintPeak = 0
        sprintRpms.clear()
    }

    private fun buildResult(): SessionResult {
        val st = _state.value
        val dur = st.elapsed
        val np = Metrics.normalized(watts)
        val ftp = profile.ftp
        val ride = Ride(
            profileId = profile.id,
            workoutId = workout.id,
            workoutName = workout.label,
            startedAt = startedAt,
            durationSec = dur,
            kj = st.kj,
            kcal = st.kcal,
            activeKcal = st.activeKcal,
            distanceM = st.distanceM,
            avgWatts = if (dur > 0) st.kj * 1000 / dur else 0.0,
            maxWatts = watts.maxOrNull()?.toDouble() ?: 0.0,
            normalizedWatts = np,
            avgRpm = if (dur > 0) rpmSum.toDouble() / dur else 0.0,
            maxRpm = maxRpm,
            avgHr = if (hrCount > 0) (hrSum / hrCount).toInt() else 0,
            maxHr = maxHr,
            tss = Metrics.tss(dur, np, ftp),
            intensityFactor = if (ftp > 0) np / ftp else 0.0,
            peak5s = Metrics.peak(watts, 5),
            peak20s = Metrics.peak(watts, 20),
            peak1m = Metrics.peak(watts, 60),
            peak5m = Metrics.peak(watts, 300),
            peak20m = Metrics.peak(watts, 1200),
            hrZoneSeconds = hrZoneSeconds.drop(1).joinToString(","),
            ftpAtRide = ftp,
        )
        var newFtp: Int? = null
        var vo2: Double? = null
        if (workout.id == "ftp20" && ride.peak20m > 0) {
            newFtp = (ride.peak20m * if (ride.peak20m * 0.95 / profile.weightKg < 3.0) 0.92 else 0.95).roundToInt()
        }
        if (workout.id == "ramp" && ride.peak1m > 0) {
            newFtp = (ride.peak1m * 0.75).roundToInt()
            vo2 = Metrics.vo2max(ride.peak1m, profile.weightKg)
        }
        val rideVo2 = vo2 ?: Metrics.vo2FromHeartRate(samples, profile)
        val vo2Source = if (vo2 != null) 2 else if (rideVo2 != null) 1 else 0
        var newSprint: Int? = null
        if (sprintEndRpm.isNotEmpty() && sprintPeaks.any { it > 30 }) {
            // Research rule: if cadence is still >110 rpm at the end of the sprint the load is too light (+5%);
            // if it has collapsed below 60 rpm it is too heavy (-5%). Otherwise keep it.
            val end = sprintEndRpm.average()
            newSprint = when {
                end > 110 -> (sprintInnate * 1.05).roundToInt() + 1
                end < 60 -> (sprintInnate * 0.95).roundToInt() - 1
                else -> sprintInnate
            }.coerceIn(40, PowerModel.MAX_INNATE)
        }
        return SessionResult(ride.copy(vo2Estimate = rideVo2 ?: 0.0, vo2Source = vo2Source), samples.toList(), newFtp, vo2, newSprint, sprintPeaks.toList(), sprintEndRpm.toList(), sprintInnate)
    }
}
