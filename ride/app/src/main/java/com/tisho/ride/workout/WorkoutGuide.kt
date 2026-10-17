package com.tisho.ride.workout

/** Plain-language explanation shown before a rider picks a workout. */
data class Guide(val forWho: String, val feels: String, val howOften: String)

object WorkoutGuides {
    private val rehitGuide = Guide(
        "Everyone short on time, including beginners and older riders (start with the starter weeks).",
        "Easy pedalling with two moments of maximal effort. The sprints are brutal for 20 seconds, then it's over.",
        "2–3 times a week, never on back-to-back days.",
    )

    private val guides = mapOf(
        "rehit" to rehitGuide,
        "rehit10" to rehitGuide.copy(feels = "Easy pedalling with two 10-second all-out bursts. Short, sharp and over quickly.", howOften = "2–3 rides in your first week, then move on to 2×15."),
        "rehit15" to rehitGuide.copy(feels = "Easy pedalling with two 15-second all-out sprints. Harder than week 1, still very short.", howOften = "2–3 rides in your second week, then graduate to 2×20."),
        "rehit3" to rehitGuide.copy(feels = "Like REHIT 2×20 with one more sprint. Not more effective, just different."),
        "micro6" to Guide("Older or returning riders, or anyone wanting a gentle intro to sprinting.",
            "Six short bursts that feel lively rather than exhausting.", "2–3 times a week."),
        "tabata" to Guide("Fit riders who like short, very hard sessions.",
            "Four minutes that get progressively harder — the last rounds are a fight.", "Once a week at most."),
        "sit4x30" to Guide("Experienced riders with several weeks of REHIT behind them.",
            "Four long, maximal sprints. Very tough; expect to need the full recoveries.", "Once a week at most."),
        "4x4" to Guide("Anyone wanting a big boost to aerobic fitness and heart health.",
            "Four solid 4-minute efforts where talking is hard. Tough but sustainable.", "1–2 times a week."),
        "4x8" to Guide("Intermediate riders looking for the next step after 4×4.",
            "Long, steady efforts just above comfortable. Mentally demanding.", "Once a week."),
        "5x3" to Guide("Riders targeting VO₂max who have done some intervals before.",
            "Hard 3-minute pushes with equal rest. Breathing heavy by the end of each.", "Once a week."),
        "10x1" to Guide("Beginners to interval training and busy riders.",
            "Ten short hard minutes with easy minutes between — manageable chunks.", "1–2 times a week."),
        "60-30" to Guide("Intermediate to advanced riders.", "Short rests make it pile up — hard but rhythmic.", "Once a week."),
        "30-15" to Guide("Trained riders chasing top-end aerobic fitness.", "Relentless short efforts; the third set is the real test.", "Once a week."),
        "40-20" to Guide("Riders new to short intervals.", "Punchy and fun, with time passing quickly.", "1–2 times a week."),
        "overunder" to Guide("Riders who already know their FTP well.", "Steady pressure with repeated surges.", "Once a week."),
        "pyramid" to Guide("Anyone wanting variety in their interval training.", "Builds up then winds down — the middle is hardest.", "Once a week."),
        "sweetspot" to Guide("Time-crunched riders building endurance and threshold.", "Comfortably hard — you can talk in short phrases.", "1–2 times a week."),
        "2x20" to Guide("Riders wanting to lift the power they can hold for long.", "Two long, focused efforts. Hard mentally more than physically.", "Once a week."),
        "hills" to Guide("Everyone — a varied, moderate ride.", "Heavier climbs and light descents at a steady cadence.", "Any day."),
        "torque" to Guide("Riders wanting muscular endurance and variety.", "Slow, heavy pedal strokes alternating with fast spins.", "Once a week."),
        "zone2-30" to Guide("Everyone, especially beginners and on easy days.", "Easy — you can hold a full conversation.", "As often as you like."),
        "zone2-45" to Guide("Everyone, especially beginners and on easy days.", "Easy — you can hold a full conversation.", "As often as you like."),
        "zone2-60" to Guide("Riders building endurance or burning more calories.", "Easy but long; stay patient.", "1–3 times a week."),
        "steady30" to Guide("Everyone — a good everyday habit.", "Moderate and steady; breathing a little faster.", "3–5 times a week."),
        "recovery" to Guide("The day after a hard session.", "Very easy, legs spinning lightly.", "After hard days."),
        "ramp" to Guide("Everyone, after a few rides — it personalises all interval targets.",
            "Easy at first, harder every minute, until you can't keep the pedals turning. Hard for the last 2–3 minutes only.",
            "Every 6–8 weeks."),
        "ftp20" to Guide("Experienced riders who can pace a 20-minute effort.",
            "A long, steady maximal effort. You set the resistance yourself.", "Every 6–8 weeks, instead of the ramp test."),
        "free" to Guide("Anyone — ride your own way.", "Whatever you make it.", "Any time."),
        "erg" to Guide("Riders who want a steady wattage without thinking about resistance.", "Steady; the bike does the work of holding the power.", "Any time."),
    )

    fun of(w: Workout): Guide = guides[w.id] ?: Guide("Your custom workout.", "As you designed it.", "Up to you.")
}
