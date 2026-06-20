package com.example.data.payment

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

/**
 * Payment transaction model for BiteDash.
 * Represents a payment transaction in the system.
 * 
 * @property transactionId Unique transaction identifier
 * @property userId User who initiated the payment
 * @property orderId Associated order ID
 * @property amount Payment amount
 * @property currency Currency code (USD, ZWG)
 * @property status Payment status (PENDING, PAID, FAILED, CANCELLED)
 * @property method Payment method used
 * @property pollUrl Paynow poll URL for status checks
 * @property paynowReference Paynow transaction reference
 * @property mobileMoneyNumber Mobile money number used
 * @property createdAt Transaction creation timestamp
 * @property updatedAt Last update timestamp
 */
data class PaymentTransaction(
    @DocumentId
    val transactionId: String = "",
    val userId: String = "",
    val orderId: String = "",
    val amount: Double = 0.0,
    val currency: String = "USD",
    val status: String = PaymentStatus.PENDING.value,
    val method: String = PaymentMethod.ECO_CASH.value,
    val pollUrl: String = "",
    val paynowReference: String = "",
    val mobileMoneyNumber: String = "",
    val errorMessage: String = "",
    @ServerTimestamp
    val createdAt: Timestamp? = null,
    @ServerTimestamp
    val updatedAt: Timestamp? = null,
    val completedAt: Timestamp? = null
) {
    /**
     * Returns the payment status as enum.
     */
    fun getPaymentStatus(): PaymentStatus = PaymentStatus.fromString(status)

    /**
     * Returns the payment method as enum.
     */
    fun getPaymentMethod(): PaymentMethod = PaymentMethod.fromString(method)

    /**
     * Check if payment is in a terminal state.
     */
    fun isTerminal(): Boolean = status in listOf(
        PaymentStatus.PAID.value,
        PaymentStatus.FAILED.value,
        PaymentStatus.CANCELLED.value
    )
}

/**
 * Result wrapper for payment operations.
 */
sealed class PaymentResult {
    data class Success(val transaction: PaymentTransaction) : PaymentResult()
    data class Error(val message: String, val code: String? = null) : PaymentResult()
    data object Cancelled : PaymentResult()
}

/**
 * Payment initiation request.
 */
data class PaymentRequest(
    val orderId: String,
    val userId: String,
    val amount: Double,
    val currency: String = "USD",
    val method: PaymentMethod,
    val mobileMoneyNumber: String = "",
    val description: String = ""
)

/**
 * Payment status check result.
 */
data class PaymentStatusResult(
    val status: PaymentStatus,
    val pollUrl: String = "",
    val paidAmount: Double = 0.0,
    val paidAt: String = "",
    val errorMessage: String = ""
)
