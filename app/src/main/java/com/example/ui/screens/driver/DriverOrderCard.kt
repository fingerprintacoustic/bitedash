package com.example.ui.screens.driver

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
import androidx.compose.material.icons.filled.LocalPhone
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.viewmodel.driver.DriverDeliveryOrder
import com.example.ui.viewmodel.driver.DriverDeliveryStatus
import com.example.ui.viewmodel.driver.DriverOrderItem
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Driver order card component.
 * 
 * Displays delivery order information with accept action.
 */
@Composable
fun DriverOrderCard(
    order: DriverDeliveryOrder,
    onAcceptDelivery: () -> Unit,
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
            
            // Pickup location (restaurant)
            PickupLocation(order = order)
            
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))
            
            // Delivery location (customer)
            DeliveryLocation(order = order)
            
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))
            
            // Order summary
            OrderSummary(order = order)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Action button for accepting delivery
            AcceptDeliveryButton(
                isLoading = isLoading,
                isAssigned = order.deliveryStatus != DriverDeliveryStatus.UNASSIGNED,
                onAccept = onAcceptDelivery
            )
        }
    }
}

/**
 * Order header with ID and time.
 */
@Composable
private fun OrderHeader(order: DriverDeliveryOrder) {
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
        
        StatusBadge(status = order.deliveryStatus)
    }
}

/**
 * Status badge showing delivery status.
 */
@Composable
private fun StatusBadge(status: DriverDeliveryStatus) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = when (status) {
                DriverDeliveryStatus.UNASSIGNED -> MaterialTheme.colorScheme.secondaryContainer
                DriverDeliveryStatus.ASSIGNED -> MaterialTheme.colorScheme.primaryContainer
                DriverDeliveryStatus.PICKED_UP -> MaterialTheme.colorScheme.tertiaryContainer
                DriverDeliveryStatus.DELIVERED -> MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = status.displayName,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = when (status) {
                DriverDeliveryStatus.UNASSIGNED -> MaterialTheme.colorScheme.onSecondaryContainer
                DriverDeliveryStatus.ASSIGNED -> MaterialTheme.colorScheme.onPrimaryContainer
                DriverDeliveryStatus.PICKED_UP -> MaterialTheme.colorScheme.onTertiaryContainer
                DriverDeliveryStatus.DELIVERED -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

/**
 * Pickup location (restaurant).
 */
@Composable
private fun PickupLocation(order: DriverDeliveryOrder) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Restaurant,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Pickup from",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = order.restaurantName,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        
        Spacer(modifier = Modifier.height(2.dp))
        
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = order.restaurantAddress,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        if (order.restaurantPhone.isNotEmpty()) {
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.LocalPhone,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = order.restaurantPhone,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Delivery location (customer).
 */
@Composable
private fun DeliveryLocation(order: DriverDeliveryOrder) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.tertiary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Deliver to",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = order.customerName,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        
        Spacer(modifier = Modifier.height(2.dp))
        
        Text(
            text = order.customerAddress,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        if (order.customerPhone.isNotEmpty()) {
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.LocalPhone,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = order.customerPhone,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Order summary showing items and totals.
 */
@Composable
private fun OrderSummary(order: DriverDeliveryOrder) {
    Column {
        // Items summary
        if (order.items.isNotEmpty()) {
            Text(
                text = "${order.items.size} item${if (order.items.size != 1) "s" else ""}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            order.items.take(3).forEach { item ->
                Text(
                    text = "${item.quantity}x ${item.itemName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (order.items.size > 3) {
                Text(
                    text = "+${order.items.size - 3} more items",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Totals
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
                text = "$${String.format("%.2f", order.deliveryFee)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Accept delivery button.
 */
@Composable
private fun AcceptDeliveryButton(
    isLoading: Boolean,
    isAssigned: Boolean,
    onAccept: () -> Unit
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
    } else if (isAssigned) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Delivery Accepted",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    } else {
        Button(
            onClick = onAccept,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Accept Delivery")
        }
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
 * Preview for DriverOrderCard.
 */
@Composable
fun DriverOrderCardPreview() {
    val previewOrder = DriverDeliveryOrder(
        orderId = "ORD_123456789",
        restaurantName = "Tasty Foods Restaurant",
        restaurantAddress = "45 Main Street, Harare",
        restaurantPhone = "0772345678",
        customerName = "John Doe",
        customerAddress = "123 Shopping Centre, Harare",
        customerPhone = "0771234567",
        items = listOf(
            DriverOrderItem(
                itemId = "1",
                itemName = "Chicken Burger",
                quantity = 2,
                price = 8.99
            ),
            DriverOrderItem(
                itemId = "2",
                itemName = "French Fries",
                quantity = 1,
                price = 3.99
            )
        ),
        subtotal = 21.97,
        deliveryFee = 5.00,
        totalCost = 26.97,
        orderStatus = "READY_FOR_PICKUP",
        deliveryStatus = DriverDeliveryStatus.UNASSIGNED
    )
    
    DriverOrderCard(
        order = previewOrder,
        onAcceptDelivery = {}
    )
}

/**
 * Preview for DriverOrderCard - Assigned.
 */
@Composable
fun DriverOrderCardAssignedPreview() {
    val previewOrder = DriverDeliveryOrder(
        orderId = "ORD_123456789",
        restaurantName = "Tasty Foods Restaurant",
        restaurantAddress = "45 Main Street, Harare",
        customerName = "John Doe",
        customerAddress = "123 Shopping Centre, Harare",
        customerPhone = "0771234567",
        items = listOf(
            DriverOrderItem(
                itemId = "1",
                itemName = "Chicken Burger",
                quantity = 1,
                price = 8.99
            )
        ),
        subtotal = 8.99,
        deliveryFee = 5.00,
        totalCost = 13.99,
        orderStatus = "READY_FOR_PICKUP",
        deliveryStatus = DriverDeliveryStatus.ASSIGNED,
        driverId = "DRIVER_001",
        driverName = "Mike Driver"
    )
    
    DriverOrderCard(
        order = previewOrder,
        onAcceptDelivery = {}
    )
}
