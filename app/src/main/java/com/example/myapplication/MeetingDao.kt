package com.example.myapplication.data

import androidx.room.*

@Dao
interface MeetingDao {
    @Insert
    suspend fun insert(meeting: MeetingEntity)

    @Query("SELECT * FROM meetings")
    suspend fun getAll(): List<MeetingEntity>

    @Delete
    suspend fun delete(meeting: MeetingEntity)

    @Query("DELETE FROM meetings WHERE datetime = :text")
    fun deleteByText(text: String)



}
