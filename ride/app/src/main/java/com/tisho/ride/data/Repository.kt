package com.tisho.ride.data

import android.content.Context
import com.tisho.ride.workout.Category
import com.tisho.ride.workout.Step
import com.tisho.ride.workout.Workout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Serializable
data class Backup(
    val version: Int = 1,
    val profiles: List<Profile>,
    val rides: List<Ride>,
    val samples: List<Sample>,
    val workouts: List<CustomWorkout>,
)

data class Settings(val voice: Boolean = true, val sounds: Boolean = true, val demo: Boolean = false, val activeProfile: Long = 0, val theme: String = "light")

class Repository(context: Context, val db: RideDb) {
    private val prefs = context.getSharedPreferences("ride", Context.MODE_PRIVATE)
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _settings = MutableStateFlow(
        Settings(prefs.getBoolean("voice", true), prefs.getBoolean("sounds", true), prefs.getBoolean("demo", false), prefs.getLong("profile", 0), prefs.getString("theme", "light") ?: "light")
    )
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    fun updateSettings(s: Settings) {
        prefs.edit().putBoolean("voice", s.voice).putBoolean("sounds", s.sounds).putBoolean("demo", s.demo)
            .putLong("profile", s.activeProfile).putString("theme", s.theme).apply()
        _settings.value = s
    }

    val profiles: Flow<List<Profile>> = db.profiles().all()
    fun profile(id: Long) = db.profiles().byId(id)
    suspend fun saveProfile(p: Profile): Long = if (p.id == 0L) db.profiles().upsert(p) else { db.profiles().update(p); p.id }

    suspend fun deleteProfile(p: Profile) {
        db.rides().deleteSamplesForProfile(p.id)
        db.rides().deleteRidesForProfile(p.id)
        db.profiles().delete(p)
        if (settings.value.activeProfile == p.id) updateSettings(settings.value.copy(activeProfile = 0))
    }

    fun rides(profileId: Long) = db.rides().forProfile(profileId)
    fun ride(id: Long) = db.rides().byId(id)
    suspend fun samples(rideId: Long) = db.rides().samples(rideId)
    suspend fun saveRide(r: Ride, s: List<Sample>) = db.rides().insertWithSamples(r, s)
    suspend fun updateRide(r: Ride) = db.rides().update(r)
    suspend fun deleteRide(id: Long) = db.rides().delete(id)
    suspend fun deleteAllRides(profileId: Long) {
        db.rides().deleteSamplesForProfile(profileId)
        db.rides().deleteRidesForProfile(profileId)
    }

    val customWorkouts: Flow<List<Workout>> = db.workouts().all().map { list -> list.map(::toWorkout) }
    suspend fun saveCustomWorkout(name: String, description: String, steps: List<Step>, id: Long = 0) =
        db.workouts().upsert(CustomWorkout(id, name, description, json.encodeToString(ListSerializer(Step.serializer()), steps)))
    suspend fun deleteCustomWorkout(id: Long) = db.workouts().delete(id)
    suspend fun customWorkout(id: Long) = db.workouts().get(id)

    fun toWorkout(w: CustomWorkout) = Workout(
        id = "custom:${w.id}", name = w.name, category = Category.CUSTOM,
        summary = w.description.ifBlank { "Your custom workout" },
        science = "Built by you.",
        steps = runCatching { json.decodeFromString(ListSerializer(Step.serializer()), w.stepsJson) }.getOrDefault(emptyList()),
    )

    suspend fun exportBackup(): String = json.encodeToString(
        Backup(
            profiles = db.profiles().allNow(),
            rides = db.profiles().allNow().flatMap { db.rides().forProfileNow(it.id) },
            samples = db.profiles().allNow().flatMap { p -> db.rides().forProfileNow(p.id).flatMap { db.rides().samples(it.id) } },
            workouts = db.workouts().allNow(),
        )
    )

