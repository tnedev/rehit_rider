package com.tisho.ride

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tisho.ride.bike.BikeStatus
import com.tisho.ride.hr.HrStatus
import com.tisho.ride.ui.AppViewModel
import com.tisho.ride.ui.Avatar
import com.tisho.ride.ui.C
import com.tisho.ride.ui.Chip
import com.tisho.ride.ui.RideTheme
import com.tisho.ride.ui.screens.BuilderScreen
import com.tisho.ride.ui.screens.HistoryScreen
import com.tisho.ride.ui.screens.HomeScreen
import com.tisho.ride.ui.screens.LeaderboardScreen
import com.tisho.ride.ui.screens.ProfileEditScreen
import com.tisho.ride.ui.screens.ProfilesScreen
import com.tisho.ride.ui.screens.ProgressScreen
import com.tisho.ride.ui.screens.RideDetailScreen
import com.tisho.ride.ui.screens.RideScreen
import com.tisho.ride.ui.screens.SettingsScreen
import com.tisho.ride.ui.screens.SummaryScreen
import com.tisho.ride.ui.screens.WorkoutDetailScreen

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            val s by vm.settings.collectAsState()
            RideTheme(s.theme) {
                val bar = C.surface.toArgb()
                SideEffect {
                    window.statusBarColor = bar
                    window.navigationBarColor = bar
                    val light = !C.palette.dark
                    var flags = window.decorView.systemUiVisibility
                    flags = if (light) flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR else flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                    if (Build.VERSION.SDK_INT >= 26) flags = if (light) flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else flags and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
                    window.decorView.systemUiVisibility = flags
                }
                AppRoot(vm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.bike.connect()
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Ride", Icons.Filled.Home),
    Tab("history", "History", Icons.Filled.History),
    Tab("progress", "Progress", Icons.Filled.Insights),
    Tab("riders", "Riders", Icons.Filled.EmojiEvents),
    Tab("me", "Profile", Icons.Filled.Person),
    Tab("settings", "Settings", Icons.Filled.Settings),
)

@Composable
fun AppRoot(vm: AppViewModel) {
    val nav = rememberNavController()
    val profile by vm.profile.collectAsState()
    val profiles by vm.profiles.collectAsState()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: ""
    val showChrome = tabs.any { it.route == route } || route.startsWith("workout/") || route.startsWith("rides/") || route.startsWith("builder")

    LaunchedEffect(profile?.id) {
        if (profile == null && route.isNotEmpty() && route != "profiles" && route != "profileEdit?id={id}&first={first}") {
            nav.navigate("profiles") { popUpTo(0) }
        }
    }

    Row(Modifier.fillMaxSize().background(C.bg)) {
        if (showChrome) Rail(nav, route)
        Column(Modifier.fillMaxSize()) {
            if (showChrome) TopStatus(vm, nav)
            Box(Modifier.weight(1f)) { Graph(vm, nav) }
        }
    }
}

@Composable
private fun Rail(nav: NavHostController, route: String) {
    NavigationRail(containerColor = C.surface, modifier = Modifier.fillMaxHeight().width(104.dp)) {
        Spacer(Modifier.padding(top = 12.dp))
        Icon(Icons.AutoMirrored.Filled.DirectionsBike, null, tint = C.accent, modifier = Modifier.padding(bottom = 24.dp))
        for (t in tabs) {
            NavigationRailItem(
                selected = route == t.route,
                onClick = { nav.navigate(t.route) { popUpTo("home"); launchSingleTop = true } },
                icon = { Icon(t.icon, t.label) },
                label = { Text(t.label) },
                colors = NavigationRailItemDefaults.colors(selectedIconColor = C.accent, selectedTextColor = C.accent, indicatorColor = C.accent.copy(alpha = 0.15f), unselectedIconColor = C.muted, unselectedTextColor = C.muted),
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun TopStatus(vm: AppViewModel, nav: NavHostController) {
    val bike by vm.bike.status.collectAsState()
    val hr by vm.hrm.status.collectAsState()
    val bpm by vm.hrm.bpm.collectAsState()
    val profile by vm.profile.collectAsState()
    Row(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(greeting(profile?.name), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        val (bikeText, bikeColor) = when (bike) {
            BikeStatus.READY -> "Bike connected" to C.good
            BikeStatus.SIMULATED -> "Demo bike" to C.level
            BikeStatus.CONNECTING -> "Waking bike…" to C.power
            BikeStatus.NEEDS_PERMISSION -> "Allow USB access" to C.power
            BikeStatus.ERROR -> "Bike error — replug" to C.heart
            BikeStatus.DISCONNECTED -> "Bike not found" to C.muted
        }
        Chip(bikeText, bikeColor, Icons.Filled.Usb) { vm.bike.connect() }
        Spacer(Modifier.width(10.dp))
        when (hr) {
            HrStatus.CONNECTED -> Chip(if (bpm > 0) "$bpm bpm" else "HR strap", C.heart, Icons.Filled.Favorite) { nav.navigate("settings") }
            HrStatus.CONNECTING -> Chip("HR strap…", C.muted, Icons.Filled.Bluetooth) { nav.navigate("settings") }
            else -> {}
        }
        Spacer(Modifier.width(16.dp))
        profile?.let { Avatar(it, 44.dp) { nav.navigate("profiles") } }
    }
}

private fun greeting(name: String?): String {
    val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val part = when (h) { in 5..11 -> "Good morning"; in 12..17 -> "Good afternoon"; else -> "Good evening" }
    return if (name == null) part else "$part, ${name.substringBefore(' ')}"
}

@Composable
private fun Graph(vm: AppViewModel, nav: NavHostController) {
    // Every launch starts with "Who's riding?"; the avatar in the top bar switches rider any time.
    NavHost(nav, startDestination = "profiles") {
        composable("profiles") { ProfilesScreen(vm, nav) }
        composable(
            "profileEdit?id={id}&first={first}",
            arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = 0L }, navArgument("first") { type = NavType.BoolType; defaultValue = false }),
        ) { ProfileEditScreen(vm, nav, it.arguments?.getLong("id") ?: 0L) }
        composable("home") { HomeScreen(vm, nav) }
        composable("workout/{id}") { WorkoutDetailScreen(vm, nav, it.arguments?.getString("id") ?: "") }
        composable(
            "builder?id={id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = 0L }),
        ) { BuilderScreen(vm, nav, it.arguments?.getLong("id") ?: 0L) }
        composable("ride") { RideScreen(vm, nav) }
        composable("summary") { SummaryScreen(vm, nav) }
        composable("history") { HistoryScreen(vm, nav) }
        composable("rides/{id}") { RideDetailScreen(vm, nav, it.arguments?.getString("id")?.toLongOrNull() ?: 0L) }
        composable("progress") { ProgressScreen(vm, nav) }
        composable("riders") { LeaderboardScreen(vm) }
        composable("me") { ProfileEditScreen(vm, nav, vm.profile.value?.id ?: 0L, embedded = true) }
        composable("settings") { SettingsScreen(vm, nav) }
    }
}
