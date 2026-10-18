package com.tisho.ride.workout

/**
 * Virtual road speed from power, the way indoor-cycling apps do it: solve the flat-road steady-state
 * power balance P = v · (Crr·m·g + ½·ρ·CdA·v²) for v. Rider + 9 kg bike, road tyres (Crr 0.004),
 * hoods position (CdA 0.32 m²), sea-level air (ρ 1.225 kg/m³).
 */
class VirtualSpeed(riderKg: Double) {
    private val mass = riderKg + 9.0
    private val rolling = 0.004 * mass * 9.81
    private val drag = 0.5 * 1.225 * 0.32
    /** Current speed in m/s, eased so it doesn't jump with every pedal stroke. */
    var speed = 0.0
        private set

    /** Advance one second at [watts]; returns metres covered in that second. */
    fun step(watts: Double): Double {
        val target = steadySpeed(watts)
        // Easing stands in for momentum: speed builds and fades over a few seconds.
        speed += (target - speed) * 0.35
        if (speed < 0.05) speed = 0.0
        return speed
    }

    fun steadySpeed(watts: Double): Double {
        if (watts <= 0) return 0.0
        var lo = 0.0
        var hi = 30.0
        repeat(40) {
            val v = (lo + hi) / 2
            if (v * (rolling + drag * v * v) > watts) hi = v else lo = v
        }
        return (lo + hi) / 2
    }
}
