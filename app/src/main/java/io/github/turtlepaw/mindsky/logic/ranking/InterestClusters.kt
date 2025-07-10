package io.github.turtlepaw.mindsky.logic.ranking

import io.github.turtlepaw.mindsky.db.Engagement
import kotlin.math.sqrt
import kotlin.random.Random

class InterestClusterer {
    private val SIMILARITY_THRESHOLD = 0.6 // Lowered for better clustering
    private val MAX_CLUSTERS = 6 // Increased for more diversity
    private val MIN_CLUSTER_SIZE = 3 // More meaningful clusters
    private val MAX_ITERATIONS = 10 // Prevent infinite loops
    private val CONVERGENCE_THRESHOLD = 0.001 // When to stop refining

    fun createClusters(likedPosts: List<Engagement>): Pair<List<InterestCluster>, FloatArray> {
        if (likedPosts.size < MIN_CLUSTER_SIZE) {
            val avgEmbedding = averageEmbeddings(likedPosts.map { it.embedding })
            return emptyList<InterestCluster>() to avgEmbedding
        }

        // Use K-means++ initialization for better clustering
        val clusters = kMeansCluster(likedPosts)
        val namedClusters = assignClusterNames(clusters, likedPosts)
        val fallback = averageEmbeddings(likedPosts.map { it.embedding })

        return namedClusters to fallback
    }

    private fun kMeansCluster(likedPosts: List<Engagement>): List<InterestCluster> {
        val k = minOf(MAX_CLUSTERS, likedPosts.size / MIN_CLUSTER_SIZE)
        if (k < 2) {
            // Not enough posts for meaningful clustering
            return listOf(
                InterestCluster(
                    centerEmbedding = averageEmbeddings(likedPosts.map { it.embedding }),
                    postIds = likedPosts.map { it.id.toString() }.toJson(),
                    name = "General Interest",
                )
            )
        }

        // Initialize centroids using K-means++
        val centroids = initializeCentroids(likedPosts, k)
        val clusters = mutableListOf<MutableList<Engagement>>()
        repeat(k) { clusters.add(mutableListOf()) }

        var hasConverged = false
        var iteration = 0

        while (!hasConverged && iteration < MAX_ITERATIONS) {
            // Clear previous assignments
            clusters.forEach { it.clear() }

            // Assign each post to nearest centroid
            for (post in likedPosts) {
                val nearestCentroid = findNearestCentroid(post.embedding, centroids)
                clusters[nearestCentroid].add(post)
            }

            // Update centroids
            val newCentroids = mutableListOf<FloatArray>()
            var totalMovement = 0.0

            for (i in clusters.indices) {
                val clusterPosts = clusters[i]
                val newCentroid = if (clusterPosts.isNotEmpty()) {
                    averageEmbeddings(clusterPosts.map { it.embedding })
                } else {
                    centroids[i].copyOf() // Keep old centroid if cluster is empty
                }

                totalMovement += euclideanDistance(centroids[i], newCentroid)
                newCentroids.add(newCentroid)
            }

            centroids.clear()
            centroids.addAll(newCentroids)

            hasConverged = totalMovement < CONVERGENCE_THRESHOLD
            iteration++
        }

        // Filter out small clusters and convert to InterestCluster
        return clusters.mapIndexed { index, clusterPosts ->
            if (clusterPosts.size >= MIN_CLUSTER_SIZE) {
                InterestCluster(
                    centerEmbedding = centroids[index],
                    postIds = clusterPosts.map { it.id.toString() }.toJson(),
                    name = "Interest ${index + 1}",
                    strength = calculateClusterStrength(clusterPosts, centroids[index])
                )
            } else null
        }.filterNotNull()
    }

    private fun initializeCentroids(posts: List<Engagement>, k: Int): MutableList<FloatArray> {
        val centroids = mutableListOf<FloatArray>()
        val random = Random.Default

        // First centroid is random
        centroids.add(posts[random.nextInt(posts.size)].embedding.copyOf())

        // Subsequent centroids chosen with probability proportional to distance
        for (i in 1 until k) {
            val distances = posts.map { post ->
                centroids.minOf { centroid ->
                    euclideanDistance(post.embedding, centroid)
                }
            }

            val totalDistance = distances.sum()
            val threshold = random.nextDouble() * totalDistance
            var cumulative = 0.0

            for (j in posts.indices) {
                cumulative += distances[j]
                if (cumulative >= threshold) {
                    centroids.add(posts[j].embedding.copyOf())
                    break
                }
            }
        }

        return centroids
    }

