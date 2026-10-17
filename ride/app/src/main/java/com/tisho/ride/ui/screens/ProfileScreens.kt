package com.tisho.ride.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.tisho.ride.data.Profile
import com.tisho.ride.ui.AppViewModel
import com.tisho.ride.ui.Avatar
import com.tisho.ride.ui.C
import com.tisho.ride.ui.Label
import com.tisho.ride.ui.Panel

@Composable
fun ProfilesScreen(vm: AppViewModel, nav: NavHostController) {
    val profiles by vm.profiles.collectAsState()
    val active by vm.profile.collectAsState()
    Column(Modifier.fillMaxSize().background(C.bg).padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Who's riding?", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(48.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(36.dp), verticalAlignment = Alignment.Top) {
            for (p in profiles) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(150.dp)) {
                    Box {
                        Box(Modifier.border(4.dp, if (p.id == active?.id) C.accent else Color.Transparent, CircleShape).padding(6.dp)) {
                            Avatar(p, 128.dp) {
                                vm.selectProfile(p.id)
                                nav.navigate("home") { popUpTo(0) }
                            }
                        }
                        IconButton(onClick = { nav.navigate("profileEdit?id=${p.id}") }, modifier = Modifier.align(Alignment.BottomEnd).clip(CircleShape).background(C.surface2)) {
                            Icon(Icons.Filled.Edit, "Edit", tint = C.text)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(p.name, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(150.dp)) {
                Box(
                    Modifier.padding(6.dp).size(128.dp).clip(CircleShape).background(C.surface2).clickable { nav.navigate("profileEdit?id=0&first=${profiles.isEmpty()}") },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Add, "Add rider", tint = C.muted, modifier = Modifier.size(56.dp)) }
                Spacer(Modifier.height(12.dp))
                Text("Add rider", style = MaterialTheme.typography.titleLarge, color = C.muted)
            }
        }
    }
}

@Composable
fun ProfileEditScreen(vm: AppViewModel, nav: NavHostController, id: Long, embedded: Boolean = false) {
    val profiles by vm.profiles.collectAsState()
    val existing = profiles.firstOrNull { it.id == id }
    var loaded by remember(id) { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(profiles.size % C.avatars.size) }
    var sex by remember { mutableStateOf("M") }
    var birthYear by remember { mutableStateOf("1985") }
    var height by remember { mutableStateOf("175") }
    var weight by remember { mutableStateOf("75") }
    var restHr by remember { mutableStateOf("60") }
    var maxHr by remember { mutableStateOf("") }
    var ftp by remember { mutableStateOf("") }
    var goal by remember { mutableStateOf("150") }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(existing?.id) {
        if (existing != null && !loaded) {
            name = existing.name; color = existing.color; sex = existing.sex; birthYear = existing.birthYear.toString()
            height = existing.heightCm.toString(); weight = "%.1f".format(existing.weightKg).removeSuffix(".0"); restHr = existing.restingHr.toString()
            maxHr = existing.maxHrOverride?.toString() ?: ""; ftp = existing.ftpOverride?.toString() ?: ""; goal = existing.weeklyGoalMinutes.toString()
            loaded = true
        }
    }

    val draft = (existing ?: Profile(name = "")).copy(
        name = name.trim(), color = color, sex = sex,
        birthYear = birthYear.toIntOrNull() ?: 1985, heightCm = height.toIntOrNull() ?: 175,
        weightKg = weight.replace(',', '.').toDoubleOrNull() ?: 75.0, restingHr = restHr.toIntOrNull() ?: 60,
        maxHrOverride = maxHr.toIntOrNull(), ftpOverride = ftp.toIntOrNull(), weeklyGoalMinutes = goal.toIntOrNull() ?: 150,
    )

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 48.dp, vertical = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(draft.copy(name = name.ifBlank { "?" }), 72.dp)
            Spacer(Modifier.width(20.dp))
            Text(if (existing == null) "New rider" else if (embedded) "Your profile" else "Edit rider", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            if (!embedded) TextButton(onClick = { nav.popBackStack() }) { Text("Cancel") }
            Button(
                onClick = {
                    vm.saveProfile(draft, select = existing == null)
                    if (existing == null) nav.navigate("home") { popUpTo(0) } else if (!embedded) nav.popBackStack()
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = C.accent),
            ) { Icon(Icons.Filled.Check, null); Spacer(Modifier.width(8.dp)); Text("Save") }
        }
        if (existing != null) {
            Spacer(Modifier.height(24.dp))
            FitnessCard(vm, existing)
        }
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Panel(Modifier.weight(1f)) {
                Label("About")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                Label("Colour")
                Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    C.avatars.forEachIndexed { i, c ->
                        Box(Modifier.size(40.dp).clip(CircleShape).background(c).border(3.dp, if (i == color) C.text else Color.Transparent, CircleShape).clickable { color = i })
                    }
                }
                Label("Sex (used for estimates)")
                Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilterChip(sex == "M", { sex = "M" }, { Text("Male") })
                    FilterChip(sex == "F", { sex = "F" }, { Text("Female") })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Num("Birth year", birthYear, Modifier.weight(1f)) { birthYear = it }
                    Num("Height (cm)", height, Modifier.weight(1f)) { height = it }
                    Num("Weight (kg)", weight, Modifier.weight(1f), decimal = true) { weight = it }
                }
            }
            Panel(Modifier.weight(1f)) {
                Label("Training")
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Num("Resting HR", restHr, Modifier.weight(1f)) { restHr = it }
                    Num("Max HR (blank = auto ${draft.copy(maxHrOverride = null).maxHr})", maxHr, Modifier.weight(1f)) { maxHr = it }
                }
                Spacer(Modifier.height(12.dp))
                Num("FTP watts (blank = estimate ${draft.copy(ftpOverride = null).ftp} W — take the ramp test)", ftp, Modifier.fillMaxWidth()) { ftp = it }
                Spacer(Modifier.height(12.dp))
                Num("Weekly goal (active minutes)", goal, Modifier.fillMaxWidth()) { goal = it }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Max HR uses Tanaka (208 − 0.7 × age) unless set. FTP drives the ERG targets of interval workouts — the ramp " +
                        "test sets it for you. WHO recommends 150–300 active minutes a week; vigorous minutes count double.",
                    style = MaterialTheme.typography.bodyMedium, color = C.muted,
                )
            }
        }
        if (existing != null) {
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (embedded) OutlinedButton(onClick = { nav.navigate("profiles") }) { Text("Switch rider") }
                OutlinedButton(onClick = { confirmDelete = true }, colors = ButtonDefaults.outlinedButtonColors(contentColor = C.heart)) { Text("Delete rider and all rides") }
            }
        }
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${existing.name}?") },
            text = { Text("This removes the profile and every ride in its history. It can't be undone — export a backup first if unsure.") },
            confirmButton = { TextButton(onClick = { vm.deleteProfile(existing); confirmDelete = false; nav.navigate("profiles") { popUpTo(0) } }) { Text("Delete", color = C.heart) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }
}

