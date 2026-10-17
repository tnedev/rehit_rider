package com.tisho.ride.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import com.tisho.ride.data.Profile
import com.tisho.ride.data.Ride
import com.tisho.ride.data.Sample
import com.tisho.ride.ui.AppViewModel
import com.tisho.ride.ui.C
import com.tisho.ride.ui.Label
import com.tisho.ride.ui.LineChart
import com.tisho.ride.ui.Panel
import com.tisho.ride.ui.Series
import com.tisho.ride.ui.Stat
import com.tisho.ride.ui.fmtDate
import com.tisho.ride.ui.fmtTime
import com.tisho.ride.workout.Metrics
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun SummaryScreen(vm: AppViewModel, nav: NavHostController) {
    val r = vm.lastResult ?: run { LaunchedEffect(Unit) { nav.popBackStack("home", false) }; return }
    val profile by vm.profile.collectAsState()
    val p = profile ?: return
    val rides by vm.rides.collectAsState()
    var rpe by remember { mutableStateOf(0) }
    var notes by remember { mutableStateOf("") }
    var applyFtp by remember { mutableStateOf(true) }
    val prs = personalRecords(r.ride, rides)

    Row(Modifier.fillMaxSize().padding(28.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.weight(1.5f).verticalScroll(rememberScrollState())) {
            Text("Nice work, ${p.name.substringBefore(' ')}!", style = MaterialTheme.typography.displaySmall)
            Text("${r.ride.workoutName} · ${fmtTime(r.ride.durationSec)}", color = C.muted, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(20.dp))
            RideReport(r.ride, r.samples, p)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (prs.isNotEmpty()) Panel {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.EmojiEvents, null, tint = C.power); Spacer(Modifier.width(8.dp)); Label("New personal records", C.power) }
                for (pr in prs) Text(pr, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
            }
            if (r.newFtp != null) Panel {
                Label("Ramp test result", C.accent)
                Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    Stat("New FTP", "${r.newFtp}", "W", C.accent, big = true)
                    Stat("W/kg", "%.2f".format(r.newFtp / p.weightKg))
                    r.vo2max?.let { Stat("VO₂max est.", "%.0f".format(it), Metrics.vo2Rating(it, p.age, p.sex == "F")) }
                }
                Text("Previous FTP: ${p.ftp} W${if (p.ftpIsEstimated) " (estimate)" else ""}", color = C.muted)
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(applyFtp, { applyFtp = it }); Text("Use as my FTP") }
            }
            if (r.newSprintInnate != null) Panel {
                Label("Sprint coach", C.accent)
                Spacer(Modifier.height(8.dp))
                Text("Peak cadence per sprint: ${r.sprintPeaks.joinToString(", ")} rpm", style = MaterialTheme.typography.titleMedium)
                val old = if (p.sprintInnate > 0) p.sprintInnate else r.newSprintInnate
                Text(
                    when {
                        r.newSprintInnate > old -> "Strong sprints! Next time the sprint load goes up to keep you in the most effective zone."
                        r.newSprintInnate < old -> "The load was a bit heavy. It's eased off slightly for next time."
                        else -> "Right on target — the sprint load stays the same."
                    }, color = C.muted,
                )
            }
            Panel {
                Label("How hard was it? (RPE)")
                Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (i in 1..10) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(if (rpe == i) C.zones[((i + 1) / 2 + 1).coerceAtMost(6)] else C.surface2)
                                .clickable { rpe = i },
                            contentAlignment = Alignment.Center,
                        ) { Text("$i") }
                    }
                }
                Text(rpeLabel(rpe), color = C.muted)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { vm.discardRide(); nav.popBackStack("home", false) }, modifier = Modifier.height(56.dp)) { Text("Discard", color = C.heart) }
                Button(
                    onClick = { vm.saveResult(r, rpe, notes, applyFtp) { id -> nav.navigate("rides/$id") { popUpTo("home") } } },
                    modifier = Modifier.weight(1f).height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = C.accent),
                ) { Text("Save ride", style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
}

private fun rpeLabel(r: Int) = when (r) {
    0 -> "Tap a number"; 1, 2 -> "Very easy"; 3, 4 -> "Easy"; 5, 6 -> "Moderate"; 7, 8 -> "Hard"; 9 -> "Very hard"; else -> "Maximal"
}

private fun personalRecords(r: Ride, history: List<Ride>): List<String> {
    val prev = history.filter { it.id != r.id && it.startedAt < r.startedAt }
    if (prev.isEmpty()) return emptyList()
    val out = ArrayList<String>()
    fun check(name: String, v: Double, best: Double) { if (v > 0 && v > best) out += "$name: ${v.toInt()} W (was ${best.toInt()} W)" }
    check("5-second power", r.peak5s, prev.maxOf { it.peak5s })
    check("20-second power", r.peak20s, prev.maxOf { it.peak20s })
    check("1-minute power", r.peak1m, prev.maxOf { it.peak1m })
    check("5-minute power", r.peak5m, prev.maxOf { it.peak5m })
    check("20-minute power", r.peak20m, prev.maxOf { it.peak20m })
    if (r.durationSec > 0 && r.durationSec > prev.maxOf { it.durationSec }) out += "Longest ride: ${fmtTime(r.durationSec)}"
    return out
}

@Composable
fun RideReport(r: Ride, samples: List<Sample>, p: Profile) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Panel(Modifier.weight(1f)) { Stat("Avg power", "${r.avgWatts.toInt()}", "W", C.power) }
        Panel(Modifier.weight(1f)) { Stat("Normalized", "${r.normalizedWatts.toInt()}", "W", C.power) }
        Panel(Modifier.weight(1f)) {
            Stat("Calories", "${r.kcal.toInt()}", "kcal", C.accent)
            Text("${r.activeKcal.toInt()} active · ${r.kj.toInt()} kJ work", color = C.muted)
        }
        Panel(Modifier.weight(1f)) { Stat("Training load", "${r.tss.toInt()}", "TSS", C.level) }
    }
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Panel(Modifier.weight(1f)) { Stat("Max power", "${r.maxWatts.toInt()}", "W") }
        Panel(Modifier.weight(1f)) { Stat("Avg cadence", "${r.avgRpm.toInt()}", "rpm", C.cadence) }
        Panel(Modifier.weight(1f)) { Stat("Avg HR", if (r.avgHr > 0) "${r.avgHr}" else "—", "bpm", C.heart) }
        Panel(Modifier.weight(1f)) { Stat("Max HR", if (r.maxHr > 0) "${r.maxHr}" else "—", "bpm", C.heart) }
    }
    Spacer(Modifier.height(16.dp))
    Panel {
        Row {
            Label("Power", C.power); Spacer(Modifier.width(16.dp)); Label("Heart rate", C.heart); Spacer(Modifier.width(16.dp)); Label("Cadence", C.cadence)
        }
        Spacer(Modifier.height(12.dp))
        val step = (samples.size / 600).coerceAtLeast(1)
        val ds = samples.filterIndexed { i, _ -> i % step == 0 }
        LineChart(
            listOf(
                Series(ds.map { it.watts }, C.power, fill = true),
                Series(ds.map { it.hr.toFloat() }, C.heart, max = p.maxHr.toFloat() * 1.05f),
                Series(ds.map { it.rpm.toFloat() }, C.cadence, max = 150f),
            ),
            Modifier.fillMaxWidth().height(200.dp),
        )
    }
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Panel(Modifier.weight(1f)) {
            Label("Best efforts")
            Spacer(Modifier.height(8.dp))
            for ((n, v) in listOf("5 s" to r.peak5s, "20 s" to r.peak20s, "1 min" to r.peak1m, "5 min" to r.peak5m, "20 min" to r.peak20m)) {
                if (v <= 0) continue
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(n, Modifier.weight(1f), color = C.muted); Text("${v.toInt()} W"); Spacer(Modifier.width(12.dp))
                    Text("%.1f W/kg".format(v / p.weightKg), color = C.muted)
                }
            }
        }
        Panel(Modifier.weight(1f)) {
            Label("Heart rate zones")
            Spacer(Modifier.height(8.dp))
            val z = r.hrZoneSeconds.split(",").mapNotNull { it.toIntOrNull() }
            val tot = z.sum().coerceAtLeast(1)
            if (z.sum() == 0) Text("No heart-rate data", color = C.muted)
            else z.forEachIndexed { i, s ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Z${i + 1} ${Metrics.hrZoneNames[i + 1]}", Modifier.width(140.dp), color = C.muted)
                    Box(Modifier.weight(1f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(C.surface2)) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(s / tot.toFloat()).clip(RoundedCornerShape(7.dp)).background(C.zones[i + 1]))
                    }
                    Text(fmtTime(s), Modifier.width(64.dp), textAlign = TextAlign.End)
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(vm: AppViewModel, nav: NavHostController) {
    val rides by vm.rides.collectAsState()
    if (rides.isEmpty()) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.AutoMirrored.Filled.DirectionsBike, null, tint = C.muted, modifier = Modifier.size(72.dp))
            Text("No rides yet", style = MaterialTheme.typography.headlineSmall)
            Text("Your rides will appear here.", color = C.muted)
        }
        return
    }
    val byWeek = rides.groupBy { Metrics.startOfWeek(it.startedAt) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for ((week, list) in byWeek) {
            item {
                Row(Modifier.padding(top = 16.dp, bottom = 4.dp)) {
                    Text("Week of ${com.tisho.ride.ui.fmtDay(week)}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Text("${list.size} rides · ${list.sumOf { it.durationSec } / 60} min · ${list.sumOf { it.kcal }.toInt()} kcal · ${list.sumOf { it.tss }.toInt()} TSS", color = C.muted)
                }
            }
            items(list, key = { it.id }) { r ->
                Panel(onClick = { nav.navigate("rides/${r.id}") }, padding = 18.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.workoutName, style = MaterialTheme.typography.titleLarge)
                            Text(fmtDate(r.startedAt), color = C.muted)
                        }
                        Stat("Time", fmtTime(r.durationSec), modifier = Modifier.width(130.dp))
                        Stat("Avg", "${r.avgWatts.toInt()}", "W", C.power, Modifier.width(120.dp))
                        Stat("Energy", "${r.kcal.toInt()}", "kcal", C.accent, Modifier.width(130.dp))
                        Stat("HR", if (r.avgHr > 0) "${r.avgHr}" else "—", "bpm", C.heart, Modifier.width(120.dp))
                        Stat("Load", "${r.tss.toInt()}", "TSS", C.level, Modifier.width(110.dp))
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun RideDetailScreen(vm: AppViewModel, nav: NavHostController, id: Long) {
    val ride by vm.repo.ride(id).collectAsState(initial = null)
    val profile by vm.profile.collectAsState()
    var samples by remember { mutableStateOf<List<Sample>>(emptyList()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var notes by remember(ride?.id) { mutableStateOf(ride?.notes ?: "") }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    LaunchedEffect(id) { samples = vm.repo.samples(id) }
    val r = ride ?: return
    val p = profile ?: return

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(r.workoutName, style = MaterialTheme.typography.displaySmall)
                Text("${fmtDate(r.startedAt)} · ${fmtTime(r.durationSec)}${if (r.rpe > 0) " · RPE ${r.rpe}" else ""}", color = C.muted, style = MaterialTheme.typography.titleMedium)
            }
            OutlinedButton(onClick = { scope.launch { shareText(ctx, "ride-${r.id}.tcx", vm.repo.exportTcx(r), "application/vnd.garmin.tcx+xml") } }) {
                Icon(Icons.Filled.Share, null); Text(" Export TCX")
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, null, tint = C.heart) }
        }
        Spacer(Modifier.height(20.dp))
        RideReport(r, samples, p)
        Spacer(Modifier.height(16.dp))
        Panel {
            Label("Notes")
            OutlinedTextField(notes, { notes = it }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            if (notes != r.notes) TextButton(onClick = { vm.updateRide(r.copy(notes = notes)) }) { Text("Save notes") }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete this ride?") },
        text = { Text("It will be removed from your history and statistics.") },
        confirmButton = { TextButton(onClick = { vm.deleteRide(r.id); confirmDelete = false; nav.popBackStack() }) { Text("Delete", color = C.heart) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}

fun shareText(ctx: Context, fileName: String, content: String, mime: String) {
    val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
    val f = File(dir, fileName).apply { writeText(content) }
    val uri = FileProvider.getUriForFile(ctx, "com.tisho.ride.files", f)
    val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(send, "Share $fileName"))
}
