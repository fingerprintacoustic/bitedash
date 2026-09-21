package com.example.ui.screens.help

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth

/** The roles a person can use BiteDash as, each with its own how-to guide. */
enum class GuideRole(val label: String, val headline: String) {
    CUSTOMER("Customer", "Ordering food"),
    RESTAURANT("Restaurant", "Running your kitchen"),
    DRIVER("Rider", "Delivering orders"),
    ADMIN("Administrator", "Managing BiteDash")
}

private data class GuideStep(val title: String, val body: String)

private fun stepsFor(role: GuideRole): List<GuideStep> = when (role) {
    GuideRole.CUSTOMER -> listOf(
        GuideStep(
            "Find a restaurant",
            "Browse lists the restaurants near you. Search by name or dish, or tap a category. " +
                "A restaurant marked \"Coming Soon\" hasn't been approved yet and can't be ordered from."
        ),
        GuideStep(
            "Build your cart",
            "Open a restaurant and tap Add on each item. Items that are sold out are hidden. " +
                "Change quantities from the Cart tab."
        ),
        GuideStep(
            "Check out",
            "In Cart, optionally add a tip for your rider, choose how to pay (a mobile-money channel " +
                "or USD Cash on delivery), confirm your delivery address and phone number, then place the order."
        ),
        GuideStep(
            "Cash on delivery",
            "Choose USD Cash to pay the rider in cash when the food arrives. Nothing is charged in the app, " +
                "and the rider sees exactly how much to collect."
        ),
        GuideStep(
            "Follow your order",
            "The Tracking tab shows live progress: waiting for the restaurant, preparing, ready for pickup, " +
                "out for delivery, then delivered."
        ),
        GuideStep(
            "Past orders",
            "History lists your earlier orders, and Reorder puts the same items back in your cart."
        ),
        GuideStep(
            "Your account",
            "The person icon lets you edit your name, phone and address, and sign out. " +
                "The arrow icon (Switch Role) takes you back to the role screen."
        )
    )

    GuideRole.RESTAURANT -> listOf(
        GuideStep(
            "Set up your restaurant",
            "After signing up as a Restaurant, enter your name, a short description, location, category, " +
                "delivery fee and delivery time, then tap Create My Restaurant."
        ),
        GuideStep(
            "Wait for approval",
            "A banner shows while your restaurant is pending admin approval. Customers see it as \"Coming Soon\" " +
                "until an admin approves it. You can set up your menu in the meantime."
        ),
        GuideStep(
            "Build your menu",
            "On the Dashboard tab, tap Manage Menu. Add each item with a name, price, category and description, " +
                "then tap Save Menu."
        ),
        GuideStep(
            "Mark items sold out",
            "In Manage Menu, switch an item to Sold Out to hide it from customers without deleting it. " +
                "Switch it back when it's available again, and Save Menu."
        ),
        GuideStep(
            "Handle orders",
            "Order Management shows incoming orders. Accept or Reject a new one, tap Start Preparing, then " +
                "Mark Ready for Pickup so a rider can claim it. The header shows how many still need your action."
        ),
        GuideStep(
            "Cash orders",
            "Cash on Delivery orders are marked unpaid. The rider collects the money at the customer's door."
        ),
        GuideStep(
            "Add staff",
            "On the Dashboard tab, Staff Access > Manage lets you add staff by email so they can help run orders."
        ),
        GuideStep(
            "Switching roles",
            "Switch Role at the top right takes you back to the role screen."
        )
    )

    GuideRole.DRIVER -> listOf(
        GuideStep(
            "Register",
            "After signing up as a Delivery Driver, enter your name and phone, choose your vehicle, " +
                "and tap Register as Rider."
        ),
        GuideStep(
            "Wait for approval",
            "You'll see \"Registration Submitted\" until an admin approves you. Once approved you can see and claim deliveries."
        ),
        GuideStep(
            "Find deliveries",
            "In My Deliveries, the Available tab lists orders that are ready for pickup. " +
                "Tap Accept Delivery to claim one; it moves to My Orders."
        ),
        GuideStep(
            "Make the delivery",
            "Under My Orders: Navigate to Restaurant, tap Pick Up Order, Navigate to Customer, " +
                "then tap Mark Delivered once you've handed the food over."
        ),
        GuideStep(
            "Cash on delivery",
            "For cash orders the card shows \"Collect cash on delivery\" with the amount to collect from the customer."
        ),
        GuideStep(
            "Your earnings",
            "The Dashboard tab shows your deliveries, base fees, rider tips and total payout."
        ),
        GuideStep(
            "Switching roles",
            "Switch Role at the top right takes you back to the role screen."
        )
    )

    GuideRole.ADMIN -> listOf(
        GuideStep(
            "Open the hub",
            "Sign in as an administrator, tap Unlock Admin Privileges, then use the gear icon at the top of the " +
                "customer screen to open the Admin Control Hub. Swipe the tab row sideways to see every tab."
        ),
        GuideStep(
            "Restaurants",
            "New restaurants show as Pending. Approve them here before customers can order. You can also add a " +
                "brand, change the order restaurants are listed in, or delete one."
        ),
        GuideStep(
            "Drivers",
            "New riders show as Pending. Approve a rider before they can see or claim deliveries."
        ),
        GuideStep(
            "Users",
            "Look an account up by email and change its role: Customer, Restaurant, Delivery Driver or Administrator. " +
                "Swipe the role chips sideways to see them all, then tap Apply Role Change."
        ),
        GuideStep(
            "Orders, transactions and payouts",
            "The Orders, TX Audit and Payouts tabs let you watch orders, review payment transactions, and settle payouts."
        ),
        GuideStep(
            "Paynow",
            "The Paynow tab holds the payment gateway settings. Only administrators can read them, so keep them safe."
        )
    )
}

