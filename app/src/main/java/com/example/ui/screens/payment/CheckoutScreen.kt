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
 * 
 * @param viewModel CheckoutViewModel instance
 * @param onNavigateBack Callback to navigate back
 * @param onPaymentComplete Callback when payment is completed successfully
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutScreen(
    viewModel: CheckoutViewModel,
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
            paymentMethods = PaymentMethod.entries,
            onMethodSelected = viewModel::selectPaymentMethod,
            onInitiatePayment = { amount, userId, orderId, phoneNumber, description ->
                viewModel.initiatePayment(amount, userId, orderId, phoneNumber, description)
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
    paymentMethods: List<PaymentMethod>,
    onMethodSelected: (PaymentMethod) -> Unit,
    onInitiatePayment: (Double, String, String, String, String) -> Unit,
    onCancelPayment: () -> Unit,
    onResetPayment: () -> Unit,
    onNavigateBack: () -> Unit,
    onPaymentComplete: (String) -> Unit,
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
            
            // Placeholder for payment button
            // TODO: Add actual payment button that calls onInitiatePayment
            // PlaceholderButton(
            //     text = "Pay Now",
            //     onClick = { /* Handle payment */ }
            // )
            
            Spacer(modifier = Modifier.height(8.dp))
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
 * Placeholder button composable for future payment button.
 * 
 * This is a placeholder for the actual payment button that will be
 * implemented in future versions. Currently disabled.
 * 
 * TODO: Enable this button once order flow integration is ready.
 */
@Composable
fun PlaceholderPaymentButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = false
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Text(
            text = if (enabled) "Pay Now" else "Payment Button (Coming Soon)",
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Preview for CheckoutScreen.
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
