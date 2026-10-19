package com.example.bluetoothupi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.UUID

@Composable
fun ProfileTab(
    profiles: List<ProfileEntity>,
    onSave: (ProfileEntity) -> Unit
) {
    var selectedType by remember {
        mutableStateOf("CUSTOMER")
    }

    val existing = profiles.firstOrNull {
        it.profileType == selectedType
    }

    var name by remember(selectedType, existing?.updatedAt) {
        mutableStateOf(existing?.displayName.orEmpty())
    }

    var phone by remember(selectedType, existing?.updatedAt) {
        mutableStateOf(existing?.phoneNumber.orEmpty())
    }

    var category by remember(selectedType, existing?.updatedAt) {
        mutableStateOf(existing?.businessCategory.orEmpty())
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("My Profile")

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { selectedType = "CUSTOMER" }
            ) {
                Text("Customer")
            }

            Button(
                onClick = { selectedType = "MERCHANT" }
            ) {
                Text("Merchant")
            }
        }

        Text(
            if (selectedType == "CUSTOMER")
                "Customer details"
            else
                "Merchant details"
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = {
                Text(
                    if (selectedType == "CUSTOMER")
                        "Customer name"
                    else
                        "Business / owner name"
                )
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Phone number (optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        if (selectedType == "MERCHANT") {
            OutlinedTextField(
                value = category,
                onValueChange = { category = it },
                label = { Text("Business category") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        Text(
            "Profile ID: ${
                existing?.profileId ?: "Created when you save"
            }"
        )

        Button(
            onClick = {
                val savedName = name.trim()

                if (savedName.isNotEmpty()) {
                    onSave(
                        ProfileEntity(
                            profileType = selectedType,
                            profileId = existing?.profileId
                                ?: UUID.randomUUID().toString(),
                            displayName = savedName,
                            phoneNumber = phone.trim(),
                            businessCategory = if (
                                selectedType == "MERCHANT"
                            ) category.trim() else "",
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Profile")
        }
    }
}
