package com.example.myapplication


data class DdayEvent(
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val eventId: String?,
    val htmlLink: String?,
    val ddayLabel: String
)
