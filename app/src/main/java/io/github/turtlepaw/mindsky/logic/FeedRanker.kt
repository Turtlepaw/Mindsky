package io.github.turtlepaw.mindsky.logic

import io.github.turtlepaw.mindsky.EmbeddedPost
import io.github.turtlepaw.mindsky.LikeVector
import kotlin.math.exp
import kotlin.math.sqrt

data class UserWeights(
    val recencyWeight: Float = 1.0f,      // How much to favor recent posts
    val similarityWeight: Float = 1.0f,    // How much user's like history matters
    val engagementWeight: Float = 0.5f,    // How much likes/replies matter
    val diversityBonus: Float = 0.3f,      // Boost for different topics/authors
    val smallCreatorBonus: Float = 0.2f    // Boost for <1K follower accounts
)

object FeedRanker {
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        val dot = a.zip(b).sumOf { (x, y) -> (x * y).toDouble() }
        val magA = sqrt(a.sumOf { it.toDouble() * it.toDouble() })
        val magB = sqrt(b.sumOf { it.toDouble() * it.toDouble() })
        return if (magA != 0.0 && magB != 0.0) (dot / (magA * magB)).toFloat() else 0f
    }

    fun decayFactor(postTime: Long): Float {
        val now = System.currentTimeMillis()
        val hoursAgo = (now - postTime).toDouble() / (1000 * 60 * 60)
        return (1.0 / (1.0 + hoursAgo)).toFloat() // e.g. 1.0 if now, 0.5 if 1 hour ago, etc.
    }

    fun calculatePostScore(
        post: EmbeddedPost,
        userLikes: List<LikeVector>,
        //recentPosts: Set<String>,
        weights: UserWeights = UserWeights(),
    ): Float {
        var score = 0f

        // 1. Similarity to user's likes (cosine similarity)
        val similarities = userLikes.map { like ->
            cosineSimilarity(post.embedding, like.vector)
        }
        val avgSimilarity = similarities.average().toFloat()
        score += avgSimilarity * weights.similarityWeight

        // 2. Recency bonus (newer = better, but not too aggressive)
        val hoursAgo = (System.currentTimeMillis() / 1000 - post.timestamp) / 3600f
        val recencyScore = exp(-hoursAgo / 24f) // Decay over 24 hours
        score += recencyScore * weights.recencyWeight

//        // 3. Engagement signals (if available)
//        // score += post.engagementScore * weights.engagementWeight
//
//        // 4. Diversity bonus - penalize if we've shown similar content recently
//        val isDiverse = !recentPosts.any { recentUri ->
//            // Check if recent post is too similar (you'd implement this)
//            isTooSimilar(post.uri, recentUri, threshold = 0.8f)
//        }
//        if (isDiverse) score += weights.diversityBonus
//
//        // 5. Small creator bonus
//        if (isSmallCreator(post.authorDid)) {
//            score += weights.smallCreatorBonus
//        }

        return score
    }
}
