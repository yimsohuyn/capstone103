package com.example.myapplication

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ProjectFileDao {
    @Insert
    suspend fun insert(file: ProjectFileEntity)

    @Query("SELECT * FROM project_files WHERE assignmentId = :assignmentId")
    suspend fun getByAssignmentId(assignmentId: Int): List<ProjectFileEntity>

    @Delete
    suspend fun delete(file: ProjectFileEntity)
}