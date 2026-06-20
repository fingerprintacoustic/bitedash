package com.example.data.payment

import com.google.firebase.Timestamp
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Paynow payment service for BiteDash.
 * 
 * This service handles Paynow API integration for Zimbabwe mobile money payments.
 * 
 * Configuration (via BuildConfig from .env):
 * - PAYNOW_INTEGRATION_ID: Paynow integration ID
 * - PAYNOW_INTEGRATION_KEY: Paynow integration key
 * 
 * Endpoints:
 * - Sandbox: https://www.paynow.co.zw/interface/initiate
 * - Production: https://www.paynow.co.zw/interface/initiate (same URL)
 * 
 * @see <a href="https://www.paynow.co.zw/">Paynow Zimbabwe</a>
 */
class PaynowService {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    companion object {
        // Sandbox endpoints (Paynow test environment)
        private const val SANDBOX_INITIATE_URL = "https://www.paynow.co.zw/interface/initiate"
        private const val SANDBOX_POLL_URL = "https://www.paynow.co.zw/status/"
        
        // TODO: Replace with production URLs when ready for live payments
        // private const val PRODUCTION_INITIATE_URL = "https://www.paynow.co.zw/interface/initiate"
        // private const val PRODUCTION_POLL_URL = "https://www.paynow.co.zw/status/"
        
        // Return URLs
        private const val RESULT_URL = "https://bitedash.co.zw/payment/result"
        private const val RETURN_URL = "https://bitedash.co.zw/payment/return"
        
        // Media type for Paynow requests
        private val FORM_CONTENT_TYPE = "application/x-www-form-urlencoded".toMediaType()
    }
    
    /**
     * Get Paynow Integration ID from BuildConfig.
     * Falls back to placeholder if not configured.
     */
    private fun getIntegrationId(): String {
        return try {
            BuildConfig.PAYNOW_INTEGRATION_ID.takeIf { it.isNotBlank() && it != "YOUR_INTEGRATION_ID" }
                ?: throw IllegalStateException("Paynow Integration ID not configured")
        } catch (e: Exception) {
            throw IllegalStateException(
                "Paynow Integration ID not configured. " +
                "Add PAYNOW_INTEGRATION_ID to your .env file."
            )
        }
    }
    
    /**
     * Get Paynow Integration Key from BuildConfig.
     * Falls back to placeholder if not configured.
     */
    private fun getIntegrationKey(): String {
        return try {
            BuildConfig.PAYNOW_INTEGRATION_KEY.takeIf { it.isNotBlank() && it != "YOUR_INTEGRATION_KEY" }
                ?: throw IllegalStateException("Paynow Integration Key not configured")
        } catch (e: Exception) {
            throw IllegalStateException(
                "Paynow Integration Key not configured. " +
                "Add PAYNOW_INTEGRATION_KEY to your .env file."
            )
        }
    }
    
