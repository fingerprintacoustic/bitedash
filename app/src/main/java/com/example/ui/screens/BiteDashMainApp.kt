package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.entity.OrderEntity
import com.example.model.CartItem
import com.example.model.MenuItem
import com.example.model.Restaurant
import com.example.ui.theme.*
import com.example.ui.viewmodel.BiteDashViewModel
import com.example.ui.viewmodel.PaymentStep
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BiteDashMainApp(
    viewModel: BiteDashViewModel,
    modifier: Modifier = Modifier
) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val activeOrder by viewModel.activeOrder.collectAsStateWithLifecycle()
    val cart by viewModel.cart.collectAsStateWithLifecycle()
    var isAdminPortalOpen by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ShoppingCart,
                                contentDescription = "BiteDash logo",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "BiteDash",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Zimbabwe's Premier Delivery",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                        }
                    }
                },
                actions = {
                    if (cart.isNotEmpty()) {
                        ElevatedCard(
                            onClick = { viewModel.selectTab(1) },
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            shape = CircleShape,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .testTag("floating_cart_pill")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ShoppingCart,
                                    contentDescription = "Cart",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "${cart.sumOf { it.quantity }} items",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = { isAdminPortalOpen = true },
                        modifier = Modifier.testTag("admin_portal_toggle_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Admin Portal Control",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                containerColor = MaterialTheme.colorScheme.background,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    icon = { Icon(imageVector = Icons.Default.Home, contentDescription = "Browse") },
                    label = { Text("Browse") },
                    modifier = Modifier.testTag("nav_browse")
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (cart.isNotEmpty()) {
                                    Badge { Text("${cart.sumOf { it.quantity }}") }
                                }
                            }
                        ) {
                            Icon(imageVector = Icons.Default.ShoppingCart, contentDescription = "Cart")
                        }
                    },
                    label = { Text("Cart") },
                    modifier = Modifier.testTag("nav_cart")
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { viewModel.selectTab(2) },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (activeOrder != null) {
                                    Badge(containerColor = MaterialTheme.colorScheme.tertiary) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(Color.White, CircleShape)
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(imageVector = Icons.Default.LocationOn, contentDescription = "Tracking")
                        }
                    },
                    label = { Text("Tracking") },
                    modifier = Modifier.testTag("nav_tracking")
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { viewModel.selectTab(3) },
                    icon = { Icon(imageVector = Icons.Default.List, contentDescription = "History") },
                    label = { Text("History") },
                    modifier = Modifier.testTag("nav_history")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> BrowseFlowScreen(viewModel)
                1 -> CartScreen(viewModel)
                2 -> ActiveTrackingScreen(viewModel)
                3 -> HistoryScreen(viewModel)
            }
        }
    }

    if (isAdminPortalOpen) {
        AdminPortalOverlay(
            viewModel = viewModel,
            onDismiss = { isAdminPortalOpen = false }
        )
    }
}

