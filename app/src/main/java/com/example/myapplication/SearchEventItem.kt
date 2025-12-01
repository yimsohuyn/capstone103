package com.example.myapplication

data class SearchEventItem(
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val eventId: String?,
    val htmlLink: String?
)