package com.example.data.model

import com.google.firebase.Timestamp

/**
 * Order payment information model.
 * 
 * Stores payment details linked to an order.
 * 
 * @property orderId The order this payment is for
 * @property paymentTransactionId Reference to the payment transaction document
 * @property paymentMethod The payment method used (ECOCASH, ONEMONEY, etc.)
 * @property paymentRef Paynow reference or mobile money reference
 * @property amount The payment amount
 * @property status Payment status (PENDING, PROCESSING, COMPLETED, FAILED)
 * @property paidAt Timestamp when payment was confirmed
 */
data class OrderPaymentInfo(
    val orderId: String = "",
    val paymentTransactionId: String = "",
    val paymentMethod: String = "",
    val paymentRef: String = "",
    val amount: Double = 0.0,
    val status: String = "PENDING",
    val paidAt: Timestamp? = null,
    val createdAt: Timestamp? = null
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_PROCESSING = "PROCESSING"
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_FAILED = "FAILED"
    }
    
    /**
     * Check if payment is complete.
     */
    fun isPaid(): Boolean = status == STATUS_COMPLETED
}
