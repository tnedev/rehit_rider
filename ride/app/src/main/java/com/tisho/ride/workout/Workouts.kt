package com.tisho.ride.workout

import kotlinx.serialization.Serializable

/** How resistance is chosen during a step. */
@Serializable
enum class TargetType {
    /** Fixed level 0..100 (rider can nudge it). */
    LEVEL,
    /** ERG: hold a percentage of the rider's FTP regardless of cadence. */
    FTP_PCT,
    /** ERG: hold absolute watts (ramp test). */
    WATTS,
    /** All-out sprint at the rider's adaptive sprint resistance. */
    SPRINT,
    /** Rider controls the level freely. */
    FREE,
}

@Serializable
data class Step(
    val seconds: Int,
    val label: String,
    val type: TargetType,
    val value: Int = 0,
    val cue: String = "",
    val rpmLow: Int = 0,
    val rpmHigh: Int = 0,
) {
    val isHard: Boolean get() = when (type) {
        TargetType.SPRINT -> true
        TargetType.FTP_PCT -> value >= 95
        TargetType.LEVEL -> value >= 45
        TargetType.WATTS -> true
        TargetType.FREE -> false
    }
}

enum class Category(val title: String) {
    QUICK("Short & intense"),
    INTERVALS("Intervals"),
    ENDURANCE("Endurance"),
    TEST("Tests"),
    FREE("Free ride"),
    CUSTOM("My workouts"),
}

data class Workout(
    val id: String,
    val name: String,
    val category: Category,
    val summary: String,
    val science: String,
    val steps: List<Step>,
    /** Open-ended ramp: keeps adding steps until the rider stops. */
    val openEnded: Boolean = false,
    val intensity: Int = 3,
) {
    val seconds: Int get() = steps.sumOf { it.seconds }
    val minutes: Int get() = (seconds + 30) / 60
}

private fun lvl(s: Int, label: String, level: Int, cue: String = "", lo: Int = 0, hi: Int = 0) = Step(s, label, TargetType.LEVEL, level, cue, lo, hi)
private fun ftp(s: Int, label: String, pct: Int, cue: String = "", lo: Int = 0, hi: Int = 0) = Step(s, label, TargetType.FTP_PCT, pct, cue, lo, hi)
private fun sprint(s: Int = 20) = Step(s, "SPRINT", TargetType.SPRINT, 0, "All out! Pedal as fast as you can", 0, 0)

private fun repeat(n: Int, vararg block: Step) = (1..n).flatMap { block.toList() }

object WorkoutLibrary {
    val freeRide = Workout(
        "free", "Free ride", Category.FREE,
        "Just ride. Set the resistance with − / + and watch your numbers.",
        "Any movement counts. WHO guidelines recommend 150–300 min of moderate or 75–150 min of vigorous activity per week; " +
            "free rides count toward your weekly goal.",
        listOf(Step(4 * 3600, "Free ride", TargetType.FREE, 20)),
        intensity = 1,
    )

    val ergRide = Workout(
        "erg", "Power hold (ERG)", Category.FREE,
        "Pick a wattage; the bike adjusts resistance so your power stays put whatever your cadence.",
        "ERG mode removes gear choice so you can focus on cadence and breathing. Use − / + to change the target in 5 W steps.",
        listOf(Step(4 * 3600, "Hold power", TargetType.FTP_PCT, 65)),
        intensity = 2,
    )

    val rehit = Workout(
        "rehit", "REHIT 2×20", Category.QUICK,
        "Under 9 minutes: two 20-second all-out sprints against heavy resistance. The sprint load adapts to you every ride.",
        "Reduced-Exertion HIIT (Metcalfe et al. 2012; Vollaard et al. 2017 meta-analysis) showed two 20-s supramaximal sprints " +
            "per session, 3×/week, improve VO₂max comparably to much longer moderate training. Gains plateau beyond two sprints, " +
            "which is why the protocol is so short. Studies on adaptive-resistance bikes (Cuddy et al. 2019) found larger VO₂max " +
            "gains than 45-min moderate sessions.",
        listOf(
            lvl(120, "Warm-up", 15, "Easy spin, 70–90 rpm", 70, 90),
            lvl(10, "Get ready", 15, "Spin up — sprint in 10 s"),
            sprint(),
            lvl(180, "Recover", 8, "Easy pedalling, breathe deep"),
            lvl(10, "Get ready", 15, "Spin up — sprint in 10 s"),
            sprint(),
            lvl(180, "Cool-down", 10, "Easy spin and recover"),
        ),
        intensity = 5,
    )

