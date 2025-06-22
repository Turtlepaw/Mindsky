package io.github.turtlepaw.mindsky.logic.ranking

import io.github.turtlepaw.mindsky.db.Engagement
import kotlin.math.sqrt

class InterestClusterer {
    private val SIMILARITY_THRESHOLD = 0.7 // How similar posts need to be to group together
    private val MAX_CLUSTERS = 4 // cats, tech, humor, news, etc.
    private val MIN_CLUSTER_SIZE = 2 // Need at least 2 posts to form a cluster

    fun createClusters(likedPosts: List<Engagement>): Pair<List<InterestCluster>, FloatArray> {
        if (likedPosts.size < 3) {
            // Not enough data for clustering, use simple average
            val avgEmbedding = averageEmbeddings(likedPosts.map { it.embedding })
            return emptyList<InterestCluster>() to avgEmbedding
        }

        val clusters = findClusters(likedPosts)
        val namedClusters = assignClusterNames(clusters, likedPosts)
        val fallback = averageEmbeddings(likedPosts.map { it.embedding })

        return namedClusters to fallback
    }

    private fun findClusters(likedPosts: List<Engagement>): List<InterestCluster> {
        val clusters = mutableListOf<InterestCluster>()

        // Start with the first post as our first cluster
        clusters.add(InterestCluster(
            centerEmbedding = likedPosts[0].embedding.copyOf(),
            postIds = mutableListOf(likedPosts[0].id.toString()).toJson(),
            name = "Interest 1"
        ))

        // For each remaining post, decide: add to existing cluster or create new one?
        for (post in likedPosts.drop(1)) {
            val bestMatch = findBestMatchingCluster(post, clusters)

            if (bestMatch != null && bestMatch.similarity > SIMILARITY_THRESHOLD) {
                // Add to existing cluster
                addPostToCluster(post, bestMatch.cluster)
            } else if (clusters.size < MAX_CLUSTERS) {
                // Create new cluster
                clusters.add(InterestCluster(
                    centerEmbedding = post.embedding.copyOf(),
                    postIds = mutableListOf(post.id.toString()).toJson(),
                    name = "Interest ${clusters.size + 1}"
                ))
            } else {
                // Add to best available cluster (forced assignment)
                val fallbackCluster = clusters.maxByOrNull { cluster ->
                    cosineSimilarity(post.embedding, cluster.centerEmbedding)
                }
                if (fallbackCluster != null) {
                    addPostToCluster(post, fallbackCluster)
                }
            }
        }

        return clusters.filter { it.postIds.toMutableStringList().size >= MIN_CLUSTER_SIZE }
    }

    private fun findBestMatchingCluster(
        post: Engagement,
        clusters: List<InterestCluster>
    ): ClusterMatch? {
        return clusters.map { cluster ->
            ClusterMatch(
                cosineSimilarity(post.embedding, cluster.centerEmbedding),
                cluster
            )
        }.maxByOrNull { it.similarity }
    }

    private fun addPostToCluster(post: Engagement, cluster: InterestCluster) {
        cluster.postIds.toMutableStringList().add(post.id.toString())
        updateClusterCenter(cluster, post.embedding)
    }

    private fun updateClusterCenter(cluster: InterestCluster, newEmbedding: FloatArray) {
        val clusterSize = cluster.postIds.toMutableStringList().size

        // Weighted average: existing center + new post
        for (i in cluster.centerEmbedding.indices) {
            cluster.centerEmbedding[i] = (
                    (cluster.centerEmbedding[i] * (clusterSize - 1)) + newEmbedding[i]
                    ) / clusterSize
        }
    }

    private fun assignClusterNames(
        clusters: List<InterestCluster>,
        likedPosts: List<Engagement>
    ): List<InterestCluster> {
        // Simple heuristic naming based on common keywords
        // You could enhance this with more sophisticated topic modeling
        return clusters.mapIndexed { index, cluster ->
            val clusterPosts = likedPosts.filter { post ->
                cluster.postIds.contains(post.id.toString())
            }

            val name = generateClusterName(clusterPosts, index)

            cluster.copy().apply {
                // Update the name - we need to create a new instance since data classes are immutable
            }
            InterestCluster(
                centerEmbedding = cluster.centerEmbedding,
                postIds = cluster.postIds,
                name = name,
                strength = cluster.strength
            )
        }
    }

    private fun generateClusterName(posts: List<Engagement>, index: Int): String {
        // Simple keyword-based naming
        // Count common words in post content
        val allWords = posts.flatMap { post ->
            post.text.lowercase()
                .replace(Regex("[^a-z0-9\\s]"), "")
                .split("\\s+".toRegex())
                .filter { it.length > 3 } // Skip short words
        }

        val wordCounts = allWords.groupingBy { it }.eachCount()
        val topWords = wordCounts.entries
            .sortedByDescending { it.value }
            .take(3)
            .map { it.key }

        // Try to create a meaningful name
        return when {
            topWords.any { it.contains("tech") || it.contains("code") || it.contains("programming") } -> "Technology"
            topWords.any { it.contains("cat") || it.contains("dog") || it.contains("pet") } -> "Pets & Animals"
            topWords.any { it.contains("funny") || it.contains("meme") || it.contains("lol") } -> "Humor"
            topWords.any { it.contains("news") || it.contains("politics") || it.contains("world") } -> "News & Current Events"
            topWords.any { it.contains("art") || it.contains("design") || it.contains("creative") } -> "Art & Design"
            topWords.any { it.contains("game") || it.contains("gaming") || it.contains("play") } -> "Gaming"
            topWords.any { it.contains("food") || it.contains("recipe") || it.contains("cooking") } -> "Food & Cooking"
            topWords.isNotEmpty() -> "${topWords.first().replaceFirstChar { it.uppercase() }} & More"
            else -> "Interest ${index + 1}"
        }
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

    companion object {
        fun averageEmbeddings(embeddings: List<FloatArray>): FloatArray {
            if (embeddings.isEmpty()) return FloatArray(384) // MiniLM size

            val avgEmbedding = FloatArray(embeddings[0].size)
            embeddings.forEach { embedding ->
                for (i in embedding.indices) {
                    avgEmbedding[i] += embedding[i]
                }
            }

            for (i in avgEmbedding.indices) {
                avgEmbedding[i] /= embeddings.size
            }

            return avgEmbedding
        }
    }
}