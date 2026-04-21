package io.github.turtlepaw.mindsky.viewmodels

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.Composable
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.turtlepaw.mindsky.auth.AuthState
import io.github.turtlepaw.mindsky.auth.OAuthClient
import io.github.turtlepaw.mindsky.auth.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val oauthClient = OAuthClient(application)
    private val sessionManager = SessionManager(application)

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    companion object {
        private const val TAG = "AuthViewModel"
    }

    init {
        viewModelScope.launch {
            try {
                val session = sessionManager.getSession()
                if (session != null) {
                    Log.d(TAG, "Found existing session for ${session.handle}")
                    _authState.value = AuthState.Authenticated(session)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading session", e)
                _authState.value = AuthState.Unauthenticated
            }
        }

        viewModelScope.launch {
            sessionManager.sessionFlow.collect { session ->
                val current = _authState.value
                when {
                    session == null && current is AuthState.Authenticated -> {
                        _authState.value = AuthState.Unauthenticated
                    }
                    session != null && current !is AuthState.Authenticated -> {
                        _authState.value = AuthState.Authenticated(session)
                    }
                }
            }
        }
    }

    fun startOAuthFlow(handle: String) {
        if (handle.isBlank()) {
            _authState.value = AuthState.Error("Handle cannot be empty")
            return
        }

        _authState.value = AuthState.Loading

        viewModelScope.launch {
            try {
                val authUrl = oauthClient.startOAuthFlow(handle)
                Log.d(TAG, "Opening auth URL: $authUrl")

                val customTabsIntent = CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .build()
                customTabsIntent.intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
                customTabsIntent.launchUrl(getApplication(), authUrl.toUri())
            } catch (e: Exception) {
                Log.e(TAG, "OAuth flow failed", e)
                _authState.value = AuthState.Error(e.message ?: "Authentication failed")
            }
        }
    }

    fun handleOAuthCallback(code: String, state: String? = null) {
        _authState.value = AuthState.Loading

        viewModelScope.launch {
            try {
                val session = oauthClient.handleCallback(code, state)
                Log.d(TAG, "Successfully authenticated as ${session.handle}")
                _authState.value = AuthState.Authenticated(session)
            } catch (e: Exception) {
                Log.e(TAG, "OAuth callback failed", e)
                _authState.value = AuthState.Error(e.message ?: "Authentication failed")
            }
        }
    }

    fun handleOAuthError(error: String) {
        _authState.value = AuthState.Error(error)
    }

    fun refreshSession() {
        val currentState = _authState.value
        if (currentState !is AuthState.Authenticated) {
            Log.w(TAG, "Cannot refresh - not authenticated")
            return
        }

        viewModelScope.launch {
            try {
                val newSession = oauthClient.refreshToken(currentState.session)
                _authState.value = AuthState.Authenticated(newSession)
            } catch (e: Exception) {
                Log.e(TAG, "Session refresh failed", e)
                _authState.value = AuthState.Error("Session expired. Please log in again.")
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            sessionManager.clearSession()
            _authState.value = AuthState.Unauthenticated
            Log.d(TAG, "User signed out")
        }
    }
}

@Composable
fun rememberAuthViewModel(): AuthViewModel {
    val activity = LocalActivity.current as? ComponentActivity
        ?: throw IllegalStateException("Not in an Activity context")
    return viewModel(activity)
}
