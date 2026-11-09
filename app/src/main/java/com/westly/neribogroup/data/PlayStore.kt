package com.westly.neribogroup.data

import java.util.UUID

/** Google Play keeps this percent of every subscription and every renewal. */
const val PLAY_FEE_PERCENT = 15L

/**
 * Google keeps part of ad money too. Google publishes a 68% payout to the publisher for AdSense
 * (so Google keeps 32%). It does not publish a separate split for AdMob, so this is the only
 * published figure available. Change this one number to change the ads cut everywhere.
 */
const val ADS_PAYOUT_PERCENT = 68L

/** What Google Play keeps from one subscription or renewal of [gross] naira (rounded to the naira). */
fun playSubscriptionFee(gross: Long): Long = (gross * PLAY_FEE_PERCENT + 50L) / 100L

/** What is paid out from [gross] naira of ad money after Google's share. */
fun adsNetAfterGoogle(gross: Long): Long = gross * ADS_PAYOUT_PERCENT / 100L

enum class PlayTxKind { SUBSCRIPTION, RENEWAL, ADS }

/** One line in the Google Play Balance: what came in, what Google kept and what reached the balance. */
data class PlayTx(
    val id: String = UUID.randomUUID().toString(),
    val kind: PlayTxKind,
    val label: String,
    val gross: Long,
    val fee: Long,
    val net: Long,
    val dateMillis: Long
)

/** One withdrawal from the Google Play Balance into profit. */
data class Withdrawal(
    val id: String = UUID.randomUUID().toString(),
    val dateMillis: Long,
    val amount: Long,
    /** An app name, or "All apps". */
    val label: String,
    /** For "All apps": which app gave how much. */
    val note: String = ""
)

enum class CampaignSize(val label: String, val factor: Double) {
    SMALL("Small", 1.5),
    MEDIUM("Medium", 2.0),
    BIG("Big", 3.0)
}

/**
 * An advertising boost for one app. Days are "yyyy-MM-dd" labels, so they compare as text.
 * [stoppedDayKey] is set when the campaign is stopped early: that day and later get no boost.
 */
data class Campaign(
    val sizeName: String,
    val startDayKey: String,
    val endDayKey: String,
    val stoppedDayKey: String = ""
) {
    val size: CampaignSize
        get() = runCatching { CampaignSize.valueOf(sizeName) }.getOrDefault(CampaignSize.SMALL)

    fun isActiveOn(day: String): Boolean =
        day >= startDayKey && day <= endDayKey && (stoppedDayKey.isEmpty() || day < stoppedDayKey)
}

/** What one daily roll did, kept as history. [faces] follows DiceKind order; -1 means that dice was hidden. */
data class RollRecord(
    val dayKey: String,
    val faces: List<Int>,
    val newUsers: Long,
    val active: Long,
    val premium: Long,
    val pro: Long,
    val renewed: Long,
    val lapsed: Long,
    val subsNet: Long,
    val adsNet: Long,
    val campaign: Boolean,
    /** Users brought by adverts on this roll (on top of the dice users). */
    val advertUsers: Long = 0
)