@Composable
private fun Num(label: String, value: String, modifier: Modifier, decimal: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, { v -> onChange(v.filter { it.isDigit() || (decimal && (it == '.' || it == ',')) }) },
        label = { Text(label, maxLines = 1) }, singleLine = true, modifier = modifier.widthIn(min = 80.dp),
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        shape = RoundedCornerShape(12.dp),
    )
}

/** This rider's current VO₂max, what it means, where it comes from, and how it has changed. */
@Composable
private fun FitnessCard(vm: AppViewModel, p: Profile) {
    val rides by vm.repo.rides(p.id).collectAsState(initial = emptyList())
    val fit = com.tisho.ride.workout.Metrics.fitness(p, rides)
    val female = p.sex == "F"
    val fitnessAge = com.tisho.ride.workout.Metrics.fitnessAge(fit.vo2, female)
    Panel {
        Label("Fitness")
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(40.dp)) {
            com.tisho.ride.ui.Stat("VO₂max", "%.0f".format(fit.vo2), "ml/kg/min", C.cadence, big = true)
            com.tisho.ride.ui.Stat("Rating", com.tisho.ride.workout.Metrics.vo2Rating(fit.vo2, p.age, female), "for age ${p.age}")
            com.tisho.ride.ui.Stat("Fitness age", "$fitnessAge", if (fitnessAge < p.age) "${p.age - fitnessAge} years younger" else if (fitnessAge > p.age) "${fitnessAge - p.age} years older" else "same as your age", C.accent)
        }
        Spacer(Modifier.height(8.dp))
        Text("Based on ${fit.source}.", color = C.muted)
        if (fit.history.size >= 2) {
            Spacer(Modifier.height(12.dp))
            com.tisho.ride.ui.LineChart(listOf(com.tisho.ride.ui.Series(fit.history.map { it.second.toFloat() }, C.cadence, fill = true,
                max = (fit.history.maxOf { it.second } * 1.15).toFloat())), Modifier.fillMaxWidth().height(110.dp))
            Text("VO₂max per ride since ${com.tisho.ride.ui.fmtDay(fit.history.first().first)}", color = C.muted, style = MaterialTheme.typography.bodyMedium)
        } else {
            Text("Ride with a heart-rate strap or the hand grips and each steady ride adds an estimate here. The ramp test gives the most accurate value.", color = C.muted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
