package io.corbado.connect.example.ui.profile

import io.corbado.connect.ConnectTokenType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class ConnectTokenRequest(
    val connectTokenType: ConnectTokenType,
    val idToken: String,
)

@Serializable
data class ConnectTokenResponse(
    val token: String,
)

object AppBackend {
    suspend fun getConnectToken(
        connectTokenType: ConnectTokenType,
        idToken: String
    ): Result<String> {
        return try {
            val urlString = "https://feature-wv.connect-next.playground.corbado.io/connectToken"
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            val request = ConnectTokenRequest(connectTokenType, idToken)
            val jsonRequest = Json.encodeToString(ConnectTokenRequest.serializer(), request)

            val writer = OutputStreamWriter(connection.outputStream)
            writer.write(jsonRequest)
            writer.flush()

            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val response = reader.readText()
            val jsonResponse = Json.decodeFromString(ConnectTokenResponse.serializer(), response)

            Result.success(jsonResponse.token)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
} 