package com.example.ui.screens.restaurant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalPhone
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.viewmodel.restaurant.RestaurantOrder
import com.example.ui.viewmodel.restaurant.RestaurantOrderItem
import com.example.ui.viewmodel.restaurant.RestaurantOrderStatus
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Restaurant order card component.
 * 
 * Displays order information with status-based actions.
 */
@Composable
fun RestaurantOrderCard(
    order: RestaurantOrder,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onStartPreparing: () -> Unit,
    onMarkReady: () -> Unit,
    onCancel: () -> Unit = {},
    modifier: Modifier = Modifier,
    isLoading: Boolean = false
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Order header
            OrderHeader(order = order)
            
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))
            
            // Customer info
            CustomerInfo(order = order)
            
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))
            
            // Order items
            OrderItems(items = order.items)
            
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))
            
            // Order totals
            OrderTotals(
                subtotal = order.subtotal,
                deliveryFee = order.deliveryFee,
                total = order.totalCost
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Action buttons based on order status
            when (order.status) {
                RestaurantOrderStatus.PAID -> {
                    ActionButtons(
                        isLoading = isLoading,
                        onAccept = onAccept,
                        onReject = onReject
                    )
                }
                RestaurantOrderStatus.ACCEPTED -> {
                    PreparingActionButtons(
                        isLoading = isLoading,
                        onStartPreparing = onStartPreparing,
                        onReject = onReject
                    )
                }
                RestaurantOrderStatus.PREPARING -> {
                    ReadyActionButtons(
                        isLoading = isLoading,
                        onMarkReady = onMarkReady,
                        onCancel = onCancel
                    )
                }
                else -> {
                    // Status badge for other statuses
                    StatusBadge(status = order.status)
                }
            }
        }
    }
}

/**
 * Order header with ID and time.
 */
@Composable
private fun OrderHeader(order: RestaurantOrder) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Order #${order.orderId.take(8).uppercase()}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            order.createdAt?.let { timestamp ->
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = formatTimestamp(timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        PaymentBadge(
            method = order.paymentMethod,
            reference = order.paymentRef
        )
    }
}

/**
 * Payment badge showing method and reference.
 */
@Composable
private fun PaymentBadge(method: String, reference: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = method.ifEmpty { "PAID" },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            if (reference.isNotEmpty()) {
                Text(
                    text = reference.take(10) + "...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * Customer information section.
 */
@Composable
private fun CustomerInfo(order: RestaurantOrder) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = order.customerName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.LocalPhone,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = order.customerPhone,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.tertiary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = order.customerAddress,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Order items list.
 */
@Composable
private fun OrderItems(items: List<RestaurantOrderItem>) {
    Column {
        Text(
            text = "Items",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        items.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${item.quantity}x ${item.itemName}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "$${String.format("%.2f", item.price * item.quantity)}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/**
 * Order totals section.
 */
@Composable
private fun OrderTotals(
    subtotal: Double,
    deliveryFee: Double,
    total: Double
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Subtotal",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "$${String.format("%.2f", subtotal)}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Delivery Fee",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "$${String.format("%.2f", deliveryFee)}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Total",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "$${String.format("%.2f", total)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Action buttons for accepting/rejecting orders.
 */
@Composable
private fun ActionButtons(
    isLoading: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    if (isLoading) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onReject,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Cancel,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Reject")
            }
            
            Button(
                onClick = onAccept,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Accept")
            }
        }
    }
}

/**
 * Action buttons for orders being prepared.
 */
@Composable
private fun PreparingActionButtons(
    isLoading: Boolean,
    onStartPreparing: () -> Unit,
    onReject: () -> Unit
) {
    if (isLoading) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onReject,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Cancel,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Cancel")
            }
            
            Button(
                onClick = onStartPreparing,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Restaurant,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Start Preparing")
            }
        }
    }
}

/**
 * Action buttons for orders ready for pickup.
 */
@Composable
private fun ReadyActionButtons(
    isLoading: Boolean,
    onMarkReady: () -> Unit,
    onCancel: () -> Unit
) {
    if (isLoading) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            ) {
                Text("Back")
            }
            
            Button(
                onClick = onMarkReady,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Ready for Pickup")
            }
        }
    }
}

