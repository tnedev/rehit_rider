package com.tisho.ride.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import java.util.Calendar

@Serializable
@Entity
data class Profile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int = 0,
    val sex: String = "M",
    val birthYear: Int = 1985,
    val heightCm: Int = 175,
    val weightKg: Double = 75.0,
    val restingHr: Int = 60,
    /** null = estimated with Tanaka (208 - 0.7 * age). */
    val maxHrOverride: Int? = null,
    /** Functional threshold power; null = estimated from weight until a ramp test. */
    val ftpOverride: Int? = null,
    /** Adaptive REHIT sprint resistance (innate scale); 0 = derive from weight. */
    val sprintInnate: Int = 0,
    val hrSensorAddress: String? = null,
    val hrSensorName: String? = null,
    val weeklyGoalMinutes: Int = 150,
    /** Write this rider's rides to Health Connect (Google Fit / Samsung Health). */
    val healthSync: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val age: Int get() = Calendar.getInstance().get(Calendar.YEAR) - birthYear
    val maxHr: Int get() = maxHrOverride ?: (208 - 0.7 * age).toInt()
    val ftp: Int get() = ftpOverride ?: (weightKg * if (sex == "F") 1.9 else 2.2).toInt()
    val ftpIsEstimated: Boolean get() = ftpOverride == null
    val initials: String get() = name.trim().split(" ").filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
}

@Serializable
@Entity(indices = [Index("profileId"), Index("startedAt")])
data class Ride(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val workoutId: String,
    val workoutName: String,
    val startedAt: Long,
    val durationSec: Int,
    val kj: Double,
    /** Total (gross) energy, personalised; see workout.EnergyModel. */
    val kcal: Double,
    val activeKcal: Double = 0.0,
    val avgWatts: Double,
    val maxWatts: Double,
    val normalizedWatts: Double,
    val avgRpm: Double,
    val maxRpm: Int,
    val avgHr: Int,
    val maxHr: Int,
    val tss: Double,
    val intensityFactor: Double,
    val peak5s: Double,
    val peak20s: Double,
    val peak1m: Double,
    val peak5m: Double,
    val peak20m: Double,
    /** Seconds spent in HR zones 1..5, comma separated. */
    val hrZoneSeconds: String = "",
    val ftpAtRide: Int,
    val rpe: Int = 0,
    val notes: String = "",
)

@Serializable
@Entity(primaryKeys = ["rideId", "t"])
data class Sample(
    val rideId: Long,
    val t: Int,
    val watts: Float,
    val rpm: Int,
    val level: Int,
    val hr: Int,
)

@Serializable
@Entity
data class CustomWorkout(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    /** JSON list of steps, see workout.Step. */
    val stepsJson: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface ProfileDao {
    @Query("SELECT * FROM Profile ORDER BY createdAt") fun all(): Flow<List<Profile>>
    @Query("SELECT * FROM Profile ORDER BY createdAt") suspend fun allNow(): List<Profile>
    @Query("SELECT * FROM Profile WHERE id = :id") fun byId(id: Long): Flow<Profile?>
    @Query("SELECT * FROM Profile WHERE id = :id") suspend fun get(id: Long): Profile?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(p: Profile): Long
    @Update suspend fun update(p: Profile)
    @Delete suspend fun delete(p: Profile)
}

@Dao
interface RideDao {
    @Query("SELECT * FROM Ride WHERE profileId = :profileId ORDER BY startedAt DESC") fun forProfile(profileId: Long): Flow<List<Ride>>
    @Query("SELECT * FROM Ride WHERE profileId = :profileId ORDER BY startedAt DESC") suspend fun forProfileNow(profileId: Long): List<Ride>
    @Query("SELECT * FROM Ride WHERE id = :id") fun byId(id: Long): Flow<Ride?>
    @Query("SELECT * FROM Ride WHERE id = :id") suspend fun get(id: Long): Ride?
    @Query("SELECT * FROM Sample WHERE rideId = :rideId ORDER BY t") suspend fun samples(rideId: Long): List<Sample>
    @Insert suspend fun insert(r: Ride): Long
    @Update suspend fun update(r: Ride)
    @Insert suspend fun insertSamples(s: List<Sample>)
    @Query("DELETE FROM Sample WHERE rideId = :rideId") suspend fun deleteSamples(rideId: Long)
    @Query("DELETE FROM Ride WHERE id = :rideId") suspend fun deleteRide(rideId: Long)
    @Query("DELETE FROM Sample WHERE rideId IN (SELECT id FROM Ride WHERE profileId = :profileId)") suspend fun deleteSamplesForProfile(profileId: Long)
    @Query("DELETE FROM Ride WHERE profileId = :profileId") suspend fun deleteRidesForProfile(profileId: Long)

    @Transaction
    suspend fun insertWithSamples(r: Ride, samples: List<Sample>): Long {
        val id = insert(r)
        insertSamples(samples.map { it.copy(rideId = id) })
        return id
    }

    @Transaction
    suspend fun delete(rideId: Long) { deleteSamples(rideId); deleteRide(rideId) }
}

@Dao
interface CustomWorkoutDao {
    @Query("SELECT * FROM CustomWorkout ORDER BY createdAt DESC") fun all(): Flow<List<CustomWorkout>>
    @Query("SELECT * FROM CustomWorkout ORDER BY createdAt DESC") suspend fun allNow(): List<CustomWorkout>
    @Query("SELECT * FROM CustomWorkout WHERE id = :id") suspend fun get(id: Long): CustomWorkout?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(w: CustomWorkout): Long
    @Query("DELETE FROM CustomWorkout WHERE id = :id") suspend fun delete(id: Long)
}

@Database(entities = [Profile::class, Ride::class, Sample::class, CustomWorkout::class], version = 2, exportSchema = false)
abstract class RideDb : RoomDatabase() {
    abstract fun profiles(): ProfileDao
    abstract fun rides(): RideDao
    abstract fun workouts(): CustomWorkoutDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Profile ADD COLUMN healthSync INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun create(context: Context) = Room.databaseBuilder(context, RideDb::class.java, "ride.db").addMigrations(MIGRATION_1_2).build()
    }
}
