package com.tisho.ride

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Opens the app after the tablet boots, for setups where it isn't the default home app. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
