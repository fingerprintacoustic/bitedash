package com.example.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.snapshots
import com.example.data.payment.PaymentMethod
import com.example.data.payment.PaymentRequest
import com.example.data.payment.PaymentResult
import com.example.data.payment.PaymentStatus
import com.example.data.payment.PaymentStatusResult
import com.example.data.payment.PaymentTransaction
import com.example.data.payment.PaynowService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Payment repository for BiteDash.
 * 
 * Handles all payment-related operations including:
 * - Initiating payments via Paynow
 * - Checking payment status
 * - Managing payment transactions in Firestore
 * 
 * Architecture:
 * - PaynowService: Handles Paynow API communication
 * - Firestore: Stores payment transactions
 * 
 * @property firestoreService Firestore service instance
 * @property paynowService Paynow service instance
 */
class PaymentRepository(
    private val firestoreService: com.example.data.firebase.FirestoreService,
    private val paynowService: PaynowService = PaynowService()
) {
    private val db = FirebaseFirestore.getInstance()

    companion object {
        private const val COLLECTION_PAYMENTS = "payments"
        
        // Singleton instance
        @Volatile
        private var instance: PaymentRepository? = null

        fun getInstance(firestoreService: com.example.data.firebase.FirestoreService): PaymentRepository {
            return instance ?: synchronized(this) {
                instance ?: PaymentRepository(firestoreService).also { instance = it }
            }
        }
    }

    // ==================== PAYMENT OPERATIONS ====================

    /**
     * Initiate a payment for an order.
     * 
     * @param request Payment request details
     * @return PaymentResult with transaction or error
     */
    suspend fun initiatePayment(request: PaymentRequest): PaymentResult {
        return withContext(Dispatchers.IO) {
            try {
                // Call Paynow service to initiate payment
                val paynowResult = paynowService.initiatePayment(request)
                
                when (paynowResult) {
                    is PaymentResult.Success -> {
                        // Save transaction to Firestore
                        val transaction = paynowResult.transaction
                        val docRef = db.collection(COLLECTION_PAYMENTS).add(transaction).await()
                        
                        // Return success with updated transaction ID
                        PaymentResult.Success(transaction.copy(transactionId = docRef.id))
                    }
                    is PaymentResult.Error -> paynowResult
                    is PaymentResult.Cancelled -> paynowResult
                }
            } catch (e: Exception) {
                PaymentResult.Error(
                    message = "Failed to initiate payment: ${e.message}",
                    code = "PAYMENT_INIT_ERROR"
                )
            }
        }
    }

    /**
     * Check payment status from Paynow and update Firestore.
     * 
     * @param transactionId The transaction ID to check
     * @return PaymentStatusResult with current status
     */
    suspend fun checkPaymentStatus(transactionId: String): PaymentStatusResult {
        return withContext(Dispatchers.IO) {
            try {
                // Get transaction from Firestore
                val transaction = getPayment(transactionId)
                    ?: return@withContext PaymentStatusResult(
                        status = PaymentStatus.FAILED,
                        errorMessage = "Transaction not found"
                    )
                
                // Check status with Paynow
                val statusResult = paynowService.checkPaymentStatus(transaction.pollUrl)
                
                // Update Firestore if status changed
                if (statusResult.status != PaymentStatus.PENDING) {
                    updatePaymentStatus(
                        transactionId = transactionId,
                        status = statusResult.status
                    )
                }
                
                statusResult
            } catch (e: Exception) {
                PaymentStatusResult(
                    status = PaymentStatus.FAILED,
                    errorMessage = "Failed to check payment status: ${e.message}"
                )
            }
        }
    }

    /**
     * Cancel a pending payment.
     * 
     * @param transactionId The transaction ID to cancel
     * @return PaymentResult indicating success or failure
     */
    suspend fun cancelPayment(transactionId: String): PaymentResult {
        return withContext(Dispatchers.IO) {
            try {
                // Get current transaction
                val transaction = getPayment(transactionId)
                    ?: return@withContext PaymentResult.Error(
                        message = "Transaction not found",
                        code = "NOT_FOUND"
                    )
                
                // Only allow cancellation of pending payments
                if (transaction.status != PaymentStatus.PENDING.value) {
                    return@withContext PaymentResult.Error(
                        message = "Cannot cancel payment with status: ${transaction.status}",
                        code = "INVALID_STATUS"
                    )
                }
                
                // Call Paynow service to cancel
                val cancelResult = paynowService.cancelPayment(transactionId)
                
                // Update Firestore with cancelled status
                if (cancelResult is PaymentResult.Cancelled || cancelResult is PaymentResult.Success) {
                    updatePaymentStatus(
                        transactionId = transactionId,
                        status = PaymentStatus.CANCELLED
                    )
                }
                
                cancelResult
            } catch (e: Exception) {
                PaymentResult.Error(
                    message = "Failed to cancel payment: ${e.message}",
                    code = "CANCEL_ERROR"
                )
            }
        }
    }

    // ==================== FIRESTORE OPERATIONS ====================

    /**
     * Get a payment by transaction ID.
     * 
     * @param transactionId The transaction ID
     * @return PaymentTransaction or null if not found
     */
    suspend fun getPayment(transactionId: String): PaymentTransaction? {
        return try {
            db.collection(COLLECTION_PAYMENTS)
                .document(transactionId)
                .get()
                .await()
                .toObject(PaymentTransaction::class.java)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get all payments for a user.
     * 
     * @param userId The user ID
     * @return Flow of payments
     */
    fun getPaymentsFlow(userId: String): Flow<List<PaymentTransaction>> {
        return db.collection(COLLECTION_PAYMENTS)
            .whereEqualTo("userId", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot -> snapshot.toObjects(PaymentTransaction::class.java) }
    }

    /**
     * Get all payments for an order.
     * 
     * @param orderId The order ID
     * @return Flow of payments
     */
    fun getPaymentsByOrderFlow(orderId: String): Flow<List<PaymentTransaction>> {
        return db.collection(COLLECTION_PAYMENTS)
            .whereEqualTo("orderId", orderId)
            .snapshots()
            .map { snapshot -> snapshot.toObjects(PaymentTransaction::class.java) }
    }

    /**
     * Get pending payments for a user.
     * 
     * @param userId The user ID
     * @return Flow of pending payments
     */
    fun getPendingPaymentsFlow(userId: String): Flow<List<PaymentTransaction>> {
        return db.collection(COLLECTION_PAYMENTS)
            .whereEqualTo("userId", userId)
            .whereEqualTo("status", PaymentStatus.PENDING.value)
            .snapshots()
            .map { snapshot -> snapshot.toObjects(PaymentTransaction::class.java) }
    }

    /**
     * Update payment status in Firestore.
     * 
     * @param transactionId The transaction ID
     * @param status The new status
     * @param errorMessage Optional error message for failed payments
     * @return true if successful
     */
    suspend fun updatePaymentStatus(
        transactionId: String,
        status: PaymentStatus,
        errorMessage: String = ""
    ): Boolean {
        return try {
            val updates = mutableMapOf<String, Any>(
                "status" to status.value,
                "updatedAt" to Timestamp.now()
            )
            
            if (errorMessage.isNotEmpty()) {
                updates["errorMessage"] = errorMessage
            }
            
            if (status == PaymentStatus.PAID) {
                updates["completedAt"] = Timestamp.now()
            }
            
            db.collection(COLLECTION_PAYMENTS)
                .document(transactionId)
                .update(updates)
                .await()
            
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Save a payment transaction to Firestore.
     * 
     * @param transaction The transaction to save
     * @return Transaction ID if successful
     */
    suspend fun savePayment(transaction: PaymentTransaction): String? {
        return try {
            val docRef = db.collection(COLLECTION_PAYMENTS).add(transaction).await()
            docRef.id
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Delete a payment transaction (admin only).
     * 
     * @param transactionId The transaction ID
     * @return true if successful
     */
    suspend fun deletePayment(transactionId: String): Boolean {
        return try {
            db.collection(COLLECTION_PAYMENTS)
                .document(transactionId)
                .delete()
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get payment statistics for a user.
     * 
     * @param userId The user ID
     * @return Map of status to count
     */
    suspend fun getPaymentStats(userId: String): Map<PaymentStatus, Int> {
        return try {
            val payments = getPaymentsFlow(userId)
            var stats = mapOf<PaymentStatus, Int>()
            
            payments.collect { paymentList ->
                stats = paymentList.groupBy { PaymentStatus.fromString(it.status) }
                    .mapValues { it.value.size }
            }
            
            stats
        } catch (e: Exception) {
            emptyMap()
        }
    }

    // ==================== ORDER PAYMENT OPERATIONS ====================

    /**
     * Mark an order as paid after successful payment.
     * 
     * Updates both the payment document and the order document.
     * Does NOT dispatch drivers or send notifications.
     * 
     * @param transactionId The payment transaction ID
     * @param orderId The order ID to mark as paid
     * @param paymentMethod The payment method used
     * @param paymentRef Paynow reference
     * @param amount The payment amount
     * @return true if both updates were successful
     */
    suspend fun markOrderAsPaid(
        transactionId: String,
        orderId: String,
        paymentMethod: String,
        paymentRef: String,
        amount: Double
    ): Boolean {
        return try {
            // 1. Update payment document status to PAID
            val paymentUpdated = updatePaymentStatus(
                transactionId = transactionId,
                status = PaymentStatus.PAID
            )
            
            // 2. Update order document payment fields
            val orderUpdated = updateOrderPaymentStatus(
                orderId = orderId,
                paymentTransactionId = transactionId,
                paymentMethod = paymentMethod,
                paymentRef = paymentRef,
                amount = amount,
                status = "PAID"
            )
            
            paymentUpdated && orderUpdated
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Update order payment status in Firestore.
     * 
     * @param orderId The order ID
     * @param paymentTransactionId The payment transaction ID
     * @param paymentMethod The payment method used
     * @param paymentRef Paynow or mobile money reference
     * @param amount The payment amount
     * @param status The payment status (PAID, FAILED, etc.)
     * @return true if successful
     */
    suspend fun updateOrderPaymentStatus(
        orderId: String,
        paymentTransactionId: String,
        paymentMethod: String,
        paymentRef: String,
        amount: Double,
        status: String
    ): Boolean {
        return try {
            val updates = mutableMapOf<String, Any>(
                "paymentStatus" to status,
                "paymentMethod" to paymentMethod,
                "paymentRef" to paymentRef,
                "updatedAt" to Timestamp.now()
            )
            
            // Set payment amount in order
            if (status == "PAID") {
                // Keep order status as PENDING_ACCEPTANCE, only payment fields change
            }
            
            db.collection("orders")
                .document(orderId)
                .update(updates)
                .await()
            
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get order by ID.
     * 
     * @param orderId The order ID
     * @return Order data as map or null if not found
     */
    suspend fun getOrder(orderId: String): Map<String, Any>? {
        return try {
            val doc = db.collection("orders")
                .document(orderId)
                .get()
                .await()
            
            if (doc.exists()) {
                doc.data
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
