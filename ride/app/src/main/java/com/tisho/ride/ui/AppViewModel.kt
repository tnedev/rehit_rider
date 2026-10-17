package com.tisho.ride.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tisho.ride.RideApp
import com.tisho.ride.data.Profile
import com.tisho.ride.data.Ride
import com.tisho.ride.data.Settings
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
        val workout = if (w.id == "ramp") WorkoutLibrary.ramp(if (p.weightKg < 60 || p.age >= 60) 10 else 20) else w
        return RideSession(viewModelScope, bike, hrm, power, p, workout, cues).also { session = it; it.start() }
    }

    fun finishRide(): SessionResult? {
        val r = session?.finish() ?: return null
        session = null
        lastResult = r
        return r
    }

    fun discardRide() { session?.finish(); session = null; lastResult = null }

    /** Saves the finished ride and applies accepted FTP / sprint adaptations. */
    fun saveResult(r: SessionResult, rpe: Int, notes: String, applyFtp: Boolean, onSaved: (Long) -> Unit) = viewModelScope.launch {
        val saved = r.ride.copy(rpe = rpe, notes = notes)
        val id = repo.saveRide(saved, r.samples)
        // Always the rider who rode, read fresh from the database, never whoever is selected now.
        val p = repo.db.profiles().get(saved.profileId)
        if (p?.healthSync == true) health.write(saved, r.samples, saved.workoutName)
        if (p != null) {
            var np = p
            if (applyFtp && r.newFtp != null) np = np.copy(ftpOverride = r.newFtp)
            if (r.newSprintInnate != null) np = np.copy(sprintInnate = r.newSprintInnate)
            if (np != p) repo.saveProfile(np)
        }
        lastResult = null
        onSaved(id)
    }

    fun deleteRide(id: Long) = viewModelScope.launch { repo.deleteRide(id) }
    fun updateRide(r: Ride) = viewModelScope.launch { repo.updateRide(r) }
}
