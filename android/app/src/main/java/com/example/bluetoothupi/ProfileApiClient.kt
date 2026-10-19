package com.example.bluetoothupi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class RegisterResponse(
    val userId: String,
    val name: String,
    val phoneNumber: String?,
    val role: String,
    val balance: Long
)

internal object ProfileApiClient {
    private const val TIMEOUT_MS = 10_000

    suspend fun registerProfile(role: String, name: String, phone: String): Result<RegisterResponse> =
        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL(BuildConfig.DEV_API_BASE_URL + "register")
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.doOutput = true
                connection.useCaches = false

                val jsonBody = JSONObject().apply {
                    put("role", role)
                    put("name", name)
                    if (phone.isNotEmpty()) {
                        put("phone_number", phone)
                    }
                }

                connection.outputStream.use { os ->
                    os.write(jsonBody.toString().toByteArray(Charsets.UTF_8))
                }

                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    val responseString = connection.inputStream.bufferedReader().use { it.readText() }
                    val respObj = JSONObject(responseString)
                    val userId = respObj.optString("user_id").ifEmpty { respObj.optString("userId") }
                    if (userId.isEmpty()) {
                        return@withContext Result.failure(Exception("Server response is missing user_id."))
                    }

                    val respName = respObj.optString("name", name)
                    val respPhone = respObj.optString("phone_number").ifEmpty { respObj.optString("phoneNumber", phone) }
                    val respRole = respObj.optString("role", role)
                    val balance = respObj.optLong("balance", 1000L)

                    Result.success(
                        RegisterResponse(
                            userId = userId,
                            name = respName,
                            phoneNumber = respPhone,
                            role = respRole,
                            balance = balance
                        )
                    )
                } else {
                    Result.failure(Exception("Server returned HTTP $responseCode"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                connection?.disconnect()
            }
        }
}
