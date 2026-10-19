package com.example.bluetoothupi

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class WalletRepository(
    private val database: WalletDatabase
) {
    private val dao = database.walletDao()

    val balancePaise: Flow<Long> =
        dao.observeWallet().map { it?.balancePaise ?: 100000L }

    val transactions: Flow<List<WalletTransaction>> =
        dao.observeTransactions()

    suspend fun initialize() {
        database.withTransaction {
            dao.createWallet(WalletEntity())
        }
    }

    suspend fun syncTransactions(): SyncSummary {
        return TransactionSyncer.sync(dao)
    }

    /**
     * Records one demo transaction per unique request ID.
     * CREDIT adds to the local balance; DEBIT subtracts.
     */
    suspend fun applyDemoTransaction(
        requestId: String,
        amountPaise: Long,
        type: String
    ): Boolean {
        if (requestId.isBlank() || amountPaise <= 0L) return false
        if (type != "CREDIT" && type != "DEBIT") return false

        return database.withTransaction {
            dao.createWallet(WalletEntity())

            val wallet = dao.getWallet() ?: return@withTransaction false

            val newBalance = when (type) {
                "DEBIT" -> {
                    if (wallet.balancePaise < amountPaise) {
                        return@withTransaction false
                    }
                    wallet.balancePaise - amountPaise
                }

                "CREDIT" -> {
                    if (wallet.balancePaise > Long.MAX_VALUE - amountPaise) {
                        return@withTransaction false
                    }
                    wallet.balancePaise + amountPaise
                }

                else -> return@withTransaction false
            }

            val inserted = dao.insertTransaction(
                WalletTransaction(
                    requestId = requestId,
                    amountPaise = amountPaise,
                    type = type
                )
            )

            // An existing request ID must never change the balance again.
            if (inserted == -1L) {
                return@withTransaction false
            }

            dao.updateBalance(newBalance)
            true
        }
    }
}
