package com.tisho.ride.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.tisho.ride.bike.BikeStatus
import com.tisho.ride.data.Profile
import com.tisho.ride.data.Ride
import com.tisho.ride.ui.AppViewModel
import com.tisho.ride.ui.C
import com.tisho.ride.ui.Chip
import com.tisho.ride.ui.Label
import com.tisho.ride.ui.Panel
import com.tisho.ride.ui.Ring
import com.tisho.ride.ui.SectionTitle
import com.tisho.ride.ui.WorkoutProfile
import com.tisho.ride.workout.Category
import com.tisho.ride.workout.Metrics
import com.tisho.ride.workout.Workout
import com.tisho.ride.workout.WorkoutLibrary
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(vm: AppViewModel, nav: NavHostController) {
    val profile by vm.profile.collectAsState()
    val rides by vm.rides.collectAsState()
    val workouts by vm.workouts.collectAsState()
    val p = profile ?: return
    val week = Metrics.startOfWeek(System.currentTimeMillis())
    val weekRides = rides.filter { it.startedAt >= week }
    val minutes = weekRides.sumOf { Metrics.activeMinutes(it) }
    val load = Metrics.trainingLoad(rides).lastOrNull()?.second
    val (rec, why) = recommend(rides, load)

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 32.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.height(260.dp)) {
                Panel(Modifier.weight(1f).fillMaxSize()) {
                    Label("This week")
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize()) {
                        Ring(minutes / p.weeklyGoalMinutes.toFloat(), C.accent, Modifier.size(170.dp), 16.dp) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$minutes", style = MaterialTheme.typography.displaySmall)
                                Text("of ${p.weeklyGoalMinutes} min", color = C.muted, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        Spacer(Modifier.width(28.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            MiniStat(Icons.Filled.LocalFireDepartment, "${Metrics.weekStreak(rides)} week streak", C.accent)
                            MiniStat(Icons.Filled.Bolt, "${weekRides.size} rides · ${weekRides.sumOf { it.kcal }.toInt()} kcal", C.power)
                            MiniStat(Icons.Filled.Schedule, "${weekRides.sumOf { it.durationSec } / 60} min ridden", C.cadence)
                        }
                    }
                }
                RecommendCard(rec, why, p, Modifier.weight(1.3f).fillMaxSize(), vm) { nav.navigate("workout/${rec.id}") }
            }
        }
        val groups = workouts.groupBy { it.category }
        for (cat in Category.entries) {
            val list = groups[cat].orEmpty()
            if (list.isEmpty() && cat != Category.CUSTOM) continue
            item {
                SectionTitle(cat.title, trailing = if (cat == Category.CUSTOM) ({
                    OutlinedButton(onClick = { nav.navigate("builder") }) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Build workout") }
                }) else null)
                if (list.isEmpty()) {
                    Text("Design your own intervals with the workout builder.", color = C.muted)
                } else LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(list, key = { it.id }) { w -> WorkoutCard(w, p) { nav.navigate("workout/${w.id}") } }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** Simple coach: hard sessions when fresh, easy when fatigued, REHIT if nothing recent. */
private fun recommend(rides: List<Ride>, load: Metrics.Load?): Pair<Workout, String> {
    val last = rides.firstOrNull()
    val daysSince = if (last == null) 99 else ((System.currentTimeMillis() - last.startedAt) / 86_400_000L).toInt()
    val form = load?.form ?: 0.0
    val lastHard = last != null && (last.intensityFactor >= 0.9 || last.workoutId.startsWith("rehit") || last.workoutId == "tabata")
    return when {
        rides.isEmpty() -> WorkoutLibrary.rehit to "Start here: under 9 minutes, scientifically proven to raise fitness fast."
        rides.none { it.workoutId == "ramp" } && rides.size >= 3 -> WorkoutLibrary.ramp to "You have a few rides in. Take the ramp test to personalise every ERG workout."
        form < -25 -> WorkoutLibrary.recovery to "Your fatigue is high (form ${form.toInt()}). Easy spin today helps you absorb the training."
        lastHard && daysSince < 1 -> WorkoutLibrary.zone2 to "You went hard recently. Aerobic base work today, intensity again tomorrow."
        daysSince >= 2 -> WorkoutLibrary.rehit to "It's been $daysSince days. A quick REHIT keeps the momentum."
        form > 5 -> WorkoutLibrary.norwegian to "You're fresh (form +${form.toInt()}). A great day for a quality VO₂max session."
        else -> WorkoutLibrary.sweetSpot to "Steady, productive work that builds threshold without much fatigue."
    }
}

@Composable
private fun MiniStat(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun RecommendCard(w: Workout, why: String, p: Profile, modifier: Modifier, vm: AppViewModel, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(C.accent.copy(alpha = 0.35f), C.surface)))
            .clickable(onClick = onClick).padding(24.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            Label("Recommended today", C.text)
            Spacer(Modifier.height(8.dp))
            Text(w.name, style = MaterialTheme.typography.displaySmall)
            Text("${w.minutes} min · ${"●".repeat(w.intensity)}${"○".repeat(5 - w.intensity)}", color = C.muted)
            Spacer(Modifier.height(8.dp))
            Text(why, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.Bottom) {
                WorkoutProfile(w.steps, p.ftp, Modifier.weight(1f).height(48.dp))
                Spacer(Modifier.width(20.dp))
                Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = C.accent)) {
                    Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Let's go")
                }
            }
        }
    }
}

