package com.example.myapplication

import android.content.Context

object QuizStatsStorage {

    private const val PREF_NAME = "quiz_stats_pref"
    private const val KEY_TOTAL_SOLVED = "total_solved"
    private const val KEY_TOTAL_CORRECT = "total_correct"
    private const val KEY_TOTAL_SESSIONS = "total_sessions"

    data class QuizStats(
        val totalSolved: Int,
        val totalCorrect: Int,
        val totalSessions: Int
    ) {
        val accuracyPercent: Int
            get() = if (totalSolved > 0) {
                ((totalCorrect * 100f) / totalSolved).toInt()
            } else {
                0
            }
    }

    fun addSession(
        context: Context,
        solvedCount: Int,
        correctCount: Int
    ) {
        if (solvedCount <= 0) return

        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        val prevSolved = prefs.getInt(KEY_TOTAL_SOLVED, 0)
        val prevCorrect = prefs.getInt(KEY_TOTAL_CORRECT, 0)
        val prevSessions = prefs.getInt(KEY_TOTAL_SESSIONS, 0)

        prefs.edit()
            .putInt(KEY_TOTAL_SOLVED, prevSolved + solvedCount)
            .putInt(KEY_TOTAL_CORRECT, prevCorrect + correctCount)
            .putInt(KEY_TOTAL_SESSIONS, prevSessions + 1)
            .apply()
    }

    fun getStats(context: Context): QuizStats {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        return QuizStats(
            totalSolved = prefs.getInt(KEY_TOTAL_SOLVED, 0),
            totalCorrect = prefs.getInt(KEY_TOTAL_CORRECT, 0),
            totalSessions = prefs.getInt(KEY_TOTAL_SESSIONS, 0)
        )
    }

    fun reset(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}