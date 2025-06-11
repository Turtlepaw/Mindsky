package io.github.turtlepaw.mindsky.repositories

import app.bsky.actor.GetProfileQueryParams
import app.bsky.actor.ProfileViewDetailed
import sh.christian.ozone.BlueskyApi
import sh.christian.ozone.api.Did

class ProfileRepository(private val api: BlueskyApi) {
    suspend fun getProfile(did: Did): ProfileViewDetailed? {
        return api.getProfile(
            GetProfileQueryParams(
                did
            )
        ).maybeResponse()
    }
}