package com.appxstudios.festivalconnection.models

data class WalletTransaction(
    val id: String,
    val amountSat: Long,
    var amountUSD: Double,
    val direction: String, // "sent" or "received"
    val timestamp: Long,
    var description: String,
    var paymentHash: String,
    var status: String,
    var fees: Long = 0
)

data class WalletAlert(
    val amountSat: Long,
    val amountUSD: Double,
    val description: String,
    val timestamp: Long
)
