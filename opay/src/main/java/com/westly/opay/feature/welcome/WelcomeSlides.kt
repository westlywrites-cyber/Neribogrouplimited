package com.westly.opay.feature.welcome

enum class WelcomeArt { Welcome, Send, Bills, Save, Secure }

data class WelcomeSlide(
    val headline: String,
    val subText: String,
    val art: WelcomeArt
)

val welcomeSlides: List<WelcomeSlide> = listOf(
    WelcomeSlide(
        headline = "Welcome to OPay",
        subText = "Fast, safe and simple payments in one app.",
        art = WelcomeArt.Welcome
    ),
    WelcomeSlide(
        headline = "Send money for free",
        subText = "Transfer to any bank account in seconds.",
        art = WelcomeArt.Send
    ),
    WelcomeSlide(
        headline = "Pay your bills",
        subText = "Airtime, data, TV and electricity in a few taps.",
        art = WelcomeArt.Bills
    ),
    WelcomeSlide(
        headline = "Save and grow",
        subText = "Earn daily interest on your savings.",
        art = WelcomeArt.Save
    ),
    WelcomeSlide(
        headline = "Secure by design",
        subText = "Your PIN and fingerprint keep your money safe.",
        art = WelcomeArt.Secure
    )
)
