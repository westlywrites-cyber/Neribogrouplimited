package com.westly.neribogroup.data

import java.util.UUID

/**
 * Where an app's users and money come from. An app can be on any mix of the three (at least one). Google Play
 * money goes to the Google Play Balance, Apple Store money to the Apple Store Balance and Website money to the
 * Paystack Balance.
 */
enum class Channel(val label: String, val balanceName: String) {
    PLAY("Google Play", "Google Play Balance"),
    APPLE("Apple Store", "Apple Store Balance"),
    WEBSITE("Website", "Paystack Balance")
}

/** Apple keeps this percent of every subscription (the Small Business Program rate). */
const val APPLE_FEE_PERCENT = 15L

/** What Apple keeps from [gross] naira of subscriptions (rounded to the naira). */
fun appleSubscriptionFee(gross: Long): Long = (gross * APPLE_FEE_PERCENT + 50L) / 100L

/**
 * What Paystack keeps from ONE local payment: 1.5% plus N100 (the N100 is not charged on payments of N2,500
 * or less), and never more than N2,000.
 */
fun paystackPaymentFee(payment: Long): Long {
    if (payment <= 0L) return 0L
    val percent = (payment * 15L + 500L) / 1000L
    val fee = if (payment <= 2_500L) percent else percent + 100L
    return minOf(fee, 2_000L)
}

/** What the channel keeps from a set of payments (each payment is one user's subscription or renewal). */
fun channelFee(channel: Channel, payments: List<Long>): Long = when (channel) {
    Channel.PLAY -> playSubscriptionFee(payments.sum())
    Channel.APPLE -> appleSubscriptionFee(payments.sum())
    Channel.WEBSITE -> payments.sumOf { paystackPaymentFee(it) }
}

/**
 * What the backend decided for one app and one day: the share of new users per channel (basis points, they add up
 * to 10,000 over the channels that are on), and, only when asked for, the sites per user (tenths) and the storage
 * growth (basis points of the capacity). Only final numbers are kept here.
 */
data class DayRolls(
    val dayKey: String,
    val playBp: Int,
    val appleBp: Int,
    val websiteBp: Int,
    val sitesTenths: Int = 0,
    val storageBp: Int = 0
) {
    fun bpOf(channel: Channel): Int = when (channel) {
        Channel.PLAY -> playBp
        Channel.APPLE -> appleBp
        Channel.WEBSITE -> websiteBp
    }
}

/** A company document (registration papers and so on). The photo is a file on this phone ("" when none). */
data class CompanyDoc(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    /** For example "Registration", "Tax", "Licence", "Agreement". */
    val kind: String = "",
    /** A number or a short note. */
    val note: String = "",
    val addedMillis: Long,
    val hasPhoto: Boolean = false
)

/** One tool or appliance the company owns. */
data class ToolItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val quantity: Int = 1,
    val note: String = "",
    val addedMillis: Long,
    val hasPhoto: Boolean = false
)

/** The company's documents, tools and the total storage the company has (typed by the owner, in MB). */
data class CompanyAssets(
    val documents: List<CompanyDoc> = emptyList(),
    val tools: List<ToolItem> = emptyList(),
    val storageTotalMb: Long = 0L
)

object ChannelRules {

    const val MAX_DOCUMENTS = 200
    const val MAX_TOOLS = 200

    /** The channels an app is on, in the fixed order Google Play, Apple Store, Website. Empty for a Manual app. */
    fun enabled(app: AppData): List<Channel> {
        if (!app.googlePlayEnabled) return emptyList()
        val list = ArrayList<Channel>()
        if (app.playOn) list.add(Channel.PLAY)
        if (app.appleOn) list.add(Channel.APPLE)
        if (app.websiteOn) list.add(Channel.WEBSITE)
        return list
    }

    /**
     * Gives [count] new users to the channels: by the backend's shares of the day (largest share first for the left
     * overs), or evenly when the backend's answer is not there. Returns how many users each channel gets, in
     * the order of [channels].
     */
    fun divide(count: Int, channels: List<Channel>, rolls: DayRolls?, today: String): List<Int> {
        if (channels.isEmpty() || count <= 0) return List(channels.size) { 0 }
        if (channels.size == 1) return listOf(count)
        val useRolls = rolls != null && rolls.dayKey == today
        val weights = channels.map { c -> if (useRolls) maxOf(0, rolls!!.bpOf(c)) else 1 }
        val sum = weights.sum()
        val shares = if (sum <= 0) channels.map { 1 } else weights
        val total = shares.sum()
        val result = ArrayList<Int>()
        var given = 0
        for (w in shares) {
            val n = (count.toLong() * w / total).toInt()
            result.add(n)
            given += n
        }
        var left = count - given
        val order = shares.indices.sortedByDescending { shares[it] }
        var i = 0
        while (left > 0) {
            val idx = order[i % order.size]
            result[idx] = result[idx] + 1
            left -= 1
            i += 1
        }
        return result
    }

    /** Sites for [users] new Neribo Deploy users: everybody has at least 1, the day's average decides the rest. */
    fun sitesFor(users: Int, tenths: Int): Int {
        if (users <= 0) return 0
        val avg = maxOf(10, tenths)
        return maxOf(users, ((users.toLong() * avg + 5L) / 10L).toInt())
    }
}
