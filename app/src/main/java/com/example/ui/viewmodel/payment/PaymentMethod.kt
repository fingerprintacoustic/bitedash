package com.example.ui.viewmodel.payment

/**
 * Payment methods supported by BiteDash.
 * 
 * These map to the payment options available in Zimbabwe:
 * - PAYNOW: Paynow online payment (card, bank, mobile)
 * - ECOCASH: Econet EcoCash mobile money
 * - ONEMONEY: NetOne OneMoney mobile money
 * - INNBUCKS: InnBucks mobile money
 * - CASH: Cash on delivery
 */
enum class PaymentMethod(
    val displayName: String,
    val value: String,
    val description: String,
    val requiresPhoneNumber: Boolean
) {
    PAYNOW(
        displayName = "Paynow",
        value = "PAYNOW",
        description = "Pay online with card, bank, or mobile money",
        requiresPhoneNumber = false
    ),
    ECOCASH(
        displayName = "EcoCash",
        value = "ECOCASH",
        description = "Pay with your EcoCash account",
        requiresPhoneNumber = true
    ),
    ONEMONEY(
        displayName = "OneMoney",
        value = "ONEMONEY",
        description = "Pay with your OneMoney account",
        requiresPhoneNumber = true
    ),
    INNBUCKS(
        displayName = "InnBucks",
        value = "INNBUCKS",
        description = "Pay with your InnBucks account",
        requiresPhoneNumber = true
    ),
    CASH(
        displayName = "Cash on Delivery",
        value = "CASH",
        description = "Pay with cash when your order arrives",
        requiresPhoneNumber = false
    );
    
    companion object {
        /**
         * Get payment method from string value.
         */
        fun fromString(value: String): PaymentMethod {
            return entries.find { it.value == value } ?: PAYNOW
        }
        
        /**
         * Get mobile money methods only.
         */
        fun mobileMoneyMethods(): List<PaymentMethod> {
            return listOf(ECOCASH, ONEMONEY, INNBUCKS)
        }
        
        /**
         * Get online payment methods.
         */
        fun onlineMethods(): List<PaymentMethod> {
            return listOf(PAYNOW)
        }
        
        /**
         * Get offline payment methods.
         */
        fun offlineMethods(): List<PaymentMethod> {
            return listOf(CASH)
        }
    }
}
