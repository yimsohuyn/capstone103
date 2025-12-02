package com.example.myapplication.team

import com.google.firebase.firestore.FirebaseFirestore

object TeamManager {

    // 🔥 기본값 추가 (null → "demo_team")
    var teamId: String = "demo_team"

    // 앱 최초 실행 시 팀ID 세팅
    fun initTeam(userUid: String) {
        teamId = "team_$userUid"
    }

    // Firestore에 팀 생성
    fun createTeamInFirestore(userUid: String, name: String) {
        val teamId = "team_$userUid"

        val data = mapOf(
            "teamName" to name
        )

        FirebaseFirestore.getInstance()
            .collection("teams")
            .document(teamId)
            .collection("info")
            .document("data")
            .set(data)
    }
}
