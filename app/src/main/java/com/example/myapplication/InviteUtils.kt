package com.example.myapplication.utils

object InviteUtils {

    // 🔵 초대 링크 생성 (Firebase 없이)
    fun createInviteLink(teamId: String): String {
        // 실제 배포 시 도메인을 연결하면 좋음
        return "https://capstone-invite.com/invite?teamId=$teamId"
    }

}
