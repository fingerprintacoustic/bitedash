package com.example.ui.screens.driver

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delivery
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.viewmodel.driver.DriverDeliveryOrder
import com.example.ui.viewmodel.driver.DriverDeliveryUiState
import com.example.ui.viewmodel.driver.DriverDeliveryViewModel

/**
 * Driver orders screen for BiteDash.
 * 
 * Displays available and assigned deliveries for drivers.
 * Allows drivers to accept deliveries.
 * 
 * Does NOT:
 * - Implement GPS tracking
 * - Send notifications
 * - Complete deliveries
 * - Assign drivers automatically
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverOrdersScreen(
    viewModel: DriverDeliveryViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Show success message
    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearSuccess()
        }
    }
    
    // Show error message
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }
    
    DriverOrdersContent(
        uiState = uiState,
        onRefresh = viewModel::refreshOrders,
        onAcceptDelivery = viewModel::acceptDelivery,
        onSelectOrder = viewModel::selectOrder,
        snackbarHostState = snackbarHostState,
        modifier = modifier
    )
}

/**
 * Driver orders content composable with tabs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverOrdersContent(
    uiState: DriverDeliveryUiState,
    onRefresh: () -> Unit,
    onAcceptDelivery: (String) -> Unit,
    onSelectOrder: (DriverDeliveryOrder) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        "Available (${uiState.availableOrders.size})",
        "My Deliveries (${uiState.myDeliveries.size})"
    )
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Deliveries") },
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tabs
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) }
                    )
                }
            }
            
            // Content
            PullToRefreshBox(
                isRefreshing = uiState.isLoading,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize()
            ) {
                when (selectedTabIndex) {
                    0 -> AvailableDeliveriesList(
                        orders = uiState.availableOrders,
                        onAcceptDelivery = onAcceptDelivery,
                        actionInProgress = uiState.actionInProgress
                    )
                    1 -> MyDeliveriesList(
                        orders = uiState.myDeliveries,
                        onSelectOrder = onSelectOrder
                    )
                }
            }
        }
    }
}

/**
 * Available deliveries list.
 */
@Composable
private fun AvailableDeliveriesList(
    orders: List<DriverDeliveryOrder>,
    onAcceptDelivery: (String) -> Unit,
    actionInProgress: String?
) {
    when {
        orders.isEmpty() -> {
            EmptyAvailableContent()
        }
        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        text = "${orders.size} delivery${if (orders.size != 1) "s" else ""} available",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                
                items(
                    items = orders,
                    key = { it.orderId }
                ) { order ->
                    DriverOrderCard(
                        order = order,
                        onAcceptDelivery = { onAcceptDelivery(order.orderId) },
                        isLoading = actionInProgress == order.orderId
                    )
                }
                
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }
}

/**
 * My deliveries list.
 */
@Composable
private fun MyDeliveriesList(
    orders: List<DriverDeliveryOrder>,
    onSelectOrder: (DriverDeliveryOrder) -> Unit
) {
    when {
        orders.isEmpty() -> {
            EmptyMyDeliveriesContent()
        }
        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        text = "${orders.size} active delivery${if (orders.size != 1) "s" else ""}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                
                items(
                    items = orders,
                    key = { it.orderId }
                ) { order ->
                    DriverOrderCard(
                        order = order,
                        onAcceptDelivery = { onSelectOrder(order) }
                    )
                }
                
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }
}

/**
 * Empty content for available deliveries.
 */
@Composable
private fun EmptyAvailableContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Delivery,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No deliveries available",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "New deliveries will appear here when restaurants mark orders as ready",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Empty content for my deliveries.
 */
@Composable
private fun EmptyMyDeliveriesContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Delivery,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No active deliveries",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Accept a delivery to get started",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Preview for DriverOrdersScreen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverOrdersScreenPreview() {
    val previewState = DriverDeliveryUiState(
        availableOrders = listOf(
            DriverDeliveryOrder(
                orderId = "ORD_123456789",
                restaurantName = "Tasty Foods",
                restaurantAddress = "45 Main Street",
                customerName = "John Doe",
                customerAddress = "123 Shopping Centre",
                customerPhone = "0771234567",
                items = listOf(
                    DriverOrderItem(
                        itemId = "1",
                        itemName = "Chicken Burger",
                        quantity = 2,
                        price = 8.99
                    )
                ),
                subtotal = 17.98,
                deliveryFee = 5.00,
                totalCost = 22.98,
                orderStatus = "READY_FOR_PICKUP",
                deliveryStatus = DriverDeliveryStatus.UNASSIGNED
            )
        ),
        isLoading = false
    )
    
    DriverOrdersContent(
        uiState = previewState,
        onRefresh = {},
        onAcceptDelivery = {},
        onSelectOrder = {},
        snackbarHostState = SnackbarHostState()
    )
}

/**
 * Preview for DriverOrdersScreen - Empty.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverOrdersScreenEmptyPreview() {
    DriverOrdersContent(
        uiState = DriverDeliveryUiState(
            availableOrders = emptyList(),
            myDeliveries = emptyList(),
            isLoading = false
        ),
        onRefresh = {},
        onAcceptDelivery = {},
        onSelectOrder = {},
        snackbarHostState = SnackbarHostState()
    )
}

/**
 * Preview for DriverOrdersScreen - Loading.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverOrdersScreenLoadingPreview() {
    DriverOrdersContent(
        uiState = DriverDeliveryUiState(
            availableOrders = emptyList(),
            myDeliveries = emptyList(),
            isLoading = true
        ),
        onRefresh = {},
        onAcceptDelivery = {},
        onSelectOrder = {},
        snackbarHostState = SnackbarHostState()
    )
}
