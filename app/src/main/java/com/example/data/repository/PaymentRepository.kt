package com.example.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.snapshots
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.example.data.payment.PaymentMethod
import com.example.data.payment.PaymentRequest
import com.example.data.payment.PaymentResult
import com.example.data.payment.PaymentStatus
import com.example.data.payment.PaymentStatusResult
import com.example.data.payment.PaymentTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Payment repository for BiteDash.
 *
 * The actual Paynow API calls (initiate + status polling) happen server-side
 * in Cloud Functions (see /functions), which hold the Paynow Integration
 * Key and verify every response's hash before trusting it. This repository
 * only ever talks to those callable functions plus read-only Firestore
 * queries — it never has enough privilege to mark a payment PAID itself
 * (firestore.rules restricts that write to Cloud Functions/admin), so a
 * compromised or tampered client can't self-report a fake payment.
 */
class PaymentRepository(
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()
) {
    private val db = FirebaseFirestore.getInstance()

    companion object {
        private const val COLLECTION_PAYMENTS = "payments"

        @Volatile
        private var instance: PaymentRepository? = null

        fun getInstance(): PaymentRepository {
            return instance ?: synchronized(this) {
                instance ?: PaymentRepository().also { instance = it }
            }
        }
    }

    // ==================== PAYMENT OPERATIONS ====================

    /**
     * Initiate a payment for an order via the initiatePaynowPayment Cloud
     * Function. Cash on Delivery never touches Paynow.
     *
     * @param request Payment request details. The order's amount is looked
     * up server-side from Firestore — request.amount is not trusted.
     * @return PaymentResult with the transaction (including the browserUrl
     * to open) or an error.
     */
    suspend fun initiatePayment(request: PaymentRequest): PaymentResult {
        if (request.method == PaymentMethod.CASH_ON_DELIVERY) {
            return PaymentResult.Error(
                message = "Cash on Delivery does not go through Paynow",
                code = "NOT_APPLICABLE"
            )
        }

        return withContext(Dispatchers.IO) {
            try {
                val data = hashMapOf(
                    "orderId" to request.orderId,
                    "method" to request.method.value,
                    "mobileMoneyNumber" to request.mobileMoneyNumber
                )
                val response = functions.getHttpsCallable("initiatePaynowPayment")
                    .call(data)
                    .await()
                    .data as? Map<*, *>
                    ?: return@withContext PaymentResult.Error("Unexpected response from server", "BAD_RESPONSE")

                val transactionId = response["transactionId"] as? String
                val browserUrl = response["browserUrl"] as? String
                val pollUrl = response["pollUrl"] as? String
                if (transactionId.isNullOrBlank() || browserUrl.isNullOrBlank() || pollUrl.isNullOrBlank()) {
                    return@withContext PaymentResult.Error("Incomplete response from server", "BAD_RESPONSE")
                }

                PaymentResult.Success(
                    PaymentTransaction(
                        transactionId = transactionId,
                        userId = request.userId,
                        orderId = request.orderId,
                        amount = request.amount,
                        currency = request.currency,
                        status = PaymentStatus.PENDING.value,
                        method = request.method.value,
                        pollUrl = pollUrl,
                        browserUrl = browserUrl,
                        mobileMoneyNumber = request.mobileMoneyNumber
                    )
                )
            } catch (e: FirebaseFunctionsException) {
                PaymentResult.Error(
                    message = e.message ?: "Failed to initiate payment",
                    code = e.code.name
                )
            } catch (e: Exception) {
                PaymentResult.Error(
                    message = "Failed to initiate payment: ${e.message}",
                    code = "PAYMENT_INIT_ERROR"
                )
            }
        }
    }

    /**
     * Ask the checkPaynowPaymentStatus Cloud Function to poll Paynow and
     * apply the verified result. The function itself updates Firestore
     * (payments + orders) once it confirms the status with Paynow — this
     * call just relays the outcome back to the UI.
     *
     * @param transactionId The transaction ID to check
     * @return PaymentStatusResult with current status
     */
    suspend fun checkPaymentStatus(transactionId: String): PaymentStatusResult {
        return withContext(Dispatchers.IO) {
            try {
                val response = functions.getHttpsCallable("checkPaynowPaymentStatus")
                    .call(hashMapOf("transactionId" to transactionId))
                    .await()
                    .data as? Map<*, *>
                    ?: emptyMap<String, Any?>()

                val statusStr = response["status"] as? String ?: "PENDING"
                val errorMessage = response["errorMessage"] as? String ?: ""
                PaymentStatusResult(
                    status = PaymentStatus.fromString(statusStr),
                    errorMessage = errorMessage
                )
            } catch (e: Exception) {
                // Treat as still-pending rather than a hard failure — a
                // transient network/function error here shouldn't end the
                // customer's payment early; the polling loop's own attempt
                // cap is what eventually times it out.
                PaymentStatusResult(
                    status = PaymentStatus.PENDING,
                    errorMessage = "Couldn't reach the server to check payment status: ${e.message}"
                )
            }
        }
    }

    /**
     * "Cancel" a pending payment from the customer's side.
     *
     * Paynow has no cancellation API, and the client has no write access to
     * the payment/order documents (see firestore.rules) — so this is purely
     * a local UI signal. The payment record itself just stays PENDING until
     * it either completes via a poll/webhook or is cleaned up.
     */
    fun cancelPayment(): PaymentResult = PaymentResult.Cancelled

    // ==================== FIRESTORE READ OPERATIONS ====================

    /**
     * Get a payment by transaction ID.
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
     */
    fun getPaymentsByOrderFlow(orderId: String): Flow<List<PaymentTransaction>> {
        return db.collection(COLLECTION_PAYMENTS)
            .whereEqualTo("orderId", orderId)
            .snapshots()
            .map { snapshot -> snapshot.toObjects(PaymentTransaction::class.java) }
    }
}
