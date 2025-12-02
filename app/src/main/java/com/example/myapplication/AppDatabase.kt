package com.example.myapplication.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TeamMemberEntity::class,
        MeetingEntity::class,
        ProjectFileEntity::class,
        AssignmentEntity::class     // ✅ 추가됨
    ],
    version = 2,                   // ✅ 반드시 버전 올려야 함!!!
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun teamMemberDao(): TeamMemberDao
    abstract fun meetingDao(): MeetingDao
    abstract fun projectFileDao(): ProjectFileDao
    abstract fun assignmentDao(): AssignmentDao   // ✅ 추가됨

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "team_project_db"
                )
                    .fallbackToDestructiveMigration()   // ✅ 버전 변경 시 충돌 방지 (중요)
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}