    val rehit3 = Workout(
        "rehit3", "REHIT 3×20", Category.QUICK,
        "The Gillen protocol: three 20-s all-out sprints inside 10 minutes.",
        "Gillen et al. 2016 (PLoS One): 3×20-s all-out sprints in a 10-min session, 3×/week for 12 weeks, matched 45-min " +
            "moderate sessions for VO₂max and insulin sensitivity gains — one fifth of the time commitment.",
        listOf(
            lvl(120, "Warm-up", 15, "Easy spin", 70, 90),
            sprint(), lvl(120, "Recover", 8, "Easy"),
            sprint(), lvl(120, "Recover", 8, "Easy"),
            sprint(), lvl(180, "Cool-down", 10, "Easy spin"),
        ),
        intensity = 5,
    )

    val norwegian = Workout(
        "4x4", "Norwegian 4×4", Category.INTERVALS,
        "Four 4-minute efforts at 85–95% max heart rate with 3-minute active recoveries.",
        "Helgerud et al. 2007 showed 4×4 intervals raised VO₂max ~7% in 8 weeks versus no change with steady training of " +
            "equal work. The Generation 100 trial (Stensvold et al. 2020) used it for healthy ageing. Aim for heart rate " +
            "85–95% of max in the second half of each interval.",
        listOf(ftp(600, "Warm-up", 55, "Build gradually", 75, 95)) +
            repeat(4, ftp(240, "4-min interval", 108, "Strong and steady, 85–95% HRmax", 80, 100), ftp(180, "Active recovery", 50, "Keep legs moving", 70, 90)).dropLast(1) +
            listOf(ftp(300, "Cool-down", 45, "Easy")),
        intensity = 4,
    )

    val tabata = Workout(
        "tabata", "Tabata 20/10", Category.QUICK,
        "Eight rounds of 20 seconds very hard, 10 seconds easy. 4 brutal minutes.",
        "Tabata et al. 1996: 6 weeks of 8×(20 s at ~170% VO₂max / 10 s rest) improved both aerobic capacity and anaerobic " +
            "capacity by 28%. Expect to be unable to hold the target in the final rounds — that's the point.",
        listOf(ftp(360, "Warm-up", 55, "Easy to moderate", 80, 95)) +
            repeat(8, ftp(20, "Hard", 160, "Go!", 90, 120), ftp(10, "Rest", 30, "Breathe")) +
            listOf(ftp(300, "Cool-down", 45, "Easy")),
        intensity = 5,
    )

    val tenByOne = Workout(
        "10x1", "10×1 minute", Category.INTERVALS,
        "Ten 1-minute efforts at ~90% max heart rate, 1 minute easy between.",
        "Little et al. 2010 and Gibala's lab: low-volume HIIT (10×60 s at ~90% HRmax, 3×/week) increased muscle mitochondrial " +
            "capacity and endurance performance similar to traditional endurance training in a fraction of the time.",
        listOf(ftp(300, "Warm-up", 55, "Easy", 80, 95)) +
            repeat(10, ftp(60, "1-min effort", 115, "Hard but controlled", 85, 105), ftp(60, "Easy", 45, "Recover")).dropLast(1) +
            listOf(ftp(300, "Cool-down", 45, "Easy")),
        intensity = 4,
    )

    val thirtyFifteen = Workout(
        "30-15", "Rønnestad 30/15", Category.INTERVALS,
        "Three sets of 13×(30 s hard / 15 s easy). Big time at high oxygen uptake.",
        "Rønnestad et al. 2015/2020: short 30/15 intervals produced greater time near VO₂max and larger performance gains in " +
            "trained cyclists than 4×5-min long intervals at the same perceived effort.",
        listOf(ftp(600, "Warm-up", 55, "Build", 80, 95)) +
            repeat(3, *(repeat(13, ftp(30, "On", 120, "Hard", 90, 110), ftp(15, "Off", 50, "Easy")).toTypedArray()), ftp(180, "Set recovery", 45, "Easy spin")).dropLast(1) +
            listOf(ftp(300, "Cool-down", 45, "Easy")),
        intensity = 5,
    )

    val sweetSpot = Workout(
        "sweetspot", "Sweet spot 3×10", Category.INTERVALS,
        "Three 10-minute blocks just below threshold. Efficient fitness builder.",
        "Riding at 88–94% of FTP gives a high training stimulus for modest fatigue (Coggan & Allen, Training and Racing with " +
            "a Power Meter). A staple for time-crunched riders.",
        listOf(ftp(480, "Warm-up", 55, "Build", 80, 95)) +
            repeat(3, ftp(600, "Sweet spot", 90, "Comfortably hard", 85, 100), ftp(180, "Easy", 50, "Recover")).dropLast(1) +
            listOf(ftp(300, "Cool-down", 45, "Easy")),
        intensity = 3,
    )

