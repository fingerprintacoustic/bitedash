package com.example.data.payment

/**
 * Payment status enumeration for BiteDash transactions.
 * Used across payment models and business logic.
 */
enum class PaymentStatus(val displayName: String, val value: String) {
    PENDING("Pending", "PENDING"),
    PAID("Paid", "PAID"),
    FAILED("Failed", "FAILED"),
    CANCELLED("Cancelled", "CANCELLED");

    companion object {
        fun fromString(value: String): PaymentStatus {
            return entries.find { it.value == value } ?: PENDING
        }
    }
}

/**
 * Payment method enumeration for BiteDash.
 * Supports Zimbabwe mobile money and cash options.
 */
enum class PaymentMethod(val displayName: String, val value: String) {
    ECO_CASH("EcoCash", "ECO_CASH"),
    ONE_MONEY("OneMoney", "ONE_MONEY"),
    INNBUCKS("InnBucks", "INNBUCKS"),
    // The other channels the checkout offers (these values are what
    // BiteDashViewModel.mapToFirestorePaymentMethod produces for them). They all
    // go through Paynow's hosted checkout page like the mobile-money ones. They
    // were missing here, so they fell through fromString() to CASH_ON_DELIVERY and
    // checkout failed with "Cash on Delivery does not go through Paynow".
    OMARI("O'Mari", "OMARI"),
    TELECASH("Telecash", "TELECASH"),
    ZIPIT("ZIPIT", "ZIPIT"),
    BANK_CARDS("Bank Cards", "BANK_CARDS"),
    // Any other online channel: still Paynow, never cash.
    ONLINE("Online payment", "PAYNOW"),
    CASH_ON_DELIVERY("Cash on Delivery", "CASH_ON_DELIVERY");

    companion object {
        // An unrecognised method is treated as an online payment, not as cash: falling
        // back to cash meant a channel the app forgot to list could never be paid for.
        fun fromString(value: String): PaymentMethod {
            return entries.find { it.value == value } ?: ONLINE
        }
    }
}
