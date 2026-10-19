package com.example.bluetoothupi

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ==========================================
// Obsidian Imperial (Red & Gold) Color Palette
// ==========================================
private val ObsidianBg = Color(0xFF0D0B0C)      // Deep Canvas Background
private val ObsidianCard = Color(0xFF151314)    // Primary Card Surface
private val ObsidianCardHigh = Color(0xFF1D1B1C) // Secondary / Elevated Surface
private val ImperialRed = Color(0xFF9E1B32)     // Primary Accent Red
private val CrimsonDeep = Color(0xFF800020)     // Dark Red Accent
private val MetallicGold = Color(0xFFD4AF37)    // Core Gold / Balance Accent
private val ChampagneGold = Color(0xFFE5C158)   // Highlights & Active Tints
private val GoldBorder = Color(0x33D4AF37)      // 20% Alpha Gold Hairline Border
private val RedBorder = Color(0x4D9E1B32)       // 30% Alpha Red Hairline Border
private val TextPrimary = Color(0xFFF5E6CA)     // Champagne Warm White
private val TextSecondary = Color(0xFF9E9283)   // Muted Sand / Grey
private val TextDisabled = Color(0xFF635A50)

@Composable
internal fun BluetoothUPIScreen(
    role: BluetoothRole,
    onRoleChange: (BluetoothRole) -> Unit,
    balancePaise: Long,
    transactions: List<WalletTransaction>,
    profiles: List<ProfileEntity>,
    onSaveProfile: (ProfileEntity) -> Unit,
    connectionState: BluetoothUiState,
    pairedDevices: List<PairedBluetoothDevice>,
    backendConnectionStatus: String,
    onTestBackendConnection: () -> Unit,
    onStartCustomer: () -> Unit,
    onStopCustomer: () -> Unit,
    onDisconnect: () -> Unit,
    onRefreshPairedDevices: () -> Unit,
    onConnect: (android.bluetooth.BluetoothDevice) -> Unit,
    onSendPaymentRequest: (Long) -> Unit,
    onCustomerDecision: (String, PaymentDecision) -> Unit,
    syncSummary: SyncSummary?,
    isSyncing: Boolean,
    onSyncTransactions: () -> Unit,
) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = ImperialRed,
            onPrimary = TextPrimary,
            background = ObsidianBg,
            onBackground = TextPrimary,
            surface = ObsidianCard,
            onSurface = TextPrimary,
            error = ImperialRed,
        ),
    ) {
        Scaffold(
            containerColor = ObsidianBg,
            bottomBar = { DisclaimerBar() },
        ) { contentPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(contentPadding)
                    .padding(horizontal = 20.dp),
            ) {
                Spacer(Modifier.height(20.dp))
                AppHeader()
                Spacer(Modifier.height(20.dp))

                var currentTab by remember { mutableStateOf("HOME") }

                // Top Segmented Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(ObsidianCard)
                        .border(1.dp, GoldBorder, RoundedCornerShape(14.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    RoleButton(
                        title = "Wallet & Pay",
                        icon = Icons.Filled.AccountBalanceWallet,
                        selected = currentTab == "HOME",
                        modifier = Modifier.weight(1f),
                        onClick = { currentTab = "HOME" },
                    )
                    RoleButton(
                        title = "Profile",
                        icon = Icons.Filled.Person,
                        selected = currentTab == "PROFILE",
                        modifier = Modifier.weight(1f),
                        onClick = { currentTab = "PROFILE" },
                    )
                }

                Spacer(Modifier.height(20.dp))

                if (connectionState.status == ConnectionStatus.CONNECTING) {
                    RadarConnectionScreen(
                        state = connectionState,
                        onCancel = onDisconnect
                    )
                } else if (currentTab == "PROFILE") {
                    ProfileTab(
                        profiles = profiles,
                        onSave = onSaveProfile
                    )
                } else {
                    // Balance Display with Gold Figures
                    BalanceCard(balancePaise = balancePaise)

                    Spacer(Modifier.height(20.dp))
                    RoleSelector(
                        selected = role,
                        onSelect = onRoleChange,
                    )
                    Spacer(Modifier.height(16.dp))
                    ConnectionStatusCard(state = connectionState)

                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onTestBackendConnection,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, GoldBorder),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ObsidianCardHigh,
                            contentColor = ChampagneGold,
                        ),
                    ) {
                        Text("Test FastAPI Connection", fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = backendConnectionStatus,
                        color = TextSecondary,
                        fontSize = 12.sp,
                    )

                    if (connectionState.status == ConnectionStatus.CONNECTED) {
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = onDisconnect,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, RedBorder),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ObsidianCardHigh,
                                contentColor = ImperialRed,
                            ),
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Disconnect", fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(Modifier.height(22.dp))

                    if (role == BluetoothRole.CUSTOMER) {
                        CustomerPanel(
                            state = connectionState,
                            onStart = onStartCustomer,
                            onStop = onStopCustomer,
                            onDecision = onCustomerDecision,
                        )
                    } else {
                        MerchantPanel(
                            state = connectionState,
                            pairedDevices = pairedDevices,
                            onRefreshDevices = onRefreshPairedDevices,
                            onConnect = onConnect,
                            onSendPaymentRequest = onSendPaymentRequest,
                        )
                    }

                    Spacer(Modifier.height(24.dp))

                    Button(
                        onClick = onSyncTransactions,
                        enabled = !isSyncing,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, GoldBorder),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ObsidianCardHigh,
                            contentColor = TextPrimary,
                            disabledContainerColor = ObsidianCard,
                            disabledContentColor = TextDisabled,
                        ),
                    ) {
                        Text(if (isSyncing) "Syncing Transactions…" else "Sync Transactions")
                    }

                    if (syncSummary != null) {
                        Spacer(Modifier.height(6.dp))
                        val summary = syncSummary
                        val summaryText = if (summary.errorMessage != null) {
                            summary.errorMessage
                        } else {
                            "Synced: ${summary.syncedCount} | Already Synced: ${summary.alreadySyncedCount} | Conflicts: ${summary.conflictCount} | Pending: ${summary.pendingCount}"
                        }
                        Text(
                            text = summaryText,
                            color = if (summary.errorMessage != null) ImperialRed else MetallicGold,
                            fontSize = 12.sp,
                        )
                    }

                    Spacer(Modifier.height(20.dp))

                    Text(
                        text = "Transaction History",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(10.dp))

                    if (transactions.isEmpty()) {
                        Text(
                            text = "No transactions recorded in offline ledger",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    } else {
                        transactions.forEach { transaction ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, GoldBorder),
                                colors = CardDefaults.cardColors(
                                    containerColor = ObsidianCard,
                                ),
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = transaction.type,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )

                                        Text(
                                            text = formatInr(transaction.amountPaise),
                                            color = MetallicGold,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                    }

                                    Spacer(Modifier.height(4.dp))

                                    Text(
                                        text = "TX // ${transaction.requestId}",
                                        fontSize = 11.sp,
                                        color = TextSecondary,
                                        fontFamily = FontFamily.Monospace
                                    )

                                    Spacer(Modifier.height(4.dp))

                                    Text(
                                        text = "Status: ${transaction.status} | Sync: ${transaction.syncStatus}",
                                        fontSize = 12.sp,
                                        color = TextSecondary
                                    )

                                    Text(
                                        text = java.text.SimpleDateFormat(
                                            "dd MMM yyyy, hh:mm a",
                                            java.util.Locale.getDefault()
                                        ).format(java.util.Date(transaction.timestamp)),
                                        fontSize = 11.sp,
                                        color = TextDisabled
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(28.dp))
                }
            }
        }
    }
}

