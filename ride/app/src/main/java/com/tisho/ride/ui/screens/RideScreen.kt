package com.tisho.ride.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.tisho.ride.ui.AppViewModel
import com.tisho.ride.ui.C
import com.tisho.ride.ui.Label
import com.tisho.ride.ui.LineChart
import com.tisho.ride.ui.Series
import com.tisho.ride.ui.WorkoutProfile
import com.tisho.ride.ui.fmtTime
import com.tisho.ride.workout.Category
import com.tisho.ride.workout.Metrics
import com.tisho.ride.workout.Phase
import com.tisho.ride.workout.TargetType

@Composable
fun RideScreen(vm: AppViewModel, nav: NavHostController) {
    // Hold on to the session for the life of this screen: finishing clears vm.session, and the screen
    // must not react to that by popping the back stack (that used to close the summary before Save).
    val session = remember { vm.session } ?: run {
        androidx.compose.runtime.LaunchedEffect(Unit) { nav.popBackStack("home", false) }
        return
    }
    val st by session.state.collectAsState()
    var confirmStop by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    val step = session.steps[st.stepIndex.coerceIn(0, session.steps.lastIndex)]
    val next = session.steps.getOrNull(st.stepIndex + 1)
    val isSprint = step.type == TargetType.SPRINT && st.phase == Phase.RUNNING
    val bg by animateColorAsState(if (isSprint) C.sprintBg else C.bg, label = "bg")
    val p = session.profile
    val open = session.workout.category == Category.FREE
    val total = session.workout.seconds

    BackHandler { confirmStop = true }

    fun finish() {
        if (finished) return
        finished = true
        vm.finishRide()
        nav.navigate("summary") { popUpTo("home") }
    }
    androidx.compose.runtime.LaunchedEffect(st.phase) {
        if (st.phase == Phase.FINISHED && !finished) finish()
    }

    Box(Modifier.fillMaxSize().background(bg)) {
        Column(Modifier.fillMaxSize().padding(28.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(session.workout.name, style = MaterialTheme.typography.headlineSmall)
                    Text(if (open) "Ride time ${fmtTime(st.elapsed)}" else "${fmtTime(st.elapsed)} / ${fmtTime(total)}", color = C.muted, style = MaterialTheme.typography.titleMedium)
                }
                Text("${st.kcal.toInt()} kcal (${st.activeKcal.toInt()} active) · ${st.kj.toInt()} kJ · avg ${st.avgWatts.toInt()} W", color = C.muted, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(24.dp))
                OutlinedButton(onClick = { session.togglePause() }) {
                    Icon(if (st.phase == Phase.PAUSED) Icons.Filled.PlayArrow else Icons.Filled.Pause, null)
                }
                Spacer(Modifier.width(8.dp))
                if (!open) OutlinedButton(onClick = { session.skipStep() }) { Icon(Icons.Filled.SkipNext, null) }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { finish() }) { Icon(Icons.Filled.Stop, null, tint = C.heart); Text(" Finish") }
            }
            Spacer(Modifier.height(18.dp))

            // Step + countdown
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Column(
                    Modifier.weight(1.15f).fillMaxHeight().clip(RoundedCornerShape(24.dp))
                        .background(if (isSprint) C.accent else C.surface).padding(28.dp),
                ) {
                    val fg = if (isSprint) C.onAccent else C.text
                    val fg2 = if (isSprint) C.onAccent else C.muted
                    Label(if (open) "Free ride" else "Step ${st.stepIndex + 1} of ${session.steps.size}", fg2)
                    Text(step.label, style = MaterialTheme.typography.displaySmall, color = fg)
                    Text(step.cue, style = MaterialTheme.typography.titleLarge, color = fg2)
                    Spacer(Modifier.weight(1f))
                    if (!open) {
                        Text(fmtTime((step.seconds - st.stepElapsed).coerceAtLeast(0)), fontSize = 120.sp, fontWeight = FontWeight.Bold, color = fg, lineHeight = 120.sp)
                        if (next != null) Text("Next: ${next.label} · ${fmtTime(next.seconds)} · ${describe(next, p.ftp)}", color = fg2, style = MaterialTheme.typography.titleMedium)
                    } else {
                        Text(fmtTime(st.elapsed), fontSize = 120.sp, fontWeight = FontWeight.Bold, color = fg, lineHeight = 120.sp)
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        Metric("Power", "${st.watts.toInt()}", "W", C.power, Modifier.weight(1f),
                            sub = st.targetWatts?.let { "target $it W" } ?: "zone ${Metrics.powerZoneNames[Metrics.powerZone(st.watts, p.ftp)]}")
                        Metric("Cadence", "${st.rpm}", "rpm", C.cadence, Modifier.weight(1f),
                            sub = if (step.rpmLow > 0) "aim ${step.rpmLow}–${step.rpmHigh}" else "",
                            warn = step.rpmLow > 0 && st.rpm > 0 && (st.rpm < step.rpmLow - 5 || st.rpm > step.rpmHigh + 10))
                    }
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        val zone = Metrics.hrZone(st.hr, p.maxHr)
                        Metric("Heart rate", if (st.hr > 0) "${st.hr}" else "—", "bpm", if (zone > 0) C.zones[zone] else C.heart, Modifier.weight(1f),
                            sub = if (st.hr > 0) "${Metrics.hrZoneNames[zone]} · ${st.hr * 100 / p.maxHr}% max" + if (!st.hrFromStrap) " · grips" else "" else "hold grips or pair strap")
                        LevelControl(st.level, step.type, Modifier.weight(1f), onMinus = { session.nudge(false) }, onPlus = { session.nudge(true) })
                    }
                }
            }
            Spacer(Modifier.height(18.dp))

            // Timeline + live chart
            Box(Modifier.fillMaxWidth().height(130.dp).clip(RoundedCornerShape(20.dp)).background(C.surface).padding(16.dp)) {
                if (!open) WorkoutProfile(session.steps, p.ftp, Modifier.fillMaxSize(), progressSec = st.elapsed)
                else Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    // Legend with live values so each line is identifiable; history covers the last 10 minutes.
                    Column(Modifier.width(210.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Label("Last 10 minutes")
                        Text("● Power ${st.watts.toInt()} W", color = C.power, style = MaterialTheme.typography.titleMedium)
                        Text("● Heart rate ${if (st.hr > 0) "${st.hr} bpm" else "—"}", color = C.heart, style = MaterialTheme.typography.titleMedium)
                        Text("● Cadence ${st.rpm} rpm", color = C.cadence, style = MaterialTheme.typography.titleMedium)
                    }
                    LineChart(
                        listOf(Series(st.wattsHistory, C.power, fill = true), Series(st.hrHistory.map { it.toFloat() }, C.heart, max = p.maxHr.toFloat()),
                            Series(st.rpmHistory.map { it.toFloat() }, C.cadence, max = 140f)),
                        Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }

        if (st.phase == Phase.COUNTDOWN) Overlay { Text("${st.countdown}", fontSize = 220.sp, fontWeight = FontWeight.Bold, color = Color.White) }
        if (st.phase == Phase.PAUSED) Overlay {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Paused", style = MaterialTheme.typography.displayMedium, color = Color.White)
                Text(if (st.autoPaused) "Start pedalling to continue" else "Tap ▶ to continue", style = MaterialTheme.typography.titleLarge, color = Color(0xFFE6E9EE))
                Spacer(Modifier.height(24.dp))
                if (!st.autoPaused) FilledIconButton(onClick = { session.togglePause() }, modifier = Modifier.size(96.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = C.accent)) {
                    Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(56.dp))
                }
            }
        }
    }

    if (confirmStop) AlertDialog(
        onDismissRequest = { confirmStop = false },
        title = { Text("Finish ride?") },
        text = { Text(if (st.elapsed < 20) "Rides under 20 seconds aren't saved." else "Your ride is saved to history.") },
        confirmButton = { TextButton(onClick = { confirmStop = false; finish() }) { Text("Finish") } },
        dismissButton = {
            Row {
                TextButton(onClick = { confirmStop = false; vm.discardRide(); nav.popBackStack("home", false) }) { Text("Discard", color = C.heart) }
                TextButton(onClick = { confirmStop = false }) { Text("Keep riding") }
            }
        },
    )
}

