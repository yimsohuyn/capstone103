package com.example.myapplication

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "project_files")
data class ProjectFileEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val assignmentId: Int,
    val fileName: String,
    val fileUri: String
)