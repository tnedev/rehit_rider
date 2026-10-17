package com.tisho.ride.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tisho.ride.ui.C
import com.tisho.ride.ui.RideTheme

/** Shown by Health Connect when the user asks why Ride wants health permissions. */
class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RideTheme {
                Column(Modifier.fillMaxSize().background(C.bg).padding(48.dp)) {
                    Text("How REHIT Rider uses Health Connect", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "REHIT Rider only writes data: after each ride it saves a stationary-bike workout with its duration, calories, " +
                            "heart rate, power and cadence, so it shows up in Google Fit, Samsung Health and other apps you allow. " +
                            "REHIT Rider does not read any health data and nothing leaves this device except through Health Connect.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { finish() }) { Text("OK") }
                }
            }
        }
    }
}