    val threshold = Workout(
        "2x20", "Threshold 2×20", Category.INTERVALS,
        "The classic: two 20-minute efforts at threshold.",
        "Sustained work at 95–100% FTP raises the power you can hold for an hour (Coggan). Hard, but the most direct way to " +
            "lift threshold.",
        listOf(ftp(600, "Warm-up", 55, "Build", 80, 95), ftp(1200, "Threshold", 97, "Steady and strong", 85, 100),
            ftp(300, "Easy", 50, "Recover"), ftp(1200, "Threshold", 97, "Hold it", 85, 100), ftp(300, "Cool-down", 45, "Easy")),
        intensity = 4,
    )

    val pyramid = Workout(
        "pyramid", "Pyramid", Category.INTERVALS,
        "1-2-3-4-3-2-1 minute efforts with equal recovery. Variety keeps it moving.",
        "Mixed-duration intervals accumulate time at high intensity while the changing structure keeps perceived effort " +
            "lower than repeating the longest interval.",
        listOf(ftp(300, "Warm-up", 55, "Build", 80, 95)) +
            listOf(1, 2, 3, 4, 3, 2, 1).flatMap { m -> listOf(ftp(m * 60, "$m-min effort", if (m <= 2) 115 else 105, "Hard", 85, 105), ftp(m * 60, "Easy", 50, "Recover")) }.dropLast(1) +
            listOf(ftp(300, "Cool-down", 45, "Easy")),
        intensity = 4,
    )

    val hills = Workout(
        "hills", "Rolling hills", Category.ENDURANCE,
        "30 minutes of climbs and descents. Resistance changes, you keep the cadence.",
        "Varying resistance at steady cadence recruits more muscle fibres and builds strength-endurance, while staying mostly " +
            "aerobic.",
        listOf(lvl(300, "Flat", 15, "Settle in", 80, 90)) +
            repeat(4, lvl(120, "Climb", 40, "Seated climb, 65–75 rpm", 65, 75), lvl(60, "Steep", 55, "Dig in", 60, 70), lvl(120, "Descent", 12, "Spin it out", 90, 100)) +
            listOf(lvl(300, "Cool-down", 10, "Easy")),
        intensity = 3,
    )

    val zone2 = Workout(
        "zone2", "Zone 2 endurance", Category.ENDURANCE,
        "45 minutes conversational pace. Builds your aerobic base and fat oxidation.",
        "Elite endurance athletes do ~80% of training at low intensity (Seiler 2010, polarized model). Zone 2 training " +
            "increases mitochondrial density and fat oxidation with little fatigue. You should be able to talk in full sentences.",
        listOf(ftp(300, "Warm-up", 50, "Easy", 80, 90), ftp(2400, "Zone 2", 68, "Conversational pace", 80, 95), ftp(300, "Cool-down", 45, "Easy")),
        intensity = 2,
    )

    val fatBurn = Workout(
        "steady30", "Steady 30", Category.ENDURANCE,
        "30 minutes moderate and steady. Good daily habit.",
        "Moderate continuous cycling 3–5×/week lowers blood pressure and improves lipid profile (ACSM position stand).",
        listOf(ftp(240, "Warm-up", 50, "Easy", 80, 90), ftp(1320, "Steady", 72, "Moderate, steady breathing", 80, 95), ftp(240, "Cool-down", 45, "Easy")),
        intensity = 2,
    )

    val recovery = Workout(
        "recovery", "Recovery spin", Category.ENDURANCE,
        "20 minutes very easy to flush the legs after hard days.",
        "Light active recovery (<55% FTP) promotes blood flow without adding training stress.",
        listOf(ftp(1200, "Easy spin", 45, "Light, high cadence", 85, 100)),
        intensity = 1,
    )

    val ramp = Workout(
        "ramp", "Ramp FTP test", Category.TEST,
        "Power rises 20 W every minute until you can't keep going. Sets your FTP and estimates VO₂max.",
        "Ramp tests estimate FTP as ~75% of the best 1-minute power (Michalik et al. 2019 showed close agreement with 20-min " +
            "tests). VO₂max is estimated from peak power with the ACSM cycling equation. Ride until failure — the test ends " +
            "when you stop pedalling or press Finish.",
        listOf(ftp(300, "Warm-up", 45, "Easy, 85–95 rpm", 85, 95)) +
            (0 until 40).map { i -> Step(60, "Ramp ${i + 1}", TargetType.WATTS, 100 + 20 * i, "Hold your cadence", 80, 100) },
        openEnded = true,
        intensity = 5,
    )

    val all: List<Workout> = listOf(rehit, rehit3, tabata, norwegian, tenByOne, thirtyFifteen, pyramid, sweetSpot, threshold,
        hills, zone2, fatBurn, recovery, ramp, freeRide, ergRide)

    fun byId(id: String) = all.firstOrNull { it.id == id }
}
