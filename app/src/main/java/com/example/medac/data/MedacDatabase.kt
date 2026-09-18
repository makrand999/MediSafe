package com.example.medac.data

import android.content.Context
import androidx.room.*

@Entity(tableName = "medications")
data class MedicationEntity(
    @PrimaryKey val id: String,
    val patientId: String,
    val enteredName: String,
    val doseValue: String,
    val doseUnit: String,
    val status: String,
    val updatedAt: String?,
    val rawJson: String
)

@Entity(tableName = "occurrences")
data class OccurrenceEntity(
    @PrimaryKey val id: String,
    val patientId: String,
    val medicationId: String,
    val scheduledAtUtc: String,
    val state: String,
    val rawJson: String
)

@Entity(tableName = "dose_events")
data class DoseEventEntity(
    @PrimaryKey val id: String,
    val patientId: String,
    val medicationId: String,
    val occurrenceId: String?,
    val clientEventId: String,
    val eventType: String,
    val actualAt: String,
    val pendingSync: Boolean = false,
    val rawJson: String
)

@Entity(tableName = "alerts")
data class AlertEntity(
    @PrimaryKey val id: String,
    val patientId: String,
    val severity: String,
    val status: String,
    val messageKey: String?,
    val rawJson: String
)

@Dao
interface MedicationDao {
    @Query("SELECT * FROM medications WHERE patientId=:pid") suspend fun byPatient(pid: String): List<MedicationEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(items: List<MedicationEntity>)
    @Query("DELETE FROM medications WHERE patientId=:pid") suspend fun clear(pid: String)
}

@Dao
interface OccurrenceDao {
    @Query("SELECT * FROM occurrences WHERE patientId=:pid AND scheduledAtUtc BETWEEN :from AND :to ORDER BY scheduledAtUtc") suspend fun range(pid: String, from: String, to: String): List<OccurrenceEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(items: List<OccurrenceEntity>)
    @Query("DELETE FROM occurrences WHERE patientId=:pid") suspend fun clear(pid: String)
}

@Dao
interface DoseEventDao {
    @Query("SELECT * FROM dose_events WHERE patientId=:pid") suspend fun byPatient(pid: String): List<DoseEventEntity>
    @Query("SELECT * FROM dose_events WHERE pendingSync=1") suspend fun pending(): List<DoseEventEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(e: DoseEventEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(items: List<DoseEventEntity>)
    @Query("UPDATE dose_events SET pendingSync=0 WHERE clientEventId=:cid") suspend fun markSynced(cid: String)
}

@Dao
interface AlertDao {
    @Query("SELECT * FROM alerts WHERE patientId=:pid AND status='open'") suspend fun open(pid: String): List<AlertEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(items: List<AlertEntity>)
    @Query("DELETE FROM alerts WHERE patientId=:pid") suspend fun clear(pid: String)
}

@Database(entities = [MedicationEntity::class, OccurrenceEntity::class, DoseEventEntity::class, AlertEntity::class], version = 1, exportSchema = false)
abstract class MedacDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun occurrenceDao(): OccurrenceDao
    abstract fun doseEventDao(): DoseEventDao
    abstract fun alertDao(): AlertDao

    companion object {
        @Volatile private var I: MedacDatabase? = null
        fun get(ctx: Context): MedacDatabase = I ?: synchronized(this) {
            I ?: Room.databaseBuilder(ctx.applicationContext, MedacDatabase::class.java, "medac.db")
                .fallbackToDestructiveMigration().build().also { I = it }
        }
    }
}
