package com.example.myapplication.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface AssignmentDao {

    @Insert
    suspend fun insert(assignment: AssignmentEntity)

    @Query("SELECT * FROM assignments ORDER BY dueDate ASC")
    suspend fun getAll(): List<AssignmentEntity>

    @Query("DELETE FROM assignments WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM assignments WHERE type = :type")
    suspend fun deleteByType(type: String)
}