    private fun findNearestCentroid(embedding: FloatArray, centroids: List<FloatArray>): Int {
        return centroids.indices.minByOrNull { index ->
            euclideanDistance(embedding, centroids[index])
        } ?: 0
    }

    private fun calculateClusterStrength(posts: List<Engagement>, centroid: FloatArray): Float {
        if (posts.isEmpty()) return 0f

        val avgDistance = posts.map {
            euclideanDistance(it.embedding, centroid)
        }.average()

        // Lower distance = higher strength, normalize to 0-1 range
        return (1.0 / (1.0 + avgDistance)).toFloat()
    }

    private fun assignClusterNames(
        clusters: List<InterestCluster>,
        likedPosts: List<Engagement>
    ): List<InterestCluster> {
        return clusters.map { cluster ->
            val clusterPosts = likedPosts.filter { post ->
                cluster.postIds.contains(post.id.toString())
            }

            val name = generateClusterName(clusterPosts)

            InterestCluster(
                centerEmbedding = cluster.centerEmbedding,
                postIds = cluster.postIds,
                name = name,
                strength = cluster.strength
            )
        }
    }

    private fun generateClusterName(posts: List<Engagement>): String {
        // Extract and count meaningful keywords
        val keywords = posts.flatMap { post ->
            extractKeywords(post.text)
        }

        val keywordCounts = keywords.groupingBy { it }.eachCount()
        val topKeywords = keywordCounts.entries
            .sortedByDescending { it.value }
            .take(15)
            .map { it.key }

        // Pattern matching for common topics
        val patterns = mapOf(
            "Technology" to listOf(
                "tech",
                "code",
                "programming",
                "developer",
                "software",
                "ai",
                "ml",
                "data"
            ),
            "Animals" to listOf("cat", "dog", "pet", "animal", "puppy", "kitten", "wildlife"),
            "Humor" to listOf("funny", "meme", "lol", "joke", "humor", "comedy", "laugh"),
            "News" to listOf("news", "politics", "world", "breaking", "update", "report"),
            "Art" to listOf("art", "design", "creative", "photo", "photography", "drawing"),
            "Gaming" to listOf("game", "gaming", "play", "gamer", "video", "console"),
            "Food" to listOf("food", "recipe", "cooking", "eat", "restaurant", "delicious"),
            "Music" to listOf("music", "song", "album", "artist", "concert", "sound"),
            "Sports" to listOf("sport", "game", "team", "player", "match", "win"),
            "Science" to listOf("science", "research", "study", "discovery", "experiment")
        )

        // Find best matching pattern
        val bestMatch = patterns.entries.maxByOrNull { (_, patternWords) ->
            patternWords.count { keyword -> topKeywords.contains(keyword) }
        }

        return when {
            bestMatch != null && bestMatch.value.any { topKeywords.contains(it) } -> bestMatch.key
            topKeywords.isNotEmpty() -> "${
                topKeywords.first().replaceFirstChar { it.uppercase() }
            } Interest"

            else -> "General Interest"
        }
    }

    private fun extractKeywords(text: String): List<String> {
        val stopWords = setOf(
            "the",
            "and",
            "for",
            "are",
            "but",
            "not",
            "you",
            "all",
            "can",
            "her",
            "was",
            "one",
            "our",
            "had",
            "but",
            "words",
            "use",
            "your",
            "way",
            "about",
            "many",
            "then",
            "them",
            "these",
            "so",
            "some",
            "her",
            "would",
            "make",
            "like",
            "into",
            "him",
            "has",
            "two",
            "more",
            "very",
            "what",
            "know",
            "will",
            "up",
            "if",
            "out",
            "who",
            "get",
            "which",
            "go",
            "me"
        )

        return text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split("\\s+".toRegex())
            .filter { it.length > 2 && !stopWords.contains(it) }
            .distinct()
    }

    private fun euclideanDistance(a: FloatArray, b: FloatArray): Double {
        require(a.size == b.size) { "Vectors must be same size" }

        var sum = 0.0
        for (i in a.indices) {
            val diff = a[i] - b[i]
            sum += diff * diff
        }
        return sqrt(sum)
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
