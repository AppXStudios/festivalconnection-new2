package com.appxstudios.festivalconnection.services

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import breez_sdk_liquid.*
import com.appxstudios.festivalconnection.BuildConfig
import com.appxstudios.festivalconnection.models.WalletTransaction
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.security.SecureRandom
import java.util.UUID

object WalletManager : EventListener {
    private var sdk: BindingLiquidSdk? = null
    private var eventListenerId: String? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _balanceSat = MutableStateFlow(0L)
    val balanceSat: StateFlow<Long> = _balanceSat

    private val _balanceUSD = MutableStateFlow(0.0)
    val balanceUSD: StateFlow<Double> = _balanceUSD

    private val _transactions = MutableStateFlow<List<WalletTransaction>>(emptyList())
    val transactions: StateFlow<List<WalletTransaction>> = _transactions

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError: StateFlow<String?> = _connectionError

    fun connect(context: Context) {
        if (sdk != null) return
        scope.launch {
            try {
                val mnemonic = getOrCreateMnemonic(context)
                val config = defaultConfig(LiquidNetwork.MAINNET, BuildConfig.BREEZ_API_KEY)
                config.workingDir = walletDirectory(context)
                val req = ConnectRequest(config, mnemonic)
                sdk = breez_sdk_liquid.connect(req)
                eventListenerId = sdk?.addEventListener(this@WalletManager)
                _isConnected.value = true
                _connectionError.value = null
                refreshBalance()
                refreshTransactions()
            } catch (e: Exception) {
                _connectionError.value = e.message
                _isConnected.value = false
            }
        }
    }

    fun disconnect() {
        try {
            eventListenerId?.let { sdk?.removeEventListener(it) }
            eventListenerId = null
            sdk?.disconnect()
        } catch (_: Exception) {}
        sdk = null
        _isConnected.value = false
    }

    // MARK: - Balance

    fun refreshBalance() {
        scope.launch {
            try {
                val info = sdk?.getInfo() ?: return@launch
                _balanceSat.value = info.walletInfo.balanceSat.toLong()
                try {
                    val rates = sdk?.fetchFiatRates()
                    val usdRate = rates?.firstOrNull { it.coin == "USD" }
                    if (usdRate != null) {
                        _balanceUSD.value = _balanceSat.value / 100_000_000.0 * usdRate.value
                    }
                } catch (_: Exception) {}
            } catch (e: Exception) {
                println("[Wallet] Balance refresh failed: ${e.message}")
            }
        }
    }

    // MARK: - Receive (Create Invoice)

    suspend fun createInvoice(amountSat: Long, description: String = ""): String {
        val sdk = sdk ?: throw WalletException("Wallet not connected")
        val prepareReq = PrepareReceiveRequest(
            paymentMethod = PaymentMethod.BOLT11_INVOICE,
            amount = ReceiveAmount.Bitcoin(payerAmountSat = amountSat.toULong())
        )
        val prepareResponse = sdk.prepareReceivePayment(prepareReq)
        val receiveReq = ReceivePaymentRequest(
            prepareResponse = prepareResponse,
            description = description
        )
        val response = sdk.receivePayment(receiveReq)
        return response.destination
    }

    // MARK: - Send (Pay Invoice)

    suspend fun sendPayment(invoice: String, amountSat: Long? = null) {
        val sdk = sdk ?: throw WalletException("Wallet not connected")
        val prepareReq = if (amountSat != null) {
            PrepareSendRequest(destination = invoice, amount = PayAmount.Bitcoin(receiverAmountSat = amountSat.toULong()))
        } else {
            PrepareSendRequest(destination = invoice)
        }
        val prepareResponse = sdk.prepareSendPayment(prepareReq)
        val sendReq = SendPaymentRequest(prepareResponse = prepareResponse)
        sdk.sendPayment(sendReq)
        refreshBalance()
        refreshTransactions()
    }

    // MARK: - Parse Invoice

    fun parseInvoice(input: String): Pair<Long?, String?> {
        val sdk = sdk ?: throw WalletException("Wallet not connected")
        return when (val parsed = sdk.parse(input)) {
            is InputType.Bolt11 -> {
                val amountSat = parsed.invoice.amountMsat?.let { it.toLong() / 1000 }
                Pair(amountSat, parsed.invoice.description)
            }
            else -> Pair(null, null)
        }
    }

    // MARK: - Transaction History

    fun refreshTransactions() {
        scope.launch {
            try {
                val payments = sdk?.listPayments(ListPaymentsRequest()) ?: return@launch
                _transactions.value = payments.mapNotNull { payment ->
                    val direction = when (payment.paymentType) {
                        PaymentType.RECEIVE -> "received"
                        else -> "sent"
                    }
                    val desc = when (val details = payment.details) {
                        is PaymentDetails.Lightning -> details.description
                        is PaymentDetails.Liquid -> details.description
                        is PaymentDetails.Bitcoin -> details.description
                        else -> ""
                    }
                    WalletTransaction(
                        id = payment.txId ?: UUID.randomUUID().toString(),
                        amountSat = payment.amountSat.toLong(),
                        amountUSD = 0.0,
                        direction = direction,
                        timestamp = (payment.timestamp?.toLong() ?: 0L) * 1000L,
                        description = desc,
                        paymentHash = payment.txId ?: "",
                        status = payment.status.toString(),
                        fees = payment.feesSat.toLong()
                    )
                }
                // Apply fiat rates
                try {
                    val rates = sdk?.fetchFiatRates()
                    val usdRate = rates?.firstOrNull { it.coin == "USD" }
                    if (usdRate != null) {
                        _transactions.value = _transactions.value.map { tx ->
                            tx.copy(amountUSD = tx.amountSat.toDouble() / 100_000_000.0 * usdRate.value)
                        }
                    }
                } catch (_: Exception) {}
            } catch (e: Exception) {
                println("[Wallet] Transaction refresh failed: ${e.message}")
            }
        }
    }

    // MARK: - Event Listener

    override fun onEvent(e: SdkEvent) {
        when (e) {
            is SdkEvent.PaymentSucceeded, is SdkEvent.PaymentPending -> {
                refreshBalance()
                refreshTransactions()
            }
            is SdkEvent.Synced -> refreshBalance()
            else -> {}
        }
    }

    // MARK: - Mnemonic Management

    private fun getOrCreateMnemonic(context: Context): String {
        val prefs = getEncryptedPrefs(context)
        val existing = prefs.getString("wallet_mnemonic", null)
        // Guard: old versions stored raw hex (64 chars, no spaces). Treat those as invalid and regenerate.
        if (existing != null && existing.trim().split(" ").size in 12..24) {
            return existing
        }

        // Generate real BIP-39 12-word mnemonic (128 bits of entropy)
        // Uses https://github.com/Electric-Coin-Company/kotlin-bip39 — accepted by Breez SDK Liquid
        val code = cash.z.ecc.android.bip39.Mnemonics.MnemonicCode(
            cash.z.ecc.android.bip39.Mnemonics.WordCount.COUNT_12
        )
        val mnemonic = String(code.chars)
        prefs.edit().putString("wallet_mnemonic", mnemonic).apply()
        return mnemonic
    }

    private fun getEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context, "fc_wallet_prefs", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun walletDirectory(context: Context): String {
        val dir = File(context.filesDir, "breez-wallet")
        if (!dir.exists()) dir.mkdirs()
        return dir.absolutePath
    }

    class WalletException(message: String) : Exception(message)
}
