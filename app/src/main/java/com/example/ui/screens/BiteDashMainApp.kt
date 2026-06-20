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
import com.example.ui.viewmodel.UserProfile
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.UserRole
import com.example.ui.viewmodel.restaurant.RestaurantOrderViewModel
import com.example.ui.viewmodel.driver.DriverDeliveryViewModel
import com.example.data.firebase.FirestoreService
import com.example.ui.screens.restaurant.RestaurantOrdersScreen
import com.example.ui.screens.driver.DriverOrdersScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BiteDashMainApp(
    viewModel: BiteDashViewModel,
    authViewModel: AuthViewModel? = null,
    userRole: UserRole = UserRole.CUSTOMER,
    modifier: Modifier = Modifier
) {
    val currentProfile by viewModel.currentProfile.collectAsStateWithLifecycle()
    val isAuthenticated = authViewModel?.isAuthenticated() == true

    // If not authenticated and auth is available, show auth gate
    if (authViewModel != null && !isAuthenticated && currentProfile is UserProfile.Idle) {
        AuthenticationGate(
            authViewModel = authViewModel,
            onAuthenticated = { /* Auth handled via state */ }
        )
        return
    }

    when (val profile = currentProfile) {
        is UserProfile.Idle -> {
            RoleSelectionGate(viewModel = viewModel, authViewModel = authViewModel, userRole = userRole)
        }
        is UserProfile.RestaurantOwner -> {
            RestaurantOwnerDashboard(owner = profile, viewModel = viewModel)
        }
        is UserProfile.Driver -> {
            DriverDashboard(driver = profile, viewModel = viewModel)
        }
        else -> {
            CustomerMainScaffold(
                viewModel = viewModel,
                currentProfile = profile,
                modifier = modifier
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerMainScaffold(
    viewModel: BiteDashViewModel,
    currentProfile: UserProfile,
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
                    if (currentProfile is UserProfile.Admin) {
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
                    }
                    IconButton(
                        onClick = { viewModel.setProfile(UserProfile.Idle) },
                        modifier = Modifier.testTag("switch_role_from_customer")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "Switch Profile Role",
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
    val selectedRestaurant by viewModel.selectedRestaurant.collectAsStateWithLifecycle()
    val driverTip by viewModel.driverTip.collectAsStateWithLifecycle()

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

            // Rider Tipping Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = "Rider Tip",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                "Show appreciation! Tip your Rider",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Text(
                            "100% of tips are directly received on your road warrior delivery driver's payout shift.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val tipOptions = listOf(0.0, 1.0, 2.0, 5.0)
                            tipOptions.forEach { option ->
                                val isSelected = driverTip == option
                                OutlinedButton(
                                    onClick = { viewModel.setDriverTip(option) },
                                    colors = if (isSelected) {
                                        ButtonDefaults.outlinedButtonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    } else {
                                        ButtonDefaults.outlinedButtonColors()
                                    },
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.weight(1f)
                                        .height(48.dp) // Touch target minimum
                                ) {
                                    Text(
                                        text = if (option == 0.0) "No Tip" else "$${String.format(Locale.US, "%.0f", option)}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
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
                        val rentFee = selectedRestaurant?.deliveryFee ?: 2.00
                        val totalBill = viewModel.getCartTotal()

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Subtotal", color = Color.Gray)
                            Text("$${String.format(Locale.US, "%.2f", subtotal)}")
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Zimbabwe Delivery Rider Fee", color = Color.Gray)
                            Text("$${String.format(Locale.US, "%.2f", rentFee)}")
                        }
                        if (driverTip > 0.0) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Appreciation Tip to Rider 💖", color = Color.Gray)
                                Text("$${String.format(Locale.US, "%.2f", driverTip)}")
                            }
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
                                    text = "$${String.format(Locale.US, "%.2f", totalBill)}",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                // Interactive display matching Zimbabwe multi-currency context
                                val mockZigRate = 22.0
                                Text(
                                    text = "≈ ZiG ${String.format(Locale.US, "%.2f", totalBill * mockZigRate)}",
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
    val payoutSchedule by viewModel.payoutSchedule.collectAsStateWithLifecycle()
    val isPayoutInProgress by viewModel.isPayoutInProgress.collectAsStateWithLifecycle()

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
                                                .padding(bottom = 8.dp),
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

                                        Card(
                                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.08f)),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f))
                                        ) {
                                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Text("💼 BiteDash Central Settlement & Escrow Ledger", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                                
                                                val completedOrders = orders.filter { o -> o.status == "COMPLETED" }
                                                val completedCount = completedOrders.size
                                                
                                                val unsettledOrders = completedOrders.filter { !it.isSettled }
                                                val unsettledTotal = unsettledOrders.sumOf { it.totalCost }
                                                val unsettledRiderTips = unsettledOrders.sumOf { it.driverTip }
                                                val unsettledRiderFees = unsettledOrders.size * 2.00
                                                val unsettledRiderTotal = unsettledRiderFees + unsettledRiderTips
                                                val unsettledRawFood = (unsettledTotal - unsettledRiderTotal).coerceAtLeast(0.0)
                                                val platformCommissionPercent = 0.10
                                                val unsettledAdminRevenue = unsettledRawFood * platformCommissionPercent
                                                val unsettledRestaurantPayout = unsettledRawFood * (1.0 - platformCommissionPercent)
                                                
                                                val settledOrders = completedOrders.filter { it.isSettled }
                                                val settledTotal = settledOrders.sumOf { it.totalCost }
                                                val settledRiderTips = settledOrders.sumOf { it.driverTip }
                                                val settledRiderFees = settledOrders.size * 2.00
                                                val settledRiderTotal = settledRiderFees + settledRiderTips
                                                val settledRawFood = (settledTotal - settledRiderTotal).coerceAtLeast(0.0)
                                                val settledAdminRevenue = settledRawFood * platformCommissionPercent
                                                val settledRestaurantPayout = settledRawFood * (1.0 - platformCommissionPercent)

                                                val adminEscrowPool = unsettledTotal
                                                
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                    Column {
                                                        Text("🏦 Admin Escrow Wallet Balance", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                                                        Text("USD $${String.format(Locale.US, "%.2f", adminEscrowPool)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                    }
                                                    Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                                                        Text("Centralized Funds", fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(4.dp))
                                                    }
                                                }
                                                
                                                Divider(color = Color.LightGray.copy(alpha = 0.3f))
                                                
                                                Text("Payout Frequency Schedule:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    listOf("Daily", "Weekly", "On-Demand (Manual)").forEach { mode ->
                                                        val isSelected = (mode == "Daily" && payoutSchedule == "Daily") ||
                                                                        (mode == "Weekly" && payoutSchedule == "Weekly") ||
                                                                        (mode == "On-Demand (Manual)" && payoutSchedule == "On-Demand (Manual)")
                                                        OutlinedButton(
                                                            onClick = { viewModel.setPayoutSchedule(mode) },
                                                            modifier = Modifier.weight(1f),
                                                            shape = RoundedCornerShape(8.dp),
                                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                                            colors = ButtonDefaults.outlinedButtonColors(
                                                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                                                            ),
                                                            border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray)
                                                        ) {
                                                            Text(mode, fontSize = 10.sp, maxLines = 1)
                                                        }
                                                    }
                                                }
                                                
                                                Spacer(modifier = Modifier.height(4.dp))
                                                
                                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                        Text("🍳 Restaurant Kitchens Share (90% of Food Cost)", fontSize = 11.sp, color = Color.Gray)
                                                        Text("Pending: USD $${String.format(Locale.US, "%.2f", unsettledRestaurantPayout)}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                    Text("          Historically Settled: USD $${String.format(Locale.US, "%.2f", settledRestaurantPayout)}", fontSize = 9.sp, color = Color.Gray)
                                                    
                                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                        Text("🛵 Delivery Riders Share (Standard Fee + Tips)", fontSize = 11.sp, color = Color.Gray)
                                                        Text("Pending: USD $${String.format(Locale.US, "%.2f", unsettledRiderTotal)}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                    Text("          Historically Settled: USD $${String.format(Locale.US, "%.2f", settledRiderTotal)}", fontSize = 9.sp, color = Color.Gray)
                                                    
                                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                        Text("💻 Platform net profit share (10% Listing Charge)", fontSize = 11.sp, color = Color.Gray)
                                                        Text("Held secure: USD $${String.format(Locale.US, "%.2f", unsettledAdminRevenue)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                    }
                                                    Text("          Historically Settled: USD $${String.format(Locale.US, "%.2f", settledAdminRevenue)}", fontSize = 9.sp, color = Color.Gray)
                                                }
                                                
                                                Divider(color = Color.LightGray.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 2.dp))
                                                
                                                if (isPayoutInProgress) {
                                                    Column(
                                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                                        Text("Broadcasting bulk payouts through Zim gateways (EcoCash, ZIPIT, InnBucks)...", fontSize = 9.sp, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)
                                                    }
                                                } else {
                                                    Button(
                                                        onClick = { viewModel.triggerPayoutSettlement() },
                                                        modifier = Modifier.fillMaxWidth(),
                                                        enabled = unsettledTotal > 0,
                                                        shape = RoundedCornerShape(8.dp),
                                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                                    ) {
                                                        Icon(Icons.Default.Send, contentDescription = "Settle payouts", modifier = Modifier.size(16.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Trigger Bulk Settle to Restaurants & Riders Now", fontSize = 11.sp)
                                                    }
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
        var restOwnerUsername by remember { mutableStateOf("owner") }
        var restOwnerPassword by remember { mutableStateOf("password") }
        
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
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = restOwnerUsername,
                                onValueChange = { restOwnerUsername = it },
                                label = { Text("Owner Login ID") },
                                placeholder = { Text("e.g. owner") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = restOwnerPassword,
                                onValueChange = { restOwnerPassword = it },
                                label = { Text("Owner Password") },
                                placeholder = { Text("password") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }
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
                                                },
                                                ownerUsername = restOwnerUsername.ifBlank { "owner" },
                                                ownerPassword = restOwnerPassword.ifBlank { "password" }
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

// ==================== ROLE-BASED ACCESS CONTROL PORTALS ====================

@Composable
fun RoleSelectionGate(
    viewModel: BiteDashViewModel,
    authViewModel: AuthViewModel? = null,
    userRole: UserRole = UserRole.CUSTOMER
) {
    val restaurants by viewModel.restaurantsState.collectAsStateWithLifecycle()
    val drivers by viewModel.driversState.collectAsStateWithLifecycle()
    val isManualMode by viewModel.isManualMode.collectAsStateWithLifecycle()
    
    // Check if user is authenticated and use their role
    val isAuthenticated = authViewModel?.isAuthenticated() == true
    val currentUserRole = if (isAuthenticated) {
        authViewModel?.getCurrentRole() ?: userRole
    } else {
        userRole
    }

    var adminPinInput by remember { mutableStateOf("") }
    var adminPinError by remember { mutableStateOf("") }
    var activeSelectionTab by remember { mutableStateOf(0) } // 0: Customer, 1: Restaurant, 2: Rider, 3: Admin
    var logoTapCount by remember { mutableStateOf(0) }
    val isAdminTabVisible = logoTapCount >= 5
    var showWebDashboardSimulator by remember { mutableStateOf(false) }

    // Owner Login details
    var ownerLoginUsername by remember { mutableStateOf("") }
    var ownerLoginPassword by remember { mutableStateOf("") }
    var ownerLoginError by remember { mutableStateOf("") }
    var showOwnerSignupForm by remember { mutableStateOf(false) }
    
    // Multi ownership intermediate selection
    var selectedMatchingRests by remember { mutableStateOf<List<com.example.model.Restaurant>?>(null) }

    // Signup specific fields
    var ownerSignupName by remember { mutableStateOf("") }
    var ownerSignupDesc by remember { mutableStateOf("") }
    var ownerSignupLoc by remember { mutableStateOf("Belgravia, Harare") }
    var ownerSignupCat by remember { mutableStateOf("Fast Food") }
    var ownerSignupUsername by remember { mutableStateOf("") }
    var ownerSignupPassword by remember { mutableStateOf("") }
    var ownerSignupError by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 450.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp))
                .border(2.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Logo containing hidden click gesture to reveal Admin tab (5 taps)
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable { logoTapCount++ },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ShoppingCart,
                    contentDescription = "Logo",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            Text(
                text = "Welcome to BiteDash",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "Choose your role profile to access corresponding platform functions & dashboards.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Button(
                onClick = { showWebDashboardSimulator = true },
                modifier = Modifier.fillMaxWidth().testTag("launch_web_payout_simulator"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Web Portal Icon", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("💻 Open Desktop Web Portal Simulator", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            // Tabs for selector
            ScrollableTabRow(
                selectedTabIndex = if (activeSelectionTab == 3 && !isAdminTabVisible) 0 else activeSelectionTab,
                containerColor = Color.Transparent,
                edgePadding = 0.dp,
                divider = {}
            ) {
                Tab(selected = activeSelectionTab == 0, onClick = { activeSelectionTab = 0 }) {
                    Text("Customer", modifier = Modifier.padding(12.dp))
                }
                Tab(selected = activeSelectionTab == 1, onClick = { activeSelectionTab = 1 }) {
                    Text("Restaurant", modifier = Modifier.padding(12.dp))
                }
                Tab(selected = activeSelectionTab == 2, onClick = { activeSelectionTab = 2 }) {
                    Text("Rider", modifier = Modifier.padding(12.dp))
                }
                if (isAdminTabVisible) {
                    Tab(selected = activeSelectionTab == 3, onClick = { activeSelectionTab = 3 }) {
                        Text("Admin", modifier = Modifier.padding(12.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val currentSelectedTab = if (activeSelectionTab == 3 && !isAdminTabVisible) 0 else activeSelectionTab
            when (currentSelectedTab) {
                0 -> {
                    // CUSTOMER ROLE
                    Text(
                        text = "Access standard customer marketplace to browse restaurants, add road runners or pizzas to cart, select carrier payment integration, and track deliveries.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )

                    // Manual simulation toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
                            .clickable { viewModel.setCheckoutModeIsManual(!isManualMode) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Checkbox(
                            checked = isManualMode,
                            onCheckedChange = { viewModel.setCheckoutModeIsManual(it) }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Manual Multi-Role Mode",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Allows you to manually accept and dispatch orders as restaurant and driver from their dashboards. Recommend leaving checked!",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray,
                                fontSize = 10.sp
                            )
                        }
                    }

                    Button(
                        onClick = { viewModel.setProfile(UserProfile.Customer) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Enter Client App")
                    }
                }
                1 -> {
                    // RESTAURANT OWNER PORTAL
                    if (selectedMatchingRests != null) {
                        // MULTI-OWNERSHIP SELECTOR HUB
                        val matching = selectedMatchingRests!!
                        Text(
                            text = "Multiple Kitchen Hubs Detected",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Your owner credentials (${ownerLoginUsername}) are linked. You can manage the following registered brands. Select kitchen node:",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )

                        LazyColumn(
                            modifier = Modifier.heightIn(max = 220.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(matching) { rest ->
                                ElevatedCard(
                                    onClick = { 
                                        viewModel.setProfile(UserProfile.RestaurantOwner(rest.id, rest.name))
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(rest.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                            Text(rest.location, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                            Text("Category: ${rest.category}", fontSize = 9.sp, color = MaterialTheme.colorScheme.primary)
                                        }
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowRight,
                                            contentDescription = "Access Kitchen"
                                        )
                                    }
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = { selectedMatchingRests = null },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Switch Account / Back to Login")
                        }
                    } else if (showOwnerSignupForm) {
                        // OWNER REGISTER BRAND
                        Text(
                            text = "Register New Kitchen Brand",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Setup a physical kitchen listing. Link with credentials to allow multi-ownership switching:",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = ownerSignupName,
                                onValueChange = { ownerSignupName = it },
                                label = { Text("Kitchen/Brand Name") },
                                placeholder = { Text("e.g. Simba Grills") },
                                modifier = Modifier.fillMaxWidth().testTag("owner_signup_name"),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = ownerSignupDesc,
                                onValueChange = { ownerSignupDesc = it },
                                label = { Text("Short Slogan/Description") },
                                placeholder = { Text("Traditional flame-kissed Zimbabwean meats.") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = ownerSignupLoc,
                                onValueChange = { ownerSignupLoc = it },
                                label = { Text("Location") },
                                placeholder = { Text("Eastlea, Harare") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            // Horiz chips for Cat
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf("Fast Food", "Traditional", "Pizza & Grills", "Cafes & Drinks").forEach { cat ->
                                    val isSel = ownerSignupCat == cat
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSel) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.2f))
                                            .clickable { ownerSignupCat = cat }
                                            .padding(vertical = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(cat.split(" ").first(), color = if (isSel) Color.White else Color.DarkGray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Divider(color = Color.LightGray.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))

                            Text("Secure Login Credentials", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                            Text("If you own another restaurant, use its exact credentials here to link them together!", fontSize = 9.sp, color = Color.Gray)

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = ownerSignupUsername,
                                    onValueChange = { ownerSignupUsername = it },
                                    label = { Text("Login ID") },
                                    placeholder = { Text("owner") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = ownerSignupPassword,
                                    onValueChange = { ownerSignupPassword = it },
                                    label = { Text("Password") },
                                    placeholder = { Text("password") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }

                            if (ownerSignupError.isNotEmpty()) {
                                Text(ownerSignupError, color = Color.Red, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { showOwnerSignupForm = false },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Back to Sign In")
                                }
                                Button(
                                    onClick = {
                                        if (ownerSignupName.isBlank() || ownerSignupUsername.isBlank() || ownerSignupPassword.isBlank()) {
                                            ownerSignupError = "Please fill name, username & password!"
                                        } else {
                                            val newId = "res_" + System.currentTimeMillis()
                                            val sampleItems = listOf(
                                                com.example.model.MenuItem(
                                                    id = "item_${newId}_1",
                                                    name = "Signature Platter",
                                                    description = "Our legendary chef-selected special flame grilled meat with standard portion of chips.",
                                                    price = 7.99,
                                                    category = "Mains"
                                                ),
                                                com.example.model.MenuItem(
                                                    id = "item_${newId}_2",
                                                    name = "Sweet Zimbabwean Lemonade",
                                                    description = "Squeezed fresh daily with local organic Chimanimani lemons.",
                                                    price = 2.50,
                                                    category = "Drinks"
                                                )
                                            )
                                            viewModel.addRestaurant(
                                                com.example.model.Restaurant(
                                                    id = newId,
                                                    name = ownerSignupName,
                                                    description = if (ownerSignupDesc.isBlank()) "Flame grilled local specialties." else ownerSignupDesc,
                                                    rating = 4.5,
                                                    deliveryTime = "15-30 min",
                                                    deliveryFee = 2.00,
                                                    category = ownerSignupCat,
                                                    location = ownerSignupLoc,
                                                    menuItems = sampleItems,
                                                    imageKeyword = when (ownerSignupCat) {
                                                        "Traditional" -> "sadza"
                                                        "Pizza & Grills" -> "pizza"
                                                        "Cafes & Drinks" -> "cafe"
                                                        else -> "chicken"
                                                    },
                                                    ownerUsername = ownerSignupUsername.trim(),
                                                    ownerPassword = ownerSignupPassword.trim()
                                                )
                                            )
                                            // Reset signup fields
                                            ownerSignupName = ""
                                            ownerSignupDesc = ""
                                            ownerSignupError = ""
                                            showOwnerSignupForm = false
                                            
                                            // Pre-fill log in credentials
                                            ownerLoginUsername = ownerSignupUsername.trim()
                                            ownerLoginPassword = ownerSignupPassword.trim()
                                        }
                                    },
                                    modifier = Modifier.weight(1.2f).testTag("register_restaurant_brand_btn"),
                                ) {
                                    Text("Register Kitchen")
                                }
                            }
                        }
                    } else {
                        // LOG IN FORM
                        Text(
                            text = "Kitchen Hub Authenticator",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Secure kitchen node gateway for Zimbabwean brand operators & managers.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = ownerLoginUsername,
                                onValueChange = { 
                                    ownerLoginUsername = it
                                    ownerLoginError = "" 
                                },
                                label = { Text("Owner Login ID (Username/Phone)") },
                                placeholder = { Text("owner") },
                                modifier = Modifier.fillMaxWidth().testTag("owner_login_username"),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = ownerLoginPassword,
                                onValueChange = { 
                                    ownerLoginPassword = it
                                    ownerLoginError = ""
                                },
                                label = { Text("Owner Password") },
                                placeholder = { Text("password") },
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth().testTag("owner_login_password"),
                                singleLine = true
                            )

                            if (ownerLoginError.isNotEmpty()) {
                                Text(ownerLoginError, color = Color.Red, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    val matched = restaurants.filter {
                                        it.ownerUsername.trim().lowercase() == ownerLoginUsername.trim().lowercase() &&
                                        it.ownerPassword.trim() == ownerLoginPassword.trim()
                                    }
                                    if (matched.isEmpty()) {
                                        ownerLoginError = "Incorrect credentials. Try 'owner' and 'password' or register a new brand."
                                    } else if (matched.size == 1) {
                                        viewModel.setProfile(UserProfile.RestaurantOwner(matched.first().id, matched.first().name))
                                    } else {
                                        // Store discovered restaurants for intermediate selector
                                        selectedMatchingRests = matched
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().testTag("owner_login_btn"),
                                shape = RoundedCornerShape(12.dp),
                                enabled = ownerLoginUsername.isNotBlank() && ownerLoginPassword.isNotBlank()
                            ) {
                                Text("Sign In & Open Kitchen")
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = {
                                        ownerLoginUsername = "owner"
                                        ownerLoginPassword = "password"
                                        ownerLoginError = ""
                                    }
                                ) {
                                    Text("🔑 Quick Demo Fill", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                TextButton(
                                    onClick = { 
                                        showOwnerSignupForm = true
                                        ownerSignupUsername = ownerLoginUsername
                                        ownerSignupPassword = ownerLoginPassword
                                    }
                                ) {
                                    Text("📝 Register New Kitchen", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                2 -> {
                    // DRIVER
                    Text(
                        "Join as active courier rider to claim delivery jobs and view routing maps:",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )

                    LazyColumn(
                        modifier = Modifier.heightIn(max = 180.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(drivers) { d ->
                            ElevatedCard(
                                onClick = { viewModel.setProfile(UserProfile.Driver(d.id, d.name)) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(d.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                        Text("${d.vehicle} • ${d.phone}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                    }
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowRight,
                                        contentDescription = "Select"
                                    )
                                }
                            }
                        }
                    }
                    if (drivers.isEmpty()) {
                        Text("No drivers registered. Login as Admin to register, approve or remove riders.", style = MaterialTheme.typography.bodySmall, color = Color.Red)
                    }
                }
                3 -> {
                    // SYSTEM ADMIN PORTAL
                    Text(
                        "Requires system passcode verification to access all features: viewing transaction reports, adding/approving/removing drivers, and approving/removing restaurants.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )

                    OutlinedTextField(
                        value = adminPinInput,
                        onValueChange = {
                            adminPinInput = it
                            adminPinError = ""
                        },
                        placeholder = { Text("Enter Admin Passcode") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        isError = adminPinError.isNotEmpty(),
                        supportingText = {
                            if (adminPinError.isNotEmpty()) {
                                Text(adminPinError, color = MaterialTheme.colorScheme.error)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    Button(
                        onClick = {
                            if (adminPinInput == "2026" || adminPinInput == "1980" || adminPinInput == "9999" || adminPinInput.trim().lowercase() == "admin") {
                                viewModel.setProfile(UserProfile.Admin)
                                adminPinInput = ""
                            } else {
                                adminPinError = "Access Denied. Invalid Admin Passcode."
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Unlock Admin Privileges")
                    }
                }
            }
        }

        if (showWebDashboardSimulator) {
            WebDashboardSimulatorDialog(
                onDismiss = { showWebDashboardSimulator = false },
                viewModel = viewModel
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestaurantOwnerDashboard(
    owner: UserProfile.RestaurantOwner,
    viewModel: BiteDashViewModel
) {
    val orderHistory by viewModel.orderHistory.collectAsStateWithLifecycle()
    val restaurants by viewModel.restaurantsState.collectAsStateWithLifecycle()
    
    val restaurant = restaurants.find { it.id == owner.restaurantId }
    var isEditMenuOpen by remember { mutableStateOf(false) }
    var showSwitchKitchenDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }

    // Create restaurant order view model for new order management
    val restaurantViewModel = remember(owner.restaurantId) {
        RestaurantOrderViewModel(FirestoreService(), owner.restaurantId)
    }

    val matchedOrders = orderHistory.filter { it.restaurantName.trim().lowercase() == owner.restaurantName.trim().lowercase() }
    val activeOrders = matchedOrders.filter { it.status != "COMPLETED" }
    val completedOrders = matchedOrders.filter { it.status == "COMPLETED" }

    val currentOwnerUser = restaurant?.ownerUsername ?: ""
    val currentOwnerPass = restaurant?.ownerPassword ?: ""
    
    val otherOwnedRestaurants = if (currentOwnerUser.isNotEmpty() && currentOwnerPass.isNotEmpty()) {
        restaurants.filter { 
            it.ownerUsername.lowercase().trim() == currentOwnerUser.lowercase().trim() &&
            it.ownerPassword.trim() == currentOwnerPass.trim() &&
            it.id != owner.restaurantId
        }
    } else {
        emptyList()
    }

    if (showSwitchKitchenDialog && otherOwnedRestaurants.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showSwitchKitchenDialog = false },
            title = {
                Text("Switch Active Kitchen Node", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Select another linked kitchen directly:", fontSize = 12.sp, color = Color.Gray)
                    otherOwnedRestaurants.forEach { otherRest ->
                        val otherMatched = orderHistory.filter { it.restaurantName.trim().lowercase() == otherRest.name.trim().lowercase() }
                        val otherPending = otherMatched.filter { it.status != "COMPLETED" }.size
                        
                        ElevatedCard(
                            onClick = {
                                viewModel.setProfile(UserProfile.RestaurantOwner(otherRest.id, otherRest.name))
                                showSwitchKitchenDialog = false
                            },
                            modifier = Modifier.fillMaxWidth().clickable {
                                viewModel.setProfile(UserProfile.RestaurantOwner(otherRest.id, otherRest.name))
                                showSwitchKitchenDialog = false
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(otherRest.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Text(otherRest.location, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                }
                                Badge(containerColor = if (otherPending > 0) MaterialTheme.colorScheme.primary else Color.LightGray) {
                                    Text("$otherPending pending", fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSwitchKitchenDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(owner.restaurantName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                            Text("Kitchen Hub", fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                },
                actions = {
                    if (otherOwnedRestaurants.isNotEmpty()) {
                        FilledTonalButton(
                            onClick = { showSwitchKitchenDialog = true },
                            modifier = Modifier.padding(end = 4.dp).testTag("dashboard_switch_kitchen_btn"),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Switch Node", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Switch Kitchen (${otherOwnedRestaurants.size})", fontSize = 10.sp)
                        }
                    }
                    TextButton(onClick = { viewModel.setProfile(UserProfile.Idle) }) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Switch profile")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Switch Role")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Order Management") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Dashboard") }
                )
            }

            when (selectedTab) {
                0 -> {
                    RestaurantOrdersScreen(
                        viewModel = restaurantViewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                1 -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Stats Summary Card
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceAround
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Total Fulfillments", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                                        Text("$" + String.format(Locale.US, "%.2f", completedOrders.sumOf { it.totalCost }), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Orders Queue", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                                        Text("${activeOrders.size} Pending", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }

                        // Quick Actions Block
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { isEditMenuOpen = true },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.List, contentDescription = "Manage Menu", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Manage Menu Logs", fontSize = 12.sp)
                                }
                            }
                        }

                        // Active Orders List
                        item {
                            Text(
                                "Incoming Orders Queue",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (activeOrders.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No active customer orders currently queued.", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        } else {
                            items(activeOrders) { order ->
                                ElevatedCard(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Order #${order.id}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                            Badge(
                                                containerColor = when (order.status) {
                                                    "PENDING_ACCEPTANCE" -> MaterialTheme.colorScheme.tertiaryContainer
                                                    "PREPARING" -> MaterialTheme.colorScheme.primaryContainer
                                                    else -> MaterialTheme.colorScheme.secondaryContainer
                                                }
                                            ) {
                                                Text(order.status)
                                            }
                                        }

                                        Text(order.itemsSummary, style = MaterialTheme.typography.bodyMedium)

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Payout: $" + String.format(Locale.US, "%.2f", order.totalCost) + " (" + order.paymentMethod + ")", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                            Text("Tel: ${order.paymentPhone}", color = Color.Gray, fontSize = 12.sp)
                                        }

                                        Divider(color = Color.LightGray.copy(alpha = 0.3f))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (order.status == "PENDING_ACCEPTANCE") {
                                                Button(
                                                    onClick = { viewModel.updateOrderStatusManual(order.id, "PREPARING") },
                                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                                ) {
                                                    Text("Accept & Start Cook")
                                                }
                                            } else if (order.status == "PREPARING") {
                                                Button(
                                                    onClick = { viewModel.updateOrderStatusManual(order.id, "READY_FOR_PICKUP") },
                                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                                ) {
                                                    Text("Mark Cooked & Ready")
                                                }
                                            } else if (order.status == "READY_FOR_PICKUP") {
                                                Text("Waiting for Rider Pickup...", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                                            } else {
                                                Text("Rider is delivering...", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Past Completed History
                        item {
                            Text(
                                "Delivered Orders Ledger",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (completedOrders.isEmpty()) {
                            item {
                                Text("No historical transactions fulfilled under this kitchen shift.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            items(completedOrders) { order ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("Order #${order.id} • ${order.itemsSummary}", fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, fontSize = 13.sp)
                                            Text("Paid $" + String.format(Locale.US, "%.2f", order.totalCost) + " via " + order.paymentMethod, fontSize = 11.sp, color = Color.Gray)
                                        }
                                        Icon(Icons.Default.CheckCircle, contentDescription = "Completed", tint = Color.Green, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (isEditMenuOpen && restaurant != null) {
        EditMenuDialog(
            restaurant = restaurant,
            viewModel = viewModel,
            onDismiss = { isEditMenuOpen = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverDashboard(
    driver: UserProfile.Driver,
    viewModel: BiteDashViewModel
) {
    val orderHistory by viewModel.orderHistory.collectAsStateWithLifecycle()
    val trackingProgress by viewModel.trackingProgress.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }

    // Create driver delivery view model
    val driverViewModel = remember(driver.driverId) {
        DriverDeliveryViewModel(FirestoreService(), driver.driverId, driver.driverName)
    }

    val claimableOrders = orderHistory.filter { (it.status == "READY_FOR_PICKUP" || it.status == "PREPARING") && it.driverId == null }
    val myActiveOrders = orderHistory.filter { it.status == "OUT_FOR_DELIVERY" && it.driverId == driver.driverId }
    val myCompletedOrders = orderHistory.filter { it.status == "COMPLETED" && it.driverId == driver.driverId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(driver.driverName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                            Text("Rider active", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.setProfile(UserProfile.Idle) }) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Switch profile")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Switch Role")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("My Deliveries") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Dashboard") }
                )
            }

            when (selectedTab) {
                0 -> {
                    DriverOrdersScreen(
                        viewModel = driverViewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                1 -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Rider shift earnings
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                            ) {
                                val baseEarnings = myCompletedOrders.size * 2.00
                                val tipsEarnings = myCompletedOrders.sumOf { it.driverTip }
                                val totalEarnings = baseEarnings + tipsEarnings

                                Column(
                                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceAround
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Deliveries", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                                            Text("${myCompletedOrders.size} runs", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Base Fees", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                                            Text("$" + String.format(Locale.US, "%.2f", baseEarnings), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Rider Tips", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                                            Text("$" + String.format(Locale.US, "%.2f", tipsEarnings), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = EcoCashGreen)
                                        }
                                    }
                                    Divider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Total Shift Payout", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                        Text("$" + String.format(Locale.US, "%.2f", totalEarnings), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }

                        // Active claim delivery Map Simulation
                        if (myActiveOrders.isNotEmpty()) {
                            item {
                                Text(
                                    "Your Current Active Run",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            items(myActiveOrders) { order ->
                                ElevatedCard(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Order #${order.id} Run", fontWeight = FontWeight.Bold)
                                            Text("Status: Out For Delivery", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        }

                                        Text("Deliver from: ${order.restaurantName}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text("Cargo: ${order.itemsSummary}\nPhone client: ${order.paymentPhone}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)

                                        // Rider progress indicator bar
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text("Your Simulation Route Progress:", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                            LinearProgressIndicator(
                                                progress = trackingProgress,
                                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                                            )
                                        }

                                        Button(
                                            onClick = { viewModel.updateOrderStatusManual(order.id, "COMPLETED") },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                                        ) {
                                            Icon(Icons.Default.Check, contentDescription = "Deliver")
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Confirm Doorstep Delivery & Handover")
                                        }
                                    }
                                }
                            }
                        }

                        // Driver Jobs Board (orders prepared or ready)
                        item {
                            Text(
                                "Harare Delivery Jobs Pool",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        val jobs = claimableOrders
                        if (jobs.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No deliveries currently in the queue. Ask a client to place a manual delivery order!", style = MaterialTheme.typography.bodyMedium, color = Color.Gray, textAlign = TextAlign.Center)
                                }
                            }
                        } else {
                            items(jobs) { job ->
                                Card(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Job #${job.id} • ${job.restaurantName}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                            Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                                                Text(job.status)
                                            }
                                        }

                                        Text("Cargo: ${job.itemsSummary}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                        Text("Est Payout: $2.00 (Standard Delivery Surcharge)", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)

                                        Button(
                                            onClick = { viewModel.claimOrderManual(job.id, driver.driverId, driver.driverName) },
                                            modifier = Modifier.fillMaxWidth(),
                                            enabled = job.status == "READY_FOR_PICKUP"
                                        ) {
                                            Text(if (job.status == "READY_FOR_PICKUP") "Pick up & Start Journey" else "Waiting for kitchen to finish cooking...")
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebDashboardSimulatorDialog(
    onDismiss: () -> Unit,
    viewModel: BiteDashViewModel
) {
    val orders by viewModel.orderHistory.collectAsStateWithLifecycle()
    val restaurants by viewModel.restaurantsState.collectAsStateWithLifecycle()
    val drivers by viewModel.driversState.collectAsStateWithLifecycle()
    val payoutSchedule by viewModel.payoutSchedule.collectAsStateWithLifecycle()
    val isPayoutInProgress by viewModel.isPayoutInProgress.collectAsStateWithLifecycle()

    var activeWebTab by remember { mutableStateOf(0) } // 0: Restaurant Center, 1: Rider Statement, 2: Admin Escrow Console
    var selectedWebRestaurantId by remember { mutableStateOf<String?>(null) }
    var selectedWebDriverId by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(restaurants) {
        if (restaurants.isNotEmpty() && selectedWebRestaurantId == null) {
            selectedWebRestaurantId = restaurants.first().id
        }
    }
    LaunchedEffect(drivers) {
        if (drivers.isNotEmpty() && selectedWebDriverId == null) {
            selectedWebDriverId = drivers.first().id
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF1F5F9))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E293B))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFEF4444)))
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFF59E0B)))
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF10B981)))
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color(0xFF334155), RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = "Secure padlock", tint = Color(0xFF10B981), modifier = Modifier.size(12.dp))
                        Text(
                            "https://bitedash.co.zw/portal-dashboard",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close window", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }

                Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    Column(
                        modifier = Modifier
                            .width(200.dp)
                            .fillMaxHeight()
                            .background(Color(0xFF0F172A))
                            .padding(vertical = 16.dp, horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "💻 BiteDash Web",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 12.dp, start = 8.dp)
                        )

                        listOf(
                            Triple(0, "🏬 Restaurant Center", Icons.Default.Home),
                            Triple(1, "🛵 Rider Statement", Icons.Default.Person),
                            Triple(2, "💼 Admin Escrow Console", Icons.Default.Settings)
                        ).forEach { (idx, label, icon) ->
                            val isActive = activeWebTab == idx
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isActive) Color(0xFF334155) else Color.Transparent)
                                    .clickable { activeWebTab = idx }
                                    .padding(vertical = 10.dp, horizontal = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(icon, contentDescription = label, tint = if (isActive) Color.White else Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                                Text(label, color = if (isActive) Color.White else Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        Text(
                            "Multi-Device Sync Live",
                            color = Color(0xFF10B981),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(Color(0xFFF8FAFC))
                            .padding(20.dp)
                    ) {
                        when (activeWebTab) {
                            0 -> {
                                Text("🏬 Restaurant Cloud Management Portal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                                Text("Select and configure active kitchen menus, audit real-time orders, and modify USD pricing live.", style = MaterialTheme.typography.bodySmall, color = Color.Gray, modifier = Modifier.padding(bottom = 16.dp))

                                if (restaurants.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("No registered restaurants available in SQLite database.", color = Color.Gray, fontSize = 12.sp)
                                    }
                                } else {
                                    val matchedRestaurant = restaurants.find { it.id == selectedWebRestaurantId } ?: restaurants.first()
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Acting Kitchen:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        restaurants.forEach { r ->
                                            val isSelected = r.id == matchedRestaurant.id
                                            OutlinedButton(
                                                onClick = { selectedWebRestaurantId = r.id },
                                                border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray),
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                                ),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Text(r.name, fontSize = 9.sp)
                                            }
                                        }
                                    }

                                    Divider(color = Color.LightGray.copy(alpha = 0.5f))

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Card(
                                            modifier = Modifier.weight(1.2f).fillMaxHeight(),
                                            colors = CardDefaults.cardColors(containerColor = Color.White),
                                            border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text("Dish USD Price Editor", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF1E293B))
                                                Text("Changes take effect instantly on client apps.", fontSize = 9.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))
                                                
                                                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    items(matchedRestaurant.menuItems) { dish ->
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .background(Color(0xFFF1F5F9), RoundedCornerShape(6.dp))
                                                                .padding(6.dp),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Column(modifier = Modifier.weight(1f)) {
                                                                Text(dish.name, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                                                Text("USD $${String.format(Locale.US, "%.2f", dish.price)}", fontSize = 9.sp, color = Color.Gray)
                                                            }
                                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                                                IconButton(
                                                                    onClick = {
                                                                        val updated = matchedRestaurant.menuItems.map {
                                                                            if (it.name == dish.name) it.copy(price = (it.price - 0.50).coerceAtLeast(1.0)) else it
                                                                        }
                                                                        viewModel.updateRestaurantMenu(matchedRestaurant.id, updated)
                                                                    },
                                                                    modifier = Modifier.size(24.dp)
                                                                ) {
                                                                    Text("-", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                                }
                                                                IconButton(
                                                                    onClick = {
                                                                        val updated = matchedRestaurant.menuItems.map {
                                                                            if (it.name == dish.name) it.copy(price = it.price + 0.50) else it
                                                                        }
                                                                        viewModel.updateRestaurantMenu(matchedRestaurant.id, updated)
                                                                    },
                                                                    modifier = Modifier.size(24.dp)
                                                                ) {
                                                                    Text("+", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        Card(
                                            modifier = Modifier.weight(1f).fillMaxHeight(),
                                            colors = CardDefaults.cardColors(containerColor = Color.White),
                                            border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                val kitchenOrders = orders.filter { it.restaurantName.trim().lowercase() == matchedRestaurant.name.trim().lowercase() }
                                                Text("Live Kitchen Queue (${kitchenOrders.size})", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF1E293B))
                                                Text("Current pending and preparable dining tickets.", fontSize = 9.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))
                                                
                                                if (kitchenOrders.isEmpty()) {
                                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                        Text("No tickets in queue", color = Color.Gray, fontSize = 9.sp)
                                                    }
                                                } else {
                                                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        items(kitchenOrders) { order ->
                                                            Column(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .border(1.dp, Color.LightGray.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                                                    .padding(6.dp)
                                                            ) {
                                                                Text("Order #${order.id} • ${order.status}", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                                Text(order.itemsSummary, fontSize = 9.sp, maxLines = 1)
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
                                Text("🛵 Rider Hub Weekly Earnings Statement", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                                Text("Riders can access their statements, monitor total deliveries completed, verify payout schedules, and check EcoCash numbers.", style = MaterialTheme.typography.bodySmall, color = Color.Gray, modifier = Modifier.padding(bottom = 16.dp))

                                if (drivers.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("No active drivers found. Build some in Admin portal first!", color = Color.Gray, fontSize = 12.sp)
                                    }
                                } else {
                                    val matchedDriver = drivers.find { it.id == selectedWebDriverId } ?: drivers.first()
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Acting Rider:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        drivers.forEach { d ->
                                            val isSelected = d.id == matchedDriver.id
                                            OutlinedButton(
                                                onClick = { selectedWebDriverId = d.id },
                                                border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray),
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                                ),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Text(d.name, fontSize = 9.sp)
                                            }
                                        }
                                    }

                                    Divider(color = Color.LightGray.copy(alpha = 0.5f))

                                    Spacer(modifier = Modifier.height(12.dp))

                                    val riderRuns = orders.filter { it.driverId == matchedDriver.id && it.status == "COMPLETED" }
                                    val riderTips = riderRuns.sumOf { it.driverTip }
                                    val riderFees = riderRuns.size * 2.00
                                    val totalDue = riderFees + riderTips
                                    
                                    val unsettledRiderRuns = orders.filter { it.driverId == matchedDriver.id && it.status == "COMPLETED" && !it.isSettled }
                                    val unsettledDue = unsettledRiderRuns.size * 2.00 + unsettledRiderRuns.sumOf { it.driverTip }

                                    Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Card(
                                            modifier = Modifier.weight(1f).fillMaxHeight(),
                                            colors = CardDefaults.cardColors(containerColor = Color.White),
                                            border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
                                        ) {
                                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text("Weekly Rider Statement", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF1E293B))
                                                Text("Settle ID: ${matchedDriver.phone}@ecocash", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                                                
                                                Divider(color = Color.LightGray.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))
                                                
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                    Text("Completed Runs:", fontSize = 11.sp, color = Color.Gray)
                                                    Text("${riderRuns.size} Deliveries", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                    Text("Delivery Fuel Fee Surcharge:", fontSize = 11.sp, color = Color.Gray)
                                                    Text("USD $${String.format(Locale.US, "%.2f", riderFees)}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                    Text("100% Customer Tips:", fontSize = 11.sp, color = Color.Gray)
                                                    Text("USD $${String.format(Locale.US, "%.2f", riderTips)}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                                
                                                Divider(color = Color.LightGray.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))
                                                
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                    Text("Historic Earnings:", fontSize = 11.sp, color = Color.Gray)
                                                    Text("USD $${String.format(Locale.US, "%.2f", totalDue)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                                }

                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                    Text("Current Unsettled Escrow Pending:", fontSize = 11.sp, color = Color.DarkGray, fontWeight = FontWeight.SemiBold)
                                                    Text("USD $${String.format(Locale.US, "%.2f", unsettledDue)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                }
                                                
                                                Spacer(modifier = Modifier.height(10.dp))
                                                
                                                Text("Settlements are triggered by Admin to EcoCash / InnBucks wallet based on payout schedule ($payoutSchedule).", fontSize = 9.sp, color = Color.Gray)
                                            }
                                        }

                                        Card(
                                            modifier = Modifier.weight(1f).fillMaxHeight(),
                                            colors = CardDefaults.cardColors(containerColor = Color.White),
                                            border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
                                        ) {
                                            Column(modifier = Modifier.padding(14.dp)) {
                                                Text("Historic Runs List", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF1E293B))
                                                Spacer(modifier = Modifier.height(8.dp))
                                                
                                                if (riderRuns.isEmpty()) {
                                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                        Text("No completed jobs on record.", color = Color.Gray, fontSize = 9.sp)
                                                    }
                                                } else {
                                                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        items(riderRuns) { order ->
                                                            Row(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .border(1.dp, Color.LightGray.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                                                    .padding(6.dp),
                                                                horizontalArrangement = Arrangement.SpaceBetween
                                                            ) {
                                                                Column {
                                                                    Text("Job #${order.id} • ${order.restaurantName}", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                                    Text("Cargo: ${order.itemsSummary}", fontSize = 8.sp, color = Color.Gray, maxLines = 1)
                                                                }
                                                                Text("USD $${String.format(Locale.US, "%.2f", 2.00 + order.driverTip)}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            2 -> {
                                Text("💻 Platform Admin Central Escrow Management Console", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                                Text("This admin interface provides visual escrow stats and automated mobile wallet distribution of EcoCash, OneMoney, InnBucks, and CABS bank payouts Zimbabwe-wide.", style = MaterialTheme.typography.bodySmall, color = Color.Gray, modifier = Modifier.padding(bottom = 14.dp))

                                val unsettledTotal = orders.filter { it.status == "COMPLETED" && !it.isSettled }.sumOf { it.totalCost }
                                
                                Card(
                                    modifier = Modifier.fillMaxWidth().weight(1f),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text("Escrow Ledger & Wallet Distribution Settle", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF1E293B))
                                        
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Column {
                                                Text("BiteDash Central Holding (All Channels):", fontSize = 10.sp, color = Color.Gray)
                                                Text("USD $${String.format(Locale.US, "%.2f", unsettledTotal)}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                            }
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                                            ) {
                                                Text("Schedule: $payoutSchedule", fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp), color = MaterialTheme.colorScheme.onSecondaryContainer)
                                            }
                                        }

                                        Divider(color = Color.LightGray.copy(alpha = 0.3f))

                                        Text("Zimbabwe Bulk payment distribution triggers via API nodes instantly:", fontSize = 10.sp, color = Color.Gray)
                                        
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Card(modifier = Modifier.weight(1f), border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.2f)), colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC))) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    Text("📲 EcoCash Merchant Bulk", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                    Text("EcoCash: 1.5% Settlement Surcharge", fontSize = 8.sp, color = Color.Gray)
                                                }
                                            }
                                            Card(modifier = Modifier.weight(1f), border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.2f)), colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC))) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    Text("🪙 InnBucks Core Payout", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                    Text("Instant USSD settlement route", fontSize = 8.sp, color = Color.Gray)
                                                }
                                            }
                                            Card(modifier = Modifier.weight(1f), border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.2f)), colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC))) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    Text("💸 ZIPIT / CABS Bank", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                    Text("Electronic interbank clearing node", fontSize = 8.sp, color = Color.Gray)
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.weight(1f))

                                        if (isPayoutInProgress) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)).padding(10.dp),
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Broadcasting mobile payout packages... Syncing ledger to phone nodes", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                            }
                                        } else {
                                            Button(
                                                onClick = { viewModel.triggerPayoutSettlement() },
                                                modifier = Modifier.fillMaxWidth(),
                                                enabled = unsettledTotal > 0,
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                            ) {
                                                Text("Disburse held funds to matching restaurants & riders", fontSize = 11.sp)
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
