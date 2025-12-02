package com.example.myapplication.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "assignments")
data class AssignmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String,       // 과제 종류
    val dueDate: String,    // 마감일 (예: "2025-12-01")
    val assignee: String? = null // 담당자 (선택)
)
