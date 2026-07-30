package com.example.ui.screens.auth

/**
 * A country's phone dialing code, for the country picker on the phone
 * sign-in screen. Zimbabwe is first/default since it's BiteDash's home
 * market, followed by other African and diaspora-relevant countries.
 */
data class CountryCode(
    val name: String,
    val flagEmoji: String,
    val dialCode: String // includes leading '+'
)

object CountryCodes {
    val all: List<CountryCode> = listOf(
        CountryCode("Zimbabwe", "\uD83C\uDDFF\uD83C\uDDFC", "+263"),
        CountryCode("South Africa", "\uD83C\uDDFF\uD83C\uDDE6", "+27"),
        CountryCode("Botswana", "\uD83C\uDDE7\uD83C\uDDFC", "+267"),
        CountryCode("Zambia", "\uD83C\uDDFF\uD83C\uDDF2", "+260"),
        CountryCode("Mozambique", "\uD83C\uDDF2\uD83C\uDDFF", "+258"),
        CountryCode("Namibia", "\uD83C\uDDF3\uD83C\uDDE6", "+264"),
        CountryCode("Malawi", "\uD83C\uDDF2\uD83C\uDDFC", "+265"),
        CountryCode("Kenya", "\uD83C\uDDF0\uD83C\uDDEA", "+254"),
        CountryCode("Ghana", "\uD83C\uDDEC\uD83C\uDDED", "+233"),
        CountryCode("United States", "\uD83C\uDDFA\uD83C\uDDF8", "+1"),
        CountryCode("Canada", "\uD83C\uDDE8\uD83C\uDDE6", "+1"),
        CountryCode("United Kingdom", "\uD83C\uDDEC\uD83C\uDDE7", "+44"),
        CountryCode("Australia", "\uD83C\uDDE6\uD83C\uDDFA", "+61"),
        CountryCode("Ireland", "\uD83C\uDDEE\uD83C\uDDEA", "+353"),
        CountryCode("Germany", "\uD83C\uDDE9\uD83C\uDDEA", "+49"),
        CountryCode("United Arab Emirates", "\uD83C\uDDE6\uD83C\uDDEA", "+971"),
        CountryCode("China", "\uD83C\uDDE8\uD83C\uDDF3", "+86"),
    )

    val default: CountryCode = all.first() // Zimbabwe
}
