package com.tisho.ride

import android.app.Application
import com.tisho.ride.bike.BikeConnection
import com.tisho.ride.bike.PowerModel
import com.tisho.ride.data.Repository
import com.tisho.ride.data.RideDb
import com.tisho.ride.health.HealthSync
import com.tisho.ride.hr.HeartRateMonitor
import com.tisho.ride.workout.Cues

class RideApp : Application() {
    lateinit var repo: Repository
    lateinit var power: PowerModel
    lateinit var bike: BikeConnection
    lateinit var hrm: HeartRateMonitor
    lateinit var cues: Cues
    lateinit var health: HealthSync

    override fun onCreate() {
        super.onCreate()
        repo = Repository(this, RideDb.create(this))
        power = PowerModel(this)
        bike = BikeConnection(this, power)
        hrm = HeartRateMonitor(this)
        cues = Cues(this)
        health = HealthSync(this)
        val s = repo.settings.value
        cues.voice = s.voice
        cues.sounds = s.sounds
        if (s.demo) bike.simulate = true else bike.connect()
    }
}
