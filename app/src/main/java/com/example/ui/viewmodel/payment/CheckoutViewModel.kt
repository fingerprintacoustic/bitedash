package com.example.ui.viewmodel.payment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.payment.PaymentMethod as DataPaymentMethod
import com.example.data.payment.PaymentRequest
import com.example.data.payment.PaymentResult
import com.example.data.payment.PaymentStatus
import com.example.data.payment.PaymentStatusResult
import com.example.data.repository.PaymentRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Checkout ViewModel for BiteDash payment flow.
 * 
 * Handles:
 * - Payment method selection
 * - Payment validation
 * - Payment initiation via Paynow
 * - Payment status polling
 * - UI state management
 * 
 * @property paymentRepository Repository for payment operations
 */
class CheckoutViewModel(
    private val paymentRepository: PaymentRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(PaymentUiState())
    val uiState: StateFlow<PaymentUiState> = _uiState.asStateFlow()
    
    private var pollingJob: Job? = null
    
    companion object {
        // Validation constants
        const val MIN_AMOUNT = 0.01
        const val MAX_AMOUNT = 100000.0
        const val POLLING_INTERVAL_MS = 5000L
        const val MAX_POLLING_ATTEMPTS = 60 // 5 minutes max
        
        // Phone number patterns
        private val ZIMBABWE_PHONE_PATTERNS = listOf(
            Regex("^\\+263[0-9]{9}$"),      // +263771234567
            Regex("^263[0-9]{9}$"),          // 263771234567
            Regex("^0[0-9]{9}$"),           // 0771234567
            Regex("^0[0-9]{8}$")            // 077123456 (8 digits after 0)
        )
    }
    
    // ==================== PAYMENT METHOD SELECTION ====================
    
    /**
     * Select a payment method.
     */
    fun selectPaymentMethod(method: PaymentMethod) {
        _uiState.update { state ->
            state.copy(
                selectedMethod = method,
                validation = PaymentValidation.Valid
            )
        }
    }
    
    // ==================== VALIDATION ====================
    
    /**
     * Validate payment request.
     * 
     * @param amount Order amount
     * @param userId User ID
     * @param orderId Order ID
     * @param phoneNumber Mobile money number (if required)
     * @return Validation result
     */
    fun validatePayment(
        amount: Double,
        userId: String,
        orderId: String,
        phoneNumber: String = ""
    ): PaymentValidation {
        val errors = mutableListOf<String>()
        
        // Validate amount
        when {
            amount <= 0 -> errors.add("Amount must be greater than zero")
            amount < MIN_AMOUNT -> errors.add("Minimum amount is $MIN_AMOUNT")
            amount > MAX_AMOUNT -> errors.add("Maximum amount is $MAX_AMOUNT")
        }
        
        // Validate user ID
        if (userId.isBlank()) {
            errors.add("User ID is required")
        }
        
        // Validate order ID
        if (orderId.isBlank()) {
            errors.add("Order ID is required")
        }
        
        // Validate phone number for mobile money methods
        if (_uiState.value.selectedMethod.requiresPhoneNumber) {
            if (phoneNumber.isBlank()) {
                errors.add("Phone number is required for ${_uiState.value.selectedMethod.displayName}")
            } else if (!isValidZimbabwePhoneNumber(phoneNumber)) {
                errors.add("Invalid phone number format. Use format: 0771234567 or +263771234567")
            }
        }
        
        val validation = if (errors.isEmpty()) {
            PaymentValidation.Valid
        } else {
            PaymentValidation.Invalid(errors)
        }
        
        _uiState.update { it.copy(validation = validation) }
        return validation
    }
    
    /**
     * Check if a phone number is valid for Zimbabwe.
     */
    fun isValidZimbabwePhoneNumber(phone: String): Boolean {
        if (phone.isBlank()) return false
        
        // Normalize phone number
        val normalized = phone.replace(" ", "").replace("-", "")
        
        return ZIMBABWE_PHONE_PATTERNS.any { it.matches(normalized) }
    }
    
    /**
     * Format phone number to Zimbabwe standard.
     */
    fun formatPhoneNumber(phone: String): String {
        val digits = phone.filter { it.isDigit() }
        
        return when {
            digits.length == 9 && digits.startsWith("0") -> "+263$digits"
            digits.length == 10 && digits.startsWith("26") -> "+$digits"
            digits.length == 12 && digits.startsWith("263") -> "+$digits"
            else -> phone
        }
    }
    
    // ==================== PAYMENT OPERATIONS ====================
    
    /**
     * Initiate a payment for an order.
     * 
     * @param amount Order amount
     * @param userId User ID
     * @param orderId Order ID
     * @param phoneNumber Mobile money number
     * @param description Payment description
     */
    fun initiatePayment(
        amount: Double,
        userId: String,
        orderId: String,
        phoneNumber: String = "",
        description: String = ""
    ) {
        // Validate first
        val validation = validatePayment(amount, userId, orderId, phoneNumber)
        if (validation is PaymentValidation.Invalid) {
            _uiState.update {
                it.copy(
                    paymentState = PaymentState.Failed(
                        errorCode = "VALIDATION_ERROR",
                        errorMessage = validation.errors.joinToString(", ")
                    )
                )
            }
            return
        }
        
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    paymentState = PaymentState.CreatingTransaction,
                    isLoading = true,
                    errorMessage = null
                )
            }
            
            try {
                // Convert UI payment method to data layer payment method
                val dataMethod = _uiState.value.selectedMethod.toDataPaymentMethod()
                
                // Format phone number if provided
                val formattedPhone = if (phoneNumber.isNotBlank()) {
                    formatPhoneNumber(phoneNumber)
                } else ""
                
                // Create payment request
                val request = PaymentRequest(
                    orderId = orderId,
                    userId = userId,
                    amount = amount,
                    method = dataMethod,
                    mobileMoneyNumber = formattedPhone,
                    description = description.ifBlank { "BiteDash Order $orderId" }
                )
                
                // Initiate payment via repository
                val result = paymentRepository.initiatePayment(request)
                
                when (result) {
                    is PaymentResult.Success -> {
                        _uiState.update {
                            it.copy(
                                paymentState = PaymentState.WaitingForPayment,
                                currentTransactionId = result.transaction.transactionId,
                                pollUrl = result.transaction.pollUrl,
                                isLoading = false
                            )
                        }
                        
                        // Start polling for payment status
                        startPolling(result.transaction.transactionId)
                    }
                    is PaymentResult.Error -> {
                        _uiState.update {
                            it.copy(
                                paymentState = PaymentState.Failed(
                                    errorCode = result.code,
                                    errorMessage = result.message
                                ),
                                isLoading = false
                            )
                        }
                    }
                    is PaymentResult.Cancelled -> {
                        _uiState.update {
                            it.copy(
                                paymentState = PaymentState.Cancelled,
                                isLoading = false
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        paymentState = PaymentState.Failed(
                            errorCode = "UNEXPECTED_ERROR",
                            errorMessage = e.message ?: "An unexpected error occurred"
                        ),
                        isLoading = false
                    )
                }
            }
        }
    }
    
    /**
     * Cancel the current payment.
     */
    fun cancelPayment() {
        pollingJob?.cancel()
        
        val transactionId = _uiState.value.currentTransactionId
        if (transactionId != null) {
            viewModelScope.launch {
                paymentRepository.cancelPayment(transactionId)
            }
        }
        
        _uiState.update {
            it.copy(
                paymentState = PaymentState.Cancelled,
                currentTransactionId = null,
                pollUrl = null,
                isLoading = false
            )
        }
    }
    
    /**
     * Reset payment state to idle.
     */
    fun resetPayment() {
        pollingJob?.cancel()
        
        _uiState.update {
            PaymentUiState(selectedMethod = it.selectedMethod)
        }
    }
    
    // ==================== POLLING ====================
    
    /**
     * Start polling for payment status.
     */
    private fun startPolling(transactionId: String) {
        pollingJob?.cancel()
        
        pollingJob = viewModelScope.launch {
            var attempts = 0
            
            while (attempts < MAX_POLLING_ATTEMPTS) {
                delay(POLLING_INTERVAL_MS)
                
                val statusResult = paymentRepository.checkPaymentStatus(transactionId)
                
                when (statusResult.status) {
                    PaymentStatus.PAID -> {
                        _uiState.update {
                            it.copy(
                                paymentState = PaymentState.Paid(
                                    transactionId = transactionId,
                                    amount = 0.0, // Will be updated from transaction
                                    method = it.selectedMethod
                                ),
                                isLoading = false
                            )
                        }
                        return@launch
                    }
                    PaymentStatus.FAILED -> {
                        _uiState.update {
                            it.copy(
                                paymentState = PaymentState.Failed(
                                    errorCode = "PAYNOW_FAILED",
                                    errorMessage = statusResult.errorMessage.ifBlank { "Payment failed" }
                                ),
                                isLoading = false
                            )
                        }
                        return@launch
                    }
                    PaymentStatus.CANCELLED -> {
                        _uiState.update {
                            it.copy(
                                paymentState = PaymentState.Cancelled,
                                isLoading = false
                            )
                        }
                        return@launch
                    }
                    PaymentStatus.PENDING -> {
                        // Continue polling
                        attempts++
                    }
                }
            }
            
            // Max attempts reached
            _uiState.update {
                it.copy(
                    paymentState = PaymentState.Failed(
                        errorCode = "TIMEOUT",
                        errorMessage = "Payment verification timed out. Please check your payment app."
                    ),
                    isLoading = false
                )
            }
        }
    }
    
    // ==================== CLEANUP ====================
    
    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }
}

/**
 * Extension function to convert UI PaymentMethod to data PaymentMethod.
 */
fun PaymentMethod.toDataPaymentMethod(): DataPaymentMethod {
    return when (this) {
        PaymentMethod.PAYNOW -> DataPaymentMethod.ECO_CASH  // Paynow uses EcoCash as default mobile money
        PaymentMethod.ECOCASH -> DataPaymentMethod.ECO_CASH
        PaymentMethod.ONEMONEY -> DataPaymentMethod.ONE_MONEY
        PaymentMethod.INNBUCKS -> DataPaymentMethod.INNBUCKS
        PaymentMethod.CASH -> DataPaymentMethod.CASH_ON_DELIVERY
    }
}
