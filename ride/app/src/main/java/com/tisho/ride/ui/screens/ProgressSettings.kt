package com.tisho.ride.ui.screens

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.tisho.ride.bike.BikeStatus
import com.tisho.ride.bike.PowerModel
import com.tisho.ride.hr.HrStatus
import com.tisho.ride.ui.AppViewModel
import com.tisho.ride.ui.C
import com.tisho.ride.ui.Label
import com.tisho.ride.ui.LineChart
import com.tisho.ride.ui.Panel
import com.tisho.ride.ui.SectionTitle
import com.tisho.ride.ui.Series
import com.tisho.ride.ui.Stat
import com.tisho.ride.ui.fmtDay
import com.tisho.ride.workout.Metrics
import kotlinx.coroutines.launch

@Composable
fun ProgressScreen(vm: AppViewModel, nav: NavHostController) {
    val rides by vm.rides.collectAsState()
    val profile by vm.profile.collectAsState()
    val p = profile ?: return
    val load = Metrics.trainingLoad(rides).takeLast(90)
    val now = load.lastOrNull()?.second
    val fit = Metrics.fitness(p, rides)
    val vo2 = fit.vo2
    val month = System.currentTimeMillis() - 30L * 86_400_000
    val durations = listOf("5 s" to Ride5s, "20 s" to Ride20s, "1 min" to Ride1m, "5 min" to Ride5m, "20 min" to Ride20m)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Panel(Modifier.weight(1f)) {
                Stat("FTP", "${p.ftp}", "W", C.accent, big = true)
                Text("%.2f W/kg%s".format(p.ftp / p.weightKg, if (p.ftpIsEstimated) " · estimate" else ""), color = C.muted)
            }
            Panel(Modifier.weight(1f)) {
                Stat("VO₂max", "%.0f".format(vo2), "ml/kg/min", C.cadence, big = true)
                Text("${Metrics.vo2Rating(vo2, p.age, p.sex == "F")} for age ${p.age} · fitness age ${Metrics.fitnessAge(vo2, p.sex == "F")}", color = C.muted)
            }
            Panel(Modifier.weight(1f)) {
                Stat("Fitness", "${now?.fitness?.toInt() ?: 0}", "CTL", C.level, big = true)
                Text("42-day training load", color = C.muted)
            }
            Panel(Modifier.weight(1f)) {
                val f = now?.form ?: 0.0
                Stat("Form", "${if (f > 0) "+" else ""}${f.toInt()}", "TSB", if (f < -20) C.heart else if (f > 5) C.good else C.power, big = true)
                Text(when { f < -25 -> "Very fatigued — rest"; f < -10 -> "Productive training"; f < 5 -> "Balanced"; else -> "Fresh — go hard" }, color = C.muted)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Panel(Modifier.weight(1.4f)) {
                Row { Label("Fitness", C.level); Spacer(Modifier.width(16.dp)); Label("Fatigue", C.heart); Spacer(Modifier.width(16.dp)); Label("Last 90 days") }
                Spacer(Modifier.height(12.dp))
                if (load.size < 2) Text("Ride a few times to see your fitness trend.", color = C.muted)
                else {
                    val top = load.maxOf { maxOf(it.second.fitness, it.second.fatigue) }.toFloat().coerceAtLeast(10f) * 1.1f
                    LineChart(
                        listOf(Series(load.map { it.second.fitness.toFloat() }, C.level, fill = true, max = top), Series(load.map { it.second.fatigue.toFloat() }, C.heart, max = top)),
                        Modifier.fillMaxWidth().height(220.dp),
                    )
                    Row { Text(fmtDay(load.first().first), color = C.muted, modifier = Modifier.weight(1f)); Text("Today", color = C.muted) }
                }
            }
            Panel(Modifier.weight(1f)) {
                Label("Active minutes per week")
                Spacer(Modifier.height(12.dp))
                val wk = 7 * 86_400_000L
                val start = Metrics.startOfWeek(System.currentTimeMillis())
                val weeks = (11 downTo 0).map { i -> rides.filter { Metrics.startOfWeek(it.startedAt) == start - i * wk }.sumOf { Metrics.activeMinutes(it) } }
                val goal = p.weeklyGoalMinutes
                val top = maxOf(goal, weeks.maxOrNull() ?: 0).toFloat() * 1.15f
                Canvas(Modifier.fillMaxWidth().height(220.dp)) {
                    val bw = size.width / weeks.size
                    weeks.forEachIndexed { i, m ->
                        val h = m / top * size.height
                        drawRoundRect(if (m >= goal) C.good else C.accent, Offset(i * bw + bw * 0.15f, size.height - h), Size(bw * 0.7f, h), CornerRadius(6f, 6f))
                    }
                    val gy = size.height - goal / top * size.height
                    drawLine(C.muted, Offset(0f, gy), Offset(size.width, gy), 2f)
                }
                Text("Line = your goal of $goal min · vigorous minutes count double", color = C.muted)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Panel(Modifier.weight(1.4f)) {
                Label("Best power")
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text("", Modifier.weight(1f)); Text("All time", Modifier.width(160.dp), color = C.muted); Text("Last 30 days", Modifier.width(160.dp), color = C.muted)
                }
                for ((label, f) in durations) {
                    val all = rides.maxOfOrNull(f) ?: 0.0
                    val recent = rides.filter { it.startedAt >= month }.maxOfOrNull(f) ?: 0.0
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                        Text(label, Modifier.weight(1f))
                        Text(if (all > 0) "${all.toInt()} W · %.1f/kg".format(all / p.weightKg) else "—", Modifier.width(160.dp), color = C.power)
                        Text(if (recent > 0) "${recent.toInt()} W" else "—", Modifier.width(160.dp))
                    }
                }
            }
            Panel(Modifier.weight(1f)) {
                Label("Lifetime")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Stat("Rides", "${rides.size}")
                    Stat("Hours", "%.1f".format(rides.sumOf { it.durationSec } / 3600.0))
                    Stat("kcal", "${rides.sumOf { it.kcal }.toInt()}", color = C.accent)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Stat("Streak", "${Metrics.weekStreak(rides)}", "weeks", C.accent)
                    Stat("REHITs", "${rides.count { it.workoutId.startsWith("rehit") }}")
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

private val Ride5s: (com.tisho.ride.data.Ride) -> Double = { it.peak5s }
private val Ride20s: (com.tisho.ride.data.Ride) -> Double = { it.peak20s }
private val Ride1m: (com.tisho.ride.data.Ride) -> Double = { it.peak1m }
private val Ride5m: (com.tisho.ride.data.Ride) -> Double = { it.peak5m }
private val Ride20m: (com.tisho.ride.data.Ride) -> Double = { it.peak20m }

@Composable
fun SettingsScreen(vm: AppViewModel, nav: NavHostController) {
    val settings by vm.settings.collectAsState()
    val profile by vm.profile.collectAsState()
    val bikeStatus by vm.bike.status.collectAsState()
    val t by vm.bike.telemetry.collectAsState()
    val hrStatus by vm.hrm.status.collectAsState()
    val found by vm.hrm.found.collectAsState()
    val bpm by vm.hrm.bpm.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var testLevel by remember { mutableStateOf(0f) }
    var confirmWipe by remember { mutableStateOf(false) }

    val blePerms = if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r -> if (r.values.all { it }) vm.hrm.scan() }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val text = vm.repo.exportBackup()
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
            Toast.makeText(ctx, "Backup saved", Toast.LENGTH_SHORT).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: return@launch
            val n = runCatching { vm.repo.importBackup(text) }.getOrElse { -1 }
            Toast.makeText(ctx, if (n >= 0) "Imported $n rides" else "Not a valid backup file", Toast.LENGTH_LONG).show()
        }
    }

    Row(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SectionTitle("Heart-rate strap")
            Panel {
                val p = profile
                Text(
                    when {
                        p?.hrSensorAddress != null && hrStatus == HrStatus.CONNECTED -> "Connected to ${p.hrSensorName} · $bpm bpm"
                        p?.hrSensorAddress != null -> "Paired: ${p.hrSensorName} (${hrStatus.name.lowercase()})"
                        hrStatus == HrStatus.UNAVAILABLE -> "Bluetooth is off or unavailable"
                        else -> "Any Bluetooth heart-rate strap or armband (Polar, Garmin, Wahoo, Coros…) works. Without one, hold the hand grips."
                    }, style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { permLauncher.launch(blePerms) }, colors = ButtonDefaults.buttonColors(containerColor = C.accent)) {
                        Text(if (hrStatus == HrStatus.SCANNING) "Scanning…" else "Find sensors")
                    }
                    if (p?.hrSensorAddress != null) OutlinedButton(onClick = { vm.saveProfile(p.copy(hrSensorAddress = null, hrSensorName = null)) }) { Text("Forget sensor") }
                }
                for (d in found) {
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${d.name}  ·  ${d.rssi} dBm", Modifier.weight(1f))
                        TextButton(onClick = {
                            vm.hrm.stopScan()
                            p?.let { vm.saveProfile(it.copy(hrSensorAddress = d.address, hrSensorName = d.name)) }
                        }) { Text("Use for ${p?.name?.substringBefore(' ') ?: "me"}") }
                    }
                }
            }
            SectionTitle("Sync to my phone")
            PhonePanel(vm)
            SectionTitle("Health Connect on this tablet")
            HealthPanel(vm)
            SectionTitle("Appearance")
            Panel {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for ((k, label) in listOf("light" to "Light", "dark" to "Dark", "system" to "Follow system")) {
                        androidx.compose.material3.FilterChip(settings.theme == k, { vm.updateSettings(settings.copy(theme = k)) }, { Text(label) })
                    }
                }
            }
            SectionTitle("Sound")
            Panel {
                ToggleRow("Voice coaching", "Speaks each step's cue", settings.voice) { vm.updateSettings(settings.copy(voice = it)) }
                ToggleRow("Beeps", "Countdown and step changes", settings.sounds) { vm.updateSettings(settings.copy(sounds = it)) }
            }
            SectionTitle("Your data")
            Panel {
                Text("Everything stays on this tablet. Back up regularly or before resetting.", color = C.muted)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { exportLauncher.launch("ride-backup.json") }) { Text("Export backup") }
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }) { Text("Import backup") }
                    OutlinedButton(onClick = { profile?.let { pr -> scope.launch { shareText(ctx, "rides-${pr.name}.csv", vm.repo.exportCsv(pr.id), "text/csv") } } }) { Text("Rides as CSV") }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { confirmWipe = true }, colors = ButtonDefaults.outlinedButtonColors(contentColor = C.heart)) { Text("Delete all my rides") }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SectionTitle("Bike")
            Panel {
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    Stat("Status", bikeStatus.name.lowercase().replace('_', ' '))
                    Stat("Firmware", if (t.firmware > 0) "${t.firmware}" else "—")
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    Stat("Cadence", "${t.rpm}", "rpm", C.cadence)
                    Stat("Power", "${t.watts.toInt()}", "W", C.power)
                    Stat("Level", "${PowerModel.levelForInnate(t.innate)}", "(${t.innate})", C.level)
                    Stat("Grip HR", if (t.gripHr > 0) "${t.gripHr}" else "—", "bpm", C.heart)
                }
                Spacer(Modifier.height(16.dp))
                Label("Resistance test")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Slider(testLevel, { testLevel = it }, valueRange = 0f..100f, modifier = Modifier.weight(1f),
                        onValueChangeFinished = { vm.bike.setResistance(PowerModel.innateForLevel(testLevel.toInt())) })
                    Text("  ${testLevel.toInt()}", style = MaterialTheme.typography.titleMedium)
                }
                Text("The motor moves at most every 2 s to protect it. Set back to 0 when done.", color = C.muted)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { vm.bike.disconnect(); vm.bike.connect() }) { Text("Reconnect bike") }
                    OutlinedButton(onClick = { openOtherHome(ctx) }) { Text("Open Android home screen") }
                }
                Spacer(Modifier.height(6.dp))
                ToggleRow("Demo bike", "Simulated bike for trying the app without riding", settings.demo) {
                    if (bikeStatus == BikeStatus.READY || it || bikeStatus == BikeStatus.SIMULATED) vm.updateSettings(settings.copy(demo = it))
                }
            }
            SectionTitle("About")
            Panel {
                Text("REHIT Rider 1.0 — free, independent trainer app for your bike. github.com/tnedev/rehit_rider", style = MaterialTheme.typography.bodyLarge)
                Text("Power is calculated from this bike's own calibration. Training metrics use Coggan TSS/CTL/ATL, Tanaka max HR and the ACSM cycling VO₂ equation. Not medical advice: stop if you feel unwell.", color = C.muted)
            }
        }
    }

    if (confirmWipe) AlertDialog(
        onDismissRequest = { confirmWipe = false },
        title = { Text("Delete all rides?") },
        text = { Text("Every ride for ${profile?.name} will be removed. Profiles and custom workouts stay.") },
        confirmButton = { TextButton(onClick = { profile?.let { scope.launch { vm.repo.deleteAllRides(it.id) } }; confirmWipe = false }) { Text("Delete", color = C.heart) } },
        dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("Cancel") } },
    )
}

