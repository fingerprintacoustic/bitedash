package com.example.ui.viewmodel.payment

/**
 * Payment UI state for BiteDash checkout flow.
 * 
 * Represents the current state of a payment transaction in the UI layer.
 */
sealed class PaymentState {
    /**
     * Initial state - no payment initiated.
     */
    data object Idle : PaymentState()
    
    /**
     * Creating payment transaction - communicating with Paynow.
     */
    data object CreatingTransaction : PaymentState()
    
    /**
     * Waiting for payment confirmation - user needs to complete payment on their phone.
     */
    data object WaitingForPayment : PaymentState()
    
    /**
     * Payment completed successfully.
     */
    data class Paid(
        val transactionId: String,
        val amount: Double,
        val method: PaymentMethod
    ) : PaymentState()
    
    /**
     * Payment failed.
     */
    data class Failed(
        val errorCode: String?,
        val errorMessage: String
    ) : PaymentState()
    
    /**
     * Payment cancelled by user.
     */
    data object Cancelled : PaymentState()
}

/**
 * Payment validation state.
 */
sealed class PaymentValidation {
    data object Valid : PaymentValidation()
    data class Invalid(val errors: List<String>) : PaymentValidation()
}

/**
 * UI model for displaying payment options.
 */
data class PaymentUiState(
    val orderId: String? = null,
    val paymentState: PaymentState = PaymentState.Idle,
    val selectedMethod: PaymentMethod = PaymentMethod.PAYNOW,
    val validation: PaymentValidation = PaymentValidation.Valid,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val currentTransactionId: String? = null,
    val pollUrl: String? = null
)
