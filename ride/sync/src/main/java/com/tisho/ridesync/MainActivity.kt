package com.tisho.ridesync

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private val Accent = Color(0xFFE2431C)
private val Muted = Color(0xFF505B69)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Accent, background = Color(0xFFF3F5F8))) { App() }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).padding(20.dp)) { content() }
}

@Composable
private fun App() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val store = remember { Store(ctx) }
    var refresh by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().background(Color(0xFFF3F5F8)).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("REHIT Rider Sync", style = MaterialTheme.typography.headlineMedium)
        Text("Sends your rides from the bike tablet to Google Fit and Samsung Health on this phone.", color = Muted)
        refresh.let { }
        if (!store.linked) LinkFlow(store) { refresh++ } else Linked(store, onChange = { refresh++ }) {
            scope.launch { Syncer.run(ctx); refresh++ }
        }
    }
}

@Composable
private fun LinkFlow(store: Store, onLinked: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var tablets by remember { mutableStateOf<List<Tablet>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var chosen by remember { mutableStateOf<Tablet?>(null) }
    var manualIp by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun search() = scope.launch { searching = true; tablets = Discovery.find(ctx); searching = false }
    LaunchedEffect(Unit) { search() }

    Card {
        Text("1. Find your bike tablet", style = MaterialTheme.typography.titleLarge)
        Text("Open REHIT Rider on the tablet. Both devices must be on the same Wi-Fi.", color = Muted)
        Spacer(Modifier.height(8.dp))
        for (t in tablets) {
            Row(Modifier.fillMaxWidth()) {
                TextButton(onClick = { chosen = t }) { Text((if (chosen == t) "● " else "○ ") + t.name) }
            }
        }
        if (tablets.isEmpty()) Text(if (searching) "Searching…" else "No tablet found yet.", color = Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { search() }, enabled = !searching) { Text("Search again") }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(manualIp, { manualIp = it; chosen = null }, label = { Text("…or tablet IP address") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    }
    Card {
        Text("2. Enter the code", style = MaterialTheme.typography.titleLarge)
        Text("On the tablet: choose your rider → Settings → Sync to my phone → Link a phone.", color = Muted)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, label = { Text("6-digit code") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth())
        error?.let { Text(it, color = Color(0xFFD01F46)) }
        Spacer(Modifier.height(8.dp))
        Button(
            enabled = code.length == 6 && (chosen != null || manualIp.isNotBlank()),
            onClick = {
                val t = chosen ?: Tablet(manualIp.trim(), manualIp.trim(), 47820)
                scope.launch {
                    runCatching { Api.pair(t, code) }.onSuccess { r ->
                        store.token = r.token; store.rider = r.rider; store.tabletName = t.name; store.host = t.host; store.port = t.port
                        store.since = 0
                        Syncer.schedule(ctx)
                        onLinked()
                    }.onFailure { error = it.message ?: "Could not reach the tablet" }
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Accent),
        ) { Text("Link") }
    }
}

@Composable
private fun Linked(store: Store, onChange: () -> Unit, syncNow: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }
    val available = remember { Health.available(ctx) }
    val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract(Health.PROVIDER)) {
        granted = it.containsAll(Health.permissions)
        if (granted) syncNow()
    }
    LaunchedEffect(Unit) { Syncer.schedule(ctx); granted = Health.granted(ctx); if (granted) { syncing = true; Syncer.run(ctx); syncing = false; onChange() } }

    Card {
        Text("Linked as ${store.rider}", style = MaterialTheme.typography.titleLarge)
        Text("Tablet: ${store.tabletName}", color = Muted)
        Text("Rides synced: ${store.synced}", color = Muted)
        if (store.lastSync > 0) Text("Last sync: ${DateFormat.getDateTimeInstance().format(Date(store.lastSync))}", color = Muted)
        if (store.lastResult.isNotEmpty()) Text(store.lastResult)
        Spacer(Modifier.height(12.dp))
        Button(onClick = { scope.launch { syncing = true; Syncer.run(ctx); syncing = false; onChange() } }, enabled = granted && !syncing,
            colors = ButtonDefaults.buttonColors(containerColor = Accent)) { Text(if (syncing) "Syncing…" else "Sync now") }
        Text("Syncs automatically about every 15 minutes and when you get home to Wi-Fi, as long as REHIT Rider is open on the tablet. Opening this app syncs straight away.", color = Muted)
    }
    Card {
        Text("Google Fit & Samsung Health", style = MaterialTheme.typography.titleLarge)
        when {
            !available -> {
                Text("Install Health Connect (built into Android 14+, from the Play Store on older phones).", color = Muted)
                Button(onClick = {
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${Health.PROVIDER}")).setPackage("com.android.vending")) }
                }) { Text("Get Health Connect") }
            }
            granted -> Text("Access granted. In Google Fit and Samsung Health, enable Health Connect sync to see your rides.", color = Muted)
            else -> Button(onClick = { launcher.launch(Health.permissions) }, colors = ButtonDefaults.buttonColors(containerColor = Accent)) { Text("Allow Health Connect access") }
        }
    }
    TextButton(onClick = { Syncer.cancel(ctx); store.clear(); onChange() }) { Text("Unlink this phone", color = Color(0xFFD01F46)) }
}
