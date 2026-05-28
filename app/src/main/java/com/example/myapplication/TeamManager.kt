package com.example.myapplication.team

import com.google.firebase.firestore.FirebaseFirestore

object TeamManager {

    var teamId: String = "demo_team"

    fun initTeam(userUid: String) {
        teamId = "team_$userUid"
    }

    fun createTeamInFirestore(userUid: String, name: String) {
        val createdTeamId = "team_$userUid"
        teamId = createdTeamId

        val data = mapOf(
            "teamName" to name
        )

        FirebaseFirestore.getInstance()
            .collection("teams")
            .document(createdTeamId)
            .collection("info")
            .document("data")
            .set(data)
    }

    fun setJoinedTeam(teamId: String) {
        this.teamId = teamId
    }
}