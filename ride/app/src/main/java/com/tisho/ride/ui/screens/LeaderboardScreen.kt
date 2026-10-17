package com.tisho.ride.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.tisho.ride.ui.AppViewModel
import com.tisho.ride.ui.Avatar
import com.tisho.ride.ui.C
import com.tisho.ride.ui.Label
import com.tisho.ride.ui.Panel
import com.tisho.ride.workout.Metrics

private enum class RankBy(val label: String) { SCORE("Score"), FITNESS("Fitness"), SPRINT("Sprint W/kg"), WEEK("This week"), IMPROVEMENT("Improvement") }

@Composable
fun LeaderboardScreen(vm: AppViewModel) {
    val scores by vm.leaderboard.collectAsState()
    val me by vm.profile.collectAsState()
    var by by remember { mutableStateOf(RankBy.SCORE) }
    val sorted = when (by) {
        RankBy.SCORE -> scores.sortedByDescending { it.total }
        RankBy.FITNESS -> scores.sortedByDescending { it.vo2 }
        RankBy.SPRINT -> scores.sortedByDescending { it.sprintWkg }
        RankBy.WEEK -> scores.sortedByDescending { it.weekMinutes }
        RankBy.IMPROVEMENT -> scores.sortedByDescending { it.improvementPct ?: Double.NEGATIVE_INFINITY }
    }

    Row(Modifier.fillMaxSize().padding(horizontal = 32.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.weight(1.7f)) {
            Text("Riders", style = MaterialTheme.typography.headlineMedium)
            Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (r in RankBy.entries) FilterChip(by == r, { by = r }, { Text(r.label) })
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(sorted, key = { _, s -> s.profile.id }) { i, s -> RiderRow(i + 1, s, by, s.profile.id == me?.id) }
            }
        }
        Panel(Modifier.weight(1f).padding(top = 8.dp)) {
            Label("How the score works")
            Spacer(Modifier.height(10.dp))
            Legend(C.cadence, "Fitness · 400", "Estimated VO₂max compared with the norm for your age and sex, so a 60-year-old and a 30-year-old compete fairly.")
            Legend(C.good, "Consistency · 300", "Active minutes this week against your own goal, plus your week streak.")
            Legend(C.accent, "Sprint · 150", "Best 5-second power per kg of body weight in the last 30 days.")
            Legend(C.level, "Improvement · 150", "Best 20-second power over the last 4 weeks against the 4 weeks before.")
            Spacer(Modifier.height(8.dp))
            Text("Take the ramp test for an accurate VO₂max; until then fitness is estimated from FTP.", color = C.muted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun Legend(color: Color, title: String, text: String) {
    Row(Modifier.padding(vertical = 6.dp)) {
        Box(Modifier.padding(top = 5.dp).width(10.dp).height(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, color = C.muted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun RiderRow(rank: Int, s: Metrics.RiderScore, by: RankBy, isMe: Boolean) {
    val medal = when (rank) { 1 -> Color(0xFFD4A017); 2 -> Color(0xFF9AA3AD); 3 -> Color(0xFFB87333); else -> C.muted }
    Panel(padding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$rank", style = MaterialTheme.typography.headlineMedium, color = medal, modifier = Modifier.width(44.dp))
            Avatar(s.profile, 48.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(s.profile.name + if (isMe) "  · you" else "", style = MaterialTheme.typography.titleLarge)
                Text(
                    "VO₂max ${"%.0f".format(s.vo2)}${if (s.vo2Estimated) "*" else ""} · sprint ${"%.1f".format(s.sprintWkg)} W/kg · " +
                        "${s.weekMinutes} min this week · ${s.streak} wk streak" +
                        (s.improvementPct?.let { " · ${if (it >= 0) "+" else ""}${"%.0f".format(it)}%" } ?: ""),
                    color = C.muted, style = MaterialTheme.typography.bodyMedium,
                )
            }
            val (value, unit) = when (by) {
                RankBy.SCORE -> "${s.total}" to "pts"
                RankBy.FITNESS -> "%.0f".format(s.vo2) to "VO₂"
                RankBy.SPRINT -> "%.1f".format(s.sprintWkg) to "W/kg"
                RankBy.WEEK -> "${s.weekMinutes}" to "min"
                RankBy.IMPROVEMENT -> (s.improvementPct?.let { "%+.0f".format(it) } ?: "—") to "%"
            }
            Text(value, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text(" $unit", color = C.muted, modifier = Modifier.padding(top = 14.dp))
        }
        Spacer(Modifier.height(12.dp))
        // Stacked score bar out of 1000.
        Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(C.surface2)) {
            for ((part, color) in listOf(s.fitness to C.cadence, s.consistency to C.good, s.sprint to C.accent, s.improvement to C.level)) {
                if (part > 0) Box(Modifier.weight(part.toFloat()).fillMaxHeight().background(color))
            }
            val rest = 1000 - s.total
            if (rest > 0) Spacer(Modifier.weight(rest.toFloat()))
        }
    }
}
