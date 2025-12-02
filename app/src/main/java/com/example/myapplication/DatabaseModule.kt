package com.example.myapplication.data

import android.content.Context

object DatabaseModule {

    fun getDatabase(context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }
}
