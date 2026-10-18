package com.tisho.ridesync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
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
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

@Serializable data class PairRequest(val code: String, val device: String)
@Serializable data class PairResponse(val token: String, val rider: String)
@Serializable data class SyncRide(
    val startedAt: Long, val durationSec: Int, val workoutName: String, val notes: String,
    val kcal: Double, val activeKcal: Double, val avgWatts: Double, val avgHr: Int,
    val distanceM: Double = 0.0,
    val samples: List<List<Int>>,
)
@Serializable data class SyncResponse(val rider: String, val rides: List<SyncRide>)

data class Tablet(val name: String, val host: String, val port: Int)

private val json = Json { ignoreUnknownKeys = true }
private const val SERVICE_TYPE = "_rehitrider._tcp."

/** Pairing state stored on the phone. */
class Store(context: Context) {
    private val p = context.getSharedPreferences("sync", Context.MODE_PRIVATE)
    var token: String? get() = p.getString("token", null); set(v) = p.edit().putString("token", v).apply()
    var rider: String get() = p.getString("rider", "") ?: ""; set(v) = p.edit().putString("rider", v).apply()
    var tabletName: String get() = p.getString("tablet", "") ?: ""; set(v) = p.edit().putString("tablet", v).apply()
    var host: String get() = p.getString("host", "") ?: ""; set(v) = p.edit().putString("host", v).apply()
    var port: Int get() = p.getInt("port", 47820); set(v) = p.edit().putInt("port", v).apply()
    var since: Long get() = p.getLong("since", 0); set(v) = p.edit().putLong("since", v).apply()
    var lastSync: Long get() = p.getLong("lastSync", 0); set(v) = p.edit().putLong("lastSync", v).apply()
    var lastResult: String get() = p.getString("lastResult", "") ?: ""; set(v) = p.edit().putString("lastResult", v).apply()
    var synced: Int get() = p.getInt("synced", 0); set(v) = p.edit().putInt("synced", v).apply()
    val linked get() = token != null
    fun clear() = p.edit().clear().apply()
}

object Discovery {
    /** Finds REHIT Rider tablets on the local network for up to [ms] milliseconds. */
    suspend fun find(context: Context, ms: Long = 6000): List<Tablet> = withContext(Dispatchers.IO) {
        val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        val found = java.util.concurrent.ConcurrentHashMap<String, Tablet>()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(info: NsdServiceInfo) {
                @Suppress("DEPRECATION")
                nsd.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onServiceResolved(r: NsdServiceInfo) {
                        val host = r.host?.hostAddress ?: return
                        found[r.serviceName] = Tablet(r.serviceName, host, r.port)
                    }
                    override fun onResolveFailed(i: NsdServiceInfo, e: Int) {}
                })
            }
            override fun onDiscoveryStarted(t: String) {}
            override fun onDiscoveryStopped(t: String) {}
            override fun onServiceLost(i: NsdServiceInfo) {}
            override fun onStartDiscoveryFailed(t: String, e: Int) {}
            override fun onStopDiscoveryFailed(t: String, e: Int) {}
        }
        nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        kotlinx.coroutines.delay(ms)
        runCatching { nsd.stopServiceDiscovery(listener) }
        found.values.toList()
    }
}

object Api {
    private fun http(url: String, method: String, token: String? = null, body: String? = null): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 5000
        c.readTimeout = 30000
        c.requestMethod = method
        if (token != null) c.setRequestProperty("Authorization", "Bearer $token")
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = c.responseCode
        val text = (if (code < 400) c.inputStream else c.errorStream)?.use { it.readBytes().decodeToString() } ?: ""
        return code to text
    }

    suspend fun pair(t: Tablet, code: String): PairResponse = withContext(Dispatchers.IO) {
        val (status, text) = http("http://${t.host}:${t.port}/pair", "POST",
            body = json.encodeToString(PairRequest.serializer(), PairRequest(code.filter { it.isDigit() }, "${Build.MANUFACTURER} ${Build.MODEL}")))
        if (status != 200) error(if (status == 403) "Wrong or expired code" else "Tablet error $status")
        json.decodeFromString(PairResponse.serializer(), text)
    }

    suspend fun rides(host: String, port: Int, token: String, since: Long): SyncResponse = withContext(Dispatchers.IO) {
        val (status, text) = http("http://$host:$port/rides?since=$since", "GET", token)
        if (status == 401) error("This phone was unlinked on the tablet")
        if (status != 200) error("Tablet error $status")
        json.decodeFromString(SyncResponse.serializer(), text)
    }
}

object Health {
    const val PROVIDER = "com.google.android.apps.healthdata"
    val permissions = setOf(
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        HealthPermission.getWritePermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getWritePermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getWritePermission(HeartRateRecord::class),
        HealthPermission.getWritePermission(PowerRecord::class),
        HealthPermission.getWritePermission(CyclingPedalingCadenceRecord::class),
        HealthPermission.getWritePermission(DistanceRecord::class),
    )

