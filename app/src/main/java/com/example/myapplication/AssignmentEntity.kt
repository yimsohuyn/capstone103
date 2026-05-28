package com.example.myapplication

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "assignments")
data class AssignmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val type: String,
    val dueDate: String,
    val assignee: String? = null,
    val assigneeEmail: String? = null,
    val fileUri: String? = null,
    val startTime: String? = "09:00",
    val endTime: String? = "10:00",
    val googleEventId: String? = null,

    // 팀 공유용
    val remoteId: String? = null,
    val teamId: String? = null
)