package io.github.turtlepaw.mindsky.logic

import android.content.Context
import android.util.Log
import io.github.turtlepaw.mindsky.db.Engagement
import io.github.turtlepaw.mindsky.db.ObjectBox
import io.github.turtlepaw.mindsky.logic.ranking.InterestCluster
import io.github.turtlepaw.mindsky.logic.ranking.toStringList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/**
 * Enhanced Feed Discovery Engine that builds on the existing clustering system
 * to provide intelligent feed recommendations based on user interests.
 */
class FeedDiscoveryEngine(private val context: Context) {
    
    companion object {
        private const val TAG = "FeedDiscoveryEngine"
        private const val MIN_SIMILARITY_THRESHOLD = 0.7f
        private const val MAX_FEED_RECOMMENDATIONS = 20
    }

    data class ClusterAnalysis(
        val cluster: InterestCluster,
        val samplePosts: List<Engagement>,
        val themes: List<String>,
        val confidence: Float
    )

    data class FeedRecommendation(
        val feedUri: String,
        val title: String,
        val description: String? = null,
        val matchedCluster: InterestCluster,
        val similarityScore: Float,
        val reason: String
    )

    /**
     * Phase 1: Debug function to inspect existing clusters
     * This helps understand what interests are naturally grouping together
     */
    suspend fun analyzeExistingClusters(): List<ClusterAnalysis> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting cluster analysis...")
        
        val objectBox = ObjectBox.store ?: return@withContext emptyList()
        val clusterBox = objectBox.boxFor(InterestCluster::class.java)
        val engagementBox = objectBox.boxFor(Engagement::class.java)
        
        val allClusters = clusterBox.all
        val allEngagements = engagementBox.all
        
        Log.d(TAG, "Found ${allClusters.size} clusters and ${allEngagements.size} engagements")
        
        val analyses = allClusters.mapNotNull { cluster ->
            try {
                val postIds = cluster.postIds.toStringList()
                val clusterEngagements = allEngagements.filter { engagement ->
                    postIds.contains(engagement.id.toString())
                }
                
                if (clusterEngagements.isEmpty()) {
                    Log.w(TAG, "Cluster ${cluster.name} has no matching engagements")
                    return@mapNotNull null
                }
                
                // Take first 5-10 posts as samples for inspection
                val samplePosts = clusterEngagements.take(10)
                
                // Extract themes from the cluster posts
                val themes = extractThemesFromPosts(samplePosts)
                
                // Calculate confidence based on cluster coherence
                val confidence = calculateClusterCoherence(cluster, clusterEngagements)
                
                Log.d(TAG, "Cluster '${cluster.name}': ${samplePosts.size} samples, themes: ${themes.joinToString(", ")}, confidence: $confidence")
                
                ClusterAnalysis(
                    cluster = cluster,
                    samplePosts = samplePosts,
                    themes = themes,
                    confidence = confidence
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error analyzing cluster ${cluster.name}", e)
                null
            }
        }
        
        Log.d(TAG, "Cluster analysis complete. Generated ${analyses.size} analyses")
        return@withContext analyses
    }

