package com.example.ui.screens.restaurant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.viewmodel.restaurant.RestaurantOrderUiState
import com.example.ui.viewmodel.restaurant.RestaurantOrderViewModel

/**
 * Restaurant orders screen for BiteDash.
 * 
 * Displays PAID orders that need restaurant action.
 * Allows restaurants to accept or reject orders.
 * 
 * Does NOT:
 * - Assign drivers
 * - Send notifications
 * - Complete deliveries
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestaurantOrdersScreen(
    viewModel: RestaurantOrderViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    
    RestaurantOrdersContent(
        uiState = uiState,
        onRefresh = viewModel::refreshOrders,
        onAccept = viewModel::acceptOrder,
        onReject = viewModel::rejectOrder,
        onSelectOrder = viewModel::selectOrder,
        modifier = modifier
    )
}

/**
 * Restaurant orders content composable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestaurantOrdersContent(
    uiState: RestaurantOrderUiState,
    onRefresh: () -> Unit,
    onAccept: (String) -> Unit,
    onReject: (String) -> Unit,
    onSelectOrder: (com.example.ui.viewmodel.restaurant.RestaurantOrder) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Orders") },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh"
                        )
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = uiState.isLoading,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading && uiState.orders.isEmpty() -> {
                    LoadingContent()
                }
                uiState.errorMessage != null -> {
                    ErrorContent(
                        message = uiState.errorMessage,
                        onRetry = onRefresh
                    )
                }
                uiState.orders.isEmpty() -> {
                    EmptyContent()
                }
                else -> {
                    OrdersList(
                        orders = uiState.orders,
                        onAccept = onAccept,
                        onReject = onReject,
                        actionInProgress = uiState.actionInProgress
                    )
                }
            }
        }
    }
}

/**
 * Loading content.
 */
@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

/**
 * Error content.
 */
@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "Something went wrong",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            IconButton(onClick = onRetry) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Retry"
                )
            }
        }
    }
}

/**
 * Empty content when no orders.
 */
@Composable
private fun EmptyContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Restaurant,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No orders yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "New orders will appear here when customers complete payment",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Orders list.
 */
@Composable
private fun OrdersList(
    orders: List<com.example.ui.viewmodel.restaurant.RestaurantOrder>,
    onAccept: (String) -> Unit,
    onReject: (String) -> Unit,
    actionInProgress: String?
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "${orders.size} order${if (orders.size != 1) "s" else ""} awaiting action",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
        
        items(
            items = orders,
            key = { it.orderId }
        ) { order ->
            RestaurantOrderCard(
                order = order,
                onAccept = { onAccept(order.orderId) },
                onReject = { onReject(order.orderId) },
                isLoading = actionInProgress == order.orderId
            )
        }
        
        item {
            Spacer(modifier = Modifier.height(80.dp)) // Bottom padding for FAB
        }
    }
}

/**
 * Preview for RestaurantOrdersScreen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestaurantOrdersScreenPreview() {
    val previewState = RestaurantOrderUiState(
        orders = listOf(
            com.example.ui.viewmodel.restaurant.RestaurantOrder(
                orderId = "ORD_123456789",
                customerName = "John Doe",
                customerAddress = "123 Main Street, Harare",
                customerPhone = "0771234567",
                items = listOf(
                    com.example.ui.viewmodel.restaurant.RestaurantOrderItem(
                        itemId = "1",
                        itemName = "Chicken Burger",
                        quantity = 2,
                        price = 8.99
                    )
                ),
                subtotal = 17.98,
                deliveryFee = 3.00,
                totalCost = 20.98,
                status = com.example.ui.viewmodel.restaurant.RestaurantOrderStatus.PAID,
                paymentMethod = "ECOCASH",
                paymentRef = "PAY123456"
            )
        ),
        isLoading = false
    )
    
    RestaurantOrdersContent(
        uiState = previewState,
        onRefresh = {},
        onAccept = {},
        onReject = {},
        onSelectOrder = {}
    )
}

/**
 * Preview for RestaurantOrdersScreen - Empty.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestaurantOrdersScreenEmptyPreview() {
    RestaurantOrdersContent(
        uiState = RestaurantOrderUiState(
            orders = emptyList(),
            isLoading = false
        ),
        onRefresh = {},
        onAccept = {},
        onReject = {},
        onSelectOrder = {}
    )
}

/**
 * Preview for RestaurantOrdersScreen - Loading.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestaurantOrdersScreenLoadingPreview() {
    RestaurantOrdersContent(
        uiState = RestaurantOrderUiState(
            orders = emptyList(),
            isLoading = true
        ),
        onRefresh = {},
        onAccept = {},
        onReject = {},
        onSelectOrder = {}
    )
}
