package com.example.bluetoothupi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Development-only reachability check for FastAPI. This does not send wallet,
 * profile, or payment data and never changes the backend.
 */
internal object BackendConnectionTester {
    private const val TIMEOUT_MS = 5_000

    suspend fun check(): String = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null

        try {
            connection = URL(BuildConfig.DEV_API_BASE_URL).openConnection()
                as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.useCaches = false

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                "FastAPI connection succeeded."
            } else {
                "FastAPI connection failed (HTTP ${connection.responseCode})."
            }
        } catch (_: IOException) {
            "FastAPI connection failed. Check the server address and network."
        } catch (_: SecurityException) {
            "FastAPI connection failed. Network access is not permitted."
        } finally {
            connection?.disconnect()
        }
    }
}
