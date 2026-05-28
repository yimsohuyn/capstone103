package com.example.myapplication

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "meetings")
data class MeetingEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val assignmentId: Int,
    val datetime: String,
    val memo: String = ""
)