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
    CASH_ON_DELIVERY("Cash on Delivery", "CASH_ON_DELIVERY");

    companion object {
        fun fromString(value: String): PaymentMethod {
            return entries.find { it.value == value } ?: CASH_ON_DELIVERY
        }
    }
}
