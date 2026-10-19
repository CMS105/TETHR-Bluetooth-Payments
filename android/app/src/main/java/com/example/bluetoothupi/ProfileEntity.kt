package com.example.bluetoothupi

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey
    val profileType: String, // CUSTOMER or MERCHANT

    val profileId: String,
    val displayName: String,
    val phoneNumber: String = "",
    val businessCategory: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)
