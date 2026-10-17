package com.tisho.ride.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tisho.ride.data.Profile
import com.tisho.ride.workout.Step
import com.tisho.ride.workout.TargetType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

@Composable
fun Panel(modifier: Modifier = Modifier, padding: Dp = 20.dp, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val m = modifier.clip(RoundedCornerShape(20.dp)).background(C.surface)
    Column((if (onClick != null) m.clickable(onClick = onClick) else m).padding(padding), content = content)
}

@Composable
fun Label(text: String, color: Color = C.muted) =
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = color)

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun Stat(label: String, value: String, unit: String = "", color: Color = C.text, modifier: Modifier = Modifier, big: Boolean = false) {
    Column(modifier) {
        Label(label)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = if (big) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium, color = color)
            if (unit.isNotEmpty()) Text(" $unit", style = MaterialTheme.typography.bodyMedium, color = C.muted, modifier = Modifier.padding(bottom = 5.dp))
        }
    }
}

@Composable
fun Avatar(p: Profile, size: Dp = 44.dp, onClick: (() -> Unit)? = null) {
    val m = Modifier.size(size).clip(CircleShape).background(C.avatars[p.color % C.avatars.size])
    Box(if (onClick != null) m.clickable(onClick = onClick) else m, contentAlignment = Alignment.Center) {
        Text(p.initials, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.38f).sp)
    }
}

@Composable
fun Chip(text: String, color: Color, icon: ImageVector? = null, onClick: (() -> Unit)? = null) {
    val m = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f)).border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(50))
    Row((if (onClick != null) m.clickable(onClick = onClick) else m).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, tint = color, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, color = color, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun Ring(progress: Float, color: Color, modifier: Modifier = Modifier, stroke: Dp = 12.dp, content: @Composable () -> Unit = {}) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            val arc = Size(size.width - s, size.height - s)
            drawArc(C.surface2, -90f, 360f, false, Offset(s / 2, s / 2), arc, style = Stroke(s, cap = StrokeCap.Round))
            drawArc(color, -90f, 360f * progress.coerceIn(0f, 1f), false, Offset(s / 2, s / 2), arc, style = Stroke(s, cap = StrokeCap.Round))
        }
        content()
    }
}

data class Series(val values: List<Float>, val color: Color, val fill: Boolean = false, val max: Float? = null)

/** Lightweight multi-series line chart; each series is scaled to its own max unless given. */
@Composable
fun LineChart(series: List<Series>, modifier: Modifier = Modifier, markers: List<Float> = emptyList()) {
    Canvas(modifier) {
        for (x in markers) drawLine(C.outline, Offset(x * size.width, 0f), Offset(x * size.width, size.height), 2f)
        for (s in series) {
            val v = s.values
            if (v.size < 2) continue
            val top = s.max ?: max(1f, v.max() * 1.1f)
            val dx = size.width / (v.size - 1)
            val path = Path()
            v.forEachIndexed { i, y ->
                val px = i * dx
                val py = size.height - (y / top).coerceIn(0f, 1f) * size.height
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            if (s.fill) {
                val area = Path().apply { addPath(path); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
                drawPath(area, s.color.copy(alpha = 0.16f))
            }
            drawPath(path, s.color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

/** Workout shape: bar height = intensity, width = duration. Highlights done/current part. */
@Composable
fun WorkoutProfile(steps: List<Step>, ftp: Int, modifier: Modifier = Modifier, progressSec: Int = -1) {
    val total = steps.sumOf { it.seconds }.coerceAtLeast(1)
    Canvas(modifier) {
        var x = 0f
        val gap = 1.5f
        for (s in steps) {
            val w = s.seconds.toFloat() / total * size.width
            val rel = intensity(s, ftp)
            val h = (0.12f + rel * 0.88f) * size.height
            val start = x / size.width * total
            val color = if (s.type == TargetType.SPRINT) C.accent else C.zones[zoneFor(rel)]
            val dim = progressSec >= 0 && start + s.seconds <= progressSec
            drawRoundRect(color.copy(alpha = if (dim) 0.35f else 0.9f), Offset(x, size.height - h), Size(max(1f, w - gap), h), CornerRadius(4f, 4f))
            x += w
        }
        if (progressSec >= 0) {
            val px = progressSec.toFloat() / total * size.width
            drawLine(C.text, Offset(px, 0f), Offset(px, size.height), 4f)
        }
    }
}

/** 0..1 relative intensity used for drawing. */
fun intensity(s: Step, ftp: Int): Float = when (s.type) {
    TargetType.SPRINT -> 1f
    TargetType.FTP_PCT -> (s.value / 170f).coerceIn(0f, 1f)
    TargetType.WATTS -> (s.value / (ftp * 1.7f)).coerceIn(0f, 1f)
    TargetType.LEVEL -> (s.value / 70f).coerceIn(0f, 1f)
    TargetType.FREE -> 0.25f
}

private fun zoneFor(rel: Float) = when {
    rel > 0.85f -> 6; rel > 0.68f -> 5; rel > 0.6f -> 4; rel > 0.52f -> 3; rel > 0.4f -> 2; else -> 1
}

fun fmtTime(sec: Int): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun fmtDate(t: Long): String = SimpleDateFormat("EEE d MMM · HH:mm", Locale.getDefault()).format(Date(t))
fun fmtDay(t: Long): String = SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(t))
