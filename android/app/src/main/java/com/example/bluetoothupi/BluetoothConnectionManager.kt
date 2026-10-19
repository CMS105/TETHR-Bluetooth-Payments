
package com.example.bluetoothupi

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.LinkedHashMap
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

internal enum class ConnectionStatus {
    DISCONNECTED,
    STARTING_SERVER,
    WAITING_FOR_MERCHANT,
    CONNECTING,
    CONNECTED,
}

internal enum class ResultStatus {
    APPROVED,
    REJECTED,
    TIMED_OUT,
    CONNECTION_LOST,
}

internal data class MerchantResult(
    val requestId: String,
    val status: ResultStatus,
    val amountPaise: Long,
)

internal data class BluetoothUiState(
    val status: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val message: String = "Choose a role to begin.",
    val peerName: String? = null,
    val error: String? = null,
    val inboundRequest: PaymentRequest? = null,
    val activeRequestId: String? = null,
    val result: MerchantResult? = null,
)

internal class BluetoothConnectionManager {
    companion object {
        private const val TAG = "BluetoothUPI"
        private const val SERVICE_NAME = "BluetoothUPI Demo"
        private const val MAX_LINE_BYTES = 4096
        private const val CONNECT_TIMEOUT_MS = 15_000L
        private const val RESPONSE_TIMEOUT_MS = 30_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lifecycleLock = Any()
    private val writeMutex = Mutex()
    private val seenCustomerRequestIds = LinkedHashMap<String, Boolean>()
    private val serviceUuid = UUID.fromString(BLUETOOTH_SERVICE_UUID)
    private val mutableState = MutableStateFlow(BluetoothUiState())

    val state: StateFlow<BluetoothUiState> = mutableState.asStateFlow()

    @Volatile private var activeSocket: BluetoothSocket? = null
    @Volatile private var pendingSocket: BluetoothSocket? = null
    @Volatile private var serverSocket: BluetoothServerSocket? = null
    @Volatile private var activeRole: BluetoothRole? = null

    private var acceptJob: Job? = null
    private var connectJob: Job? = null
    private var connectTimeoutJob: Job? = null
    private var readerJob: Job? = null
    private var timeoutJob: Job? = null

    private val requestAmounts = LinkedHashMap<String, Long>()

    private data class TransportSnapshot(
        val activeSocket: BluetoothSocket?,
        val pendingSocket: BluetoothSocket?,
        val serverSocket: BluetoothServerSocket?,
        val jobs: List<Job>,
    )

    fun startCustomerServer(adapter: BluetoothAdapter) {
        closeTransport()
        activeRole = BluetoothRole.CUSTOMER

        mutableState.value = BluetoothUiState(
            status = ConnectionStatus.STARTING_SERVER,
            message = "Starting the Bluetooth demo listener…",
        )

        acceptJob = scope.launch {
            try {
                val server = adapter.listenUsingRfcommWithServiceRecord(
                    SERVICE_NAME,
                    serviceUuid,
                )
                serverSocket = server

                mutableState.update {
                    it.copy(
                        status = ConnectionStatus.WAITING_FOR_MERCHANT,
                        message = "Listening for a paired Merchant phone.",
                        error = null,
                    )
                }

                val socket = server.accept()
                serverSocket = null
                runCatching { server.close() }

                attachConnectedSocket(
                    socket,
                    BluetoothRole.CUSTOMER,
                    remoteName(socket),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (security: SecurityException) {
                failWhileWaiting(
                    "Bluetooth permission denied. Allow Nearby devices access.",
                )
            } catch (exception: Exception) {
                if (
                    scope.isActive &&
                    mutableState.value.status != ConnectionStatus.DISCONNECTED
                ) {
                    Log.w(TAG, "Customer listener failed", exception)
                    failWhileWaiting(
                        "Bluetooth listener stopped. Check Bluetooth and retry.",
                    )
                }
            }
        }
    }

    fun connectToPairedDevice(device: BluetoothDevice) {
        closeTransport()
        activeRole = BluetoothRole.MERCHANT

        mutableState.value = BluetoothUiState(
            status = ConnectionStatus.CONNECTING,
            message = "Connecting to the paired Customer phone…",
        )

        connectJob = scope.launch {
            var socket: BluetoothSocket? = null
            var timer: Job? = null
            val timedOut = AtomicBoolean(false)

            try {
                socket = device.createRfcommSocketToServiceRecord(serviceUuid)
                val attemptSocket = socket

                synchronized(lifecycleLock) {
                    pendingSocket = attemptSocket
                }

                timer = scope.launch {
                    delay(CONNECT_TIMEOUT_MS)

                    val shouldClose = synchronized(lifecycleLock) {
                        pendingSocket === attemptSocket
                    }

                    if (shouldClose) {
                        timedOut.set(true)
                        runCatching { attemptSocket.close() }
                    }
                }

                synchronized(lifecycleLock) {
                    connectTimeoutJob = timer
                }

                socket.connect()

                synchronized(lifecycleLock) {
                    if (pendingSocket === socket) pendingSocket = null
                    if (connectTimeoutJob === timer) connectTimeoutJob = null
                }

                timer.cancel()

                attachConnectedSocket(
                    socket,
                    BluetoothRole.MERCHANT,
                    remoteName(socket) ?: safeDeviceName(device),
                )
            } catch (cancelled: CancellationException) {
                socket?.let { runCatching { it.close() } }
                throw cancelled
            } catch (exception: Exception) {
                socket?.let { runCatching { it.close() } }

                synchronized(lifecycleLock) {
                    pendingSocket = null
                }

                Log.w(TAG, "RFCOMM connection failed", exception)

                if (mutableState.value.status == ConnectionStatus.CONNECTING) {
                    mutableState.update {
                        it.copy(
                            status = ConnectionStatus.DISCONNECTED,
                            peerName = null,
                            error = if (timedOut.get()) {
                                "Connection timed out. Check the Customer listener."
                            } else {
                                "Connection failed. Confirm both phones are paired and retry."
                            },
                            message = "Not connected. You can retry.",
                        )
                    }
                }
            } finally {
                timer?.cancel()

                synchronized(lifecycleLock) {
                    if (connectTimeoutJob === timer) {
                        connectTimeoutJob = null
                    }
                }
            }
        }
    }

    fun sendPaymentRequest(amountPaise: Long): String? {
        val current = mutableState.value

        if (
            current.status != ConnectionStatus.CONNECTED ||
            activeRole != BluetoothRole.MERCHANT ||
            current.activeRequestId != null ||
            amountPaise <= 0L
        ) {
            return null
        }

        val request = PaymentRequest(
            UUID.randomUUID().toString(),
            amountPaise,
        )

        synchronized(lifecycleLock) {
            requestAmounts[request.requestId] = amountPaise
        }

        mutableState.update {
            it.copy(
                activeRequestId = request.requestId,
                result = null,
                error = null,
                message = "Demo request sent. Waiting for the Customer…",
            )
        }

        scope.launch {
            try {
                writeLine(PaymentProtocol.encodeRequest(request))

                timeoutJob?.cancel()
                timeoutJob = scope.launch {
                    delay(RESPONSE_TIMEOUT_MS)

                    val amount = synchronized(lifecycleLock) {
                        requestAmounts.remove(request.requestId)
                    } ?: request.amountPaise

                    mutableState.update { state ->
                        if (state.activeRequestId == request.requestId) {
                            state.copy(
                                activeRequestId = null,
                                result = MerchantResult(
                                    requestId = request.requestId,
                                    status = ResultStatus.TIMED_OUT,
                                    amountPaise = amount,
                                ),
                                message = "No response arrived. The demo request timed out.",
                            )
                        } else {
                            state
                        }
                    }
                }
            } catch (exception: Exception) {
                handleWriteFailure(exception)
            }
        }

        return request.requestId
    }

    fun respondToPayment(
        requestId: String,
        decision: PaymentDecision,
    ) {
        val request = mutableState.value.inboundRequest

        if (
            activeRole != BluetoothRole.CUSTOMER ||
            request?.requestId != requestId
        ) {
            return
        }

        // Clear the request before sending to prevent repeated button taps.
        mutableState.update {
            if (it.inboundRequest?.requestId == requestId) {
                it.copy(
                    inboundRequest = null,
                    message = if (decision == PaymentDecision.APPROVED) {
                        "Demo approval sent. No real money was transferred."
                    } else {
                        "Demo request rejected. No real money was transferred."
                    },
                    error = null,
                )
            } else {
                it
            }
        }

        scope.launch {
            try {
                writeLine(
                    PaymentProtocol.encodeResponse(requestId, decision),
                )
            } catch (exception: Exception) {
                handleWriteFailure(exception)
            }
        }
    }

    fun disconnect() {
        closeTransport()
        activeRole = null

        synchronized(lifecycleLock) {
            requestAmounts.clear()
        }

        mutableState.value = BluetoothUiState(
            status = ConnectionStatus.DISCONNECTED,
            message = "Disconnected. Choose a role to reconnect.",
        )
    }

    fun reportError(message: String) {
        mutableState.update {
            it.copy(error = message, message = message)
        }
    }

    fun reportNotice(message: String) {
        mutableState.update {
            it.copy(error = null, message = message)
        }
    }

    fun close() {
        disconnect()
        scope.cancel()
    }

    private fun attachConnectedSocket(
        socket: BluetoothSocket,
        role: BluetoothRole,
        peerName: String?,
    ) {
        if (!socket.isConnected) {
            runCatching { socket.close() }
            failWhileWaiting("Bluetooth did not establish a socket connection.")
            return
        }

        synchronized(lifecycleLock) {
            activeSocket = socket
            pendingSocket = null
            serverSocket = null
        }

        activeRole = role

        mutableState.update {
            it.copy(
                status = ConnectionStatus.CONNECTED,
                peerName = peerName?.takeIf(String::isNotBlank),
                error = null,
                message = if (peerName.isNullOrBlank()) {
                    "Bluetooth connected. Demo requests are ready."
                } else {
                    "Connected to $peerName. Demo requests are ready."
                },
            )
        }

        readerJob = scope.launch {
            readLoop(socket, role)
        }
    }

    private suspend fun readLoop(
        socket: BluetoothSocket,
        role: BluetoothRole,
    ) {
        try {
            val input = socket.inputStream

            while (scope.isActive && socket.isConnected) {
                val line = readProtocolLine(input)
                    ?: throw EOFException("The other phone closed the connection.")

                when (val message = PaymentProtocol.parse(line)) {
                    is PaymentMessage.ProtocolError -> {
                        handleConnectionLost(
                            socket,
                            "The other phone rejected the message: ${message.message}",
                            isError = true,
                        )
                        return
                    }

                    else -> processMessage(message, role)
                }
            }

            handleConnectionLost(socket, "Bluetooth connection closed.")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: ProtocolException) {
            try {
                writeLine(
                    PaymentProtocol.encodeError(
                        exception.message ?: "Invalid message.",
                    ),
                )
            } catch (_: Exception) {
                // Best effort.
            }

            handleConnectionLost(
                socket,
                "Protocol error: ${exception.message ?: "invalid message"}",
                isError = true,
            )
        } catch (security: SecurityException) {
            handleConnectionLost(
                socket,
                "Bluetooth permission was revoked. Reconnect and grant access.",
                isError = true,
            )
        } catch (exception: Exception) {
            handleConnectionLost(
                socket,
                "Bluetooth connection lost: ${exception.message ?: "connection closed"}.",
            )
        }
    }

    private fun processMessage(
        message: PaymentMessage,
        role: BluetoothRole,
    ) {
        when (message) {
            is PaymentMessage.Request -> {
                if (
                    role != BluetoothRole.CUSTOMER ||
                    activeRole != BluetoothRole.CUSTOMER
                ) {
                    throw ProtocolException(
                        "A payment request arrived at the wrong endpoint.",
                    )
                }

                val request = message.request

                synchronized(lifecycleLock) {
                    if (seenCustomerRequestIds.containsKey(request.requestId)) {
                        throw ProtocolException(
                            "A duplicate payment request ID was received.",
                        )
                    }

                    if (mutableState.value.inboundRequest != null) {
                        throw ProtocolException(
                            "A second request arrived before the first was decided.",
                        )
                    }

                    seenCustomerRequestIds[request.requestId] = true

                    // Bound memory use for this live connection.
                    while (seenCustomerRequestIds.size > 1000) {
                        val oldest = seenCustomerRequestIds.keys.first()
                        seenCustomerRequestIds.remove(oldest)
                    }
                }

                mutableState.update {
                    it.copy(
                        inboundRequest = request,
                        error = null,
                        message = "Review the demo payment request before deciding.",
                    )
                }
            }

            is PaymentMessage.Response -> {
                if (
                    role != BluetoothRole.MERCHANT ||
                    activeRole != BluetoothRole.MERCHANT
                ) {
                    throw ProtocolException(
                        "A payment response arrived at the wrong endpoint.",
                    )
                }

                val activeId = mutableState.value.activeRequestId

                if (
                    activeId == null ||
                    activeId != message.requestId
                ) {
                    throw ProtocolException(
                        "The response does not match the active payment request.",
                    )
                }

                val amount = synchronized(lifecycleLock) {
                    requestAmounts.remove(message.requestId)
                } ?: throw ProtocolException(
                    "The original amount for this request was not found.",
                )

                timeoutJob?.cancel()
                timeoutJob = null

                val resultStatus =
                    if (message.decision == PaymentDecision.APPROVED) {
                        ResultStatus.APPROVED
                    } else {
                        ResultStatus.REJECTED
                    }

                mutableState.update {
                    if (it.activeRequestId == message.requestId) {
                        it.copy(
                            activeRequestId = null,
                            result = MerchantResult(
                                requestId = message.requestId,
                                status = resultStatus,
                                amountPaise = amount,
                            ),
                            error = null,
                            message = if (resultStatus == ResultStatus.APPROVED) {
                                "Customer approved the demo request. No money was transferred."
                            } else {
                                "Customer rejected the demo request. No money was transferred."
                            },
                        )
                    } else {
                        it
                    }
                }
            }

            is PaymentMessage.ProtocolError -> Unit
        }
    }

    private suspend fun readProtocolLine(
        input: java.io.InputStream,
    ): String? {
        val bytes = ByteArrayOutputStream()

        while (true) {
            val next = input.read()

            if (next == -1) {
                if (bytes.size() == 0) return null
                throw ProtocolException(
                    "The other phone closed the connection mid-message.",
                )
            }

            if (next == '\n'.code) {
                val line = bytes.toByteArray()
                    .toString(StandardCharsets.UTF_8)
                    .removeSuffix("\r")

                if (line.isBlank()) {
                    throw ProtocolException("An empty message was received.")
                }

                return line
            }

            if (bytes.size() >= MAX_LINE_BYTES) {
                throw ProtocolException("The received message is too large.")
            }

            bytes.write(next)
        }
    }

    private suspend fun writeLine(line: String) {
        val socket = activeSocket
            ?: throw IOException("There is no connected Bluetooth socket.")

        if (!socket.isConnected) {
            throw IOException("The Bluetooth socket is no longer connected.")
        }

        writeMutex.withLock {
            val bytes = (line + "\n").toByteArray(StandardCharsets.UTF_8)
            socket.outputStream.write(bytes)
            socket.outputStream.flush()
        }
    }

    private fun handleWriteFailure(exception: Exception) {
        Log.w(TAG, "Could not write Bluetooth message", exception)

        val socket = activeSocket

        if (socket != null) {
            handleConnectionLost(
                socket,
                "Could not send the demo message. Bluetooth connection was lost.",
            )
        } else {
            reportError("Could not send the demo message. Reconnect and try again.")
        }
    }

    private fun handleConnectionLost(
        socket: BluetoothSocket,
        message: String,
        isError: Boolean = false,
    ) {
        if (activeSocket !== socket) return

        val stateBeforeClose = mutableState.value
        val activeRequestId = stateBeforeClose.activeRequestId

        val lostResult = activeRequestId?.let { id ->
            val amount = synchronized(lifecycleLock) {
                requestAmounts.remove(id)
            }

            amount?.let {
                MerchantResult(
                    requestId = id,
                    status = ResultStatus.CONNECTION_LOST,
                    amountPaise = it,
                )
            }
        }

        closeTransport()
        activeRole = null

        mutableState.update {
            it.copy(
                status = ConnectionStatus.DISCONNECTED,
                peerName = null,
                inboundRequest = null,
                activeRequestId = null,
                result = lostResult ?: it.result,
                error = if (isError) message else null,
                message = message,
            )
        }
    }

    private fun failWhileWaiting(message: String) {
        if (mutableState.value.status == ConnectionStatus.CONNECTED) return

        closeTransport()
        activeRole = null

        mutableState.update {
            it.copy(
                status = ConnectionStatus.DISCONNECTED,
                peerName = null,
                inboundRequest = null,
                activeRequestId = null,
                error = message,
                message = message,
            )
        }
    }

    private fun closeTransport() {
        val snapshot = synchronized(lifecycleLock) {
            val current = TransportSnapshot(
                activeSocket = activeSocket,
                pendingSocket = pendingSocket,
                serverSocket = serverSocket,
                jobs = listOfNotNull(
                    acceptJob,
                    connectJob,
                    connectTimeoutJob,
                    readerJob,
                    timeoutJob,
                ),
            )

            activeSocket = null
            pendingSocket = null
            serverSocket = null
            acceptJob = null
            connectJob = null
            connectTimeoutJob = null
            readerJob = null
            timeoutJob = null

            current
        }

        snapshot.jobs.forEach { it.cancel() }
        runCatching { snapshot.activeSocket?.close() }
        runCatching { snapshot.pendingSocket?.close() }
        runCatching { snapshot.serverSocket?.close() }
    }

    private fun remoteName(socket: BluetoothSocket): String? =
        try {
            socket.remoteDevice?.name
        } catch (_: SecurityException) {
            null
        }

    private fun safeDeviceName(device: BluetoothDevice): String? =
        try {
            device.name
        } catch (_: SecurityException) {
            null
        }
}

internal enum class BluetoothRole {
    CUSTOMER,
    MERCHANT,
}
