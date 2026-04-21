package io.github.turtlepaw.mindsky.repositories

import app.bsky.actor.GetProfileQueryParams
import app.bsky.actor.ProfileViewDetailed
import io.github.turtlepaw.mindsky.cache.ProfileCache
import sh.christian.ozone.BlueskyApi
import sh.christian.ozone.api.Did

class ProfileRepository(
    private val api: BlueskyApi,
    private val cache: ProfileCache
) {
    suspend fun getProfile(did: Did, useCache: Boolean = true): ProfileViewDetailed? {
        if (useCache) {
            val cached = cache.getCachedProfile(did)
            if (cached != null) return cached
        }

        val profile = api.getProfile(
            GetProfileQueryParams(
                did
            )
        ).maybeResponse()

        if (profile != null) {
            cache.cacheProfile(profile)
        }

        return profile
    }
}