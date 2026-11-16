package com.westly.opay.feature.receipt

import com.westly.opay.data.model.Bank
import kotlinx.serialization.Serializable
import java.net.URI
import java.util.Locale
import kotlin.math.abs

private const val LOGO_HOST = "cdn.jsdelivr.net"
private const val LOGO_BASE = "https://cdn.jsdelivr.net/gh/supermx1/nigerian-banks-api@main/logos/"
private const val MIN_CONTAINED_LENGTH = 4

private val DroppedWords = setOf(
    "bank", "plc", "ltd", "limited", "of", "nigeria", "the", "microfinance", "mfb", "mf"
)
private val ParenthesisText = Regex("\\([^)]*\\)")
private val NonAlphanumeric = Regex("[^a-z0-9 ]")

@Serializable
data class LogoBank(
    val name: String = "",
    val slug: String = "",
    val code: String = "",
    val logo: String = ""
)

object BankLogoMatcher {

    fun normalise(name: String): String =
        name.replace(ParenthesisText, " ")
            .lowercase(Locale.ROOT)
            .replace("&", "and")
            .replace(NonAlphanumeric, "")
            .split(' ')
            .filter { it.isNotEmpty() && it !in DroppedWords }
            .joinToString("")

    fun find(bankName: String?, appBanks: List<Bank>, catalog: List<LogoBank>): LogoBank? {
        if (bankName.isNullOrBlank()) return null
        val key = normalise(bankName)
        if (key.isEmpty()) return null

        val code = appBanks.firstOrNull { normalise(it.name) == key }?.code?.trim().orEmpty()
        if (code.isNotEmpty()) {
            catalog.firstOrNull { it.code.trim() == code }?.let { return it }
        }

        catalog.firstOrNull { normalise(it.name) == key }?.let { return it }

        return catalog
            .map { it to normalise(it.name) }
            .filter { (_, normalised) -> containsEachOther(normalised, key) }
            .minByOrNull { (_, normalised) -> abs(normalised.length - key.length) }
            ?.first
    }

    fun logoUrl(entry: LogoBank): String? {
        val logo = entry.logo.trim()
        val slug = entry.slug.trim()
        val candidate = when {
            logo.startsWith("https://") -> logo
            slug.isNotEmpty() -> "$LOGO_BASE$slug.png"
            else -> return null
        }
        val host = try {
            URI(candidate).host
        } catch (e: java.net.URISyntaxException) {
            null
        }
        return if (host.equals(LOGO_HOST, ignoreCase = true)) candidate else null
    }

    fun isOpayBank(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val normalised = normalise(name)
        return normalised.startsWith("opay") || normalised.startsWith("paycom")
    }

    private fun containsEachOther(first: String, second: String): Boolean {
        val shorter = if (first.length <= second.length) first else second
        val longer = if (first.length <= second.length) second else first
        return shorter.length >= MIN_CONTAINED_LENGTH && longer.contains(shorter)
    }
}

fun isOpayBank(name: String?): Boolean = BankLogoMatcher.isOpayBank(name)