@Composable
private fun AppHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ImperialRed),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Bluetooth,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                text = "TETHR",
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
            )
            Text(
                text = "Commerce without borders · RFCOMM Offline",
                color = TextSecondary,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun BalanceCard(balancePaise: Long) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, GoldBorder),
        colors = CardDefaults.cardColors(
            containerColor = ObsidianCard,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    "OFFLINE AVAILABLE BALANCE",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = formatInr(balancePaise),
                    color = MetallicGold,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Attested Enclave · Local Room DB",
                    color = TextSecondary,
                    fontSize = 12.sp,
                )
            }
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(CrimsonDeep)
                    .border(1.dp, GoldBorder, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.AccountBalanceWallet,
                    contentDescription = null,
                    tint = MetallicGold,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun RoleSelector(
    selected: BluetoothRole,
    onSelect: (BluetoothRole) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ObsidianCard)
            .border(1.dp, GoldBorder, RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        RoleButton(
            title = "Customer",
            icon = Icons.Filled.Person,
            selected = selected == BluetoothRole.CUSTOMER,
            modifier = Modifier.weight(1f),
            onClick = { onSelect(BluetoothRole.CUSTOMER) },
        )

        RoleButton(
            title = "Merchant",
            icon = Icons.Filled.Storefront,
            selected = selected == BluetoothRole.MERCHANT,
            modifier = Modifier.weight(1f),
            onClick = { onSelect(BluetoothRole.MERCHANT) },
        )
    }
}

