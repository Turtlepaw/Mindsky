package io.github.turtlepaw.mindsky

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity

class OAuthCallbackActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d("OAuthCallback", "Received callback with URI: ${intent?.data}")

        intent?.data?.let { uri ->
            val code = uri.getQueryParameter("code")
            val state = uri.getQueryParameter("state")
            val error = uri.getQueryParameter("error")
            val errorDescription = uri.getQueryParameter("error_description")

            Log.d("OAuthCallback", "Code: $code, State: $state, Error: $error")

            if (error != null) {
                Log.e("OAuthCallback", "OAuth error: $error - $errorDescription")
                val mainIntent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("oauth_error", errorDescription ?: error)
                }
                startActivity(mainIntent)
            } else if (code != null) {
                val mainIntent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("oauth_code", code)
                    putExtra("oauth_state", state)
                }
                startActivity(mainIntent)
            } else {
                Log.e("OAuthCallback", "No authorization code found in callback")
                startActivity(Intent(this, MainActivity::class.java))
            }
        } ?: run {
            Log.e("OAuthCallback", "No URI data in intent")
            startActivity(Intent(this, MainActivity::class.java))
        }

        finish()
    }
}
