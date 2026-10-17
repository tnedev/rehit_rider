package com.tisho.ride.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.tisho.ride.ui.AppViewModel
import com.tisho.ride.ui.C
import com.tisho.ride.ui.Label
import com.tisho.ride.ui.Panel
import com.tisho.ride.ui.WorkoutProfile
import com.tisho.ride.ui.fmtTime
import com.tisho.ride.workout.Step
import com.tisho.ride.workout.TargetType
import kotlinx.coroutines.launch

@Composable
fun BuilderScreen(vm: AppViewModel, nav: NavHostController, id: Long) {
    val profile by vm.profile.collectAsState()
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("My workout") }
    var desc by remember { mutableStateOf("") }
    val steps = remember {
        mutableStateListOf(
            Step(300, "Warm-up", TargetType.FTP_PCT, 55),
            Step(60, "Hard", TargetType.FTP_PCT, 110),
            Step(60, "Easy", TargetType.FTP_PCT, 50),
            Step(300, "Cool-down", TargetType.FTP_PCT, 45),
        )
    }
    LaunchedEffect(id) {
        if (id != 0L) vm.repo.customWorkout(id)?.let { cw ->
            val w = vm.repo.toWorkout(cw)
            name = w.name; desc = cw.description; steps.clear(); steps.addAll(w.steps)
        }
    }
    val ftp = profile?.ftp ?: 200

    Row(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.weight(1f)) {
            Text(if (id == 0L) "Build a workout" else "Edit workout", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(desc, { desc = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            Spacer(Modifier.height(20.dp))
            Panel {
                Row { Label("Shape"); Spacer(Modifier.weight(1f)); Text(fmtTime(steps.sumOf { it.seconds }), color = C.accent) }
                Spacer(Modifier.height(12.dp))
                WorkoutProfile(steps.toList(), ftp, Modifier.fillMaxWidth().height(110.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text("Targets: % FTP holds watts automatically (ERG); Level fixes the resistance; Sprint uses your adaptive sprint load.", color = C.muted)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = { nav.popBackStack() }) { Text("Cancel") }
                Button(
                    onClick = {
                        scope.launch {
                            val newId = vm.repo.saveCustomWorkout(name.trim().ifEmpty { "My workout" }, desc.trim(), steps.toList(), id)
                            nav.popBackStack()
                            nav.navigate("workout/custom:$newId")
                        }
                    },
                    enabled = steps.isNotEmpty(),
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = C.accent),
                ) { Text("Save workout") }
            }
        }
        Column(Modifier.weight(1.3f)) {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(steps) { i, s -> StepEditor(s, ftp, onChange = { steps[i] = it }, onDelete = { steps.removeAt(i) },
                    onCopy = { steps.add(i + 1, s) },
                    onUp = { if (i > 0) { steps.removeAt(i); steps.add(i - 1, s) } },
                    onDown = { if (i < steps.lastIndex) { steps.removeAt(i); steps.add(i + 1, s) } }) }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { steps.add(Step(60, "Interval", TargetType.FTP_PCT, 100)) }) { Icon(Icons.Filled.Add, null); Text(" Step") }
                OutlinedButton(onClick = { steps.add(Step(20, "Sprint", TargetType.SPRINT)) }) { Icon(Icons.Filled.Add, null); Text(" Sprint") }
                OutlinedButton(onClick = {
                    repeat(4) { steps.add(Step(60, "Hard", TargetType.FTP_PCT, 110)); steps.add(Step(60, "Easy", TargetType.FTP_PCT, 50)) }
                }) { Icon(Icons.Filled.Add, null); Text(" 4× intervals") }
            }
        }
    }
}

@Composable
private fun StepEditor(s: Step, ftp: Int, onChange: (Step) -> Unit, onDelete: () -> Unit, onCopy: () -> Unit, onUp: () -> Unit, onDown: () -> Unit) {
    Panel(padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(s.label, { onChange(s.copy(label = it)) }, singleLine = true, modifier = Modifier.weight(1f), label = { Text("Label") })
            Spacer(Modifier.width(10.dp))
            Stepper("Time", fmtTime(s.seconds), { onChange(s.copy(seconds = (s.seconds - if (s.seconds > 60) 30 else 5).coerceAtLeast(5))) }, { onChange(s.copy(seconds = s.seconds + if (s.seconds >= 60) 30 else 5)) })
            if (s.type != TargetType.SPRINT && s.type != TargetType.FREE) {
                Spacer(Modifier.width(10.dp))
                val unit = when (s.type) { TargetType.FTP_PCT -> "%"; TargetType.WATTS -> " W"; else -> "" }
                Stepper(if (s.type == TargetType.LEVEL) "Level" else "Target", "${s.value}$unit",
                    { onChange(s.copy(value = (s.value - 5).coerceAtLeast(0))) }, { onChange(s.copy(value = s.value + 5)) })
            }
            IconButton(onClick = onUp) { Icon(Icons.Filled.KeyboardArrowUp, "Up") }
            IconButton(onClick = onDown) { Icon(Icons.Filled.KeyboardArrowDown, "Down") }
            IconButton(onClick = onCopy) { Icon(Icons.Filled.ContentCopy, "Duplicate") }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Delete", tint = C.heart) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            for ((t, label) in listOf(TargetType.FTP_PCT to "% FTP", TargetType.LEVEL to "Level", TargetType.SPRINT to "Sprint", TargetType.FREE to "Free")) {
                FilterChip(s.type == t, {
                    onChange(s.copy(type = t, value = when (t) { TargetType.FTP_PCT -> 75; TargetType.LEVEL -> 25; else -> 0 }))
                }, { Text(label) })
            }
            Spacer(Modifier.weight(1f))
            Text(describe(s, ftp), color = C.muted)
        }
    }
}

@Composable
private fun Stepper(label: String, value: String, minus: () -> Unit, plus: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Label(label)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = minus) { Text("−", style = MaterialTheme.typography.titleLarge) }
            Text(value, style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(64.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            TextButton(onClick = plus) { Text("+", style = MaterialTheme.typography.titleLarge) }
        }
    }
}
