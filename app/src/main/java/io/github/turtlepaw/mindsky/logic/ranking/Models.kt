package io.github.turtlepaw.mindsky.logic.ranking

import io.github.turtlepaw.mindsky.logic.ranking.InterestClusterer.Companion.averageEmbeddings
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import kotlinx.serialization.json.Json.Default.decodeFromString
import kotlinx.serialization.json.Json.Default.encodeToString

@Entity
data class PostScore(
    @Id var id: Long = 0,
    val postId: Long,
    // Store post separately or use a relation
    val postUri: String, // Instead of embedding the whole post
    val finalScore: Double,
    // Flatten the breakdown
    val semanticSimilarity: Double = 0.0,
    val engagement: Double = 0.0,
    val recency: Double = 0.0,
    val diversity: Double = 0.0,
    val matchedInterest: String? = null
) {
    // TODO: verify that the breakdown is included

    // Convenience property to get breakdown
    val breakdown: ScoreBreakdown
        get() = ScoreBreakdown(semanticSimilarity, engagement, recency, diversity)

    fun withBreakdown(breakdown: ScoreBreakdown): PostScore {
        return this.copy(
            semanticSimilarity = breakdown.semanticSimilarity,
            engagement = breakdown.engagement,
            recency = breakdown.recency,
            diversity = breakdown.diversity,
        )
    }
}

// Keep ScoreBreakdown as a regular data class for API
data class ScoreBreakdown(
    val semanticSimilarity: Double,
    val engagement: Double,
    val recency: Double,
    val diversity: Double
)

data class MultiInterestUserProfile(
    var fallbackEmbedding: FloatArray = floatArrayOf(),
    var clusters: List<InterestCluster>
) {
    companion object {
        fun fromInterestClusters(clusters: List<InterestCluster>): MultiInterestUserProfile {
            return MultiInterestUserProfile(
                fallbackEmbedding = averageEmbeddings(clusters.map { it.centerEmbedding }),
                clusters = clusters,
            )
        }
    }
}

@Entity
data class InterestCluster(
    @Id var id: Long = 0,
    var centerEmbedding: FloatArray,
    var postIds: String, // JSON-encoded list of post IDs
    var name: String,
    var strength: Float = 0.0f,
    var profileId: Long = 0 // foreign key for backlink
)

data class ClusterMatch(
    var similarity: Double,
    var cluster: InterestCluster
)

fun List<String>.toJson(): String = encodeToString(this)

fun String.toStringList(): List<String> =
    decodeFromString(this)
