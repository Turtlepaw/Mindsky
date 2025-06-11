package io.github.turtlepaw.mindsky.logic

import app.bsky.feed.FeedViewPost

object FeedTuner {
    fun cleanReplies(feed: List<FeedViewPost>): List<FeedViewPost> {
        // Filter out replies that are not top-level posts
        return feed.filter { post ->
            post.reply == null
        }
    }
}