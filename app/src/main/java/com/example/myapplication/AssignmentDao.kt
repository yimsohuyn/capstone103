package com.example.myapplication.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface AssignmentDao {

    @Insert
    suspend fun insert(assignment: AssignmentEntity): Long

    @Query("SELECT * FROM assignments ORDER BY dueDate ASC")
    suspend fun getAll(): List<AssignmentEntity>

    @Query("DELETE FROM assignments WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM assignments WHERE type = :type")
    suspend fun deleteByType(type: String)

    @Query("SELECT * FROM assignments WHERE dueDate = :date ORDER BY id DESC")
    suspend fun getByDueDate(date: String): List<AssignmentEntity>

    @Update
    suspend fun update(assignment: AssignmentEntity)

    @Query("SELECT * FROM assignments WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): AssignmentEntity?


}
