package com.westly.opay.backend

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * How a payment is authorised: the 4-digit payment PIN or the pay token of this device. Neither value is ever
 * printed, so an accidental log line cannot expose it.
 */
sealed interface PayAuth {
    class Pin(val pin: String) : PayAuth {
        override fun toString(): String = "PayAuth.Pin"
    }

    class Biometric(val token: String) : PayAuth {
        override fun toString(): String = "PayAuth.Biometric"
    }
}

/** What `submit_transfer` needs about the transfer itself. The authorisation is passed separately. */
data class TransferRequest(
    val accountNumber: String,
    val bankCode: String,
    val amountKobo: Long,
    val remark: String?,
    val category: String?,
    val counterpartyName: String?,
    val bankName: String?,
    /** True when the recipient is a real OPay account found only by the real-time lookup. */
    val opayExternal: Boolean = false
)

/** The one backend call a transfer needs. A fake of this is used in unit tests. */
interface TransferGateway {
    /** False while the phone has no connection. A payment is never sent, queued or retried in that state. */
    fun isOnline(): Boolean

    suspend fun submit(request: TransferRequest, auth: PayAuth): BackendResult<TransactionRow>
}

/**
 * Sends the transfer exactly once per call: through the `submit_transfer` database function, or, for a real OPay
 * account that is not registered in this app, through the `transfer-opay-external` Edge Function, which looks the
 * recipient name up again on the server.
 */
class RpcTransferGateway(
    private val rpc: RpcApi,
    private val edge: EdgeApi,
    private val deviceIdentity: DeviceIdentity,
    private val online: () -> Boolean
) : TransferGateway {

    override fun isOnline(): Boolean = online()

    override suspend fun submit(request: TransferRequest, auth: PayAuth): BackendResult<TransactionRow> =
        when (auth) {
            is PayAuth.Pin -> send(request, pin = auth.pin, payToken = null, deviceId = null)
            is PayAuth.Biometric -> {
                val deviceId = withContext(Dispatchers.IO) { deviceIdentity.deviceId() }
                send(request, pin = null, payToken = auth.token, deviceId = deviceId)
            }
        }

    private suspend fun send(
        request: TransferRequest,
        pin: String?,
        payToken: String?,
        deviceId: String?
    ): BackendResult<TransactionRow> = if (request.opayExternal) {
        edge.transferOpayExternal(
            TransferOpayExternalRequest(
                accountNumber = request.accountNumber,
                amountKobo = request.amountKobo,
                remark = request.remark,
                category = request.category,
                pin = pin,
                payToken = payToken,
                deviceId = deviceId
            )
        )
    } else rpc.submitTransfer(
        accountNumber = request.accountNumber,
        bankCode = request.bankCode,
        amountKobo = request.amountKobo,
        remark = request.remark,
        category = request.category,
        pin = pin,
        payToken = payToken,
        deviceId = deviceId,
        counterpartyName = request.counterpartyName,
        bankName = request.bankName
    )
}