@Composable
private fun PhonePanel(vm: AppViewModel) {
    val profile by vm.profile.collectAsState()
    val links by vm.sync.links.collectAsState()
    val p = profile ?: return
    var code by remember { mutableStateOf<String?>(null) }
    val mine = links.filter { it.profileId == p.id }
    Panel {
        Text(
            "Send ${p.name.substringBefore(' ')}'s rides to their own phone's Google Fit / Samsung Health with the free " +
                "REHIT Rider Sync app. Each phone only ever receives the rides of the rider it is linked to.",
            color = C.muted,
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = { code = vm.sync.newCode(p.id) }, colors = ButtonDefaults.buttonColors(containerColor = C.accent)) { Text("Link a phone") }
        for (l in mine) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${l.device} · linked ${com.tisho.ride.ui.fmtDay(l.createdAt)}", Modifier.weight(1f))
                TextButton(onClick = { vm.sync.unlink(l.token) }) { Text("Unlink", color = C.heart) }
            }
        }
    }
    code?.let { c ->
        AlertDialog(
            onDismissRequest = { code = null },
            title = { Text("Link ${p.name.substringBefore(' ')}'s phone") },
            text = {
                Column {
                    Text("1. Install REHIT Rider Sync on the phone (same Wi-Fi as this tablet).\n2. Open it, pick this tablet and enter:", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(16.dp))
                    Text(c.chunked(3).joinToString(" "), style = MaterialTheme.typography.displayMedium, color = C.accent)
                    Text("Code valid for 10 minutes.", color = C.muted)
                }
            },
            confirmButton = { TextButton(onClick = { code = null }) { Text("Done") } },
        )
    }
}

