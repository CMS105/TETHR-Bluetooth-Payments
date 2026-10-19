package com.example.bluetoothupi

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "wallet")
data class WalletEntity(
    @PrimaryKey val id: Int = 1,
    val balancePaise: Long = 100000L
)

@Entity(tableName = "wallet_transactions")
data class WalletTransaction(
    @PrimaryKey val requestId: String,
    val amountPaise: Long,
    val type: String,
    val status: String = "COMPLETED",
    val timestamp: Long = System.currentTimeMillis(),
    val syncStatus: String = "PENDING"
)

@Dao
interface WalletDao {
    @Query("SELECT * FROM wallet WHERE id = 1")
    fun observeWallet(): Flow<WalletEntity?>

    @Query("SELECT * FROM wallet WHERE id = 1")
    suspend fun getWallet(): WalletEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun createWallet(wallet: WalletEntity)

    @Query("UPDATE wallet SET balancePaise = :balance WHERE id = 1")
    suspend fun updateBalance(balance: Long)

    @Query("SELECT * FROM wallet_transactions ORDER BY timestamp DESC")
    fun observeTransactions(): Flow<List<WalletTransaction>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransaction(transaction: WalletTransaction): Long

    @Query("SELECT * FROM wallet_transactions WHERE status = 'COMPLETED' AND syncStatus = 'PENDING'")
    suspend fun getUnsyncedTransactions(): List<WalletTransaction>

    @Query("SELECT * FROM wallet_transactions")
    suspend fun getAllTransactions(): List<WalletTransaction>

    @Query("UPDATE wallet_transactions SET syncStatus = :status WHERE requestId = :requestId AND type = :type")
    suspend fun updateTransactionSyncStatus(requestId: String, type: String, status: String)
}

@Database(
    entities = [WalletEntity::class, WalletTransaction::class, ProfileEntity::class],
    version = 3,
    exportSchema = false
)
abstract class WalletDatabase : RoomDatabase() {
    abstract fun walletDao(): WalletDao
    abstract fun profileDao(): ProfileDao

    companion object {
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(
                database: androidx.sqlite.db.SupportSQLiteDatabase
            ) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS profiles (
                        profileType TEXT NOT NULL PRIMARY KEY,
                        profileId TEXT NOT NULL,
                        displayName TEXT NOT NULL,
                        phoneNumber TEXT NOT NULL,
                        businessCategory TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(
                database: androidx.sqlite.db.SupportSQLiteDatabase
            ) {
                database.execSQL(
                    "ALTER TABLE wallet_transactions ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'PENDING'"
                )
            }
        }

        @Volatile
        private var INSTANCE: WalletDatabase? = null

        fun getInstance(context: Context): WalletDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    WalletDatabase::class.java,
                    "bluetoothupi_wallet.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { INSTANCE = it }
            }
        }
    }
}