    /**
     * Phase 1 Helper: Extract meaningful themes from cluster posts
     */
    private fun extractThemesFromPosts(posts: List<Engagement>): List<String> {
        val allText = posts.joinToString(" ") { it.text.lowercase() }
        
        // Enhanced keyword patterns for better theme detection
        val themePatterns = mapOf(
            "Technology" to listOf("tech", "code", "programming", "developer", "software", "ai", "ml", "data", "algorithm", "api", "framework", "javascript", "python", "react", "android", "ios"),
            "Science" to listOf("science", "research", "study", "discovery", "experiment", "biology", "physics", "chemistry", "climate", "space", "astronomy", "medicine", "health"),
            "Arts & Creativity" to listOf("art", "design", "creative", "photo", "photography", "drawing", "music", "painting", "illustration", "aesthetic", "visual", "artist", "gallery"),
            "Gaming" to listOf("game", "gaming", "play", "gamer", "video", "console", "steam", "nintendo", "xbox", "playstation", "rpg", "fps", "indie"),
            "Food & Cooking" to listOf("food", "recipe", "cooking", "eat", "restaurant", "delicious", "chef", "kitchen", "baking", "ingredients", "meal", "dinner", "lunch"),
            "Sports & Fitness" to listOf("sport", "game", "team", "player", "match", "win", "fitness", "workout", "gym", "running", "training", "exercise", "basketball", "football", "soccer"),
            "News & Politics" to listOf("news", "politics", "world", "breaking", "update", "report", "government", "election", "policy", "democracy", "vote", "crisis", "economy"),
            "Entertainment" to listOf("movie", "film", "show", "tv", "series", "actor", "celebrity", "hollywood", "netflix", "streaming", "comedy", "drama", "thriller"),
            "Animals & Nature" to listOf("cat", "dog", "pet", "animal", "puppy", "kitten", "wildlife", "nature", "forest", "ocean", "bird", "horse", "cute", "adorable"),
            "Humor" to listOf("funny", "meme", "lol", "joke", "humor", "comedy", "laugh", "hilarious", "weird", "silly", "wtf", "omg"),
            "Business" to listOf("business", "startup", "entrepreneur", "money", "finance", "investment", "market", "stock", "crypto", "bitcoin", "economy", "job", "career", "work"),
            "Education" to listOf("learn", "education", "school", "university", "student", "teacher", "course", "book", "reading", "knowledge", "tutorial", "lesson")
        )
        
        val detectedThemes = mutableListOf<String>()
        
        themePatterns.forEach { (theme, keywords) ->
            val matches = keywords.count { keyword -> allText.contains(keyword) }
            val relevanceScore = matches.toFloat() / keywords.size
            
            if (relevanceScore > 0.1f) { // At least 10% of keywords match
                detectedThemes.add(theme)
                Log.d(TAG, "Theme '$theme' detected with relevance: $relevanceScore")
            }
        }
        
        return detectedThemes.ifEmpty { listOf("General Interest") }
    }

    /**
     * Phase 1 Helper: Calculate cluster coherence (how well posts in cluster relate to centroid)
     */
    private fun calculateClusterCoherence(cluster: InterestCluster, posts: List<Engagement>): Float {
        if (posts.isEmpty()) return 0f
        
        val similarities = posts.map { post ->
            cosineSimilarity(post.embedding, cluster.centerEmbedding)
        }
        
        val averageSimilarity = similarities.average()
        val variance = similarities.map { (it - averageSimilarity) * (it - averageSimilarity) }.average()
        val coherence = (averageSimilarity - variance).toFloat()
        
        return coherence.coerceIn(0f, 1f)
    }

    /**
     * Helper: Calculate cosine similarity between two embedding vectors
     */
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

    /**
     * Phase 1: Debug function to log cluster insights for manual inspection
     */
    suspend fun logClusterInsights() = withContext(Dispatchers.IO) {
        val analyses = analyzeExistingClusters()
        
        Log.i(TAG, "=== CLUSTER ANALYSIS REPORT ===")
        analyses.forEachIndexed { index, analysis ->
            Log.i(TAG, "\n--- Cluster ${index + 1}: ${analysis.cluster.name} ---")
            Log.i(TAG, "Strength: ${analysis.cluster.strength}")
            Log.i(TAG, "Confidence: ${analysis.confidence}")
            Log.i(TAG, "Detected Themes: ${analysis.themes.joinToString(", ")}")
            Log.i(TAG, "Sample Posts (first 5):")
            
            analysis.samplePosts.take(5).forEach { post ->
                val shortText = post.text.take(100).replace("\n", " ")
                Log.i(TAG, "  - ${shortText}...")
            }
        }
        Log.i(TAG, "=== END CLUSTER ANALYSIS ===")
    }
}