@Composable
private fun WorkoutCard(w: Workout, p: Profile, onClick: () -> Unit) {
    Panel(Modifier.width(300.dp).height(200.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(w.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (w.category == Category.FREE) "∞" else "${w.minutes}′", style = MaterialTheme.typography.titleLarge, color = C.accent)
        }
        Spacer(Modifier.height(6.dp))
        Text(w.summary, style = MaterialTheme.typography.bodyMedium, color = C.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f))
        WorkoutProfile(w.steps, p.ftp, Modifier.fillMaxWidth().height(52.dp))
    }
}

@Composable
fun WorkoutDetailScreen(vm: AppViewModel, nav: NavHostController, id: String) {
    val profile by vm.profile.collectAsState()
    val bike by vm.bike.status.collectAsState()
    val w = vm.workout(id) ?: return
    val p = profile ?: return
    val scope = rememberCoroutineScope()
    Row(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.weight(1.4f).verticalScroll(rememberScrollState())) {
            Text(w.name, style = MaterialTheme.typography.displaySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 12.dp)) {
                Chip(if (w.category == Category.FREE) "Open" else "${w.minutes} min", C.cadence)
                Chip("Intensity ${w.intensity}/5", C.accent)
                Chip(w.category.title, C.level)
            }
            Text(w.summary, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(20.dp))
            Panel {
                Label("Workout shape")
                Spacer(Modifier.height(12.dp))
                WorkoutProfile(w.steps, p.ftp, Modifier.fillMaxWidth().height(120.dp))
            }
            Spacer(Modifier.height(20.dp))
            Panel {
                Label("The science")
                Spacer(Modifier.height(8.dp))
                Text(w.science, style = MaterialTheme.typography.bodyLarge)
            }
        }
        Column(Modifier.weight(1f)) {
            Panel(Modifier.weight(1f).fillMaxWidth()) {
                Label("Steps")
                Spacer(Modifier.height(8.dp))
                val merged = w.steps.fold(mutableListOf<Pair<String, Int>>()) { acc, s ->
                    if (acc.isNotEmpty() && acc.last().first == s.label) acc[acc.lastIndex] = s.label to acc.last().second + s.seconds else acc += s.label to s.seconds
                    acc
                }
                LazyColumn {
                    items(w.steps.size.coerceAtMost(60)) { i ->
                        val s = w.steps[i]
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                            Text(s.label, modifier = Modifier.weight(1f))
                            Text(describe(s, p.ftp), color = C.muted)
                            Spacer(Modifier.width(16.dp))
                            Text(com.tisho.ride.ui.fmtTime(s.seconds), color = C.text)
                        }
                    }
                }
                if (merged.isEmpty()) Text("No steps", color = C.muted)
            }
            Spacer(Modifier.height(16.dp))
            if (bike != BikeStatus.READY && bike != BikeStatus.SIMULATED) {
                Text("Bike not connected — plug in the bike's USB cable, or turn on Demo bike in Settings.", color = C.power, modifier = Modifier.padding(bottom = 8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (w.category == Category.CUSTOM) {
                    val cid = w.id.removePrefix("custom:").toLong()
                    OutlinedButton(onClick = { nav.navigate("builder?id=$cid") }) { Icon(Icons.Filled.Edit, null) }
                    OutlinedButton(onClick = { scope.launch { vm.repo.deleteCustomWorkout(cid) }; nav.popBackStack() }) { Icon(Icons.Filled.Delete, null) }
                }
                Button(
                    onClick = { vm.startRide(w); nav.navigate("ride") },
                    modifier = Modifier.weight(1f).height(64.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = C.accent),
                ) { Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Start ride", style = MaterialTheme.typography.titleLarge) }
            }
        }
    }
}

fun describe(s: com.tisho.ride.workout.Step, ftp: Int): String = when (s.type) {
    com.tisho.ride.workout.TargetType.LEVEL -> "Level ${s.value}"
    com.tisho.ride.workout.TargetType.FTP_PCT -> "${ftp * s.value / 100} W (${s.value}%)"
    com.tisho.ride.workout.TargetType.WATTS -> "${s.value} W"
    com.tisho.ride.workout.TargetType.SPRINT -> "All out"
    com.tisho.ride.workout.TargetType.FREE -> "Your choice"
}
