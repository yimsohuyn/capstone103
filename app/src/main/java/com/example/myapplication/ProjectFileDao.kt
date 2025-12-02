package com.example.myapplication.data

import androidx.room.*

@Dao
interface ProjectFileDao {
    @Insert
    suspend fun insert(file: ProjectFileEntity)

    @Query("SELECT * FROM project_files")
    suspend fun getAll(): List<ProjectFileEntity>

    @Delete
    suspend fun delete(file: ProjectFileEntity)
}
