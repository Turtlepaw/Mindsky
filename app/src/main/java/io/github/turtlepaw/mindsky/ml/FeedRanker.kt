package io.github.turtlepaw.mindsky.ml

import io.github.turtlepaw.mindsky.Post
import kotlin.math.sqrt

class FeedRanker {
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

    fun rankPosts(posts: List<Post>, userVector: FloatArray): List<Post> {
        return posts.sortedByDescending { post ->
            val similarity = cosineSimilarity(post.embedding, userVector)
            val decay = decayFactor(post.timestamp)
            similarity * decay // Combined score
        }
    }
}