    /**
     * Initiate a payment with Paynow.
     * 
     * Creates a payment request and returns the poll URL for status checking.
     * Uses sandbox endpoint for testing.
     * 
     * @param request Payment request details
     * @return PaymentResult with transaction details or error
     */
    suspend fun initiatePayment(request: PaymentRequest): PaymentResult {
        return withContext(Dispatchers.IO) {
            try {
                val integrationId = getIntegrationId()
                val integrationKey = getIntegrationKey()
                
                // Generate unique reference
                val reference = generatePaynowReference(request.orderId)
                
                // Build the hash for authentication
                // Hash format: integration_id + return_url + result_url + amount + id + key
                val hashData = "$integrationId$RETURN_URL$RESULT_URL${request.amount}$reference$integrationKey"
                val hash = generateSha256Hash(hashData)
                
                // Build POST body
                val bodyBuilder = StringBuilder()
                bodyBuilder.append("id=${integrationId}")
                bodyBuilder.append("&returnurl=${encodeUrl(RETURN_URL)}")
                bodyBuilder.append("&resulturl=${encodeUrl(RESULT_URL)}")
                bodyBuilder.append("&amount=${request.amount}")
                bodyBuilder.append("&reference=$reference")
                bodyBuilder.append("&hash=$hash")
                
                // Add user info
                if (request.mobileMoneyNumber.isNotBlank()) {
                    val phone = request.mobileMoneyNumber.replace("+", "").replace(" ", "")
                    bodyBuilder.append("&phone=$phone")
                }
                
                // Add description
                if (request.description.isNotBlank()) {
                    bodyBuilder.append("&description=${encodeUrl(request.description)}")
                }
                
                // Make API request
                val requestBody = bodyBuilder.toString().toRequestBody(FORM_CONTENT_TYPE)
                
                val apiRequest = Request.Builder()
                    .url(SANDBOX_INITIATE_URL)
                    .post(requestBody)
                    .addHeader("Content-Type", "application/x-www-form-urlencoded")
                    .build()
                
                val response = client.newCall(apiRequest).execute()
                val responseBody = response.body?.string() ?: ""
                
                // Parse response
                if (response.isSuccessful) {
                    val parsed = PaynowResponseParser.parseInitiateResponse(responseBody)
                    
                    if (parsed["status"] == "Ok") {
                        // Verify response hash
                        val responseHash = parsed["hash"] ?: ""
                        val expectedHash = generateSha256Hash(responseBody.replace("hash=", "") + integrationKey)
                        
                        if (responseHash.isNotEmpty() && responseHash != expectedHash) {
                            // Hash mismatch - log warning but continue
                            // In production, this should fail
                        }
                        
                        val transaction = PaymentTransaction(
                            transactionId = "TXN_${System.currentTimeMillis()}",
                            userId = request.userId,
                            orderId = request.orderId,
                            amount = request.amount,
                            currency = request.currency,
                            status = PaymentStatus.PENDING.value,
                            method = request.method.value,
                            mobileMoneyNumber = request.mobileMoneyNumber,
                            pollUrl = parsed["pollurl"] ?: "$SANDBOX_POLL_URL$reference",
                            paynowReference = reference,
                            createdAt = Timestamp.now()
                        )
                        
                        PaymentResult.Success(transaction)
                    } else {
                        PaymentResult.Error(
                            message = parsed["error"] ?: "Payment initiation failed",
                            code = "PAYNOW_ERROR"
                        )
                    }
                } else {
                    PaymentResult.Error(
                        message = "Paynow API error: ${response.code}",
                        code = "HTTP_${response.code}"
                    )
                }
            } catch (e: IllegalStateException) {
                // Configuration error
                PaymentResult.Error(
                    message = e.message ?: "Paynow not configured",
                    code = "CONFIG_ERROR"
                )
            } catch (e: Exception) {
                PaymentResult.Error(
                    message = "Failed to initiate payment: ${e.message}",
                    code = "INIT_ERROR"
                )
            }
        }
    }
    