// BROWSE RESTAURANTS FLOW
@Composable
fun BrowseFlowScreen(viewModel: BiteDashViewModel) {
    val selectedRestaurant by viewModel.selectedRestaurant.collectAsStateWithLifecycle()

    AnimatedContent(
        targetState = selectedRestaurant,
        transitionSpec = {
            if (targetState != null) {
                slideInHorizontally { width -> width } + fadeIn() togetherWith
                        slideOutHorizontally { width -> -width } + fadeOut()
            } else {
                slideInHorizontally { width -> -width } + fadeIn() togetherWith
                        slideOutHorizontally { width -> width } + fadeOut()
            }
        },
        label = "restaurant_navigation"
    ) { restaurant ->
        if (restaurant == null) {
            RestaurantFeedScreen(viewModel)
        } else {
            RestaurantDetailScreen(restaurant = restaurant, viewModel = viewModel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestaurantFeedScreen(viewModel: BiteDashViewModel) {
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val restaurants by viewModel.restaurantsState.collectAsStateWithLifecycle()

    val categories = listOf("All", "Fast Food", "Traditional", "Pizza & Grills", "Cafes & Drinks")

    // Filtered restaurants
    val filteredList = restaurants.filter { rest ->
        (selectedCategory == "All" || rest.category == selectedCategory) &&
                (searchQuery.isEmpty() || rest.name.contains(searchQuery, ignoreCase = true) ||
                        rest.description.contains(searchQuery, ignoreCase = true))
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(12.dp))
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search local dishes or food spots...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("restaurant_search_input"),
                shape = RoundedCornerShape(24.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.LightGray.copy(alpha = 0.5f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        }

        item {
            // Horizontal categories row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(categories) { category ->
                    val isSelected = category == selectedCategory
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setSelectedCategory(category) },
                        label = { Text(category) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White,
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        border = null,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.testTag("category_chip_$category")
                    )
                }
            }
        }

        item {
            Text(
                text = "Popular Spots in Harare / Bulawayo",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        if (filteredList.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "No spots found",
                            tint = Color.Gray,
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = "No restaurants match your search.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(filteredList) { restaurant ->
                RestaurantCard(restaurant = restaurant, onClick = { viewModel.selectRestaurant(restaurant) })
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun RestaurantCard(restaurant: Restaurant, onClick: () -> Unit) {
    // Generate styled visual colors depending on category
    val (primaryBrush, emoji) = when (restaurant.imageKeyword) {
        "chicken" -> Pair(
            Brush.linearGradient(listOf(Color(0xFFFF9800), Color(0xFFE65100))),
            "🍗"
        )
        "sadza" -> Pair(
            Brush.linearGradient(listOf(Color(0xFF388E3C), Color(0xFF1B5E20))),
            "🍲"
        )
        "pizza" -> Pair(
            Brush.linearGradient(listOf(Color(0xFFFF7043), Color(0xFFD84315))),
            "🍕"
        )
        else -> Pair(
            Brush.linearGradient(listOf(Color(0xFF8D6E63), Color(0xFF4E342E))),
            "☕"
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("restaurant_card_${restaurant.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // Mock Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .background(primaryBrush),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = emoji,
                        fontSize = 44.sp
                    )
                    Badge(
                        containerColor = Color.White.copy(alpha = 0.2f),
                        contentColor = Color.White
                    ) {
                        Text(
                            text = restaurant.category,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = restaurant.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "rating",
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "${restaurant.rating}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Text(
                    text = restaurant.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Divider(
                    thickness = 0.5.dp,
                    color = Color.LightGray.copy(alpha = 0.3f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = "location",
                            tint = Color.Gray,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = restaurant.location,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "delivery time",
                                tint = Color.Gray,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = restaurant.deliveryTime,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                        }

                        Text(
                            text = if (restaurant.deliveryFee == 0.0) "Free Delivery" else "Fee: $${String.format(Locale.US, "%.2f", restaurant.deliveryFee)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RestaurantDetailScreen(restaurant: Restaurant, viewModel: BiteDashViewModel) {
    val cart by viewModel.cart.collectAsStateWithLifecycle()
    var isEditMenuOpen by remember { mutableStateOf(false) }

    val (primaryBrush, emoji) = when (restaurant.imageKeyword) {
        "chicken" -> Pair(Brush.linearGradient(listOf(Color(0xFFFF9800), Color(0xFFE65100))), "🍗")
        "sadza" -> Pair(Brush.linearGradient(listOf(Color(0xFF388E3C), Color(0xFF1B5E20))), "🍲")
        "pizza" -> Pair(Brush.linearGradient(listOf(Color(0xFFFF7043), Color(0xFFD84315))), "🍕")
        else -> Pair(Brush.linearGradient(listOf(Color(0xFF8D6E63), Color(0xFF4E342E))), "☕")
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            // Header Image Box with custom styled Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(primaryBrush)
            ) {
                // Back Button
                IconButton(
                    onClick = { viewModel.selectRestaurant(null) },
                    modifier = Modifier
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .align(Alignment.TopStart)
                        .testTag("detail_back_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                // Edit Menu Button
                FilledTonalButton(
                    onClick = { isEditMenuOpen = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color.White,
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .padding(16.dp)
                        .align(Alignment.TopEnd)
                        .testTag("detail_manage_menu_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Menu",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Manage Menu", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Badge(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White) {
                        Text(restaurant.category, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = restaurant.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Text(
                    text = emoji,
                    fontSize = 60.sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                )
            }
        }

        item {
            // Description card
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "About the Spot",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = restaurant.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                    Divider(color = Color.LightGray.copy(alpha = 0.3f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Star, contentDescription = "Rating", tint = Color(0xFFFFB300), modifier = Modifier.size(16.dp))
                            Text("${restaurant.rating} / 5.0", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(
                            text = "Loc: ${restaurant.location}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "Menu Catalog (USD)",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        items(restaurant.menuItems) { item ->
            val cartItem = cart.find { it.menuItem.id == item.id }
            val quantity = cartItem?.quantity ?: 0

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("menu_item_${item.id}"),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(0.5.dp, Color.LightGray.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$${String.format(Locale.US, "%.2f", item.price)}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Quantity controls or Add button
                    if (quantity == 0) {
                        Button(
                            onClick = { viewModel.addToCart(item) },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                            modifier = Modifier
                                .testTag("add_item_${item.id}")
                                .minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                CircleShape
                            ).padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            IconButton(
                                onClick = { viewModel.removeFromCart(item) },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("decrease_item_${item.id}")
                            ) {
                                Text("-", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            Text(
                                text = "$quantity",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )

                            IconButton(
                                onClick = { viewModel.addToCart(item) },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("increase_item_${item.id}")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (isEditMenuOpen) {
        EditMenuDialog(
            restaurant = restaurant,
            viewModel = viewModel,
            onDismiss = { isEditMenuOpen = false }
        )
    }
}

// CART SCREEN
@Composable
fun CartScreen(viewModel: BiteDashViewModel) {
    val cart by viewModel.cart.collectAsStateWithLifecycle()
    val checkoutMethod by viewModel.checkoutMethod.collectAsStateWithLifecycle()
    val phoneInput by viewModel.phoneInput.collectAsStateWithLifecycle()
    val paymentStep by viewModel.paymentStep.collectAsStateWithLifecycle()

    if (cart.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ShoppingCart,
                        contentDescription = "Empty Cart",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Text(
                    text = "Your BiteDash cart is empty",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Browse outstanding local spots around Zimbabwe and select some mouth-watering road runner chicken, sadza, or pizzas!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = { viewModel.selectTab(0) },
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text("Explore Cuisine Now")
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "BiteDash Checkout",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            // Items List
            items(cart) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.menuItem.name, fontWeight = FontWeight.Bold)
                        Text("$${String.format(Locale.US, "%.2f", item.menuItem.price)} each", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IconButton(
                            onClick = { viewModel.removeFromCart(item.menuItem) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Text("-", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${item.quantity}", fontWeight = FontWeight.Bold)
                        IconButton(
                            onClick = { viewModel.addToCart(item.menuItem) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "increase")
                        }
                    }
                }
            }

            // Pricing Summary Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Invoice Breakdown", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        val subtotal = cart.sumOf { it.menuItem.price * it.quantity }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Subtotal", color = Color.Gray)
                            Text("$${String.format(Locale.US, "%.2f", subtotal)}")
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Zimbabwe Delivery Rider Fee", color = Color.Gray)
                            Text("$2.00")
                        }
                        Divider(modifier = Modifier.padding(vertical = 4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Total Outlay (USD)", fontWeight = FontWeight.Bold)
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "$${String.format(Locale.US, "%.2f", subtotal + 2.0)}",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                // Interactive display matching Zimbabwe multi-currency context
                                val mockZigRate = 22.0
                                Text(
                                    text = "≈ ZiG ${String.format(Locale.US, "%.2f", (subtotal + 2.0) * mockZigRate)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }

            // Mobile Money Payment Selectors
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Pay via Zimbabwean Payment Channels",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )

                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        items(viewModel.checkoutMethods) { method ->
                            val isSelected = method == checkoutMethod
                            val borderCol = when (method) {
                                "EcoCash" -> EcoCashGreen
                                "InnBucks" -> InnBucksGold
                                "OneMoney" -> OneMoneyBlue
                                "O'Mari" -> Color(0xFFE65100)
                                "Telecash" -> TelecashRed
                                "ZIPIT" -> Color(0xFF004D40)
                                "Bank Cards" -> Color(0xFF311B92)
                                "USD Cash" -> Color(0xFF33691E)
                                else -> Color.Gray
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) borderCol.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface)
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) borderCol else Color.LightGray.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { viewModel.setCheckoutMethod(method) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                                    .testTag("payment_method_$method"),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val dotColor = when (method) {
                                        "EcoCash" -> Color(0xFF2563EB) // Blue dot
                                        "InnBucks" -> Color(0xFFEA580C) // Orange dot
                                        "OneMoney" -> OneMoneyBlue
                                        "O'Mari" -> Color(0xFFF57C00)
                                        "Telecash" -> Color(0xFFDC2626) // Red dot
                                        "ZIPIT" -> Color(0xFF10B981) // Green dot
                                        "Bank Cards" -> Color(0xFF673AB7)
                                        "USD Cash" -> Color(0xFF059669) // Green dot
                                        else -> borderCol
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(dotColor, CircleShape)
                                    )
                                    Text(
                                        text = method,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) borderCol else MaterialTheme.colorScheme.onSurface,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Payment Input Box
            item {
                val inputColor = when (checkoutMethod) {
                    "EcoCash" -> EcoCashGreen
                    "InnBucks" -> InnBucksGold
                    "OneMoney" -> OneMoneyBlue
                    "O'Mari" -> Color(0xFFE65100)
                    "Telecash" -> TelecashRed
                    "ZIPIT" -> Color(0xFF004D40)
                    "Bank Cards" -> Color(0xFF311B92)
                    "USD Cash" -> Color(0xFF33691E)
                    else -> MaterialTheme.colorScheme.primary
                }

                val textLabel = when (checkoutMethod) {
                    "EcoCash" -> "EcoCash Mobile Number"
                    "InnBucks" -> "InnBucks Account/Mobile Number"
                    "OneMoney" -> "OneMoney Mobile Number"
                    "O'Mari" -> "O'Mari Registered Number"
                    "Telecash" -> "Telecash Mobile Number"
                    "ZIPIT" -> "Contact Phone (ZIPIT Transfer)"
                    "Bank Cards" -> "Contact Phone (Visa/Mastercard/ZimSwitch)"
                    "USD Cash" -> "Delivery/Contact Phone Number"
                    else -> "Phone Number"
                }
                val textPlaceholder = when (checkoutMethod) {
                    "OneMoney" -> "e.g. 0731234567"
                    "Telecash" -> "e.g. 0711234567"
                    else -> "e.g. 0771234567"
                }
                val merchantLabel = when (checkoutMethod) {
                    "USD Cash" -> "Cash On Delivery (COD) - Pay driver in physical USD cash"
                    "ZIPIT", "Bank Cards" -> "Merchant gateway: Simbisa Pay / ZimSwitch Secure"
                    "O'Mari" -> "O'Mari Gateway: Old Mutual Zimbabwe Pin Prompt"
                    else -> "Recipient Merchant: BiteDash Mobile Services"
                }

                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = merchantLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.Gray,
                            fontWeight = FontWeight.Bold
                        )

                        OutlinedTextField(
                            value = phoneInput,
                            onValueChange = { viewModel.setPhoneInput(it) },
                            label = { Text(textLabel) },
                            placeholder = { Text(textPlaceholder) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("checkout_phone_input"),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = inputColor,
                                focusedLabelColor = inputColor
                            )
                        )

                        Button(
                            onClick = { viewModel.processCheckout() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("pay_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = inputColor
                            )
                        ) {
                            Text(if (checkoutMethod == "USD Cash") "Place Cash Order & Track 🛵" else "Secure Pay & Track Order 🛵", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // Payment Processing Simulation Modal
    if (paymentStep != PaymentStep.Idle) {
        Dialog(onDismissRequest = { if (paymentStep is PaymentStep.Success || paymentStep is PaymentStep.Error) viewModel.resetPaymentState() }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Carrier Processing",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Divider(color = Color.LightGray.copy(alpha = 0.3f))

                    when (val currentStep = paymentStep) {
                        is PaymentStep.SendingPush -> {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Text("Contacting Ecocash/Telecash/InnBucks servers...", textAlign = TextAlign.Center)
                            Text("Establishing USSD Push tunnel to handset...", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                        is PaymentStep.WaitingForHandsetPin -> {
                            // Infinite pulsing indicator representing prompt waiting
                            val scale = rememberInfiniteTransition().animateFloat(
                                initialValue = 0.8f,
                                targetValue = 1.2f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1000, easing = LinearEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "scale"
                            )
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape)
                                    .scale(scale.value),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Phone, contentDescription = "Handset prompt waiting", tint = MaterialTheme.colorScheme.primary)
                            }
                            Text("Check your phone push prompt!", fontWeight = FontWeight.Bold)
                            Text("Please enter your EcoCash / InnBucks passcode on your hand set to confirm payment total.", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                        }
                        is PaymentStep.ProcessingConfirmation -> {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.tertiary)
                            Text("Verifying secured balance clearing with Econet/Simbisa...", textAlign = TextAlign.Center)
                        }
                        is PaymentStep.Success -> {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Success", tint = EcoCashGreen, modifier = Modifier.size(64.dp))
                            Text("Payment Confirmed!", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = EcoCashGreen)
                            Text("Mobile Money cleared successfully. Your delivery has been routed to the restaurant kitchen.", textAlign = TextAlign.Center)
                            Text("Tx Ref: ${currentStep.transactionRef}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                            Button(
                                onClick = { viewModel.resetPaymentState() },
                                colors = ButtonDefaults.buttonColors(containerColor = EcoCashGreen)
                            ) {
                                Text("Track Delivery 🛵")
                            }
                        }
                        is PaymentStep.Error -> {
                            Icon(Icons.Default.Close, contentDescription = "Error", tint = TelecashRed, modifier = Modifier.size(64.dp))
                            Text("Payment Issue", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = TelecashRed)
                            Text(currentStep.message, textAlign = TextAlign.Center)
                            Button(
                                onClick = { viewModel.resetPaymentState() },
                                colors = ButtonDefaults.buttonColors(containerColor = TelecashRed)
                            ) {
                                Text("Acknowledge & Retry")
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }
}

// REAL-TIME TRACKING SCREEN
@Composable
fun ActiveTrackingScreen(viewModel: BiteDashViewModel) {
    val activeOrder by viewModel.activeOrder.collectAsStateWithLifecycle()
    val trackingProgress by viewModel.trackingProgress.collectAsStateWithLifecycle()
    val trackingStatusText by viewModel.trackingStatusText.collectAsStateWithLifecycle()

    if (activeOrder == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "No active tracker",
                    tint = Color.Gray,
                    modifier = Modifier.size(80.dp)
                )
                Text(
                    text = "No active deliveries",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Once you place a restaurant food order using EcoCash, InnBucks, OneMoney, O'Mari, or Bank Cards, you can view the live GPS route simulator and rider path here!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
                Button(onClick = { viewModel.selectTab(0) }) {
                    Text("Order Food First")
                }
            }
        }
    } else {
        val currentOrder = activeOrder!!
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Live Delivery Tracking",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Order #${currentOrder.id} from ${currentOrder.restaurantName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }

                    Badge(containerColor = MaterialTheme.colorScheme.secondary, contentColor = Color.Black) {
                        Text("Active Ride", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }

            // High Fidelity Custom GPS Animated Map (Harare)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        HarareCanvasMap(progress = trackingProgress)

                        // Top Float panel detailing remaining minutes
                        ElevatedCard(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(12.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                                )
                                Text(
                                    text = if (trackingProgress >= 1.0f) "Delivered!" else "ETA: ${String.format(Locale.US, "%.0f", (1.0f - trackingProgress) * 20)} mins",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }

            // Status Card
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Delivery Status Tracker",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )

                        Text(
                            text = trackingStatusText,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { trackingProgress },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    }
                }
            }

            // Courier profile
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🛵", fontSize = 24.sp)
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text("Delivery Courier", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            Text("Tinashe (BiteDash Partner)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                            Text("Riding: Black Delivery Motorbike", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }

                        IconButton(
                            onClick = { /* Call Tinashe simulation feedback */ },
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                .size(40.dp)
                        ) {
                            Icon(Icons.Default.Phone, contentDescription = "Call Courier", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

// CUSTOM CANVAS MAP HARARE RENDERING
@Composable
fun HarareCanvasMap(progress: Float) {
    // Pulse circle animation for the Courier
    val infiniteTransition = rememberInfiniteTransition(label = "gps_concentric_pulses")
    val pulseRadius = infiniteTransition.animateFloat(
        initialValue = 8f,
        targetValue = 28f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_radius"
    )
    val pulseAlpha = infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_alpha"
    )

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF1F5F9))
    ) {
        val w = size.width
        val h = size.height

        // Define Points on Map
        // Starting Point - Restaurant (Belgravia Shopping Area)
        val restPt = Offset(w * 0.18f, h * 0.72f)

        // Intersection 1 (Julius Nyerere Way)
        val int1Pt = Offset(w * 0.42f, h * 0.72f)

        // Intersection 2 (Samora Machel Avenue Turnout)
        val int2Pt = Offset(w * 0.42f, h * 0.35f)

        // Intersection 3 (Baines Avenue Bypass)
        val int3Pt = Offset(w * 0.72f, h * 0.35f)

        // Final Destination Point - Custom Customer Home (Eastlea / Avondale suburb)
        val homePt = Offset(w * 0.82f, h * 0.18f)

        // Draw Map Grid / Landscape Roads
        val roadColor = Color(0xFFE2E8F0)
        val roadOutlineColor = Color(0xFFCBD5E1)

        // Samora Machel Avenue (Main wide horizontal highway across center)
        drawLine(
            color = roadColor,
            start = Offset(0f, h * 0.35f),
            end = Offset(w, h * 0.35f),
            strokeWidth = 32f
        )
        // Julius Nyerere Way (Vertical road crossing)
        drawLine(
            color = roadColor,
            start = Offset(w * 0.42f, 0f),
            end = Offset(w * 0.42f, h),
            strokeWidth = 24f
        )
        // Belgravia Link Expressway
        drawLine(
            color = roadColor,
            start = Offset(0f, h * 0.72f),
            end = Offset(w * 0.65f, h * 0.72f),
            strokeWidth = 24f
        )
        // Chiedza Road / Baines Suburban Way
        drawLine(
            color = roadColor,
            start = Offset(w * 0.72f, h * 0.35f),
            end = Offset(w * 0.82f, h * 0.18f),
            strokeWidth = 16f
        )

        // Draw road borders/outlines for premium visual depth
        val dottedLines = Stroke(
            width = 1.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
        )
        // Samora Machel Lane divider
        drawLine(
            color = Color.White.copy(alpha = 0.8f),
            start = Offset(0f, h * 0.35f),
            end = Offset(w, h * 0.35f),
            strokeWidth = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
        )

        // Label Harare Landmarks
        // We write little visual cues on the grid
        // (Text drawing is possible, but we can do clean colored layout circles to represent buildings!)
        // National Gallery Area
        drawCircle(color = Color(0x223B82F6), center = Offset(w * 0.65f, h * 0.58f), radius = 20f)
        drawCircle(color = Color(0x1110B981), center = Offset(w * 0.25f, h * 0.20f), radius = 35f) // Park

        // Draw the Delivery Path from Restaurant -> Home
        val deliveryPath = Path().apply {
            moveTo(restPt.x, restPt.y)
            lineTo(int1Pt.x, int1Pt.y)
            lineTo(int2Pt.x, int2Pt.y)
            lineTo(int3Pt.x, int3Pt.y)
            lineTo(homePt.x, homePt.y)
        }
        drawPath(
            path = deliveryPath,
            color = Color(0xFFF35C15).copy(alpha = 0.35f),
            style = Stroke(width = 8f)
        )

        // Interpolate current Rider position based on progress
        val riderPt = when {
            progress <= 0.25f -> {
                // Segment 1: restPt -> int1Pt
                val frac = progress / 0.25f
                Offset(
                    restPt.x + (int1Pt.x - restPt.x) * frac,
                    restPt.y + (int1Pt.y - restPt.y) * frac
                )
            }
            progress <= 0.55f -> {
                // Segment 2: int1Pt -> int2Pt
                val frac = (progress - 0.25f) / 0.30f
                Offset(
                    int1Pt.x + (int2Pt.x - int1Pt.x) * frac,
                    int1Pt.y + (int2Pt.y - int1Pt.y) * frac
                )
            }
            progress <= 0.80f -> {
                // Segment 3: int2Pt -> int3Pt
                val frac = (progress - 0.55f) / 0.25f
                Offset(
                    int2Pt.x + (int3Pt.x - int2Pt.x) * frac,
                    int2Pt.y + (int3Pt.y - int2Pt.y) * frac
                )
            }
            else -> {
                // Segment 4: int3Pt -> homePt
                val frac = (progress - 0.80f) / 0.20f
                val clampedFrac = frac.coerceIn(0f, 1f)
                Offset(
                    int3Pt.x + (homePt.x - int3Pt.x) * clampedFrac,
                    int3Pt.y + (homePt.y - int3Pt.y) * clampedFrac
                )
            }
        }

        // Draw Nodes
        // 1. Restaurant Node (Belgravia Office Park Hub)
        drawCircle(color = Color.White, center = restPt, radius = 15f)
        drawCircle(color = Color(0xFFE65100), center = restPt, radius = 10f)

        // 2. Customer Home Node (Eastlea Estate)
        drawCircle(color = Color.White, center = homePt, radius = 15f)
        drawCircle(color = Color(0xFF2E7D32), center = homePt, radius = 10f)

        // 3. Draw Moving Courier Rider Tinashe
        if (progress < 1.0f) {
            // Animated Pulse rings around Rider
            drawCircle(
                color = Color(0xFFF35C15).copy(alpha = pulseAlpha.value),
                center = riderPt,
                radius = pulseRadius.value
            )
            // Main solid Rider Marker
            drawCircle(color = Color.White, center = riderPt, radius = 12f)
            drawCircle(color = Color(0xFFF35C15), center = riderPt, radius = 8f)
        } else {
            // Once finished, draw a checkmark or special crown over the home node!
            drawCircle(color = Color(0xFF2E7D32).copy(alpha = 0.3f), center = homePt, radius = 32f)
        }
    }
}

// HISTORY SCREEN
@Composable
fun HistoryScreen(viewModel: BiteDashViewModel) {
    val orderHistory by viewModel.orderHistory.collectAsStateWithLifecycle()

    if (orderHistory.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.List,
                        contentDescription = "Empty History",
                        tint = Color.Gray,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Text(
                    text = "No order history yet",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Place orders for local traditional plates or chicken buckets, and your full transaction records from EcoCash, InnBucks, OneMoney, O'Mari, and Bank Cards will populate securely in SQLite.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
                Button(onClick = { viewModel.selectTab(0) }) {
                    Text("Order Food")
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Transaction Records",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            items(orderHistory) { order ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("history_item_${order.id}"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column {
                                Text(
                                    text = order.restaurantName,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = formatTimestamp(order.timestamp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }

                            Badge(
                                containerColor = if (order.status == "COMPLETED") Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
                                contentColor = if (order.status == "COMPLETED") EcoCashGreen else Color(0xFFEF6C00)
                            ) {
                                Text(
                                    text = order.status,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Divider(color = Color.LightGray.copy(alpha = 0.3f))

                        Text(
                            text = order.itemsSummary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.DarkGray
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Paid with:", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                    Text(
                                        text = order.paymentMethod,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = when (order.paymentMethod) {
                                            "EcoCash" -> EcoCashGreen
                                            "InnBucks" -> InnBucksGold
                                            else -> TelecashRed
                                        }
                                    )
                                }
                                Text("Ph: ${order.paymentPhone}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "$${String.format(Locale.US, "%.2f", order.totalCost)} USD",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Button(
                                    onClick = { viewModel.reorderPastItems(order) },
                                    shape = CircleShape,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    modifier = Modifier
                                        .testTag("reorder_button_${order.id}")
                                        .height(32.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = "reorder", modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Reorder", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun formatTimestamp(timestamp: Long): String {
    val date = Date(timestamp)
    val format = SimpleDateFormat("MMM dd, yyyy h:mm a", Locale.getDefault())
    return format.format(date)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPortalOverlay(
    viewModel: BiteDashViewModel,
    onDismiss: () -> Unit
) {
    var activeSubTab by remember { mutableStateOf(0) } // 0: Restaurants, 1: Drivers, 2: Audits
    val restaurants by viewModel.restaurantsState.collectAsStateWithLifecycle()
    val drivers by viewModel.driversState.collectAsStateWithLifecycle()
    val orders by viewModel.orderHistory.collectAsStateWithLifecycle()

    var showAddRestaurantDialog by remember { mutableStateOf(false) }
    var showAddDriverDialog by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 16.dp, horizontal = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = "Admin icon",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "Admin Control Hub",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Manage BiteDash Operations",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_admin_hub")) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close Hub")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tab Row
                TabRow(
                    selectedTabIndex = activeSubTab,
                    containerColor = Color.Transparent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = activeSubTab == 0,
                        onClick = { activeSubTab = 0 },
                        text = { Text("Restaurants", fontSize = 13.sp) },
                        modifier = Modifier.testTag("admin_tab_restaurants")
                    )
                    Tab(
                        selected = activeSubTab == 1,
                        onClick = { activeSubTab = 1 },
                        text = { Text("Drivers", fontSize = 13.sp) },
                        modifier = Modifier.testTag("admin_tab_drivers")
                    )
                    Tab(
                        selected = activeSubTab == 2,
                        onClick = { activeSubTab = 2 },
                        text = { Text("TX Audit", fontSize = 13.sp) },
                        modifier = Modifier.testTag("admin_tab_audit")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Content
                Box(modifier = Modifier.weight(1f)) {
                    when (activeSubTab) {
                        0 -> {
                            // Restaurants List
                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${restaurants.size} Brands Registered",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Button(
                                        onClick = { showAddRestaurantDialog = true },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.height(36.dp).testTag("add_restaurant_button")
                                    ) {
                                        Text("+ Add Brand", fontSize = 12.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                if (restaurants.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("No active brands. Click Add Brand or seed to populate.", color = Color.Gray)
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(restaurants) { rest ->
                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                                border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.3f))
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(12.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(rest.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                                        Text("${rest.category} • ${rest.location}", fontSize = 12.sp, color = Color.Gray)
                                                        Text("Delivery: $${String.format(Locale.US, "%.2f", rest.deliveryFee)} • ${rest.menuItems.size} items", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                                    }
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        IconButton(
                                                            onClick = { viewModel.moveRestaurantUp(rest) },
                                                            modifier = Modifier.testTag("move_up_${rest.id}")
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.KeyboardArrowUp,
                                                                contentDescription = "Move Up",
                                                                tint = MaterialTheme.colorScheme.primary
                                                            )
                                                        }
                                                        IconButton(
                                                            onClick = { viewModel.moveRestaurantDown(rest) },
                                                            modifier = Modifier.testTag("move_down_${rest.id}")
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.KeyboardArrowDown,
                                                                contentDescription = "Move Down",
                                                                tint = MaterialTheme.colorScheme.primary
                                                            )
                                                        }
                                                        IconButton(
                                                            onClick = { viewModel.removeRestaurant(rest.id) },
                                                            modifier = Modifier.testTag("delete_rest_${rest.id}")
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Delete,
                                                                contentDescription = "Delete Restaurant",
                                                                tint = MaterialTheme.colorScheme.error
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        1 -> {
                            // Drivers List
                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${drivers.size} Riders Online",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Button(
                                        onClick = { showAddDriverDialog = true },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.height(36.dp).testTag("add_driver_button")
                                    ) {
                                        Text("+ Add Rider", fontSize = 12.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                if (drivers.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("No active drivers. Register riders to track deliveries.", color = Color.Gray)
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(drivers) { d ->
                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                                border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.3f))
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(12.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(d.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                                        Text("Phone: ${d.phone} • Vehicle: ${d.vehicle}", fontSize = 12.sp, color = Color.Gray)
                                                    }
                                                    IconButton(
                                                        onClick = { viewModel.removeDriver(d.id) },
                                                        modifier = Modifier.testTag("delete_driver_${d.id}")
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Delete,
                                                            contentDescription = "Delete Rider",
                                                            tint = MaterialTheme.colorScheme.error
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        2 -> {
                            // Transaction Audit Panel
                            Column(modifier = Modifier.fillMaxSize()) {
                                Text(
                                    text = "SQLite Transaction Ledgers",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "All mobile payments and cash transactions processed locally.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                if (orders.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("No transaction history detected in DB.", color = Color.Gray)
                                    }
                                } else {
                                    val totalSales = orders.sumOf { it.totalCost }
                                    val ecocashCount = orders.count { it.paymentMethod.contains("EcoCash", ignoreCase = true) }
                                    val innbucksCount = orders.count { it.paymentMethod.contains("InnBucks", ignoreCase = true) }
                                    val onemoneyCount = orders.count { it.paymentMethod.contains("OneMoney", ignoreCase = true) }
                                    val omariCount = orders.count { it.paymentMethod.contains("O'Mari", ignoreCase = true) }
                                    val cardsCount = orders.count { it.paymentMethod.contains("Card", ignoreCase = true) }
                                    val zipitCount = orders.count { it.paymentMethod.contains("ZIPIT", ignoreCase = true) }
                                    val cashCount = orders.count { it.paymentMethod.contains("Cash", ignoreCase = true) }

                                    Column(modifier = Modifier.fillMaxSize()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 12.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Card(
                                                modifier = Modifier.weight(1f),
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    Text("Total DB Volume", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                                    Text("USD $${String.format(Locale.US, "%.2f", totalSales)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                                    Text("${orders.size} TXs recorded", fontSize = 9.sp, color = Color.Gray)
                                                }
                                            }

                                            Card(
                                                modifier = Modifier.weight(1.3f),
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                                border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.3f))
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    Text("Channel Split", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                                    Text("📲 Eco: $ecocashCount | 🪙 Inn: $innbucksCount", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                                    Text("💎 One: $onemoneyCount | ✨ Omari: $omariCount | 💳 Card: $cardsCount", fontSize = 9.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                                                    Text("💵 Cash: $cashCount | 💸 ZIPIT: $zipitCount", fontSize = 9.sp, color = Color.Gray)
                                                }
                                            }
                                        }

                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            items(orders) { o ->
                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                                                border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.3f))
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text("TX ID: #${o.id}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                        Text(
                                                            text = o.status,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 11.sp,
                                                            color = if (o.status == "COMPLETED") EcoCashGreen else Color(0xFFEF6C00)
                                                        )
                                                    }
                                                    Text(o.restaurantName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                                    Text(o.itemsSummary, fontSize = 12.sp, color = Color.Gray)
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text("Auth: ${o.paymentMethod} • ${o.paymentPhone}", fontSize = 11.sp, color = Color.Gray)
                                                        Text("USD $${String.format(Locale.US, "%.2f", o.totalCost)}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }

    // ADD RESTAURANT DIALOG
    if (showAddRestaurantDialog) {
        var restName by remember { mutableStateOf("") }
        var restDesc by remember { mutableStateOf("") }
        var restLoc by remember { mutableStateOf("Belgravia, Harare") }
        var restCat by remember { mutableStateOf("Fast Food") }
        var restFee by remember { mutableStateOf("2.00") }
        var restTime by remember { mutableStateOf("20-30 min") }
        
        // Menu Items builder inside brand dialog
        var itemName by remember { mutableStateOf("") }
        var itemPrice by remember { mutableStateOf("3.50") }
        var itemDesc by remember { mutableStateOf("") }
        var itemCat by remember { mutableStateOf("Mains") }
        val addedMenuItems = remember { mutableStateListOf<MenuItem>() }

        Dialog(onDismissRequest = { showAddRestaurantDialog = false }) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 24.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                LazyColumn(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Text("Add New Food Brand", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Divider(color = Color.LightGray.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))
                    }

                    item {
                        OutlinedTextField(
                            value = restName,
                            onValueChange = { restName = it },
                            label = { Text("Brand Name") },
                            placeholder = { Text("e.g. Simbisa Chicken") },
                            modifier = Modifier.fillMaxWidth().testTag("add_rest_name"),
                            singleLine = true
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = restDesc,
                            onValueChange = { restDesc = it },
                            label = { Text("Description") },
                            placeholder = { Text("Zimbabwe's favorite flame grilled treats") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = restLoc,
                            onValueChange = { restLoc = it },
                            label = { Text("Location") },
                            placeholder = { Text("Borrowdale, Harare") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }

                    item {
                        Text("Category", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf("Fast Food", "Traditional", "Pizza & Grills", "Cafes & Drinks").forEach { cat ->
                                val selected = restCat == cat
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.2f))
                                        .clickable { restCat = cat }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = cat.split(" ").first(),
                                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = restFee,
                                onValueChange = { restFee = it },
                                label = { Text("Delivery Fee ($)") },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = restTime,
                                onValueChange = { restTime = it },
                                label = { Text("Time (min)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }
                    }

                    // MENU BUILDER SUB-CARD
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Menu Builder (${addedMenuItems.size} items)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                
                                OutlinedTextField(
                                    value = itemName,
                                    onValueChange = { itemName = it },
                                    label = { Text("Item Name") },
                                    placeholder = { Text("2-Piece Box") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = itemPrice,
                                        onValueChange = { itemPrice = it },
                                        label = { Text("Price ($)") },
                                        modifier = Modifier.weight(1.0f),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = itemDesc,
                                        onValueChange = { itemDesc = it },
                                        label = { Text("Item short info") },
                                        modifier = Modifier.weight(1.5f),
                                        singleLine = true
                                    )
                                }
                                Button(
                                    onClick = {
                                        if (itemName.isNotBlank() && itemPrice.isNotBlank()) {
                                            val pr = itemPrice.toDoubleOrNull() ?: 1.00
                                            addedMenuItems.add(
                                                MenuItem(
                                                    id = "item_" + System.currentTimeMillis().toString(),
                                                    name = itemName,
                                                    description = if (itemDesc.isBlank()) "Crispy fresh gourmet hot food" else itemDesc,
                                                    price = pr,
                                                    category = itemCat
                                                )
                                            )
                                            itemName = ""
                                            itemDesc = ""
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(36.dp).testTag("add_item_to_rest")
                                ) {
                                    Text("+ Append Menu Item", fontSize = 11.sp)
                                }

                                if (addedMenuItems.isNotEmpty()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        addedMenuItems.forEachIndexed { idx, it ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("• ${it.name} ($${String.format(Locale.US, "%.2f", it.price)})", fontSize = 11.sp, color = Color.Gray)
                                                IconButton(onClick = { addedMenuItems.removeAt(idx) }, modifier = Modifier.size(24.dp)) {
                                                    Icon(imageVector = Icons.Default.Close, contentDescription = null, tint = Color.Red, modifier = Modifier.size(12.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Divider(color = Color.LightGray.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = { showAddRestaurantDialog = false },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Cancel")
                            }
                            Button(
                                onClick = {
                                    if (restName.isNotBlank() && addedMenuItems.isNotEmpty()) {
                                        viewModel.addRestaurant(
                                            Restaurant(
                                                id = "res_" + System.currentTimeMillis().toString(),
                                                name = restName,
                                                description = if (restDesc.isBlank()) "Delicious local meals delivered." else restDesc,
                                                rating = 4.5,
                                                deliveryTime = restTime,
                                                deliveryFee = restFee.toDoubleOrNull() ?: 2.00,
                                                category = restCat,
                                                location = restLoc,
                                                menuItems = addedMenuItems.toList(),
                                                imageKeyword = when (restCat) {
                                                    "Traditional" -> "sadza"
                                                    "Pizza & Grills" -> "pizza"
                                                    "Cafes & Drinks" -> "cafe"
                                                    else -> "chicken"
                                                }
                                            )
                                        )
                                        showAddRestaurantDialog = false
                                    }
                                },
                                modifier = Modifier.weight(1f).testTag("save_restaurant_button"),
                                enabled = restName.isNotBlank() && addedMenuItems.isNotEmpty()
                            ) {
                                Text("Save Brand")
                            }
                        }
                    }
                }
            }
        }
    }

    // ADD DRIVER DIALOG
    if (showAddDriverDialog) {
        var dName by remember { mutableStateOf("") }
        var dPhone by remember { mutableStateOf("") }
        var dVehicle by remember { mutableStateOf("Motorbike") }

        Dialog(onDismissRequest = { showAddDriverDialog = false }) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Register New Rider", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Divider(color = Color.LightGray.copy(alpha = 0.3f))

                    OutlinedTextField(
                        value = dName,
                        onValueChange = { dName = it },
                        label = { Text("Rider Name") },
                        placeholder = { Text("e.g. Kuda") },
                        modifier = Modifier.fillMaxWidth().testTag("add_driver_name"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = dPhone,
                        onValueChange = { dPhone = it },
                        label = { Text("Zimbabwe contact phone") },
                        placeholder = { Text("e.g. 0774789123") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true
                    )

                    Text("Vehicle Type", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("Motorbike", "Bicycle", "Honda Fit").forEach { veh ->
                            val selected = dVehicle == veh
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.2f))
                                    .clickable { dVehicle = veh }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = veh,
                                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = { showAddDriverDialog = false },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                if (dName.isNotBlank() && dPhone.isNotBlank()) {
                                    viewModel.addDriver(dName, dPhone, dVehicle)
                                    showAddDriverDialog = false
                                }
                            },
                            modifier = Modifier.weight(1f).testTag("save_driver_button"),
                            enabled = dName.isNotBlank() && dPhone.isNotBlank()
                        ) {
                            Text("Save Rider")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditMenuDialog(
    restaurant: Restaurant,
    viewModel: BiteDashViewModel,
    onDismiss: () -> Unit
) {
    val localItems = remember { mutableStateListOf<MenuItem>().apply { addAll(restaurant.menuItems) } }

    var newName by remember { mutableStateOf("") }
    var newPrice by remember { mutableStateOf("") }
    var newDesc by remember { mutableStateOf("") }
    var newCat by remember { mutableStateOf("Mains") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Manage Restaurant Menu",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = restaurant.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_menu_editor")) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close Menu Editor", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }

                Divider(color = Color.LightGray.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 8.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Add New Item to Menu",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                OutlinedTextField(
                                    value = newName,
                                    onValueChange = { newName = it },
                                    label = { Text("Item Name") },
                                    modifier = Modifier.fillMaxWidth().testTag("edit_new_item_name"),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = newPrice,
                                        onValueChange = { newPrice = it },
                                        label = { Text("Price ($)") },
                                        modifier = Modifier.weight(1f).testTag("edit_new_item_price"),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                    OutlinedTextField(
                                        value = newCat,
                                        onValueChange = { newCat = it },
                                        label = { Text("Category") },
                                        modifier = Modifier.weight(1.2f),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                }
                                OutlinedTextField(
                                    value = newDesc,
                                    onValueChange = { newDesc = it },
                                    label = { Text("Short Description") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                Button(
                                    onClick = {
                                        if (newName.isNotBlank() && newPrice.isNotBlank()) {
                                            val pr = newPrice.toDoubleOrNull() ?: 1.00
                                            localItems.add(
                                                MenuItem(
                                                    id = "item_" + System.currentTimeMillis().toString(),
                                                    name = newName,
                                                    description = if (newDesc.isBlank()) "Flame grilled delicious meal" else newDesc,
                                                    price = pr,
                                                    category = newCat
                                                )
                                            )
                                            newName = ""
                                            newPrice = ""
                                            newDesc = ""
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().testTag("add_item_to_loc_list"),
                                    enabled = newName.isNotBlank() && newPrice.isNotBlank()
                                ) {
                                    Text("Add New Item +")
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            text = "Current Live Menu Items (${localItems.size})",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    if (localItems.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Menu is empty! Add items above.", color = Color.Gray)
                            }
                        }
                    } else {
                        items(localItems.size) { idx ->
                            val item = localItems[idx]
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.2f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Item #${idx + 1}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                        IconButton(
                                            onClick = { localItems.removeAt(idx) },
                                            modifier = Modifier.size(28.dp).testTag("delete_item_idx_$idx")
                                        ) {
                                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                        }
                                    }

                                    OutlinedTextField(
                                        value = item.name,
                                        onValueChange = { updatedName -> localItems[idx] = item.copy(name = updatedName) },
                                        label = { Text("Name") },
                                        modifier = Modifier.fillMaxWidth().testTag("edit_item_name_$idx"),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                        )
                                    )

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        var priceText by remember(item.id) { mutableStateOf(item.price.toString()) }
                                        OutlinedTextField(
                                            value = priceText,
                                            onValueChange = { updatedPrice ->
                                                priceText = updatedPrice
                                                val parsedPrice = updatedPrice.toDoubleOrNull() ?: item.price
                                                localItems[idx] = item.copy(price = parsedPrice)
                                            },
                                            label = { Text("Price ($)") },
                                            modifier = Modifier.weight(1f).testTag("edit_item_price_$idx"),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                            )
                                        )
                                        OutlinedTextField(
                                            value = item.category,
                                            onValueChange = { updatedCat -> localItems[idx] = item.copy(category = updatedCat) },
                                            label = { Text("Category") },
                                            modifier = Modifier.weight(1.2f),
                                            singleLine = true,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                            )
                                        )
                                    }

                                    OutlinedTextField(
                                        value = item.description,
                                        onValueChange = { updatedDesc -> localItems[idx] = item.copy(description = updatedDesc) },
                                        label = { Text("Description") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                Divider(color = Color.LightGray.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            viewModel.updateRestaurantMenu(restaurant.id, localItems.toList())
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f).testTag("save_menu_editor_button")
                    ) {
                        Text("Save Menu 💾")
                    }
                }
            }
        }
    }
}
