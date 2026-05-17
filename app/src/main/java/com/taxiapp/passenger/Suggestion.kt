package com.taxiapp.passenger

data class Suggestion(
    val id: String,
    val name: String,
    val address: String,
    var lat: Double,
    var lng: Double
)
