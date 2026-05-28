package com.example.myapplication.utils

object InviteUtils {

    fun createInviteLink(teamId: String, assignmentRemoteId: String): String {
        return "https://capstone103-d14a5.web.app?teamId=$teamId&assignmentRemoteId=$assignmentRemoteId"
    }
}