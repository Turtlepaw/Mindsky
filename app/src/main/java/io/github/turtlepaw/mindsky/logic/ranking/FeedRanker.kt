package io.github.turtlepaw.mindsky.logic.ranking

import io.github.turtlepaw.mindsky.db.EmbeddedPost
import io.github.turtlepaw.mindsky.db.Engagement
import io.github.turtlepaw.mindsky.db.EngagementType
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.math.tanh

object PostRanker {
    // Weights - tune these based on your preferences
    private val SIMILARITY_WEIGHT = 0.4
    private val ENGAGEMENT_WEIGHT = 0.3
    private val RECENCY_WEIGHT = 0.2
    private val DIVERSITY_WEIGHT = 0.1

    // Enhanced scoring with multi-interest support
    fun scorePost(
        post: EmbeddedPost,
        userProfile: MultiInterestUserProfile,
        userEngagementHistory: List<Engagement>
    ): PostScore {
        val (similarity, matchedInterest) = calculateBestClusterSimilarity(post, userProfile)
        val engagement = calculateEngagementScore(post, userEngagementHistory)
        val recency = calculateRecencyScore(post.createdAt)
        val diversity = calculateDiversityBonus(post, userProfile)

        val finalScore = (similarity * SIMILARITY_WEIGHT) +
                (engagement * ENGAGEMENT_WEIGHT) +
                (recency * RECENCY_WEIGHT) +
                (diversity * DIVERSITY_WEIGHT)

        return PostScore(
            postId = post.id,
            finalScore = finalScore,
            matchedInterest = matchedInterest
        ).withBreakdown(
            ScoreBreakdown(
                similarity,
                engagement,
                recency,
                diversity
            )
        )
    }

    private fun calculateBestClusterSimilarity(
        post: EmbeddedPost,
        userProfile: MultiInterestUserProfile
    ): Pair<Double, String?> {
        if (userProfile.clusters.isEmpty()) {
            // Fall back to simple similarity
            val similarity = cosineSimilarity(post.embedding, userProfile.fallbackEmbedding)
            return Pair(similarity, null)
        }

        // Find the best matching cluster
        val bestMatch = userProfile.clusters.map { cluster ->
            Pair(
                cosineSimilarity(post.embedding, cluster.centerEmbedding) * cluster.strength,
                cluster.name
            )
        }.maxByOrNull { it.first }

        return bestMatch ?: Pair(0.0, null)
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Double {
        require(a.size == b.size) { "Vectors must be same size" }

        var dotProduct = 0.0
        var normA = 0.0
        var normB = 0.0

        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }

        return if (normA == 0.0 || normB == 0.0) 0.0
        else dotProduct / (sqrt(normA) * sqrt(normB))
    }

    private fun calculateEngagementScore(post: EmbeddedPost, history: List<Engagement>): Double {
        val likeWeight = 1.0
        val repostWeight = 1.5
        val commentWeight = 2.0
        val viewTimeWeight = 0.5

        var score = 0.0

        // Global engagement (from post metadata)
        score += post.likeCount * 0.1
        score += post.repostCount * 0.15
        score += post.commentCount * 0.2

        // Personal engagement history with similar content/authors
        // Note: topicSimilarity not implemented yet, so we'll just check author
        history.filter { it.authorDid == post.authorDid }
            .forEach { engagement ->
                score += when (engagement.type) {
                    EngagementType.Like -> likeWeight
                    EngagementType.Repost -> repostWeight
                    EngagementType.Comment -> commentWeight
                    EngagementType.View -> viewTimeWeight * (engagement.dwellTimeMs / 1000.0)
                }
            }

        return tanh(score / 10.0) // Normalize to 0-1 range
    }

    private fun calculateRecencyScore(createdAt: Long): Double {
        val now = System.currentTimeMillis()
        val ageHours = (now - createdAt) / (1000 * 60 * 60)

        // Exponential decay: newer posts get higher scores
        return exp(-ageHours / 24.0) // 24-hour half-life
    }

    private fun calculateDiversityBonus(post: EmbeddedPost, userProfile: MultiInterestUserProfile): Double {
        if (userProfile.clusters.isEmpty()) {
            return calculateDiversityBonusWithFallback(post, userProfile.fallbackEmbedding)
        }

        // Check similarity against all clusters
        val maxSimilarity = userProfile.clusters.maxOfOrNull { cluster ->
            cosineSimilarity(post.embedding, cluster.centerEmbedding)
        } ?: 0.0

        return when {
            maxSimilarity > 0.8 -> 0.0  // Too similar, no bonus
            maxSimilarity < 0.3 -> 0.0  // Too different, irrelevant
            else -> 1.0 - maxSimilarity // Sweet spot: familiar but different
        }
    }

    private fun calculateDiversityBonusWithFallback(post: EmbeddedPost, fallbackEmbedding: FloatArray): Double {
        val similarity = cosineSimilarity(post.embedding, fallbackEmbedding)
        return when {
            similarity > 0.8 -> 0.0
            similarity < 0.3 -> 0.0
            else -> 1.0 - similarity
        }
    }
}