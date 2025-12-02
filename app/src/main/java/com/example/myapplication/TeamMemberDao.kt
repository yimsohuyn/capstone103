package com.example.myapplication.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Delete

@Dao
interface TeamMemberDao {
    @Insert
    suspend fun insert(member: TeamMemberEntity)

    @Query("SELECT * FROM team_members")
    suspend fun getAll(): List<TeamMemberEntity>

    @Delete
    suspend fun delete(member: TeamMemberEntity)

    @Query("DELETE FROM team_members WHERE name = :name")
    suspend fun deleteByName(name: String)

}
