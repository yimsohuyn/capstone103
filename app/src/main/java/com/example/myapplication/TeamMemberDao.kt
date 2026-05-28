package com.example.myapplication

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface TeamMemberDao {
    @Insert
    suspend fun insert(member: TeamMemberEntity)

    @Query("SELECT * FROM team_members WHERE assignmentId = :assignmentId")
    suspend fun getByAssignmentId(assignmentId: Int): List<TeamMemberEntity>

    @Delete
    suspend fun delete(member: TeamMemberEntity)

    @Query("DELETE FROM team_members WHERE assignmentId = :assignmentId AND name = :name")
    suspend fun deleteByAssignmentIdAndName(assignmentId: Int, name: String)
}