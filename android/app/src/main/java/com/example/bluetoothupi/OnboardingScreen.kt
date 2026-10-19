package com.example.bluetoothupi

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ObsidianBg = Color(0xFF0D0B0C)
private val ObsidianCard = Color(0xFF151314)
private val ObsidianCardHigh = Color(0xFF1D1B1C)
private val ImperialRed = Color(0xFF9E1B32)
private val MetallicGold = Color(0xFFD4AF37)
private val GoldBorder = Color(0x33D4AF37)
private val RedBorder = Color(0x4D9E1B32)
private val TextPrimary = Color(0xFFF5E6CA)
private val TextSecondary = Color(0xFF9E9283)
private val ErrorRed = Color(0xFFBA3038)

@Composable
internal fun OnboardingScreen(
    isLoading: Boolean,
    errorMessage: String?,
    onRegister: (name: String, phone: String, role: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf("customer") } // "customer" or "merchant"
    var nameError by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = ObsidianBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ImperialRed),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Bluetooth,
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = "Welcome to TETHR",
                color = TextPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Connect locally. Pay offline.",
                color = TextSecondary,
                fontSize = 16.sp
            )

            Spacer(Modifier.height(32.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, GoldBorder),
                colors = CardDefaults.cardColors(containerColor = ObsidianCard),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Account Setup",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    // Role selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RoleSelectionCard(
                            title = "Customer",
                            icon = Icons.Filled.Person,
                            selected = selectedRole == "customer",
                            modifier = Modifier.weight(1f),
                            onClick = { selectedRole = "customer" }
                        )
                        RoleSelectionCard(
                            title = "Merchant",
                            icon = Icons.Filled.Storefront,
                            selected = selectedRole == "merchant",
                            modifier = Modifier.weight(1f),
                            onClick = { selectedRole = "merchant" }
                        )
                    }

                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            if (it.isNotBlank()) nameError = false
                        },
                        label = { Text("Full name *", color = TextSecondary) },
                        isError = nameError,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MetallicGold,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = MetallicGold,
                            unfocusedBorderColor = GoldBorder,
                            focusedLabelColor = MetallicGold,
                            unfocusedLabelColor = TextSecondary,
                            cursorColor = MetallicGold,
                            focusedContainerColor = ObsidianCardHigh,
                            unfocusedContainerColor = ObsidianCardHigh
                        )
                    )
                    if (nameError) {
                        Text(
                            text = "Please enter your full name",
                            color = ErrorRed,
                            fontSize = 12.sp
                        )
                    }

                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Phone number (optional)", color = TextSecondary) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MetallicGold,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = MetallicGold,
                            unfocusedBorderColor = GoldBorder,
                            focusedLabelColor = MetallicGold,
                            unfocusedLabelColor = TextSecondary,
                            cursorColor = MetallicGold,
                            focusedContainerColor = ObsidianCardHigh,
                            unfocusedContainerColor = ObsidianCardHigh
                        )
                    )
                }
            }

            if (!errorMessage.isNullOrBlank()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = errorMessage,
                    color = ErrorRed,
                    fontSize = 13.sp
                )
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    if (name.isBlank()) {
                        nameError = true
                    } else {
                        onRegister(name.trim(), phone.trim(), selectedRole)
                    }
                },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ImperialRed,
                    contentColor = TextPrimary,
                    disabledContainerColor = ObsidianCardHigh,
                    disabledContentColor = TextSecondary
                )
            ) {
                Text(
                    text = if (isLoading) "Registering…" else "Continue",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun RoleSelectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val borderColor = if (selected) MetallicGold else GoldBorder
    val bgColor = ObsidianCardHigh
    val contentColor = if (selected) MetallicGold else TextSecondary

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = title,
                color = contentColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
