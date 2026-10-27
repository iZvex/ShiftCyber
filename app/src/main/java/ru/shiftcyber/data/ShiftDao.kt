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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(shift: ShiftEntity): Long

    @Query("DELETE FROM shifts WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM shifts WHERE startMillis >= :from AND startMillis < :to ORDER BY startMillis ASC")
    suspend fun between(from: Long, to: Long): List<ShiftEntity>
}
