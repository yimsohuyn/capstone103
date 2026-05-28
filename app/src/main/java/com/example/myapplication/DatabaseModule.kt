package com.example.myapplication

import android.content.Context

object DatabaseModule {

    fun getDatabase(context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }
}
