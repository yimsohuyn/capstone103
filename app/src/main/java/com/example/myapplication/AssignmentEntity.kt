package com.example.myapplication.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "assignments")
data class AssignmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val type: String,       // 과제 종류
    val dueDate: String,    // 마감일 (예: "2025-12-01")
    val assignee: String? = null,   //  담당자 추가
    val fileUri: String? = null,
    val startTime: String? = "09:00",
    val endTime: String? = "10:00",// 파일 첨부 추가
    val googleEventId: String? = null
)