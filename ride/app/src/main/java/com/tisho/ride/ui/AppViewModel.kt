package com.tisho.ride.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.tisho.ride.RideApp
import com.tisho.ride.data.Profile
import com.tisho.ride.data.Ride
import com.tisho.ride.data.Settings
import com.tisho.ride.workout.Metrics
import com.tisho.ride.workout.RideSession
import com.tisho.ride.workout.SessionResult
import com.tisho.ride.workout.Workout
import com.tisho.ride.workout.WorkoutLibrary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
private const val MIN_SAVE_SEC = 20

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val ride = app as RideApp
    val repo = ride.repo
    val bike = ride.bike
    val hrm = ride.hrm
    val power = ride.power
    val cues = ride.cues
    val health = ride.health
    val sync = ride.sync

    val settings: StateFlow<Settings> = repo.settings
    val profiles = repo.profiles.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val profile: StateFlow<Profile?> = combine(repo.settings, profiles) { s, list -> list.firstOrNull { it.id == s.activeProfile } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val rides: StateFlow<List<Ride>> = profile.flatMapLatest { p -> if (p == null) flowOf(emptyList()) else repo.rides(p.id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val workouts: StateFlow<List<Workout>> = repo.customWorkouts.map { WorkoutLibrary.all + it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, WorkoutLibrary.all)

    /** Every rider's score for the leaderboard. */
    val leaderboard: StateFlow<List<Metrics.RiderScore>> = combine(profiles, repo.db.rides().all()) { ps, all ->
        val byRider = all.groupBy { it.profileId }
        ps.filterNot { it.guest }.map { Metrics.riderScore(it, byRider[it.id].orEmpty()) }.sortedByDescending { it.total }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    var session: RideSession? = null
        private set
    var lastResult: SessionResult? = null
        private set

    init {
        // Reconnect the rider's own heart-rate strap whenever the active profile changes.
        viewModelScope.launch {
            profile.collect { p ->
                val addr = p?.hrSensorAddress
                if (addr != null) hrm.connect(addr) else hrm.disconnect()
            }
        }
    }

    fun selectProfile(id: Long) = repo.updateSettings(settings.value.copy(activeProfile = id))

    /** Ride as the shared guest rider (created on first use with average defaults). */
    fun startGuest(onReady: () -> Unit) = viewModelScope.launch {
        val existing = profiles.value.firstOrNull { it.guest }
        val id = existing?.id ?: repo.saveProfile(Profile(name = "Guest", color = 7, guest = true))
        selectProfile(id)
        onReady()
    }

    fun saveProfile(p: Profile, select: Boolean = false) = viewModelScope.launch {
        val id = repo.saveProfile(p)
        if (select) selectProfile(id)
    }

    /**
     * Health Connect belongs to the tablet's Google account, so exactly one rider may sync to it.
     * Turning it on for [p] turns it off for every other rider.
     */
    fun setHealthOwner(p: Profile, on: Boolean) = viewModelScope.launch {
        if (on) for (other in profiles.value) if (other.id != p.id && other.healthSync) repo.saveProfile(other.copy(healthSync = false))
        repo.saveProfile(p.copy(healthSync = on))
    }

    fun deleteProfile(p: Profile) = viewModelScope.launch { sync.unlinkProfile(p.id); repo.deleteProfile(p) }

    fun updateSettings(s: Settings) {
        repo.updateSettings(s)
        cues.voice = s.voice
        cues.sounds = s.sounds
        if (bike.simulate != s.demo) bike.simulate = s.demo
    }

    fun workout(id: String): Workout? = workouts.value.firstOrNull { it.id == id }

    fun startRide(w: Workout): RideSession? {
        val p = profile.value ?: return null
        session?.finish()
        val workout = if (w.id == "ramp") WorkoutLibrary.ramp(if (p.weightKg < 60 || p.age >= 60) 10 else 20).copy(name = w.name, protocol = w.protocol) else w
        return RideSession(viewModelScope, bike, hrm, power, p, workout, cues).also { session = it; it.start() }
    }

    /** Id of the auto-saved ride for the summary screen; null while saving or when too short to keep. */
    var savedRideId by androidx.compose.runtime.mutableStateOf<Long?>(null)
        private set

    /** Ends the ride and saves it straight away (rides under 20 s are dropped as accidental starts). */
    fun finishRide(): SessionResult? {
        val r = session?.finish() ?: return null
        session = null
        lastResult = r
        savedRideId = null
        if (r.ride.durationSec >= MIN_SAVE_SEC) viewModelScope.launch { savedRideId = save(r) }
        return r
    }

    fun discardRide() { session?.finish(); session = null; lastResult = null }

    private suspend fun save(r: SessionResult): Long {
        val id = repo.saveRide(r.ride, r.samples)
        // Always the rider who rode, read fresh from the database, never whoever is selected now.
        val p = repo.db.profiles().get(r.ride.profileId)
        if (p?.healthSync == true && !p.guest) health.write(r.ride, r.samples, r.ride.workoutName)
        if (p != null && r.newSprintInnate != null && r.newSprintInnate != p.sprintInnate) repo.saveProfile(p.copy(sprintInnate = r.newSprintInnate))
        return id
    }

    /** Effort rating and notes from the summary screen. */
    fun annotateRide(id: Long, rpe: Int, notes: String) = viewModelScope.launch {
        repo.db.rides().get(id)?.let { repo.updateRide(it.copy(rpe = rpe, notes = notes)) }
    }

    fun applyFtp(profileId: Long, ftp: Int) = viewModelScope.launch {
        repo.db.profiles().get(profileId)?.let { repo.saveProfile(it.copy(ftpOverride = ftp)) }
    }

    fun deleteFinishedRide() {
        savedRideId?.let { deleteRide(it) }
        savedRideId = null
        lastResult = null
    }

    fun deleteRide(id: Long) = viewModelScope.launch { repo.deleteRide(id) }
    fun updateRide(r: Ride) = viewModelScope.launch { repo.updateRide(r) }
}
