package io.github.turtlepaw.mindsky.db

import io.github.turtlepaw.mindsky.EngagementTypeConverter
import io.objectbox.annotation.Convert
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

@Entity
data class EmbeddedPost(
    @Id var id: Long = 0,
    val createdAt: Long,
    // identifiers
    val uri: String, // Post URI
    val authorDid: String,
    // content
    val text: String,
    // metadata
    val liked: Boolean = false,
    val score: Float? = null, // Similarity score, optional
    // embedding
    val embedding: FloatArray,
    var embeddedAt: Long = System.currentTimeMillis(),
    // stats
    var likeCount: Int = 0,
    var repostCount: Int = 0,
    var commentCount: Int = 0,
)

@Entity
data class UserLikeVector(
    @Id var id: Long = 0,
    var vector: FloatArray
)

@Entity
data class Engagement(
    @Id var id: Long = 0,
    // basic metadata
    var uri: String,
    var cid: String,
    var createdAt: Long,
    // data
    var embedding: FloatArray,
    var text: String,
    // metadata
    var authorDid: String,
    @Convert(converter = EngagementTypeConverter::class, dbType = Int::class)
    var type: EngagementType,
    var dwellTimeMs: Long = 0,
)

@Entity
data class FeedInterest(
    @Id var id: Long = 0,
    var hashtag: String,
)

enum class EngagementType {
    Like,
    Repost,
    Comment,
    View
}