private fun guideKey(role: GuideRole): String {
    // Per account, so someone signing in on a shared phone still gets their own first-time guide.
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous"
    return "seen_${role.name}_$uid"
}

private fun hasSeenGuide(context: Context, role: GuideRole): Boolean =
    try {
        context.getSharedPreferences("role_guides", Context.MODE_PRIVATE).getBoolean(guideKey(role), false)
    } catch (e: Exception) {
        // If preferences can't be read, don't nag every time.
        true
    }

private fun markGuideSeen(context: Context, role: GuideRole) {
    try {
        context.getSharedPreferences("role_guides", Context.MODE_PRIVATE).edit().putBoolean(guideKey(role), true).apply()
    } catch (e: Exception) {
        // Best effort only.
    }
}

/**
 * A help icon for a top bar. Tapping it opens this role's how-to guide, and the guide also opens by
 * itself the first time an account uses the role (unless [autoShow] is false).
 */
@Composable
fun RoleGuideButton(role: GuideRole, autoShow: Boolean = true) {
    val context = LocalContext.current
    var open by remember(role) { mutableStateOf(autoShow && !hasSeenGuide(context, role)) }

    IconButton(
        onClick = { open = true },
        modifier = Modifier.testTag("role_guide_button_${role.name.lowercase()}")
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = "How to use BiteDash as a ${role.label.lowercase()}",
            tint = MaterialTheme.colorScheme.primary
        )
    }

    if (open) {
        RoleGuideDialog(role = role, onDismiss = {
            markGuideSeen(context, role)
            open = false
        })
    }
}

@Composable
fun RoleGuideDialog(role: GuideRole, onDismiss: () -> Unit) {
    val steps = stepsFor(role)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(role.headline, fontWeight = FontWeight.Bold)
                Text(
                    "How to use BiteDash as a ${role.label.lowercase()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                steps.forEachIndexed { index, step ->
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "${index + 1}",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(step.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                step.body,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Text(
                    "You can reopen this any time from the (i) icon at the top of the screen.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("role_guide_got_it")) { Text("Got it") }
        }
    )
}
