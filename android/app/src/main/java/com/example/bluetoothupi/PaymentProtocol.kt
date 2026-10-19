package com.example.bluetoothupi

import org.json.JSONException
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

internal const val BLUETOOTH_SERVICE_UUID = "e7d2a6c0-3f41-4c10-9a28-b7f12c18d050"

internal enum class PaymentDecision(val wireValue: String) {
    APPROVED("approved"),
    REJECTED("rejected"),
}

internal data class PaymentRequest(
    val requestId: String,
    val amountPaise: Long,
)

internal sealed interface PaymentMessage {
    data class Request(val request: PaymentRequest) : PaymentMessage
    data class Response(val requestId: String, val decision: PaymentDecision) : PaymentMessage
    data class ProtocolError(val message: String) : PaymentMessage
}

internal class ProtocolException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/**
 * Wire format: one UTF-8 JSON object per line. Amounts are sent as integer paise,
 * avoiding locale and floating-point differences between the two phones.
 */
internal object PaymentProtocol {
    private const val TYPE_REQUEST = "payment_request"
    private const val TYPE_RESPONSE = "payment_response"
    private const val TYPE_ERROR = "protocol_error"

    fun encodeRequest(request: PaymentRequest): String =
        JSONObject()
            .put("type", TYPE_REQUEST)
            .put("requestId", request.requestId)
            .put("amountPaise", request.amountPaise)
            .toString()

    fun encodeResponse(requestId: String, decision: PaymentDecision): String =
        JSONObject()
            .put("type", TYPE_RESPONSE)
            .put("requestId", requestId)
            .put("decision", decision.wireValue)
            .toString()

    fun encodeError(message: String): String =
        JSONObject()
            .put("type", TYPE_ERROR)
            .put("message", message.take(120))
            .toString()

    @Throws(ProtocolException::class)
    fun parse(line: String): PaymentMessage {
        val json = try {
            JSONObject(line)
        } catch (exception: JSONException) {
            throw ProtocolException("The other phone sent invalid JSON.", exception)
        }

        return when (json.opt("type")) {
            TYPE_REQUEST -> {
                val requestId = requireRequestId(json.opt("requestId"))
                val paiseValue = json.opt("amountPaise")
                val amountPaise = when (paiseValue) {
                    is Int -> paiseValue.toLong()
                    is Long -> paiseValue
                    else -> throw ProtocolException("The request amount must be an integer number of paise.")
                }
                if (amountPaise <= 0L) {
                    throw ProtocolException("The request amount must be greater than zero.")
                }
                PaymentMessage.Request(PaymentRequest(requestId, amountPaise))
            }

            TYPE_RESPONSE -> {
                val requestId = requireRequestId(json.opt("requestId"))
                val decision = when (json.opt("decision")) {
                    PaymentDecision.APPROVED.wireValue -> PaymentDecision.APPROVED
                    PaymentDecision.REJECTED.wireValue -> PaymentDecision.REJECTED
                    else -> throw ProtocolException("The response decision is not recognized.")
                }
                PaymentMessage.Response(requestId, decision)
            }

            TYPE_ERROR -> {
                val message = json.opt("message")
                if (message !is String || message.isBlank()) {
                    throw ProtocolException("The other phone sent an invalid protocol error.")
                }
                PaymentMessage.ProtocolError(message.take(120))
            }

            else -> throw ProtocolException("The other phone sent an unexpected message type.")
        }
    }

    private fun requireRequestId(value: Any?): String {
        if (value !is String) {
            throw ProtocolException("The message is missing a valid request ID.")
        }
        val parsed = try {
            UUID.fromString(value)
        } catch (exception: IllegalArgumentException) {
            throw ProtocolException("The message contains an invalid request ID.", exception)
        }
        if (parsed.toString() != value) {
            throw ProtocolException("The message contains a non-canonical request ID.")
        }
        return value
    }
}

internal fun parseAmountToPaise(rawAmount: String): Long? {
    val text = rawAmount.trim()
    if (!Regex("^\\d+(?:\\.\\d{0,2})?$").matches(text)) return null
    return try {
        BigDecimal(text)
            .setScale(2, RoundingMode.UNNECESSARY)
            .movePointRight(2)
            .longValueExact()
            .takeIf { it > 0L }
    } catch (_: ArithmeticException) {
        null
    } catch (_: NumberFormatException) {
        null
    }
}

internal fun formatInr(amountPaise: Long): String {
    val amount = BigDecimal.valueOf(amountPaise, 2)
    return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN")).format(amount)
}
