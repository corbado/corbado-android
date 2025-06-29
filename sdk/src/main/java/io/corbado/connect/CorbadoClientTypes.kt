package com.corbado.connect

import kotlinx.serialization.Serializable

@Serializable
data class AssertionRequest(
    val publicKey: PublicKey
) {
    @Serializable
    data class PublicKey(
        val challenge: String,
        val rpId: String,
        val userVerification: String? = null,
        val allowCredentials: List<CredentialWithTransports>? = null
    )
}

@Serializable
data class AuthenticateResponse(
    val id: String,
    val rawId: String,
    val type: String,
    val response: AssertionResponse
) {
    @Serializable
    data class AssertionResponse(
        val clientDataJSON: String,
        val authenticatorData: String,
        val signature: String,
        val userHandle: String
    )
}

@Serializable
data class CredentialWithTransports(
    val type: String,
    val id: String,
    val transports: List<String>? = null
)

@Serializable
data class AttestationRequest(
    val publicKey: PublicKey
) {
    @Serializable
    data class PublicKey(
        val rp: RelyingParty,
        val user: User,
        val challenge: String,
        val timeout: Int,
        val pubKeyCredParams: List<PubKeyCredParam>,
        val authenticatorSelection: AuthenticatorSelection,
        val attestation: String,
        val excludeCredentials: List<CredentialWithTransports>? = null
    )

    @Serializable
    data class RelyingParty(
        val name: String,
        val id: String
    )

    @Serializable
    data class User(
        val name: String,
        val displayName: String,
        val id: String
    )

    @Serializable
    data class PubKeyCredParam(
        val type: String,
        val alg: Int
    )

    @Serializable
    data class AuthenticatorSelection(
        val residentKey: String,
        val userVerification: String
    )
}

@Serializable
data class AttestationResponse(
    val id: String,
    val rawId: String,
    val type: String = "public-key",
    val response: AttestationResponseData
) {
    @Serializable
    data class AttestationResponseData(
        val clientDataJSON: String,
        val attestationObject: String,
        val transports: List<String?>
    )
}

/**
 * A protocol that handles the presentation of the passkey authorization UI.
 */
typealias AuthorizationControllerProtocol = com.corbado.simplecredentialmanager.AuthorizationController
/**
 * The default implementation of [AuthorizationControllerProtocol].
 */
typealias RealAuthorizationController = com.corbado.simplecredentialmanager.real.RealAuthorizationController

