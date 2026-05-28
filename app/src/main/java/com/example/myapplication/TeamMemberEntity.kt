package com.example.myapplication

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "team_members")
data class TeamMemberEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val assignmentId: Int,
    val name: String
)