    /** Merges a backup: profiles and workouts get new ids, rides are re-linked. */
    suspend fun importBackup(text: String): Int {
        val b = json.decodeFromString(Backup.serializer(), text)
        val profileIds = HashMap<Long, Long>()
        for (p in b.profiles) profileIds[p.id] = db.profiles().upsert(p.copy(id = 0))
        val samplesByRide = b.samples.groupBy { it.rideId }
        var n = 0
        for (r in b.rides) {
            val pid = profileIds[r.profileId] ?: continue
            db.rides().insertWithSamples(r.copy(id = 0, profileId = pid), samplesByRide[r.id].orEmpty())
            n++
        }
        for (w in b.workouts) db.workouts().upsert(w.copy(id = 0))
        return n
    }

    /** TCX for Strava / Garmin Connect / TrainingPeaks import. */
    suspend fun exportTcx(ride: Ride): String {
        val samples = samples(ride.id)
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>
<TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2" xmlns:ns3="http://www.garmin.com/xmlschemas/ActivityExtension/v2">
<Activities><Activity Sport="Biking"><Id>${iso.format(Date(ride.startedAt))}</Id>
<Lap StartTime="${iso.format(Date(ride.startedAt))}"><TotalTimeSeconds>${ride.durationSec}</TotalTimeSeconds><DistanceMeters>${ride.distanceM.toInt()}</DistanceMeters><Calories>${ride.activeKcal.toInt()}</Calories><Intensity>Active</Intensity><TriggerMethod>Manual</TriggerMethod><Track>
""")
        // Spread the ride's virtual distance over its samples in proportion to speed from power.
        val vs = com.tisho.ride.workout.VirtualSpeed(70.0)
        val raw = samples.map { vs.steadySpeed(it.watts.toDouble()) }
        val scale = if (raw.sum() > 0) ride.distanceM / raw.sum() else 0.0
        var dist = 0.0
        for ((i, s) in samples.withIndex()) {
            dist += raw[i] * scale
            sb.append("<Trackpoint><Time>").append(iso.format(Date(ride.startedAt + s.t * 1000L))).append("</Time>")
            if (s.hr > 0) sb.append("<HeartRateBpm><Value>").append(s.hr).append("</Value></HeartRateBpm>")
            sb.append("<DistanceMeters>").append(dist.toInt()).append("</DistanceMeters>")
            sb.append("<Cadence>").append(s.rpm).append("</Cadence><Extensions><ns3:TPX><ns3:Watts>")
                .append(s.watts.toInt()).append("</ns3:Watts></ns3:TPX></Extensions></Trackpoint>\n")
        }
        sb.append("</Track></Lap><Notes>${ride.workoutName}</Notes></Activity></Activities></TrainingCenterDatabase>\n")
        return sb.toString()
    }

    suspend fun exportCsv(profileId: Long): String {
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val sb = StringBuilder("date,workout,duration_min,avg_watts,np_watts,max_watts,kj,kcal_total,kcal_active,distance_km,avg_rpm,avg_hr,max_hr,tss,peak_20s,peak_1m,peak_5m,rpe,notes\n")
        for (r in db.rides().forProfileNow(profileId)) {
            sb.append("${df.format(Date(r.startedAt))},\"${r.workoutName}\",${"%.1f".format(r.durationSec / 60.0)},${r.avgWatts.toInt()},${r.normalizedWatts.toInt()},${r.maxWatts.toInt()}," +
                "${r.kj.toInt()},${r.kcal.toInt()},${r.activeKcal.toInt()},${"%.2f".format(r.distanceM / 1000)},${r.avgRpm.toInt()},${r.avgHr},${r.maxHr},${r.tss.toInt()},${r.peak20s.toInt()},${r.peak1m.toInt()},${r.peak5m.toInt()},${r.rpe},\"${r.notes.replace("\"", "'")}\"\n")
        }
        return sb.toString()
    }
}