@Composable
private fun RoleButton(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) ImperialRed else Color.Transparent,
            contentColor = if (selected) TextPrimary else TextSecondary,
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
        ),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) MetallicGold else TextSecondary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(title, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ConnectionStatusCard(state: BluetoothUiState) {
    val label = when (state.status) {
        ConnectionStatus.DISCONNECTED ->
            "Not connected"

        ConnectionStatus.STARTING_SERVER ->
            "Starting listener"

        ConnectionStatus.WAITING_FOR_MERCHANT ->
            "Waiting for Merchant"

        ConnectionStatus.CONNECTING ->
            "Connecting"

        ConnectionStatus.CONNECTED ->
            "RFCOMM Active"
    }

    val indicatorColor = if (state.status == ConnectionStatus.CONNECTED) MetallicGold else ImperialRed

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, GoldBorder),
        colors = CardDefaults.cardColors(
            containerColor = ObsidianCard,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(indicatorColor),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    label,
                    color = indicatorColor,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )

                if (state.peerName != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "· ${state.peerName}",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                state.message,
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )

            if (state.error != null) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(CrimsonDeep.copy(alpha = 0.25f))
                        .border(1.dp, RedBorder, RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = null,
                        tint = ImperialRed,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        state.error,
                        color = TextPrimary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomerPanel(
    state: BluetoothUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onDecision: (String, PaymentDecision) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionHeading(
            "Customer Node",
            "Receive & verify offline voucher request",
        )

        when (state.status) {
            ConnectionStatus.STARTING_SERVER,
            ConnectionStatus.WAITING_FOR_MERCHANT -> {
                Button(
                    onClick = onStop,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, RedBorder),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ObsidianCardHigh,
                        contentColor = ImperialRed,
                    ),
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Stop Listening", fontWeight = FontWeight.SemiBold)
                }
            }

            ConnectionStatus.CONNECTED -> Unit

            else -> {
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ImperialRed,
                        contentColor = TextPrimary,
                    ),
                ) {
                    Icon(
                        Icons.Filled.Bluetooth,
                        contentDescription = null,
                        tint = MetallicGold,
                        modifier = Modifier.size(19.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Start Customer Listener",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        PairingGuidance()

        val request = state.inboundRequest
        if (request != null) {
            PaymentReviewCard(
                request = request,
                onApprove = {
                    onDecision(
                        request.requestId,
                        PaymentDecision.APPROVED,
                    )
                },
                onReject = {
                    onDecision(
                        request.requestId,
                        PaymentDecision.REJECTED,
                    )
                },
            )
        }
    }
}

@Composable
private fun PairingGuidance() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, GoldBorder),
        colors = CardDefaults.cardColors(
            containerColor = ObsidianCard,
        ),
    ) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Devices,
                    contentDescription = null,
                    tint = MetallicGold,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Before Connecting",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Pair both phones in Android Bluetooth Settings first. " +
                        "When the listener starts, you can allow this phone " +
                        "to be visible for up to 5 minutes. Keep this screen " +
                        "open while the Merchant connects.",
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun PaymentReviewCard(
    request: PaymentRequest,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, RedBorder),
        colors = CardDefaults.cardColors(
            containerColor = ObsidianCard,
        ),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                "REQUESTED OFFLINE AMOUNT",
                color = MetallicGold,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                formatInr(request.amountPaise),
                color = MetallicGold,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Demo Voucher · Off-grid cryptographic attestation",
                color = TextSecondary,
                fontSize = 12.sp,
            )

            Spacer(Modifier.height(10.dp))

            Text(
                "NODE NONCE: ${request.requestId}",
                color = TextSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onReject,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, RedBorder),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ObsidianCardHigh,
                        contentColor = TextSecondary,
                    ),
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Decline")
                }

                Button(
                    onClick = onApprove,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ImperialRed,
                        contentColor = TextPrimary,
                    ),
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MetallicGold,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Sign Voucher",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun MerchantPanel(
    state: BluetoothUiState,
    pairedDevices: List<PairedBluetoothDevice>,
    onRefreshDevices: () -> Unit,
    onConnect: (android.bluetooth.BluetoothDevice) -> Unit,
    onSendPaymentRequest: (Long) -> Unit,
) {
    var amountText by rememberSaveable {
        mutableStateOf("")
    }

    val amountPaise = parseAmountToPaise(amountText)

    val canSend =
        state.status == ConnectionStatus.CONNECTED &&
                state.activeRequestId == null &&
                amountPaise != null

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionHeading(
            "Merchant Terminal",
            "Dispatch request to paired Customer node",
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, GoldBorder),
            colors = CardDefaults.cardColors(
                containerColor = ObsidianCard,
            ),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Discovered Terminals",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Paired Bluetooth devices in range",
                            color = TextSecondary,
                            fontSize = 11.sp,
                        )
                    }

                    Button(
                        onClick = onRefreshDevices,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, GoldBorder),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ObsidianCardHigh,
                            contentColor = ChampagneGold,
                        ),
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Refresh", fontSize = 12.sp)
                    }
                }

                Spacer(Modifier.height(10.dp))

                if (pairedDevices.isEmpty()) {
                    Text(
                        "No paired terminals discovered. Pair customer device in Android Settings.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(ObsidianCardHigh)
                            .padding(12.dp),
                    )
                } else {
                    pairedDevices.forEachIndexed { index, paired ->
                        if (index > 0) {
                            Spacer(Modifier.height(8.dp))
                        }
                        PairedDeviceRow(
                            paired = paired,
                            onConnect = onConnect,
                        )
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, GoldBorder),
            colors = CardDefaults.cardColors(
                containerColor = ObsidianCard,
            ),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "Request Valuation",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Set numeric voucher value (INR demo token)",
                    color = TextSecondary,
                    fontSize = 12.sp,
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { newValue ->
                        if (
                            newValue.length <= 15 &&
                            Regex("^\\d{0,12}(?:\\.\\d{0,2})?$").matches(newValue)
                        ) {
                            amountText = newValue
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Amount (₹)", color = TextSecondary) },
                    leadingIcon = {
                        Text(
                            "₹",
                            color = MetallicGold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MetallicGold,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = MetallicGold,
                        unfocusedBorderColor = GoldBorder,
                        focusedLabelColor = MetallicGold,
                        unfocusedLabelColor = TextSecondary,
                        cursorColor = MetallicGold,
                        focusedContainerColor = ObsidianCardHigh,
                        unfocusedContainerColor = ObsidianCardHigh,
                    ),
                )

                if (amountText.isNotEmpty() && amountPaise == null) {
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "Enter an amount greater than ₹0.00 with at most two decimal places.",
                        color = ImperialRed,
                        fontSize = 11.sp,
                    )
                }

                Spacer(Modifier.height(14.dp))

                Button(
                    onClick = {
                        amountPaise?.let(onSendPaymentRequest)
                    },
                    enabled = canSend,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ImperialRed,
                        contentColor = TextPrimary,
                        disabledContainerColor = ObsidianCardHigh,
                        disabledContentColor = TextDisabled,
                    ),
                ) {
                    Text(
                        if (state.activeRequestId != null)
                            "Waiting for Customer Node…"
                        else
                            "Dispatch Demo Request",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        MerchantResultCard(state)
    }
}

@Composable
private fun PairedDeviceRow(
    paired: PairedBluetoothDevice,
    onConnect: (android.bluetooth.BluetoothDevice) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(ObsidianCardHigh)
            .border(1.dp, GoldBorder, RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Bluetooth,
            contentDescription = null,
            tint = MetallicGold,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                paired.displayName,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                paired.shortAddress?.let {
                    "MAC: …$it · SPP Ch 01"
                } ?: "Paired Hardware Node",
                color = TextSecondary,
                fontSize = 10.sp,
            )
        }

        Button(
            onClick = { onConnect(paired.device) },
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ImperialRed,
                contentColor = TextPrimary,
            ),
        ) {
            Text("Connect", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MerchantResultCard(state: BluetoothUiState) {
    val result = state.result ?: return

    val (title, detail, tint) = when (result.status) {
        ResultStatus.APPROVED -> Triple(
            "Voucher Attested · Approved",
            "The customer node signed this offline payload. Recorded to Room DB.",
            MetallicGold,
        )

        ResultStatus.REJECTED -> Triple(
            "Transfer Declined",
            "The customer node declined this transaction.",
            TextSecondary,
        )

        ResultStatus.TIMED_OUT -> Triple(
            "Handshake Timed Out",
            "No response from peer within deadline.",
            ImperialRed,
        )

        ResultStatus.CONNECTION_LOST -> Triple(
            "RFCOMM Link Interrupted",
            "Connection dropped before voucher verification completed.",
            ImperialRed,
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, RedBorder),
        colors = CardDefaults.cardColors(
            containerColor = ObsidianCard,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                if (result.status == ResultStatus.APPROVED) {
                    Icons.Filled.CheckCircle
                } else {
                    Icons.Filled.Info
                },
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )

            Spacer(Modifier.width(10.dp))

            Column {
                Text(
                    title,
                    color = tint,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = formatInr(result.amountPaise),
                    color = MetallicGold,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    detail,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    "HASH // ${result.requestId}",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SectionHeading(
    title: String,
    subtitle: String,
) {
    Column {
        Text(
            title,
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            subtitle,
            color = TextSecondary,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun RadarConnectionScreen(
    state: BluetoothUiState,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .size(220.dp)
                .border(BorderStroke(1.dp, GoldBorder), CircleShape)
                .padding(24.dp)
                .border(BorderStroke(1.dp, GoldBorder), CircleShape)
                .padding(24.dp)
                .border(BorderStroke(1.dp, MetallicGold), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(ImperialRed)
                    .border(2.dp, MetallicGold, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Bluetooth,
                    contentDescription = null,
                    tint = MetallicGold,
                    modifier = Modifier.size(32.dp),
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = "ESTABLISHING RFCOMM LINK",
            color = MetallicGold,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = state.peerName?.let { "Connecting to $it…" } ?: "Scanning secure RFCOMM channel…",
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = state.message,
            color = TextSecondary,
            fontSize = 13.sp,
        )

        Spacer(Modifier.height(40.dp))

        Button(
            onClick = onCancel,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, RedBorder),
            colors = ButtonDefaults.buttonColors(
                containerColor = ObsidianCardHigh,
                contentColor = ImperialRed,
            ),
        ) {
            Text("Cancel Connection", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DisclaimerBar() {
    Surface(color = ObsidianBg) {
        Column {
            HorizontalDivider(
                color = GoldBorder,
                thickness = 1.dp,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianBg)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = null,
                    tint = MetallicGold,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Demo only — zero real currency transferred",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
