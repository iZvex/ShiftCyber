package ru.shiftcyber.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ShiftDao {
    @Query("SELECT * FROM shifts ORDER BY startMillis ASC")
    fun observeAll(): Flow<List<ShiftEntity>>

    @Query("SELECT * FROM shifts ORDER BY startMillis ASC")
    suspend fun getAll(): List<ShiftEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(shift: ShiftEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(shifts: List<ShiftEntity>): List<Long>

    @Query("SELECT * FROM shifts WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ShiftEntity?

    @Query("DELETE FROM shifts WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM shifts WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM shifts")
    suspend fun deleteAll()

    @Query("SELECT * FROM shifts WHERE startMillis >= :from AND startMillis < :to ORDER BY startMillis ASC")
    suspend fun between(from: Long, to: Long): List<ShiftEntity>

    @Query("SELECT * FROM shifts WHERE startMillis < :to AND endMillis > :from ORDER BY startMillis ASC")
    suspend fun overlapping(from: Long, to: Long): List<ShiftEntity>
}
