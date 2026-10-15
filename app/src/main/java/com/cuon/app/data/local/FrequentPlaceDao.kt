package com.cuon.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FrequentPlaceDao {
    @Query("SELECT * FROM frequent_places ORDER BY id ASC")
    fun getAll(): Flow<List<FrequentPlaceEntity>>

    @Query("SELECT * FROM frequent_places ORDER BY id ASC")
    suspend fun getAllOnce(): List<FrequentPlaceEntity>

    @Query("DELETE FROM frequent_places")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM frequent_places WHERE label = :label")
    suspend fun countByLabel(label: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(place: FrequentPlaceEntity): Long

    @Delete
    suspend fun delete(place: FrequentPlaceEntity)

    /** 同 label 是否已被别的记录占用（编辑时排除自身） */
    @Query("SELECT COUNT(*) FROM frequent_places WHERE label = :label AND id != :excludeId")
    suspend fun countByLabelExcluding(label: String, excludeId: Long): Int
}
