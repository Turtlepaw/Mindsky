package io.github.turtlepaw.mindsky.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

sealed class AuthState {
    data object Unauthenticated : AuthState()
    data object Loading : AuthState()
    data class Authenticated(val session: UserSession) : AuthState()
    data class Error(val message: String) : AuthState()
}

@Serializable
data class ActorProfile(
    val did: String,
    val handle: String,
    val displayName: String? = null,
    val description: String? = null,
    val avatar: String? = null,
    val banner: String? = null,
    val followersCount: Int? = null,
    val followsCount: Int? = null,
    val postsCount: Int? = null
)

@Serializable
data class AuthorizationServerMetadata(
    val issuer: String,
    @SerialName("authorization_endpoint") val authorizationEndpoint: String,
    @SerialName("token_endpoint") val tokenEndpoint: String,
    @SerialName("pushed_authorization_request_endpoint") val pushedAuthorizationRequestEndpoint: String? = null,
    @SerialName("registration_endpoint") val registrationEndpoint: String? = null,
    @SerialName("jwks_uri") val jwksUri: String? = null,
    @SerialName("scopes_supported") val scopesSupported: List<String>? = null,
    @SerialName("response_types_supported") val responseTypesSupported: List<String>? = null,
    @SerialName("grant_types_supported") val grantTypesSupported: List<String>? = null,
    @SerialName("code_challenge_methods_supported") val codeChallengeMethodsSupported: List<String>? = null,
    @SerialName("dpop_signing_alg_values_supported") val dpopSigningAlgValuesSupported: List<String>? = null,
    @SerialName("token_endpoint_auth_methods_supported") val tokenEndpointAuthMethodsSupported: List<String>? = null
)

@Serializable
data class DidDocument(
    val id: String,
    @SerialName("alsoKnownAs") val alsoKnownAs: List<String>? = null,
    val service: List<ServiceEndpoint>? = null
)

@Serializable
data class ServiceEndpoint(
    val id: String,
    val type: String,
    val serviceEndpoint: String
)

@Serializable
data class ClientMetadata(
    @SerialName("client_id") val clientId: String,
    @SerialName("client_name") val clientName: String = "Mindsky",
    @SerialName("redirect_uris") val redirectUris: List<String>,
    @SerialName("grant_types") val grantTypes: List<String> = listOf("authorization_code", "refresh_token"),
    @SerialName("response_types") val responseTypes: List<String> = listOf("code"),
    @SerialName("scope") val scope: String = "atproto transition:generic",
    @SerialName("token_endpoint_auth_method") val tokenEndpointAuthMethod: String = "none",
    @SerialName("application_type") val applicationType: String = "native",
    @SerialName("dpop_bound_access_tokens") val dpopBoundAccessTokens: Boolean = true
)

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("expires_in") val expiresIn: Int? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("scope") val scope: String? = null,
    val sub: String? = null
)

@Serializable
data class PARResponse(
    @SerialName("request_uri") val requestUri: String,
    @SerialName("expires_in") val expiresIn: Int
)