    fun available(context: Context) = HealthConnectClient.getSdkStatus(context, PROVIDER) == HealthConnectClient.SDK_AVAILABLE
    fun client(context: Context) = HealthConnectClient.getOrCreate(context, PROVIDER)
    suspend fun granted(context: Context) = available(context) && client(context).permissionController.getGrantedPermissions().containsAll(permissions)

    /** Writes one ride; clientRecordIds make re-syncs update instead of duplicate. */
    suspend fun write(context: Context, r: SyncRide) {
        val start = Instant.ofEpochMilli(r.startedAt)
        val end = start.plusSeconds(r.durationSec.toLong())
        val zone = ZoneId.systemDefault().rules.getOffset(start)
        val meta = { s: String -> Metadata.activelyRecorded(Device(type = Device.TYPE_PHONE), clientRecordId = "rehit-${r.startedAt}-$s") }
        val records = ArrayList<Record>()
        records += ExerciseSessionRecord(start, zone, end, zone, meta("session"), ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY,
            "REHIT Rider · ${r.workoutName}", r.notes.ifBlank { null })
        records += TotalCaloriesBurnedRecord(start, zone, end, zone, Energy.kilocalories(r.kcal), meta("total"))
        if (r.distanceM > 0) records += DistanceRecord(start, zone, end, zone, Length.meters(r.distanceM), meta("distance"))
        if (r.activeKcal > 0) records += ActiveCaloriesBurnedRecord(start, zone, end, zone, Energy.kilocalories(r.activeKcal), meta("active"))
        r.samples.chunked(600).forEachIndexed { i, chunk ->
            val cs = start.plusSeconds(chunk.first()[0].toLong())
            val ce = start.plusSeconds(chunk.last()[0].toLong() + 1)
            val at = { s: List<Int> -> start.plusSeconds(s[0].toLong()) }
            val hr = chunk.filter { it[3] in 1..300 }.map { HeartRateRecord.Sample(at(it), it[3].toLong()) }
            if (hr.isNotEmpty()) records += HeartRateRecord(cs, zone, ce, zone, hr, meta("hr$i"))
            records += PowerRecord(cs, zone, ce, zone, chunk.map { PowerRecord.Sample(at(it), Power.watts(it[1].toDouble())) }, meta("power$i"))
            records += CyclingPedalingCadenceRecord(cs, zone, ce, zone, chunk.map { CyclingPedalingCadenceRecord.Sample(at(it), it[2].toDouble()) }, meta("cad$i"))
        }
        client(context).insertRecords(records)
    }
}

object Syncer {
    /** One sync pass: find the tablet (its IP may have changed), pull new rides, write them. Returns rides written. */
    suspend fun run(context: Context): Int {
        val store = Store(context)
        val token = store.token ?: return 0
        if (!Health.granted(context)) { store.lastResult = "Allow Health Connect access"; return 0 }
        val response = runCatching { Api.rides(store.host, store.port, token, store.since) }.getOrElse {
            val t = withTimeoutOrNull(8000) { Discovery.find(context, 5000) }?.firstOrNull { it.name == store.tabletName }
            if (t == null) { store.lastResult = "Tablet not found — open REHIT Rider on the same Wi-Fi"; return 0 }
            store.host = t.host; store.port = t.port
            runCatching { Api.rides(t.host, t.port, token, store.since) }.getOrElse { e -> store.lastResult = e.message ?: "Sync failed"; return 0 }
        }
        var n = 0
        for (r in response.rides.sortedBy { it.startedAt }) {
            try {
                Health.write(context, r)
                store.since = r.startedAt
                n++
            } catch (e: Exception) {
                Log.w("RideSync", "write failed", e)
                store.lastResult = "Health Connect write failed: ${e.message}"
                return n
            }
        }
        store.synced = store.synced + n
        store.lastSync = System.currentTimeMillis()
        store.lastResult = if (n == 0) "Up to date" else "Synced $n new ride${if (n == 1) "" else "s"}"
        return n
    }

    /**
     * Every 15 minutes (Android's minimum for background work) while on Wi-Fi, plus an extra pass as
     * soon as the phone joins Wi-Fi.
     */
    fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context)
        // Wi-Fi only: away from home the tablet can't be reached, so don't spend battery trying.
        val periodic = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).setRequiresBatteryNotLow(true).build())
            .build()
        wm.enqueueUniquePeriodicWork("rehit-sync", ExistingPeriodicWorkPolicy.UPDATE, periodic)
        val onWifi = androidx.work.OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
            .build()
        wm.enqueueUniqueWork("rehit-sync-wifi", androidx.work.ExistingWorkPolicy.KEEP, onWifi)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork("rehit-sync")
        WorkManager.getInstance(context).cancelUniqueWork("rehit-sync-wifi")
    }
}

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Syncer.run(applicationContext)
        // Re-arm the "next time on Wi-Fi" pass so a ride is picked up when you get home.
        Syncer.schedule(applicationContext)
        return Result.success()
    }
}
