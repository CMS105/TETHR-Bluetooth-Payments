
package com.example.bluetoothupi

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val appViewModel: BluetoothUpiViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            BluetoothUPIApp(
                connectionManager = appViewModel.connectionManager
            )
        }
    }
}

internal class BluetoothUpiViewModel : ViewModel() {

    val connectionManager = BluetoothConnectionManager()

    override fun onCleared() {
        connectionManager.close()
        super.onCleared()
    }
}

@Composable
private fun BluetoothUPIApp(
    connectionManager: BluetoothConnectionManager
) {
    val context = LocalContext.current
    val uiScope = rememberCoroutineScope()

    val connectionState by connectionManager.state.collectAsState()

    // Room wallet and repository
    val walletRepository = remember {
        WalletRepository(
            WalletDatabase.getInstance(context.applicationContext)
        )
    }

    val walletBalance by walletRepository.balancePaise.collectAsState(
        initial = 100000L
    )

    val transactions by walletRepository.transactions.collectAsState(
        initial = emptyList()
    )

    val profileDao = remember {
        WalletDatabase.getInstance(context.applicationContext).profileDao()
    }

    val profiles by profileDao.observeProfiles().collectAsState(
        initial = emptyList()
    )

    var role by remember {
        mutableStateOf(BluetoothRole.CUSTOMER)
    }

    var isOnboardingComplete by remember {
        mutableStateOf(false)
    }

    var isRegistering by remember { mutableStateOf(false) }
    var registerError by remember { mutableStateOf<String?>(null) }

    if (!isOnboardingComplete) {
        OnboardingScreen(
            isLoading = isRegistering,
            errorMessage = registerError,
            onRegister = { name, phone, selectedRole ->
                isRegistering = true
                registerError = null
                uiScope.launch {
                    ProfileApiClient.registerProfile(selectedRole, name, phone).fold(
                        onSuccess = { resp ->
                            withContext(Dispatchers.IO) {
                                val profileType = if (resp.role.equals("merchant", ignoreCase = true)) "MERCHANT" else "CUSTOMER"
                                profileDao.saveProfile(
                                    ProfileEntity(
                                        profileType = profileType,
                                        profileId = resp.userId,
                                        displayName = resp.name,
                                        phoneNumber = resp.phoneNumber ?: phone,
                                        businessCategory = "",
                                        updatedAt = System.currentTimeMillis()
                                    )
                                )
                            }
                            role = if (resp.role.equals("merchant", ignoreCase = true)) BluetoothRole.MERCHANT else BluetoothRole.CUSTOMER
                            isOnboardingComplete = true
                            isRegistering = false
                        },
                        onFailure = { err ->
                            registerError = err.message ?: "Registration failed. Check server connection."
                            isRegistering = false
                        }
                    )
                }
            }
        )
        return
    }

    var backendConnectionStatus by remember {
        mutableStateOf("FastAPI connection has not been tested.")
    }

    var syncSummary by remember { mutableStateOf<SyncSummary?>(null) }
    var isSyncing by remember { mutableStateOf(false) }

    // Initialize the demo wallet.
    LaunchedEffect(walletRepository) {
        withContext(Dispatchers.IO) {
            walletRepository.initialize()
        }
    }

    var pairedDevices by remember {
        mutableStateOf<List<PairedBluetoothDevice>>(emptyList())
    }

    var pendingPermissionNames by remember {
        mutableStateOf<List<String>>(emptyList())
    }

    var pendingPermissionAction by remember {
        mutableStateOf<(() -> Unit)?>(null)
    }

    var pendingPermissionDenial by remember {
        mutableStateOf<(() -> Unit)?>(null)
    }

    var pendingEnableAction by remember {
        mutableStateOf<(() -> Unit)?>(null)
    }

    val enableBluetoothLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val action = pendingEnableAction
            pendingEnableAction = null

            val adapter = bluetoothAdapter(context)

            val enabled = try {
                adapter?.isEnabled == true
            } catch (_: SecurityException) {
                false
            }

            if (action != null && enabled) {
                action()
            } else if (
                action != null ||
                result.resultCode != Activity.RESULT_OK
            ) {
                connectionManager.reportError(
                    "Bluetooth is still off. Turn it on to use the demo."
                )
            }
        }

    val visibilityLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                connectionManager.reportNotice(
                    "This phone is discoverable temporarily. Keep the Customer listener running."
                )
            } else {
                connectionManager.reportNotice(
                    "The listener is running. Pair the phones in Android Bluetooth Settings before connecting."
                )
            }
        }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            val action = pendingPermissionAction
            val denial = pendingPermissionDenial
            val requested = pendingPermissionNames

            pendingPermissionAction = null
            pendingPermissionDenial = null
            pendingPermissionNames = emptyList()

            if (action == null) {
                return@rememberLauncherForActivityResult
            }

            val granted = requested.all { permission ->
                result[permission] == true ||
                        context.checkSelfPermission(permission) ==
                        PackageManager.PERMISSION_GRANTED
            }

            if (granted) {
                action()
            } else {
                denial?.invoke()
            }
        }

    fun requestPermissions(
        permissions: List<String>,
        onGranted: () -> Unit,
        onDenied: () -> Unit = {
            connectionManager.reportError(
                "Bluetooth permission was denied. Allow Nearby devices access in Android Settings."
            )
        }
    ) {
        val missing = permissions.distinct().filter { permission ->
            context.checkSelfPermission(permission) !=
                    PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            onGranted()
        } else {
            pendingPermissionNames = missing
            pendingPermissionAction = onGranted
            pendingPermissionDenial = onDenied
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    fun withBluetoothConnect(
        action: (BluetoothAdapter) -> Unit
    ) {
        val permissions =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                listOf(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                emptyList()
            }

        requestPermissions(
            permissions = permissions,
            onGranted = {
                val adapter = bluetoothAdapter(context)

                if (adapter == null) {
                    connectionManager.reportError(
                        "This phone does not have a Bluetooth adapter."
                    )
                    return@requestPermissions
                }

                val enabled = try {
                    adapter.isEnabled
                } catch (_: SecurityException) {
                    false
                }

                if (enabled) {
                    action(adapter)
                } else {
                    pendingEnableAction = {
                        val refreshedAdapter = bluetoothAdapter(context)

                        val nowEnabled = try {
                            refreshedAdapter?.isEnabled == true
                        } catch (_: SecurityException) {
                            false
                        }

                        if (refreshedAdapter != null && nowEnabled) {
                            action(refreshedAdapter)
                        } else {
                            connectionManager.reportError(
                                "Bluetooth is still off. Turn it on to use the demo."
                            )
                        }
                    }

                    enableBluetoothLauncher.launch(
                        Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    )
                }
            }
        )
    }

    fun requestCustomerVisibility() {
        val launchPrompt = {
            visibilityLauncher.launch(
                Intent(
                    BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE
                ).apply {
                    putExtra(
                        BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,
                        300
                    )
                }
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissions(
                permissions = listOf(
                    Manifest.permission.BLUETOOTH_ADVERTISE
                ),
                onGranted = launchPrompt,
                onDenied = {
                    connectionManager.reportNotice(
                        "The listener is running. Pair the phones in Android Bluetooth Settings."
                    )
                }
            )
        } else {
            launchPrompt()
        }
    }

    /*
     * MERCHANT WALLET CREDIT
     *
     * Credit this phone only when it is acting as the Merchant
     * and its Bluetooth manager reports an approved result.
     *
     * Room's unique requestId prevents the same request from
     * crediting this wallet twice.
     */
    LaunchedEffect(
        connectionState.result?.requestId,
        connectionState.result?.status,
        role
    ) {
        val result = connectionState.result

        if (
            role == BluetoothRole.MERCHANT &&
            result != null &&
            result.status == ResultStatus.APPROVED
        ) {
            val credited = withContext(Dispatchers.IO) {
                walletRepository.applyDemoTransaction(
                    requestId = result.requestId,
                    amountPaise = result.amountPaise,
                    type = "CREDIT"
                )
            }

            if (credited) {
                connectionManager.reportNotice(
                    "Demo payment received: ${formatInr(result.amountPaise)}"
                )
            } else {
                connectionManager.reportError(
                    "Merchant wallet was not credited. This request may already have been processed."
                )
            }
        }
    }

    BluetoothUPIScreen(
        role = role,

        onRoleChange = { newRole ->
            if (
                newRole != role &&
                connectionState.status != ConnectionStatus.DISCONNECTED
            ) {
                connectionManager.disconnect()
            }

            role = newRole
        },

        balancePaise = walletBalance,
        transactions = transactions,
        profiles = profiles,
        onSaveProfile = { profile ->
            uiScope.launch {
                withContext(Dispatchers.IO) {
                    profileDao.saveProfile(profile)
                }
            }
        },
        syncSummary = syncSummary,
        isSyncing = isSyncing,
        onSyncTransactions = {
            isSyncing = true
            syncSummary = null
            uiScope.launch {
                val result = walletRepository.syncTransactions()
                syncSummary = result
                isSyncing = false
            }
        },
        connectionState = connectionState,
        pairedDevices = pairedDevices,
        backendConnectionStatus = backendConnectionStatus,
        onTestBackendConnection = {
            backendConnectionStatus = "Checking FastAPI connection…"
            uiScope.launch {
                backendConnectionStatus = BackendConnectionTester.check()
            }
        },

        onStartCustomer = {
            withBluetoothConnect { adapter ->
                connectionManager.startCustomerServer(adapter)
                requestCustomerVisibility()
            }
        },

        onStopCustomer = {
            connectionManager.disconnect()
        },

        onDisconnect = {
            connectionManager.disconnect()
        },

        onRefreshPairedDevices = {
            withBluetoothConnect { adapter ->
                uiScope.launch {
                    try {
                        val loadedDevices = withContext(Dispatchers.IO) {
                            adapter.bondedDevices
                                .map { device ->
                                    device.toPairedBluetoothDevice()
                                }
                                .sortedBy {
                                    it.displayName.lowercase()
                                }
                        }

                        pairedDevices = loadedDevices

                        if (loadedDevices.isEmpty()) {
                            connectionManager.reportNotice(
                                "No paired phones found. Pair them in Android Bluetooth Settings, then refresh."
                            )
                        } else {
                            connectionManager.reportNotice(
                                "Showing paired Bluetooth phones."
                            )
                        }
                    } catch (_: SecurityException) {
                        connectionManager.reportError(
                            "Allow Nearby devices access to view paired phones."
                        )
                    }
                }
            }
        },

        onConnect = { device ->
            withBluetoothConnect {
                connectionManager.connectToPairedDevice(device)
            }
        },

        onSendPaymentRequest = { amountPaise ->
            val requestId =
                connectionManager.sendPaymentRequest(amountPaise)

            if (requestId == null) {
                connectionManager.reportError(
                    "Connect to a Customer phone first before sending a request."
                )
            }
        },

        onCustomerDecision = { requestId, decision ->
            if (decision == PaymentDecision.REJECTED) {
                connectionManager.respondToPayment(
                    requestId,
                    PaymentDecision.REJECTED
                )
            } else {
                val pendingRequest = connectionState.inboundRequest

                if (pendingRequest == null ||
                    pendingRequest.requestId != requestId
                ) {
                    connectionManager.reportError(
                        "Payment request not found. Please try again."
                    )
                } else {
                    uiScope.launch {
                        val debited = withContext(Dispatchers.IO) {
                            walletRepository.applyDemoTransaction(
                                requestId = requestId,
                                amountPaise = pendingRequest.amountPaise,
                                type = "DEBIT"
                            )
                        }

                        if (debited) {
                            connectionManager.respondToPayment(
                                requestId,
                                PaymentDecision.APPROVED
                            )
                        } else {
                            connectionManager.reportError(
                                "Debit failed. Check the balance or duplicate transaction."
                            )
                        }
                    }
                }
            }
        }
    )
}

private fun bluetoothAdapter(
    context: Context
): BluetoothAdapter? {
    return context.getSystemService(
        BluetoothManager::class.java
    )?.adapter
}

internal data class PairedBluetoothDevice(
    val device: BluetoothDevice,
    val displayName: String,
    val shortAddress: String?
)

private fun BluetoothDevice.toPairedBluetoothDevice():
        PairedBluetoothDevice {

    val safeName = try {
        name?.takeIf(String::isNotBlank)
    } catch (_: SecurityException) {
        null
    }

    val safeAddress = try {
        address?.takeLast(5)
    } catch (_: SecurityException) {
        null
    }

    return PairedBluetoothDevice(
        device = this,
        displayName = safeName ?: "Unnamed Bluetooth phone",
        shortAddress = safeAddress
    )
}
