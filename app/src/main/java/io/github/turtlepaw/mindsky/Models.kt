package io.github.turtlepaw.mindsky

import io.objectbox.annotation.*
import kotlinx.datetime.Instant
import sh.christian.ozone.api.model.Timestamp

@Entity
data class EmbeddedPost(
    @Id var id: Long = 0,
    val uri: String, // Post URI
    val text: String,
    val embedding: FloatArray,
    val authorDid: String,
    val timestamp: Long,
    val liked: Boolean = false,
    val score: Float? = null, // Similarity score, optional
)

@Entity
data class UserLikeVector(
    @Id var id: Long = 0,
    var vector: FloatArray
)

@Entity
data class LikeVector(
    @Id var id: Long = 0,
    var uri: String,
    var cid: String,
    var createdAt: Long,
    var vector: FloatArray
)
