package com.example.myapplication

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface MeetingDao {
    @Insert
    suspend fun insert(meeting: MeetingEntity)

    @Query("SELECT * FROM meetings WHERE assignmentId = :assignmentId ORDER BY id DESC")
    suspend fun getByAssignmentId(assignmentId: Int): List<MeetingEntity>

    @Delete
    suspend fun delete(meeting: MeetingEntity)

    @Query("DELETE FROM meetings WHERE id = :meetingId")
    suspend fun deleteById(meetingId: Int)
}