@Composable
private fun Overlay(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xCC0E1116)), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun Metric(label: String, value: String, unit: String, color: Color, modifier: Modifier, sub: String = "", warn: Boolean = false) {
    Column(modifier.fillMaxHeight().clip(RoundedCornerShape(24.dp)).background(C.surface).padding(22.dp)) {
        Label(label, color)
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.displayLarge, color = if (warn) C.power else C.text, lineHeight = 96.sp)
            Text(" $unit", style = MaterialTheme.typography.titleLarge, color = C.muted, modifier = Modifier.padding(bottom = 16.dp))
        }
        Text(sub, color = C.muted, style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}

@Composable
private fun LevelControl(level: Int, type: TargetType, modifier: Modifier, onMinus: () -> Unit, onPlus: () -> Unit) {
    Column(modifier.fillMaxHeight().clip(RoundedCornerShape(24.dp)).background(C.surface).padding(22.dp)) {
        Label("Resistance", C.level)
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$level", style = MaterialTheme.typography.displayLarge, color = C.text, modifier = Modifier.weight(1f), lineHeight = 96.sp)
            if (type != TargetType.SPRINT) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledIconButton(onClick = onPlus, modifier = Modifier.size(72.dp), shape = CircleShape, colors = IconButtonDefaults.filledIconButtonColors(containerColor = C.surface2)) { Icon(Icons.Filled.Add, "Harder", modifier = Modifier.size(36.dp)) }
                FilledIconButton(onClick = onMinus, modifier = Modifier.size(72.dp), shape = CircleShape, colors = IconButtonDefaults.filledIconButtonColors(containerColor = C.surface2)) { Icon(Icons.Filled.Remove, "Easier", modifier = Modifier.size(36.dp)) }
            }
        }
        Text(when (type) { TargetType.FTP_PCT, TargetType.WATTS -> "ERG · ± changes target 5 W"; TargetType.SPRINT -> "Sprint load (adaptive)"; else -> "of 100" }, color = C.muted, style = MaterialTheme.typography.titleMedium)
    }
}
