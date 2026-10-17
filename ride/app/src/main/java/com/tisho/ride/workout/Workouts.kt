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

    /** All-out or VO₂max-level work: needs a clear health check (PAR-Q+) first. */
    val vigorous: Boolean get() = steps.any { it.type == TargetType.SPRINT || it.type == TargetType.WATTS || (it.type == TargetType.FTP_PCT && it.value >= 105) }
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

    private const val REHIT_SCIENCE =
        "Reduced-Exertion HIIT: two 20-s all-out sprints are enough. A 2026 meta-analysis of 93 trials (Hutchinson, Metcalfe, " +
            "Vollaard, Scand J Med Sci Sports) found sprint interval training raises VO₂max ~8% and extra sprints add nothing — " +
            "what matters is sticking with it for more weeks. 2×20 s beat 2×10 s (≈10% vs 4% VO₂max, Nalçakan 2018) and two " +
            "sessions a week work as well as three or four (Thomas 2020). In the CAROL bike trial (Cuddy 2019) REHIT raised VO₂max " +
            "12% vs 7% for 30-min moderate rides and lowered systolic blood pressure. Your sprint load starts near 7.5% of body " +
            "mass and adapts every ride so cadence stays in the effective range."

    private fun rehitWorkout(id: String, name: String, sprintSec: Int, summary: String) = Workout(
        id, name, Category.QUICK, summary, REHIT_SCIENCE,
        listOf(
            ftp(180, "Warm-up", 40, "Easy spin, 70–90 rpm", 70, 90),
            sprint(sprintSec),
            ftp(180, "Recover", 30, "Easy pedalling, breathe deep", 60, 80),
            sprint(sprintSec),
            ftp(180, "Cool-down", 30, "Easy spin and recover", 60, 80),
        ),
        intensity = 5,
    )

    val rehit = rehitWorkout("rehit", "REHIT 2×20", 20,
        "Under 10 minutes: two 20-second all-out sprints. Twice a week is enough. The sprint load adapts to you every ride.")
    val rehit10 = rehitWorkout("rehit10", "REHIT starter 2×10", 10,
        "Week 1 of REHIT: two 10-second sprints to learn the all-out effort. Ride it 2–3 times, then move to 2×15.")
    val rehit15 = rehitWorkout("rehit15", "REHIT build 2×15", 15,
        "Week 2 of REHIT: two 15-second sprints. Ride it 2–3 times, then graduate to 2×20.")

    val rehit3 = Workout(
        "rehit3", "REHIT 3×20", Category.QUICK,
        "The Gillen protocol: three 20-s all-out sprints inside 10 minutes.",
        "Gillen et al. 2016 (PLoS One): 3×20-s sprints, 3×/week for 12 weeks, matched 45-min moderate sessions for VO₂max and " +
            "insulin sensitivity. Newer meta-analyses show the third sprint adds little — choose it for variety, not extra gains.",
        listOf(
            ftp(180, "Warm-up", 40, "Easy spin", 70, 90),
            sprint(), ftp(120, "Recover", 30, "Easy", 60, 80),
            sprint(), ftp(120, "Recover", 30, "Easy", 60, 80),
            sprint(), ftp(180, "Cool-down", 30, "Easy spin", 60, 80),
        ),
        intensity = 5,
    )

    val microSprints = Workout(
        "micro6", "Micro-sprints 6×6", Category.QUICK,
        "Six 6-second sprints with a minute easy between. Gentle on the body, great for older or returning riders.",
        "Twice-weekly 6-s sprints in adults ~66 years old improved blood pressure, arterial stiffness and the timed up-and-go " +
            "test (Adamson et al. 2019, Sport Sci Health). Short sprints keep blood-pressure peaks lower than 20-s efforts.",
        listOf(ftp(180, "Warm-up", 40, "Easy spin", 70, 90)) +
            repeat(6, Step(6, "SPRINT", TargetType.SPRINT, 0, "Quick burst!", 80, 110), ftp(60, "Easy", 30, "Recover", 60, 80)) +
            listOf(ftp(180, "Cool-down", 30, "Easy spin", 60, 80)),
        intensity = 3,
    )

    val sit4x30 = Workout(
        "sit4x30", "Sprint intervals 4×30", Category.QUICK,
        "Advanced: four 30-second all-out Wingate sprints with 4-minute recoveries.",
        "Classic sprint interval training raises VO₂max and muscle oxidative capacity (Vollaard & Metcalfe 2017, Sports Med), " +
            "but it is no better than 2×20 s REHIT and much harder to tolerate. Only for riders with several weeks of REHIT behind them.",
        listOf(ftp(300, "Warm-up", 50, "Build gradually", 75, 95)) +
            repeat(4, Step(30, "SPRINT", TargetType.SPRINT, 0, "All out for 30 seconds!", 90, 120), ftp(240, "Recover", 30, "Easy, keep moving", 60, 80)).dropLast(1) +
            listOf(ftp(180, "Cool-down", 30, "Easy spin", 60, 80)),
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

    private val warm = listOf(ftp(200, "Warm-up", 50, "Easy", 80, 95), ftp(200, "Warm-up", 62, "Build", 85, 95), ftp(200, "Warm-up", 75, "Ready", 85, 95))

    val seiler4x8 = Workout(
        "4x8", "Seiler 4×8", Category.INTERVALS,
        "Four 8-minute efforts just above threshold with 2-minute recoveries. A proven VO₂max builder.",
        "In recreational cyclists, 4×8-min intervals (~90% HRmax) improved performance more than 4×4 or 4×16 (Seiler et al. " +
            "2013). Meta-analysis: intervals ≥2 min and ≥15 min of total hard work give the largest VO₂max gains (Wen 2019).",
        warm + repeat(4, ftp(480, "8-min effort", 105, "Strong and steady", 85, 100), ftp(120, "Recover", 50, "Easy", 80, 90)).dropLast(1) +
            listOf(ftp(300, "Cool-down", 45, "Easy", 80, 90)),
        intensity = 4,
    )

    val vo2_5x3 = Workout(
        "5x3", "VO₂max 5×3", Category.INTERVALS,
        "Five 3-minute efforts at 115% FTP with equal recovery.",
        "Long intervals (≥2 min) near maximal aerobic power give the biggest VO₂max gains in meta-analyses (Wen et al. 2019, " +
            "J Sci Med Sport). A step up from 4×4 and a bridge to 4×8.",
        warm + repeat(5, ftp(180, "3-min effort", 115, "Hard, breathe rhythmically", 90, 105), ftp(180, "Recover", 50, "Easy", 80, 90)).dropLast(1) +
            listOf(ftp(300, "Cool-down", 45, "Easy", 80, 90)),
        intensity = 5,
    )

    val sixtyThirty = Workout(
        "60-30", "60/30 intervals", Category.INTERVALS,
        "Two sets of 8×(60 s hard / 30 s easy). Lots of time near VO₂max.",
        "60/30 intervals keep oxygen uptake above 90% of max for much longer than 30/30 (531 s vs 149 s, Millet et al. 2003), " +
            "and short-interval formats matched or beat long intervals for performance (Rønnestad 2015; Almquist 2020).",
        warm + repeat(2, *(repeat(8, ftp(60, "On", 115, "Hard", 90, 105), ftp(30, "Off", 50, "Easy"))).toTypedArray(), ftp(240, "Set recovery", 45, "Easy spin")).dropLast(1) +
            listOf(ftp(300, "Cool-down", 45, "Easy")),
        intensity = 5,
    )

    val microbursts = Workout(
        "40-20", "40/20 microbursts", Category.INTERVALS,
        "Two sets of 10×(40 s at 120% / 20 s easy). Punchy but manageable.",
        "A common coaching format for accumulating time at high intensity with short recoveries. Evidence is mostly " +
            "physiological rather than long-term trials — a good, varied entry to short intervals.",
        warm + repeat(2, *(repeat(10, ftp(40, "On", 120, "Punch it", 90, 110), ftp(20, "Off", 50, "Easy"))).toTypedArray(), ftp(300, "Set recovery", 45, "Easy spin")).dropLast(1) +
            listOf(ftp(300, "Cool-down", 45, "Easy")),
        intensity = 4,
    )

    val overUnders = Workout(
        "overunder", "Over-unders 3×9", Category.INTERVALS,
        "Three 9-minute blocks alternating just under and just over threshold. Teaches you to clear fatigue at pace.",
        "Coaching practice rather than a studied protocol: surging above threshold then recovering slightly below it is used " +
            "by coaches to train lactate clearance and pacing.",
        warm + repeat(3, *(repeat(3, ftp(120, "Under", 95, "Just below threshold", 85, 95), ftp(60, "Over", 108, "Surge", 85, 95))).toTypedArray(), ftp(300, "Easy", 50, "Recover")).dropLast(1) +
            listOf(ftp(300, "Cool-down", 45, "Easy")),
        intensity = 4,
    )

    val torque = Workout(
        "torque", "Low-cadence tempo 4×6", Category.ENDURANCE,
        "Four 6-minute big-gear efforts at 55–65 rpm, with fast spins between. Strength-endurance variety.",
        "Low-cadence work wasn't better than normal cadence for performance (Kristoffersen et al. 2014), so treat it as " +
            "variety and muscular conditioning. Keep cadence at or above 55 rpm to protect your knees.",
        listOf(ftp(600, "Warm-up", 55, "Easy", 80, 95)) +
            repeat(4, ftp(360, "Big gear", 80, "Seated, smooth pedal strokes", 55, 65), ftp(180, "Spin", 55, "Light and fast", 90, 100)).dropLast(1) +
            listOf(ftp(300, "Cool-down", 45, "Easy")),
        intensity = 3,
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

    private fun zone2(minutes: Int) = Workout(
        "zone2-$minutes", "Zone 2 · $minutes min", Category.ENDURANCE,
        "$minutes minutes at conversational pace. Builds your aerobic base with little fatigue.",
        "Most endurance training should be easy (Seiler 2010). A 2025 review (Storoschuk, Sports Med) found zone 2 isn't " +
            "magically better than other intensities for VO₂max, but it is valuable low-stress volume and improves fat oxidation " +
            "in untrained people. You should be able to talk in full sentences.",
        listOf(ftp(300, "Warm-up", 50, "Easy", 80, 90), ftp(minutes * 60 - 600, "Zone 2", 65, "Conversational pace", 85, 95), ftp(300, "Cool-down", 45, "Easy")),
        intensity = 2,
    )

    val zone2 = zone2(45)
    val zone2short = zone2(30)
    val zone2long = zone2(60)

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

    /** Ramp test: +20 W/min, or +10 W/min for riders under 60 kg or aged 60+. */
    fun ramp(stepWatts: Int = 20) = Workout(
        "ramp", "Ramp FTP test", Category.TEST,
        "Power rises every minute until you can't keep going. Sets your FTP and estimates VO₂max.",
        "FTP ≈ 75% of your best 1-minute power in a ramp test (coaching standard; true value is usually 72–77%). Riders with a " +
            "big sprint may be overestimated. VO₂max is estimated from peak power with the ACSM cycling equation. Ride until " +
            "failure — the test ends when your cadence collapses or you press Finish.",
        listOf(ftp(300, "Warm-up", 45, "Easy, 85–95 rpm", 85, 95)) +
            (0 until 50).map { i -> Step(60, "Ramp ${i + 1}", TargetType.WATTS, (if (stepWatts == 20) 100 else 60) + stepWatts * i, "Hold your cadence", 80, 100) },
        openEnded = true,
        intensity = 5,
    )

    val ftp20 = Workout(
        "ftp20", "20-minute FTP test", Category.TEST,
        "Classic test: warm up, one 5-minute effort, then your best steady 20 minutes. You control the resistance.",
        "FTP = 95% of 20-min power for trained riders. Against lab lactate tests 95% tends to overestimate recreational riders " +
            "(Borszcz 2019 found ~91% fits better), so the app uses 92% if your power-to-weight is under 3 W/kg. Pace evenly: " +
            "start a little conservative and finish strong.",
        listOf(ftp(600, "Warm-up", 55, "Easy, build gradually", 85, 95), ftp(300, "Opener", 105, "Hard, but not all out", 90, 100),
            ftp(300, "Recover", 45, "Easy"), Step(1200, "20-min test", TargetType.FREE, 30, "Your best steady effort. Use − / +", 85, 100),
            ftp(300, "Cool-down", 40, "Easy")),
        intensity = 5,
    )

    val ramp = ramp()

    val all: List<Workout> = listOf(rehit, rehit10, rehit15, rehit3, microSprints, tabata, sit4x30, norwegian, seiler4x8, vo2_5x3,
        tenByOne, sixtyThirty, thirtyFifteen, microbursts, overUnders, pyramid, sweetSpot, threshold, hills, torque, zone2short, zone2,
        zone2long, fatBurn, recovery, ramp, ftp20, freeRide, ergRide)

    fun byId(id: String) = all.firstOrNull { it.id == id }
}
