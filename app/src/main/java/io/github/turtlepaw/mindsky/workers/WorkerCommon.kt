package io.github.turtlepaw.mindsky.workers

import android.content.Context
import android.util.Log
import io.github.turtlepaw.mindsky.auth.SessionManager
import io.github.turtlepaw.mindsky.auth.UserSession
import io.github.turtlepaw.mindsky.db.ObjectBox
import io.objectbox.BoxStore
import sh.christian.ozone.api.AuthenticatedXrpcBlueskyApi

object WorkerCommon {
    const val API_REQUEST_LIMIT = 100L // Standard API limit for pagination
    const val THERMAL_COOLDOWN_MS = 200L // Short delay for CPU cooling
    const val MAX_PAGES_TO_FETCH_LIKES = 10 // Limit to prevent excessive API calls
    const val PROGRESS = "progress"
    const val STAGE = "stage"

    fun getSession(appContext: Context): UserSession? {
        val sessionManager = SessionManager(appContext)
        return sessionManager.getSession()
    }

    fun getBlueskyApi(appContext: Context): AuthenticatedXrpcBlueskyApi {
        return (appContext.applicationContext as io.github.turtlepaw.mindsky.MindskyApplication).blueskyApi
    }

    fun safelyGetObjectBox(appContext: Context): BoxStore {
        return if (ObjectBox.store == null) {
            ObjectBox.init(appContext)
        } else {
            ObjectBox.store!!
        }
    }
}