package com.example.bluetoothupi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class SyncSummary(
    val syncedCount: Int = 0,
    val alreadySyncedCount: Int = 0,
    val conflictCount: Int = 0,
    val pendingCount: Int = 0,
    val errorMessage: String? = null
)

internal object TransactionSyncer {
    private const val TIMEOUT_MS = 10_000

    suspend fun sync(dao: WalletDao): SyncSummary = withContext(Dispatchers.IO) {
        val pending = dao.getUnsyncedTransactions()
        if (pending.isEmpty()) {
            val all = dao.getAllTransactions()
            return@withContext SyncSummary(
                alreadySyncedCount = all.count { it.syncStatus == "SYNCED" },
                conflictCount = all.count { it.syncStatus == "CONFLICT" },
                pendingCount = 0
            )
        }

        var connection: HttpURLConnection? = null
        try {
            val url = URL(BuildConfig.DEV_API_BASE_URL + "sync/transactions")
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.doOutput = true
            connection.useCaches = false

            val jsonBody = JSONObject().apply {
                val recordsArray = JSONArray()
                for (tx in pending) {
                    val recordObj = JSONObject().apply {
                        put("requestId", tx.requestId)
                        put("request_id", tx.requestId)
                        put("amountPaise", tx.amountPaise)
                        put("amount_paise", tx.amountPaise)
                        put("type", tx.type)
                        put("type_lower", tx.type.lowercase())
                        put("status", tx.status)
                        put("status_lower", tx.status.lowercase())
                        put("timestamp", tx.timestamp)
                    }
                    recordsArray.put(recordObj)
                }
                put("records", recordsArray)
            }

            connection.outputStream.use { os ->
                os.write(jsonBody.toString().toByteArray(Charsets.UTF_8))
            }

            val responseCode = connection.responseCode
            if (responseCode == -1) {
                return@withContext SyncSummary(
                    pendingCount = dao.getAllTransactions().count { it.syncStatus == "PENDING" },
                    errorMessage = "Cannot connect to server. Ensure FastAPI is running at http://10.0.2.2:8001/"
                )
            }

            if (responseCode in 200..299) {
                val responseString = connection.inputStream.bufferedReader().use { it.readText() }
                
                val respObj: JSONObject
                try {
                    respObj = JSONObject(responseString)
                } catch (_: Exception) {
                    return@withContext SyncSummary(
                        pendingCount = dao.getAllTransactions().count { it.syncStatus == "PENDING" },
                        errorMessage = "Invalid sync response: malformed JSON object."
                    )
                }

                val results = respObj.optJSONArray("results") ?: respObj.optJSONArray("records")
                if (results == null) {
                    return@withContext SyncSummary(
                        pendingCount = dao.getAllTransactions().count { it.syncStatus == "PENDING" },
                        errorMessage = "Invalid sync response: missing results array."
                    )
                }

                val validatedUpdates = mutableMapOf<Pair<String, String>, String>()
                val categoryMap = mutableMapOf<Pair<String, String>, String>()
                val contradictoryKeys = mutableSetOf<Pair<String, String>>()

                for (i in 0 until results.length()) {
                    val resItem = results.optJSONObject(i)
                    if (resItem == null) {
                        return@withContext SyncSummary(
                            pendingCount = dao.getAllTransactions().count { it.syncStatus == "PENDING" },
                            errorMessage = "Invalid sync response: result entry is not a JSON object."
                        )
                    }

                    val reqId = resItem.optString("requestId").ifEmpty { resItem.optString("request_id") }
                    val type = resItem.optString("type")
                    val status = resItem.optString("status")

                    if (reqId.isEmpty() || type.isEmpty() || status.isEmpty()) {
                        return@withContext SyncSummary(
                            pendingCount = dao.getAllTransactions().count { it.syncStatus == "PENDING" },
                            errorMessage = "Invalid sync response: missing required fields in result entry."
                        )
                    }

                    val normalizedStatus = status.lowercase()
                    if (normalizedStatus !in listOf("synced", "already_synced", "conflict")) {
                        return@withContext SyncSummary(
                            pendingCount = dao.getAllTransactions().count { it.syncStatus == "PENDING" },
                            errorMessage = "Invalid sync response: unknown status '$status'."
                        )
                    }

                    val matchingTx = pending.firstOrNull { 
                        it.requestId == reqId && it.type.equals(type, ignoreCase = true) 
                    }
                    if (matchingTx == null) {
                        // Unmatched record: ignore without changing local data
                        continue
                    }

                    val key = Pair(matchingTx.requestId, matchingTx.type)
                    val expectedDbStatus = if (normalizedStatus == "conflict") "CONFLICT" else "SYNCED"

                    if (key in contradictoryKeys) {
                        // Once contradictory, never apply an update for this record.
                    } else if (validatedUpdates.containsKey(key)) {
                        if (validatedUpdates[key] != expectedDbStatus) {
                            validatedUpdates.remove(key)
                            categoryMap.remove(key)
                            contradictoryKeys.add(key)
                        }
                    } else {
                        validatedUpdates[key] = expectedDbStatus
                        categoryMap[key] = normalizedStatus
                    }
                }

                var syncedInc = 0
                var alreadySyncedInc = 0
                var conflictInc = 0

                for ((key, dbStatus) in validatedUpdates) {
                    dao.updateTransactionSyncStatus(key.first, key.second, dbStatus)
                    when (categoryMap[key]) {
                        "synced" -> syncedInc++
                        "already_synced" -> alreadySyncedInc++
                        "conflict" -> conflictInc++
                    }
                }

                val allTransactions = dao.getAllTransactions()
                SyncSummary(
                    syncedCount = syncedInc,
                    alreadySyncedCount = alreadySyncedInc,
                    conflictCount = conflictInc,
                    pendingCount = allTransactions.count { it.syncStatus == "PENDING" }
                )
            } else {
                val errorBody = try {
                    connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                } catch (_: Exception) {
                    ""
                }
                val detail = if (errorBody.isNotBlank()) " — $errorBody" else ""
                SyncSummary(
                    pendingCount = dao.getAllTransactions().count { it.syncStatus == "PENDING" },
                    errorMessage = "Server returned HTTP $responseCode$detail"
                )
            }
        } catch (e: Exception) {
            SyncSummary(
                pendingCount = dao.getAllTransactions().count { it.syncStatus == "PENDING" },
                errorMessage = "Sync failed: ${e.message ?: "Network error"}"
            )
        } finally {
            connection?.disconnect()
        }
    }
}
