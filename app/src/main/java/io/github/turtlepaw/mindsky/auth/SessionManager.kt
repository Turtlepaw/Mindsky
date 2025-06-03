package io.github.turtlepaw.mindsky.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.google.gson.Gson // We'll need Gson to serialize/deserialize the UserSession

class SessionManager(context: Context) {

    private val sharedPreferences = EncryptedSharedPreferences.create(
        "mindsky_session",
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context.applicationContext,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val gson = Gson()

    companion object {
        private const val KEY_USER_SESSION = "user_session"
    }

    fun saveSession(session: UserSession) {
        val sessionJson = gson.toJson(session)
        sharedPreferences.edit().putString(KEY_USER_SESSION, sessionJson).apply()
    }

    fun getSession(): UserSession? {
        val sessionJson = sharedPreferences.getString(KEY_USER_SESSION, null)
        return sessionJson?.let { gson.fromJson(it, UserSession::class.java) }
    }

    fun clearSession() {
        sharedPreferences.edit().remove(KEY_USER_SESSION).apply()
    }
}