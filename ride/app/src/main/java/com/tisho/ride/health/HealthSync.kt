package com.tisho.ride.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.CyclingPedalingCadenceRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.PowerRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Power
import com.tisho.ride.data.Ride
import com.tisho.ride.data.Sample
import java.time.Instant
import java.time.ZoneId

/**
 * Writes finished rides to Health Connect, which Google Fit and Samsung Health both read from.
 */
class HealthSync(private val context: Context) {
    private val tag = "RideHealth"

    val permissions = setOf(
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        HealthPermission.getWritePermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getWritePermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getWritePermission(HeartRateRecord::class),
        HealthPermission.getWritePermission(PowerRecord::class),
        HealthPermission.getWritePermission(CyclingPedalingCadenceRecord::class),
        HealthPermission.getWritePermission(DistanceRecord::class),
    )

    enum class Availability { AVAILABLE, NEEDS_INSTALL, UNSUPPORTED }

    fun availability(): Availability = when (HealthConnectClient.getSdkStatus(context, PROVIDER)) {
        HealthConnectClient.SDK_AVAILABLE -> Availability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> Availability.NEEDS_INSTALL
        else -> Availability.UNSUPPORTED
    }

    fun installIntent() = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PROVIDER&url=healthconnect%3A%2F%2Fonboarding"))
        .setPackage("com.android.vending")

    fun permissionContract() = PermissionController.createRequestPermissionResultContract(PROVIDER)

    private fun client() = HealthConnectClient.getOrCreate(context, PROVIDER)

    suspend fun hasPermissions(): Boolean = availability() == Availability.AVAILABLE &&
        client().permissionController.getGrantedPermissions().containsAll(permissions)

    /** Returns true when the ride was written. */
    suspend fun write(ride: Ride, samples: List<Sample>, title: String): Boolean {
        if (!hasPermissions() || ride.durationSec <= 0) return false
        val start = Instant.ofEpochMilli(ride.startedAt)
        val end = start.plusSeconds(ride.durationSec.toLong())
        val zone = ZoneId.systemDefault().rules.getOffset(start)
        val meta = { suffix: String -> Metadata.activelyRecorded(Device(type = Device.TYPE_UNKNOWN), clientRecordId = "ride-${ride.startedAt}-$suffix") }
        val records = ArrayList<Record>()
        records += ExerciseSessionRecord(start, zone, end, zone, meta("session"), ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY, title, ride.notes.ifBlank { null })
        records += TotalCaloriesBurnedRecord(start, zone, end, zone, Energy.kilocalories(ride.kcal), meta("total"))
        if (ride.distanceM > 0) records += DistanceRecord(start, zone, end, zone, Length.meters(ride.distanceM), meta("distance"))
        if (ride.activeKcal > 0) records += ActiveCaloriesBurnedRecord(start, zone, end, zone, Energy.kilocalories(ride.activeKcal), meta("active"))

        // Series records in 10-minute chunks.
        samples.chunked(600).forEachIndexed { i, chunk ->
            val cs = start.plusSeconds(chunk.first().t.toLong())
            val ce = start.plusSeconds(chunk.last().t.toLong() + 1)
            val hr = chunk.filter { it.hr in 1..300 }.map { HeartRateRecord.Sample(start.plusSeconds(it.t.toLong()), it.hr.toLong()) }
            if (hr.isNotEmpty()) records += HeartRateRecord(cs, zone, ce, zone, hr, meta("hr$i"))
            records += PowerRecord(cs, zone, ce, zone, chunk.map { PowerRecord.Sample(start.plusSeconds(it.t.toLong()), Power.watts(it.watts.toDouble())) }, meta("power$i"))
            records += CyclingPedalingCadenceRecord(cs, zone, ce, zone, chunk.map { CyclingPedalingCadenceRecord.Sample(start.plusSeconds(it.t.toLong()), it.rpm.toDouble()) }, meta("cad$i"))
        }
        return try {
            client().insertRecords(records)
            true
        } catch (e: Exception) {
            Log.w(tag, "Health Connect write failed", e)
            false
        }
    }

    companion object {
        const val PROVIDER = "com.google.android.apps.healthdata"
    }
}
