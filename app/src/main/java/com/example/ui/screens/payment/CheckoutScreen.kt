package com.example.ui.screens.payment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.viewmodel.payment.CheckoutViewModel
import com.example.ui.viewmodel.payment.PaymentMethod
import com.example.ui.viewmodel.payment.PaymentState
import com.example.ui.viewmodel.payment.PaymentUiState
import com.example.ui.viewmodel.payment.PaymentValidation

/**
 * Checkout screen for payment processing.
 * 
 * This screen displays payment method selection and status.
 * Uses Paynow sandbox for payment processing.
 * 
 * @param viewModel CheckoutViewModel instance
 * @param orderId The order ID for this checkout
 * @param userId The user ID making the payment
 * @param amount The payment amount
 * @param onNavigateBack Callback to navigate back
 * @param onPaymentComplete Callback when payment is completed successfully
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutScreen(
    viewModel: CheckoutViewModel,
    orderId: String,
    userId: String,
    amount: Double,
    onNavigateBack: () -> Unit,
    onPaymentComplete: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Checkout") }
            )
        }
    ) { paddingValues ->
        CheckoutContent(
            uiState = uiState,
            orderId = orderId,
            userId = userId,
            amount = amount,
            paymentMethods = PaymentMethod.entries,
            onMethodSelected = viewModel::selectPaymentMethod,
            onInitiatePayment = { amt, uid, oid, phone, desc ->
                viewModel.initiatePayment(amt, uid, oid, phone, desc)
            },
            onCancelPayment = viewModel::cancelPayment,
            onResetPayment = viewModel::resetPayment,
            onNavigateBack = onNavigateBack,
            onPaymentComplete = onPaymentComplete,
            modifier = Modifier.padding(paddingValues)
        )
    }
}

/**
 * Checkout content composable.
 */
