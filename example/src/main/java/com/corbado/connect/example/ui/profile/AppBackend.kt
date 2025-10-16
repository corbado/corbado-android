package com.corbado.connect.example.ui.profile

import com.corbado.connect.core.ConnectTokenType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class GetConnectTokenRequest(
    val connectTokenType: ConnectTokenType,
    val idToken: String
)

@Serializable
data class GetConnectTokenResponse(
    val token: String
)

@Serializable
data class VerifySignedPasskeyDataRequest(
    val signedPasskeyData: String
)

@Serializable
data class VerifySignedPasskeyDataResponse(
    val success: Boolean,
    val idToken: String
)

object AppBackend {
    // Get connectToken from backend (which gets it from Corbado Backend API)
    suspend fun getConnectToken(
        connectTokenType: ConnectTokenType,
        idToken: String
    ): Result<String> {
        return try {
            // 1. Set up request
            val request = GetConnectTokenRequest(connectTokenType, idToken)
            val jsonRequest = Json.encodeToString(GetConnectTokenRequest.serializer(), request)

            // 2. Perform request
            val urlString = "https://feature-wv.connect-next.playground.corbado.io/connectToken"
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            val writer = OutputStreamWriter(connection.outputStream)
            writer.write(jsonRequest)
            writer.flush()

            // 3. Check response
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val response = reader.readText()

            // 4. Decode the JSON response
            val jsonResponse = Json.decodeFromString(GetConnectTokenResponse.serializer(), response)

            Result.success(jsonResponse.token)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Verify signedPasskeyData with backend (which verifies with Corbado Backend API)
    // If you are using an external Identity Provider (IdP) such as Amazon Cognito or Firebase,
    // you can delegate the verification process to those systems by making a call to them.
    suspend fun verifySignedPasskeyData(signedPasskeyData: String): Result<Pair<Boolean, String>> {
        return try {
            // 1. Set up request
            val request = VerifySignedPasskeyDataRequest(signedPasskeyData)
            val jsonRequest = Json.encodeToString(VerifySignedPasskeyDataRequest.serializer(), request)

            // 2. Perform request
            val urlString = "https://<your-backend>/auth/verifySignedPasskeyData"
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            val writer = OutputStreamWriter(connection.outputStream)
            writer.write(jsonRequest)
            writer.flush()

            // 3. Check response
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val response = reader.readText()

            // 4. Decode the JSON response
            val jsonResponse = Json.decodeFromString(VerifySignedPasskeyDataResponse.serializer(), response)

            Result.success(Pair(jsonResponse.success, jsonResponse.idToken))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
} 