/**
 * Status badge for non-PAID orders.
 */
@Composable
private fun StatusBadge(status: RestaurantOrderStatus) {
    val (icon, color) = when (status) {
        RestaurantOrderStatus.ACCEPTED -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.primary
        RestaurantOrderStatus.REJECTED -> Icons.Default.Cancel to MaterialTheme.colorScheme.error
        RestaurantOrderStatus.PREPARING -> Icons.Default.Restaurant to MaterialTheme.colorScheme.tertiary
        RestaurantOrderStatus.READY_FOR_PICKUP -> Icons.Default.Pending to MaterialTheme.colorScheme.secondary
        else -> Icons.Default.Pending to MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = color
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = status.displayName,
            style = MaterialTheme.typography.labelLarge,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Format timestamp for display.
 */
private fun formatTimestamp(timestamp: com.google.firebase.Timestamp): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    return sdf.format(timestamp.toDate())
}

/**
 * Preview for RestaurantOrderCard.
 */
@Composable
fun RestaurantOrderCardPreview() {
    val previewOrder = RestaurantOrder(
        orderId = "ORD_123456789",
        customerName = "John Doe",
        customerAddress = "123 Main Street, Harare, Zimbabwe",
        customerPhone = "0771234567",
        items = listOf(
            RestaurantOrderItem(
                itemId = "1",
                itemName = "Chicken Burger",
                quantity = 2,
                price = 8.99
            ),
            RestaurantOrderItem(
                itemId = "2",
                itemName = "French Fries",
                quantity = 1,
                price = 3.99
            )
        ),
        subtotal = 21.97,
        deliveryFee = 3.00,
        totalCost = 24.97,
        status = RestaurantOrderStatus.PAID,
        paymentMethod = "ECOCASH",
        paymentRef = "PAY123456"
    )
    
    RestaurantOrderCard(
        order = previewOrder,
        onAccept = {},
        onReject = {},
        onStartPreparing = {},
        onMarkReady = {}
    )
}

/**
 * Preview for RestaurantOrderCard - Accepted.
 */
@Composable
fun RestaurantOrderCardAcceptedPreview() {
    val previewOrder = RestaurantOrder(
        orderId = "ORD_123456789",
        customerName = "John Doe",
        customerAddress = "123 Main Street, Harare",
        customerPhone = "0771234567",
        items = listOf(
            RestaurantOrderItem(
                itemId = "1",
                itemName = "Chicken Burger",
                quantity = 1,
                price = 8.99
            )
        ),
        subtotal = 8.99,
        deliveryFee = 3.00,
        totalCost = 11.99,
        status = RestaurantOrderStatus.ACCEPTED,
        paymentMethod = "ECOCASH",
        paymentRef = "PAY123456"
    )
    
    RestaurantOrderCard(
        order = previewOrder,
        onAccept = {},
        onReject = {},
        onStartPreparing = {},
        onMarkReady = {}
    )
}

/**
 * Preview for RestaurantOrderCard - Preparing.
 */
@Composable
fun RestaurantOrderCardPreparingPreview() {
    val previewOrder = RestaurantOrder(
        orderId = "ORD_123456789",
        customerName = "John Doe",
        customerAddress = "123 Main Street, Harare",
        customerPhone = "0771234567",
        items = listOf(
            RestaurantOrderItem(
                itemId = "1",
                itemName = "Chicken Burger",
                quantity = 1,
                price = 8.99
            )
        ),
        subtotal = 8.99,
        deliveryFee = 3.00,
        totalCost = 11.99,
        status = RestaurantOrderStatus.PREPARING,
        paymentMethod = "ECOCASH",
        paymentRef = "PAY123456"
    )
    
    RestaurantOrderCard(
        order = previewOrder,
        onAccept = {},
        onReject = {},
        onStartPreparing = {},
        onMarkReady = {}
    )
}