@Composable
private fun HealthPanel(vm: AppViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val profile by vm.profile.collectAsState()
    val p = profile ?: return
    val avail = remember { vm.health.availability() }
    var granted by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(p.healthSync) { granted = vm.health.hasPermissions() }
    val launcher = rememberLauncherForActivityResult(vm.health.permissionContract()) { got ->
        granted = got.containsAll(vm.health.permissions)
        vm.setHealthOwner(p, granted)
    }
    val profiles by vm.profiles.collectAsState()
    val owner = profiles.firstOrNull { it.healthSync }
    Panel {
        if (owner != null && owner.id != p.id) {
            Text("Syncing ${owner.name}'s rides only. Other riders' rides stay in Ride and never reach Health Connect.", style = MaterialTheme.typography.titleMedium, color = C.good)
            Spacer(Modifier.height(8.dp))
        }
        Text("${p.name.substringBefore(' ')}'s rides can be saved to Health Connect, which Google Fit and Samsung Health read from. Health Connect belongs to this tablet's Google account, so only one rider can sync — turning it on here turns it off for everyone else. In Samsung Health, enable Health Connect sync.", color = C.muted)
        Spacer(Modifier.height(12.dp))
        when (avail) {
            com.tisho.ride.health.HealthSync.Availability.AVAILABLE -> ToggleRow(
                "Sync ${p.name.substringBefore(' ')}'s rides",
                if (p.healthSync && granted) "On — each saved ride is written as a stationary-bike workout" else "Off",
                p.healthSync && granted,
            ) { on ->
                if (!on) vm.setHealthOwner(p, false)
                else scope.launch { if (vm.health.hasPermissions()) vm.setHealthOwner(p, true) else launcher.launch(vm.health.permissions) }
            }
            com.tisho.ride.health.HealthSync.Availability.NEEDS_INSTALL -> Button(onClick = { runCatching { ctx.startActivity(vm.health.installIntent()) } }) { Text("Install Health Connect") }
            else -> Text("Health Connect isn't supported on this device.", color = C.power)
        }
    }
}

@Composable
private fun ToggleRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(sub, color = C.muted)
        }
        Switch(checked, onChange)
    }
}

/** Leaves the app for the tablet's normal launcher (the app may be set as the home app). */
private fun openOtherHome(ctx: android.content.Context) {
    val pm = ctx.packageManager
    val home = android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME)
    val other = pm.queryIntentActivities(home, 0).firstOrNull { it.activityInfo.packageName != ctx.packageName }
    val intent = if (other != null) android.content.Intent(home).setClassName(other.activityInfo.packageName, other.activityInfo.name)
    else android.content.Intent(android.provider.Settings.ACTION_SETTINGS)
    ctx.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
}
