package com.example.sgp

data class User(
    var name: String = "",
    var email: String = "",
    var phone: String = "",
    var location: String = "",
    var password: String = "",
    var rating: Double = 0.0,
    var completedTrades: Int = 0,
    var profileImage: String = "",
    var userType: String = "standard",
    var joinedDate: Long = System.currentTimeMillis(),
    var skillsTeach: String = "",
    var skillsLearn: String = "",
    var credits: Int = 0
)
