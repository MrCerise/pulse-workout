package com.mrcerise.intervalcoach.journey

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "journey_enrollment")
data class JourneyEnrollment(
    @PrimaryKey val id: Int = 1,
    val level: String,
    val enrolledAt: Long,
    val zoneId: String,
    /** Monday = bit 0, Sunday = bit 6. */
    val trainingDays: Int,
    val reminderMinute: Int? = null,
    val lastReminderDate: String? = null,
)

@Entity(tableName = "journey_targets")
data class JourneyTarget(@PrimaryKey val week: String, val days: Int)

/** A successful attempt, including replays. The session UUID makes completion idempotent. */
@Entity(tableName = "journey_credits")
data class JourneyCredit(@PrimaryKey val sessionId: String, val nodeId: String, val endedAt: Long)

@Entity(tableName = "journey_readiness")
data class JourneyReadiness(@PrimaryKey val level: String, val ready: Boolean, val answeredAt: Long)

@Dao
interface JourneyDao {
    @Query("SELECT * FROM journey_enrollment WHERE id = 1")
    fun observeEnrollment(): Flow<JourneyEnrollment?>
    @Query("SELECT * FROM journey_enrollment WHERE id = 1")
    suspend fun enrollment(): JourneyEnrollment?
    @Upsert suspend fun saveEnrollment(value: JourneyEnrollment)
    @Query("SELECT * FROM journey_targets ORDER BY week")
    fun observeTargets(): Flow<List<JourneyTarget>>
    @Query("SELECT * FROM journey_targets ORDER BY week")
    suspend fun targets(): List<JourneyTarget>
    @Upsert suspend fun saveTarget(value: JourneyTarget)
    @Query("SELECT * FROM journey_credits ORDER BY endedAt")
    fun observeCredits(): Flow<List<JourneyCredit>>
    @Query("SELECT * FROM journey_credits ORDER BY endedAt")
    suspend fun credits(): List<JourneyCredit>
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun credit(value: JourneyCredit)
    @Query("SELECT * FROM journey_readiness")
    fun observeReadiness(): Flow<List<JourneyReadiness>>
    @Upsert suspend fun saveReadiness(value: JourneyReadiness)
    @Query("DELETE FROM journey_credits")
    suspend fun deleteCredits()
    @Query("DELETE FROM journey_readiness")
    suspend fun deleteReadiness()
    @Query("UPDATE journey_enrollment SET lastReminderDate = :date WHERE id = 1")
    suspend fun markReminded(date: String)
}