@Composable
fun CheckoutContent(
    uiState: PaymentUiState,
    orderId: String,
    userId: String,
    amount: Double,
    paymentMethods: List<PaymentMethod>,
    onMethodSelected: (PaymentMethod) -> Unit,
    onInitiatePayment: (Double, String, String, String, String) -> Unit,
    onCancelPayment: () -> Unit,
    onResetPayment: () -> Unit,
    onNavigateBack: () -> Unit,
    onPaymentComplete: (String) -> Unit,
    phoneNumber: String = "",
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Payment Status Card
        PaymentStatusCard(
            state = uiState.paymentState,
            modifier = Modifier.fillMaxWidth()
        )
        
        // Show payment method selector only when idle
        if (uiState.paymentState is PaymentState.Idle) {
            // Payment Method Selector
            PaymentMethodSelector(
                selectedMethod = uiState.selectedMethod,
                onMethodSelected = onMethodSelected,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Validation errors
            if (uiState.validation is PaymentValidation.Invalid) {
                val errors = (uiState.validation as PaymentValidation.Invalid).errors
                Column {
                    errors.forEach { error ->
                        Text(
                            text = "• $error",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            
            // Pay Now button
            // Connects to CheckoutViewModel.initiatePayment()
            Button(
                onClick = {
                    onInitiatePayment(
                        amount = amount,
                        userId = userId,
                        orderId = orderId,
                        phoneNumber = phoneNumber,
                        description = "BiteDash Order $orderId"
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.validation is PaymentValidation.Valid
            ) {
                Text(
                    text = "Pay Now",
                    fontWeight = FontWeight.SemiBold
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        // Loading indicator for Creating Transaction state
        if (uiState.paymentState is PaymentState.CreatingTransaction) {
            Text(
                text = "Creating your payment transaction...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
        
        // Cancel button (shown during payment process)
        if (uiState.paymentState is PaymentState.CreatingTransaction ||
            uiState.paymentState is PaymentState.WaitingForPayment) {
            OutlinedButton(
                onClick = onCancelPayment,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel Payment")
            }
        }
        
        // Retry/Reset button (shown after failure or cancellation)
        if (uiState.paymentState is PaymentState.Failed ||
            uiState.paymentState is PaymentState.Cancelled) {
            Button(
                onClick = onResetPayment,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Try Again")
            }
        }
        
        // Navigate back button
        if (uiState.paymentState is PaymentState.Idle ||
            uiState.paymentState is PaymentState.Failed ||
            uiState.paymentState is PaymentState.Cancelled) {
            OutlinedButton(
                onClick = onNavigateBack,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Back to Cart")
            }
        }
    }
}

/**
 * Preview for CheckoutScreen - Idle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutScreenIdlePreview() {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Checkout") }) }
    ) { paddingValues ->
        CheckoutContent(
            uiState = PaymentUiState(
                paymentState = PaymentState.Idle,
                selectedMethod = PaymentMethod.ECOCASH,
                validation = PaymentValidation.Valid
            ),
            orderId = "ORDER_123",
            userId = "USER_456",
            amount = 25.99,
            paymentMethods = PaymentMethod.entries,
            onMethodSelected = {},
            onInitiatePayment = { _, _, _, _, _ -> },
            onCancelPayment = {},
            onResetPayment = {},
            onNavigateBack = {},
            onPaymentComplete = {},
            modifier = Modifier.padding(paddingValues)
        )
    }
}

/**
 * Preview for CheckoutScreen - Creating Transaction.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutScreenCreatingPreview() {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Checkout") }) }
    ) { paddingValues ->
        CheckoutContent(
            uiState = PaymentUiState(
                paymentState = PaymentState.CreatingTransaction,
                selectedMethod = PaymentMethod.ECOCASH,
                currentTransactionId = "TXN_123456"
            ),
            orderId = "ORDER_123",
            userId = "USER_456",
            amount = 25.99,
            paymentMethods = PaymentMethod.entries,
            onMethodSelected = {},
            onInitiatePayment = { _, _, _, _, _ -> },
            onCancelPayment = {},
            onResetPayment = {},
            onNavigateBack = {},
            onPaymentComplete = {},
            modifier = Modifier.padding(paddingValues)
        )
    }
}

/**
 * Preview for CheckoutScreen - Waiting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutScreenWaitingPreview() {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Checkout") }) }
    ) { paddingValues ->
        CheckoutContent(
            uiState = PaymentUiState(
                paymentState = PaymentState.WaitingForPayment,
                selectedMethod = PaymentMethod.ECOCASH,
                currentTransactionId = "TXN_123456",
                pollUrl = "https://paynow.co.zw/status/123"
            ),
            orderId = "ORDER_123",
            userId = "USER_456",
            amount = 25.99,
            paymentMethods = PaymentMethod.entries,
            onMethodSelected = {},
            onInitiatePayment = { _, _, _, _, _ -> },
            onCancelPayment = {},
            onResetPayment = {},
            onNavigateBack = {},
            onPaymentComplete = {},
            modifier = Modifier.padding(paddingValues)
        )
    }
}

/**
 * Preview for CheckoutScreen - Paid.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutScreenPaidPreview() {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Checkout") }) }
    ) { paddingValues ->
        CheckoutContent(
            uiState = PaymentUiState(
                paymentState = PaymentState.Paid(
                    transactionId = "TXN_123456",
                    amount = 25.99,
                    method = PaymentMethod.ECOCASH
                ),
                selectedMethod = PaymentMethod.ECOCASH,
                currentTransactionId = "TXN_123456"
            ),
            orderId = "ORDER_123",
            userId = "USER_456",
            amount = 25.99,
            paymentMethods = PaymentMethod.entries,
            onMethodSelected = {},
            onInitiatePayment = { _, _, _, _, _ -> },
            onCancelPayment = {},
            onResetPayment = {},
            onNavigateBack = {},
            onPaymentComplete = {},
            modifier = Modifier.padding(paddingValues)
        )
    }
}

/**
 * Preview for CheckoutScreen - Failed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutScreenFailedPreview() {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Checkout") }) }
    ) { paddingValues ->
        CheckoutContent(
            uiState = PaymentUiState(
                paymentState = PaymentState.Failed(
                    errorCode = "E001",
                    errorMessage = "Payment was declined"
                ),
                selectedMethod = PaymentMethod.ECOCASH
            ),
            orderId = "ORDER_123",
            userId = "USER_456",
            amount = 25.99,
            paymentMethods = PaymentMethod.entries,
            onMethodSelected = {},
            onInitiatePayment = { _, _, _, _, _ -> },
            onCancelPayment = {},
            onResetPayment = {},
            onNavigateBack = {},
            onPaymentComplete = {},
            modifier = Modifier.padding(paddingValues)
        )
    }
}

/**
 * Preview for CheckoutScreen - Cancelled.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutScreenCancelledPreview() {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Checkout") }) }
    ) { paddingValues ->
        CheckoutContent(
            uiState = PaymentUiState(
                paymentState = PaymentState.Cancelled,
                selectedMethod = PaymentMethod.ECOCASH
            ),
            orderId = "ORDER_123",
            userId = "USER_456",
            amount = 25.99,
            paymentMethods = PaymentMethod.entries,
            onMethodSelected = {},
            onInitiatePayment = { _, _, _, _, _ -> },
            onCancelPayment = {},
            onResetPayment = {},
            onNavigateBack = {},
            onPaymentComplete = {},
            modifier = Modifier.padding(paddingValues)
        )
    }
}
