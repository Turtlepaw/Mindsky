package io.github.turtlepaw.mindsky.auth

data class UserSession(
    val did: String,
    val handle: String,
    val accessToken: String,
    val refreshToken: String,
    val host: String
)