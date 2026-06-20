package com.example.ui.screens.payment

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.viewmodel.payment.PaymentState

/**
 * Payment status card component.
 * 
 * Displays the current payment status with appropriate visual feedback.
 * 
 * @param state Current payment state
 * @param modifier Modifier for styling
 */
@Composable
fun PaymentStatusCard(
    state: PaymentState,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = state.backgroundColor
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status icon
            Icon(
                imageVector = state.icon,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = state.iconColor
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Status title
            Text(
                text = state.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = state.iconColor,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Status description
            Text(
                text = state.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            // Additional info for specific states
            when (state) {
                is PaymentState.Failed -> {
                    if (state.errorMessage.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = state.errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                is PaymentState.Paid -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Transaction: ",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = state.transactionId.take(12) + "...",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                is PaymentState.WaitingForPayment -> {
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                else -> {}
            }
        }
    }
}

/**
 * Compact payment status indicator.
 */
@Composable
fun PaymentStatusIndicator(
    state: PaymentState,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (state) {
            is PaymentState.Idle -> {
                Icon(
                    imageVector = Icons.Default.HourglassEmpty,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            is PaymentState.CreatingTransaction -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
            }
            is PaymentState.WaitingForPayment -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            is PaymentState.Paid -> {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50)
                )
            }
            is PaymentState.Failed -> {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            }
            is PaymentState.Cancelled -> {
                Icon(
                    imageVector = Icons.Default.Cancel,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary
                )
            }
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Text(
            text = state.title,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

/**
 * Extension property for PaymentState icon.
 */
val PaymentState.icon: ImageVector
    @Composable
    get() = when (this) {
        is PaymentState.Idle -> Icons.Default.HourglassEmpty
        is PaymentState.CreatingTransaction -> Icons.Default.Pending
        is PaymentState.WaitingForPayment -> Icons.Default.Pending
        is PaymentState.Paid -> Icons.Default.CheckCircle
        is PaymentState.Failed -> Icons.Default.Error
        is PaymentState.Cancelled -> Icons.Default.Cancel
    }

/**
 * Extension property for PaymentState title.
 */
val PaymentState.title: String
    @Composable
    get() = when (this) {
        is PaymentState.Idle -> "Ready to Pay"
        is PaymentState.CreatingTransaction -> "Creating Transaction"
        is PaymentState.WaitingForPayment -> "Waiting for Payment"
        is PaymentState.Paid -> "Payment Complete"
        is PaymentState.Failed -> "Payment Failed"
        is PaymentState.Cancelled -> "Payment Cancelled"
    }

/**
 * Extension property for PaymentState description.
 */
val PaymentState.description: String
    @Composable
    get() = when (this) {
        is PaymentState.Idle -> "Select a payment method to continue"
        is PaymentState.CreatingTransaction -> "Please wait while we set up your payment..."
        is PaymentState.WaitingForPayment -> "Complete payment on your phone, then check back here"
        is PaymentState.Paid -> "Your payment has been confirmed. Thank you!"
        is PaymentState.Failed -> "Something went wrong with your payment. Please try again."
        is PaymentState.Cancelled -> "Your payment was cancelled."
    }

/**
 * Extension property for PaymentState icon color.
 */
val PaymentState.iconColor: Color
    @Composable
    get() = when (this) {
        is PaymentState.Idle -> MaterialTheme.colorScheme.onSurfaceVariant
        is PaymentState.CreatingTransaction -> MaterialTheme.colorScheme.primary
        is PaymentState.WaitingForPayment -> MaterialTheme.colorScheme.primary
        is PaymentState.Paid -> Color(0xFF4CAF50)
        is PaymentState.Failed -> MaterialTheme.colorScheme.error
        is PaymentState.Cancelled -> MaterialTheme.colorScheme.tertiary
    }

/**
 * Extension property for PaymentState background color.
 */
val PaymentState.backgroundColor: Color
    @Composable
    get() = when (this) {
        is PaymentState.Idle -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        is PaymentState.CreatingTransaction -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        is PaymentState.WaitingForPayment -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        is PaymentState.Paid -> Color(0xFF4CAF50).copy(alpha = 0.1f)
        is PaymentState.Failed -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        is PaymentState.Cancelled -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
    }

/**
 * Preview for PaymentStatusCard.
 */
@Composable
fun PaymentStatusCardIdlePreview() {
    PaymentStatusCard(state = PaymentState.Idle)
}

/**
 * Preview for PaymentStatusCard - Creating.
 */
@Composable
fun PaymentStatusCardCreatingPreview() {
    PaymentStatusCard(state = PaymentState.CreatingTransaction)
}

/**
 * Preview for PaymentStatusCard - Waiting.
 */
@Composable
fun PaymentStatusCardWaitingPreview() {
    PaymentStatusCard(state = PaymentState.WaitingForPayment)
}

/**
 * Preview for PaymentStatusCard - Paid.
 */
@Composable
fun PaymentStatusCardPaidPreview() {
    PaymentStatusCard(
        state = PaymentState.Paid(
            transactionId = "ABC123DEF456",
            amount = 25.99,
            method = com.example.ui.viewmodel.payment.PaymentMethod.ECOCASH
        )
    )
}

/**
 * Preview for PaymentStatusCard - Failed.
 */
@Composable
fun PaymentStatusCardFailedPreview() {
    PaymentStatusCard(
        state = PaymentState.Failed(
            errorCode = "E001",
            errorMessage = "Payment was declined by your bank"
        )
    )
}

/**
 * Preview for PaymentStatusCard - Cancelled.
 */
@Composable
fun PaymentStatusCardCancelledPreview() {
    PaymentStatusCard(state = PaymentState.Cancelled)
}