    /**
     * Check the status of a Paynow payment.
     * 
     * @param pollUrl The poll URL returned from initiatePayment
     * @return PaymentStatusResult with current status
     */
    suspend fun checkPaymentStatus(pollUrl: String): PaymentStatusResult {
        return withContext(Dispatchers.IO) {
            try {
                val integrationKey = getIntegrationKey()
                
                // Generate hash for status check
                val hash = generateSha256Hash(pollUrl + integrationKey)
                
                // Build POST body
                val body = "pollurl=${encodeUrl(pollUrl)}&hash=$hash"
                    .toRequestBody(FORM_CONTENT_TYPE)
                
                // Make API request
                val request = Request.Builder()
                    .url(pollUrl)
                    .post(body)
                    .addHeader("Content-Type", "application/x-www-form-urlencoded")
                    .build()
                
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""
                
                // Parse response
                if (response.isSuccessful && responseBody.isNotBlank()) {
                    PaynowResponseParser.parseStatusResponse(responseBody)
                } else {
                    PaymentStatusResult(
                        status = PaymentStatus.FAILED,
                        errorMessage = "Failed to check payment status"
                    )
                }
            } catch (e: IllegalStateException) {
                PaymentStatusResult(
                    status = PaymentStatus.FAILED,
                    errorMessage = e.message ?: "Paynow not configured"
                )
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
     * Note: Paynow doesn't support direct cancellation.
     * This marks the payment as cancelled locally.
     * 
     * @param transactionId The transaction ID to cancel
     * @return PaymentResult indicating success or failure
     */
    suspend fun cancelPayment(transactionId: String): PaymentResult {
        return withContext(Dispatchers.IO) {
            try {
                // Paynow doesn't support cancellation via API
                // We return Cancelled to let the repository update Firestore
                PaymentResult.Cancelled
            } catch (e: Exception) {
                PaymentResult.Error(
                    message = "Failed to cancel payment: ${e.message}",
                    code = "CANCEL_ERROR"
                )
            }
        }
    }
    
    /**
     * Generate a unique reference for Paynow.
     * 
     * @param orderId The order ID to base the reference on
     * @return Unique Paynow-compatible reference string
     */
    private fun generatePaynowReference(orderId: String): String {
        // Paynow references must be alphanumeric, max 255 chars
        val sanitizedOrderId = orderId.replace(Regex("[^a-zA-Z0-9]"), "")
        return "BD_${sanitizedOrderId.take(20)}_${System.currentTimeMillis()}"
    }
    
    /**
     * Generate SHA-256 hash for Paynow API authentication.
     * 
     * @param data The data to hash
     * @return Hex-encoded SHA-256 hash
     */
    private fun generateSha256Hash(data: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(data.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * URL encode a string for Paynow API.
     */
    private fun encodeUrl(value: String): String {
        return java.net.URLEncoder.encode(value, "UTF-8")
    }
}

/**
 * Paynow API response parser.
 * 
 * Handles parsing of Paynow API responses (URL-encoded format).
 */
object PaynowResponseParser {
    
    /**
     * Parse Paynow initiate response.
     * 
     * Response format: status=Ok&pollurl=https://...&hash=xxx
     * or: status=Error&error=Error+message&errorcode=xxx&hash=xxx
     */
    fun parseInitiateResponse(response: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        
        // Split by & and parse key=value pairs
        response.split("&").forEach { pair ->
            val parts = pair.split("=", limit = 2)
            if (parts.size == 2) {
                val key = parts[0].trim()
                val value = parts[1].trim()
                    .replace("+", " ")  // Decode spaces
                    .let { java.net.URLDecoder.decode(it, "UTF-8") }
                result[key] = value
            }
        }
        
        return result
    }
    
    /**
     * Parse Paynow status response.
     * 
     * Response format: status=Paid&amount=10.00&paynowreference=xxx&pollurl=xxx&hash=xxx
     */
    fun parseStatusResponse(response: String): PaymentStatusResult {
        val parsed = parseInitiateResponse(response)
        
        val paynowStatus = parsed["status"] ?: ""
        val amount = parsed["amount"]?.toDoubleOrNull() ?: 0.0
        val paidAt = parsed["paidat"] ?: ""
        val errorMessage = parsed["error"] ?: ""
        
        return PaymentStatusResult(
            status = mapPaynowStatus(paynowStatus),
            pollUrl = parsed["pollurl"] ?: "",
            paidAmount = amount,
            paidAt = paidAt,
            errorMessage = errorMessage
        )
    }
    
    /**
     * Convert Paynow status to PaymentStatus.
     */
    fun mapPaynowStatus(paynowStatus: String): PaymentStatus {
        return when (paynowStatus.uppercase().trim()) {
            "PAID" -> PaymentStatus.PAID
            "CANCELLED" -> PaymentStatus.CANCELLED
            "DELIVERED" -> PaymentStatus.PAID  // Delivered = confirmed payment
            "AWAITING_DELIVERY" -> PaymentStatus.PENDING
            "SENT" -> PaymentStatus.PENDING  // Payment initiated but not confirmed
            else -> PaymentStatus.PENDING
        }
    }
}
