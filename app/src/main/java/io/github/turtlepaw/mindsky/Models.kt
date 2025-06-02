package io.github.turtlepaw.mindsky

import io.objectbox.annotation.*

@Entity
data class Post(
    @Id var id: Long = 0,
    var content: String,
    var embedding: FloatArray, // This is supported by ObjectBox!
    var timestamp: Long
)

@Entity
data class UserLikeVector(
    @Id var id: Long = 0,
    var vector: FloatArray
)
