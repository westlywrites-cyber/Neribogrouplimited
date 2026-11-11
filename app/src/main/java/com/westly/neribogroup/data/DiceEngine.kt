package com.westly.neribogroup.data

import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sqrt
import kotlin.random.Random

/** The seven dice. The order here is the order of [RollRecord.faces]. */
enum class DiceKind { NEW, ACTIVE, PREMIUM, PRO, RENEW, LOST, ADS }

enum class RollBlock { OK, ALREADY_ROLLED, DATE_WENT_BACK }

/** One made-up person offered by Claude for the NEW dice. It is checked before it is used. */
class PresetIdentity(val name: String, val email: String, val phone: String)

/**
 * Faces and identities chosen by the AI. [faces] is indexed by DiceKind.ordinal (size 7, each -1..5);
 * -1 means "no answer for this dice". [identities] are candidates for the NEW dice, in order.
 * [allowedDomains] are the only email domains an identity may use.
 */
class PresetRoll(
    val faces: IntArray,
    val identities: List<PresetIdentity>,
    val allowedDomains: List<String> = IdentityGenerator.DEFAULT_AI_DOMAINS
)

/**
 * The daily dice. Every dice shows 0 to 5. 0 always means nothing happens.
 * 1 to 5 are bigger and bigger ranges that depend on how big the app is.
 * Nothing here needs the internet or AI.
 */
object DiceEngine {

    const val MAX_CAMPAIGN_DAYS = 14
    private const val MAX_NEW_PER_ROLL = 400

    // Dice 1 to 5 for new users: a range of people per day for an app with 1,000 users.
    private val NEW_RANGES = arrayOf(1 to 5, 6 to 12, 13 to 20, 21 to 30, 31 to 45)

    // Dice 1 to 5 as a share of a group of people (low, high).
    private val ACTIVE_SHARE = arrayOf(
        0.03 to 0.08, 0.08 to 0.15, 0.15 to 0.25, 0.25 to 0.35, 0.35 to 0.50
    )
    private val UPGRADE_SHARE = arrayOf(
        0.0005 to 0.0015, 0.0015 to 0.003, 0.003 to 0.006, 0.006 to 0.010, 0.010 to 0.018
    )
    private val RENEW_SHARE = arrayOf(
        0.20 to 0.35, 0.35 to 0.50, 0.50 to 0.65, 0.65 to 0.80, 0.80 to 0.95
    )
    private val LOST_SHARE = arrayOf(
        0.05 to 0.10, 0.10 to 0.20, 0.20 to 0.30, 0.30 to 0.45, 0.45 to 0.60
    )

    // Dice 1 to 5 for ads: naira per free user per day before Google's share (low, high).
    private val ADS_RATE = arrayOf(
        0.3 to 0.8, 0.8 to 1.6, 1.6 to 2.8, 2.8 to 4.2, 4.2 to 6.5
    )

    private fun priceOf(app: AppData, plan: Plan): Long = when (plan) {
        Plan.PREMIUM -> app.premiumPrice
        Plan.PRO -> app.proPrice
        Plan.FREE -> 0L
    }

    /** Dice users on the Free plan. Only these can upgrade. */
    private fun upgradePool(app: AppData): List<AppUser> =
        app.users.filter { it.simulated && it.plan == Plan.FREE }

    /** Dice users whose paid plan has ended and whose plan still exists (has a price). */
    private fun renewalPool(app: AppData, now: Long): List<AppUser> =
        app.users.filter { user ->
            user.simulated && user.plan != Plan.FREE &&
                (user.planDaysLeft(now) ?: 1L) <= 0L && priceOf(app, user.plan) > 0L
        }

    /** Whether the dice can be rolled now. One roll per calendar day, by the phone's date. */
    fun rollBlock(app: AppData, now: Long): RollBlock {
        val today = dayKey(now)
        val last = app.lastRollDayKey
        return when {
            last.isEmpty() -> RollBlock.OK
            last == today -> RollBlock.ALREADY_ROLLED
            last > today -> RollBlock.DATE_WENT_BACK
            else -> RollBlock.OK
        }
    }

    /** Only the dice that can bring something real are shown. */
    fun visibleDice(app: AppData, now: Long): List<DiceKind> {
        val list = ArrayList<DiceKind>()
        list.add(DiceKind.NEW)
        if (app.totalUsers >= 1L) list.add(DiceKind.ACTIVE)
        val canUpgrade = upgradePool(app).isNotEmpty()
        if (app.premiumPrice > 0L && canUpgrade) list.add(DiceKind.PREMIUM)
        if (app.proPrice > 0L && canUpgrade) list.add(DiceKind.PRO)
        if (renewalPool(app, now).isNotEmpty()) {
            list.add(DiceKind.RENEW)
            list.add(DiceKind.LOST)
        }
        if (app.adsEnabled && app.freeUsers >= 1L) list.add(DiceKind.ADS)
        return list
    }

    // -----------------------------------------------------------------------------------------
    // Turning a dice face into a real number
    // -----------------------------------------------------------------------------------------

    private fun newUsersFor(face: Int, total: Long, factor: Double, random: Random): Int {
        if (face <= 0) return 0
        val scale = max(0.1, sqrt(total / 1000.0))
        val range = NEW_RANGES[face - 1]
        val low = max(1, (range.first * scale).roundToInt())
        val high = max(low, (range.second * scale).roundToInt())
        val base = random.nextInt(low, high + 1)
        return min(MAX_NEW_PER_ROLL, max(1, (base * factor).roundToInt()))
    }

    private fun activeFor(face: Int, total: Long, factor: Double, random: Random): Long {
        if (face <= 0 || total <= 0L) return 0L
        val range = ACTIVE_SHARE[face - 1]
        val boost = 1.0 + (factor - 1.0) / 2.0
        val share = random.nextDouble(range.first, range.second) * boost
        return min(total, (total * share).roundToLong())
    }

    /** How many people out of [pool] a dice face picks. At least 1 for a face above 0, never more than [pool]. */
    private fun countFromShare(
        pool: Int,
        face: Int,
        table: Array<Pair<Double, Double>>,
        factor: Double,
        weight: Double,
        random: Random
    ): Int {
        if (face <= 0 || pool <= 0) return 0
        val range = table[face - 1]
        val share = random.nextDouble(range.first, range.second) * weight * factor
        return min(pool, max(1, (pool * share).roundToInt()))
    }

    private fun tx(kind: PlayTxKind, label: String, gross: Long, fee: Long, now: Long, channel: Channel = Channel.PLAY): PlayTx =
        PlayTx(kind = kind, label = label, gross = gross, fee = fee, net = gross - fee, dateMillis = now, channel = channel)

    /**
     * One balance line per channel for users who each paid their own price. The money goes to the balance of the
     * channel the user came through, after that channel's own fee (Google Play, Apple Store or Paystack).
     */
    private fun channelLines(
        kind: PlayTxKind,
        users: List<AppUser>,
        priceOf: (AppUser) -> Long,
        one: String,
        many: String,
        now: Long
    ): List<PlayTx> {
        val out = ArrayList<PlayTx>()
        for (channel in Channel.values()) {
            val group = users.filter { it.channel == channel }
            if (group.isEmpty()) continue
            val payments = group.map { priceOf(it) }
            val label = plural(group.size, one, many) + (if (channel == Channel.PLAY) "" else " (" + channel.label + ")")
            out.add(tx(kind, label, payments.sum(), channelFee(channel, payments), now, channel))
        }
        return out
    }

    /**
     * Gives the dice's new users their channel (by the backend's split of the day, or evenly when the backend's
     * answer is not there) and, for a Neribo Deploy style app, their sites (the backend's average of the day).
     */
    private fun placeNewUsers(users: List<AppUser>, app: AppData, today: String, random: Random): List<AppUser> {
        if (users.isEmpty()) return users
        val channels = ChannelRules.enabled(app).ifEmpty { listOf(Channel.PLAY) }
        val counts = ChannelRules.divide(users.size, channels, app.dayRolls, today)
        val placed = ArrayList<AppUser>()
        var index = 0
        for (i in channels.indices) {
            for (k in 0 until counts[i]) {
                placed.add(users[index].copy(channel = channels[i]))
                index += 1
            }
        }
        if (!app.deployTracking) return placed
        val tenths = app.dayRolls?.takeIf { it.dayKey == today }?.sitesTenths ?: 10
        val extra = ChannelRules.sitesFor(placed.size, tenths) - placed.size
        val sites = IntArray(placed.size) { 1 }
        for (k in 0 until extra) {
            val at = random.nextInt(placed.size)
            sites[at] = sites[at] + 1
        }
        return placed.mapIndexed { i, u -> u.copy(sites = sites[i]) }
    }

    private fun plural(count: Int, one: String, many: String): String =
        if (count == 1) "1 $one" else "$count $many"

    /** Draws one random face (0 to 5) for every visible dice, in dice order. -1 for a hidden dice. */
    fun drawFaces(random: Random, visible: List<DiceKind>): List<Int> =
        DiceKind.values().map { kind -> if (kind in visible) random.nextInt(0, 6) else -1 }

    /** New users for a roll: the names Claude gave (after checks), then made-up ones for the rest. */
    private fun makeNewUsers(
        count: Int,
        ledger: Ledger,
        now: Long,
        dayStart: Long,
        random: Random,
        preset: PresetRoll?
    ): List<AppUser> {
        val given = preset?.identities
        if (preset == null || given == null || given.isEmpty()) {
            return IdentityGenerator.make(count, ledger, now, dayStart, random)
        }
        val accepted = IdentityGenerator.acceptAiIdentities(
            given, count, ledger, now, dayStart, random, preset.allowedDomains
        )
        val rest = count - accepted.size
        val generated = if (rest > 0) {
            IdentityGenerator.make(rest, ledger, now, dayStart, random, accepted)
        } else {
            emptyList()
        }
        return accepted + generated
    }

    /** The largest number of new users the NEW dice (face 5) could create for this app today. */
    fun maxNewUsers(app: AppData, now: Long = System.currentTimeMillis()): Int {
        val factor = app.campaign?.takeIf { it.isActiveOn(dayKey(now)) }?.size?.factor ?: 1.0
        val scale = max(0.1, sqrt(app.totalUsers / 1000.0))
        val range = NEW_RANGES[NEW_RANGES.size - 1]
        val low = max(1, (range.first * scale).roundToInt())
        val high = max(low, (range.second * scale).roundToInt())
        return min(MAX_NEW_PER_ROLL, max(1, (high * factor).roundToInt()))
    }

    /** One plain sentence about what the dice controls. */
    fun diceMeaning(kind: DiceKind): String = when (kind) {
        DiceKind.NEW -> "Decides how many new Free users join the app today."
        DiceKind.ACTIVE -> "Decides how many of the app's users are active today."
        DiceKind.PREMIUM -> "Decides how many Free users upgrade to Premium today."
        DiceKind.PRO -> "Decides how many Free users upgrade to Pro today."
        DiceKind.RENEW -> "Decides how many users whose plan ended renew it today."
        DiceKind.LOST -> "Decides how many users whose plan ended do not renew and go back to Free."
        DiceKind.ADS -> "Decides how much Google Ads money the app earns today."
    }

    /** The name of the dice as the owner knows it (colour and job). */
    fun diceTitle(kind: DiceKind): String = when (kind) {
        DiceKind.NEW -> "Green dice - new users"
        DiceKind.ACTIVE -> "Blue dice - active users"
        DiceKind.PREMIUM -> "Yellow dice - Premium upgrades"
        DiceKind.PRO -> "Purple dice - Pro upgrades"
        DiceKind.RENEW -> "Orange dice - renewals"
        DiceKind.LOST -> "Red dice - plans not renewed"
        DiceKind.ADS -> "Gold dice - Google Ads"
    }

    private fun trimNumber(value: Double): String {
        val text = when {
            value >= 10.0 -> String.format(java.util.Locale.US, "%.0f", value)
            value >= 1.0 -> String.format(java.util.Locale.US, "%.1f", value)
            else -> String.format(java.util.Locale.US, "%.2f", value)
        }
        return if (text.contains('.')) text.trimEnd('0').trimEnd('.') else text
    }

    private fun percentRange(range: Pair<Double, Double>, multiplier: Double): String =
        trimNumber(range.first * multiplier * 100.0) + " to " + trimNumber(range.second * multiplier * 100.0) + " %"

    /**
     * Six one-line effects (face 0 to 5) of one dice for this app today, made from the same numbers
     * the roll uses. The ranges are the dice's own; nothing here changes the app.
     */
    fun faceTable(app: AppData, kind: DiceKind, now: Long): List<String> {
        val factor = app.campaign?.takeIf { it.isActiveOn(dayKey(now)) }?.size?.factor ?: 1.0
        val boost = 1.0 + (factor - 1.0) / 2.0
        val scale = max(0.1, sqrt(app.totalUsers / 1000.0))
        val rows = ArrayList<String>()
        rows.add("Nothing happens.")
        for (face in 1..5) {
            val index = face - 1
            val text = when (kind) {
                DiceKind.NEW -> {
                    val range = NEW_RANGES[index]
                    val low = max(1, (range.first * scale).roundToInt())
                    val high = max(low, (range.second * scale).roundToInt())
                    val lowF = min(MAX_NEW_PER_ROLL, max(1, (low * factor).roundToInt()))
                    val highF = min(MAX_NEW_PER_ROLL, max(lowF, (high * factor).roundToInt()))
                    "About $lowF to $highF new users join."
                }
                DiceKind.ACTIVE ->
                    "About " + percentRange(ACTIVE_SHARE[index], boost) + " of all users are active (never fewer than today's new users)."
                DiceKind.PREMIUM ->
                    "About " + percentRange(UPGRADE_SHARE[index], factor) + " of Free users upgrade to Premium (at least 1 person)."
                DiceKind.PRO ->
                    "About " + percentRange(UPGRADE_SHARE[index], factor * 0.5) + " of the Free users left upgrade to Pro (at least 1 person)."
                DiceKind.RENEW ->
                    "About " + percentRange(RENEW_SHARE[index], 1.0) + " of the people whose plan ended renew (at least 1 person)."
                DiceKind.LOST ->
                    "About " + percentRange(LOST_SHARE[index], 1.0) + " of the people whose plan ended do not renew."
                DiceKind.ADS -> {
                    val range = ADS_RATE[index]
                    "About " + trimNumber(range.first) + " to " + trimNumber(range.second) +
                        " naira per Free user, before Google's share."
                }
            }
            rows.add(text)
        }
        return rows
    }

    /**
     * Rolls every dice of one app for today and returns the new ledger.
     * Returns the ledger unchanged if the app is missing or today's roll is not allowed.
     * Money goes only into the app's Google Play Balance, never straight into profit.
     */
    fun roll(
        ledger: Ledger,
        appId: String,
        now: Long,
        random: Random = Random.Default,
        preset: PresetRoll? = null
    ): Ledger {
        val (rolled, facts) = rollWithFacts(ledger, appId, now, random, preset)
        if (facts == null) return rolled
        return CaseGenerator.maybeCreate(rolled, appId, facts, now, random)
    }

    /**
     * The same roll as [roll], but it does NOT create a customer case. It returns the rolled ledger and what
     * the roll really did ([RollFacts]). The facts are null (and the ledger is the very same object) when the
     * roll was not possible. Phase 5: AI mode decides the case skeleton from these facts.
     */
    fun rollWithFacts(
        ledger: Ledger,
        appId: String,
        now: Long,
        random: Random = Random.Default,
        preset: PresetRoll? = null
    ): Pair<Ledger, RollFacts?> {
        val app = ledger.apps.firstOrNull { it.id == appId } ?: return Pair(ledger, null)
        if (!app.googlePlayEnabled) return Pair(ledger, null)
        if (rollBlock(app, now) != RollBlock.OK) return Pair(ledger, null)

        val today = dayKey(now)
        val dayStart = dayStartMillis(now)
        val campaignToday = app.campaign?.takeIf { it.isActiveOn(today) }
        val factor = campaignToday?.size?.factor ?: 1.0

        val visible = visibleDice(app, now)
        // The random faces are always drawn, so the random numbers used later are the same with or
        // without a preset. A preset face only replaces the drawn face of a visible dice.
        val drawn = drawFaces(random, visible)
        val faces = DiceKind.values().map { kind ->
            val given = if (preset != null && kind in visible) preset.faces.getOrElse(kind.ordinal) { -1 } else -1
            if (given in 0..5) given else drawn[kind.ordinal]
        }
        fun face(kind: DiceKind): Int = faces[kind.ordinal]
        fun timeToday(): Long = dayStart + random.nextLong(max(1L, now - dayStart))

        var users: List<AppUser> = app.users
        var lapsed = 0

        // A paid plan that no longer exists (price 0) lapses by itself when it ends.
        val dead = users.filter { user ->
            user.simulated && user.plan != Plan.FREE &&
                (user.planDaysLeft(now) ?: 1L) <= 0L && priceOf(app, user.plan) <= 0L
        }.map { it.id }.toSet()
        if (dead.isNotEmpty()) {
            users = users.map {
                if (it.id in dead) it.copy(plan = Plan.FREE, planStartMillis = 0L, planExpiryMillis = 0L) else it
            }
            lapsed += dead.size
        }

        val lines = ArrayList<PlayTx>()

        // New users (green). They are Free users with a made-up name, email and phone.
        val newCount = newUsersFor(face(DiceKind.NEW), app.totalUsers, factor, random)
        val newUsers = if (newCount > 0) {
            placeNewUsers(makeNewUsers(newCount, ledger, now, dayStart, random, preset), app, today, random)
        } else {
            emptyList()
        }

        // Adverts: users that celebrities' and blogs' followers bring. The backend already decided how many
        // arrive and how many of them start on Premium or Pro the same day; they come on top of the dice and
        // are NOT multiplied by the Campaign. No random numbers are drawn when the app has no advert users.
        val delivery = AdvertEngine.deliverAll(app, today)
        val advertTotal = delivery.users
        var advertRows: List<AppUser> = emptyList()
        var advertPremium = 0
        var advertPro = 0
        var audienceApple = 0L
        var audienceWebsite = 0L
        if (advertTotal > 0L) {
            val room = max(0, AdvertEngine.MAX_STORED_USER_ROWS_PER_APP - (app.users.size + newCount))
            val rowCap = min(AdvertEngine.MAX_ROWS_PER_ROLL, room)
            val rows = ArrayList<AppUser>()
            for (channel in Channel.values()) {
                val part = delivery.byChannel[channel] ?: continue
                if (part.users <= 0L) continue
                val make = max(0L, min(part.users, (rowCap - rows.size).toLong())).toInt()
                var made: List<AppUser> = if (make > 0) {
                    IdentityGenerator.make(make, ledger, now, dayStart, random, newUsers + rows)
                } else {
                    emptyList()
                }
                // Paid users must be listed one by one. A plan without a price does not exist, so those users stay Free.
                val premium = if (app.premiumPrice > 0L) min(part.premium, made.size.toLong()).toInt() else 0
                val pro = if (app.proPrice > 0L) min(part.pro, (made.size - premium).toLong()).toInt() else 0
                made = made.mapIndexed { index, u ->
                    // They upgrade on the day they arrive: the plan starts when the user joined.
                    when {
                        index < premium -> u.copy(
                            channel = channel,
                            plan = Plan.PREMIUM,
                            planStartMillis = u.addedMillis,
                            planExpiryMillis = u.addedMillis + PLAN_DAYS * DAY_MILLIS
                        )
                        index < premium + pro -> u.copy(
                            channel = channel,
                            plan = Plan.PRO,
                            planStartMillis = u.addedMillis,
                            planExpiryMillis = u.addedMillis + PLAN_DAYS * DAY_MILLIS
                        )
                        else -> u.copy(channel = channel)
                    }
                }
                rows.addAll(made)
                advertPremium += premium
                advertPro += pro
                val counted = part.users - made.size
                if (channel == Channel.APPLE) audienceApple += counted
                if (channel == Channel.WEBSITE) audienceWebsite += counted
            }
            advertRows = rows
            if (advertPremium + advertPro > 0) {
                lines.addAll(
                    channelLines(
                        PlayTxKind.SUBSCRIPTION, advertRows.filter { it.plan == Plan.PREMIUM }, { app.premiumPrice },
                        "new Premium subscription from adverts", "new Premium subscriptions from adverts", now
                    )
                )
                lines.addAll(
                    channelLines(
                        PlayTxKind.SUBSCRIPTION, advertRows.filter { it.plan == Plan.PRO }, { app.proPrice },
                        "new Pro subscription from adverts", "new Pro subscriptions from adverts", now
                    )
                )
            }
        }
        // The rest is only counted (Free users that are not listed one by one).
        val audienceAdded = advertTotal - advertRows.size

        // Active users (blue). Never more than all users, never fewer than today's new users
        // (advert users are active on the day they arrive).
        val totalAfter = app.totalUsers + newCount + advertTotal
        val fromDice = activeFor(face(DiceKind.ACTIVE), totalAfter, factor, random)
        // A Neribo Deploy app: the users you added yourself are always Active.
        val alwaysActive = if (app.deployTracking) app.users.count { !it.simulated }.toLong() else 0L
        val activeToday = min(totalAfter, max(max(fromDice, newCount + advertTotal), alwaysActive))

        // Upgrades (yellow and purple). Only dice users who were Free before this roll.
        val freePool = upgradePool(app).shuffled(random)
        val premiumPicked = if (app.premiumPrice > 0L) {
            freePool.take(
                countFromShare(freePool.size, face(DiceKind.PREMIUM), UPGRADE_SHARE, factor, 1.0, random)
            )
        } else {
            emptyList()
        }
        val proPicked = if (app.proPrice > 0L) {
            val rest = freePool.drop(premiumPicked.size)
            rest.take(countFromShare(rest.size, face(DiceKind.PRO), UPGRADE_SHARE, factor, 0.5, random))
        } else {
            emptyList()
        }
        val changed = HashMap<String, AppUser>()
        for (u in premiumPicked) {
            val start = timeToday()
            changed[u.id] = u.copy(
                plan = Plan.PREMIUM,
                planStartMillis = start,
                planExpiryMillis = start + PLAN_DAYS * DAY_MILLIS
            )
        }
        for (u in proPicked) {
            val start = timeToday()
            changed[u.id] = u.copy(
                plan = Plan.PRO,
                planStartMillis = start,
                planExpiryMillis = start + PLAN_DAYS * DAY_MILLIS
            )
        }
        lines.addAll(
            channelLines(
                PlayTxKind.SUBSCRIPTION, premiumPicked, { app.premiumPrice },
                "new Premium subscription", "new Premium subscriptions", now
            )
        )
        lines.addAll(
            channelLines(
                PlayTxKind.SUBSCRIPTION, proPicked, { app.proPrice },
                "new Pro subscription", "new Pro subscriptions", now
            )
        )

        // Renewals (orange) and cancellations (red). Both take people from the same group
        // (dice users whose plan has ended), so nobody is counted twice.
        val endedPool = renewalPool(app, now).shuffled(random)
        val renewCount = countFromShare(endedPool.size, face(DiceKind.RENEW), RENEW_SHARE, 1.0, 1.0, random)
        val renewedUsers = endedPool.take(renewCount)
        val leftOver = endedPool.drop(renewCount)
        val lostCount = min(
            leftOver.size,
            countFromShare(endedPool.size, face(DiceKind.LOST), LOST_SHARE, 1.0, 1.0, random)
        )
        val lostUsers = leftOver.take(lostCount)
        for (u in renewedUsers) {
            val base = max(now, u.planExpiryMillis)
            changed[u.id] = u.copy(planExpiryMillis = base + PLAN_DAYS * DAY_MILLIS)
        }
        for (u in lostUsers) {
            changed[u.id] = u.copy(plan = Plan.FREE, planStartMillis = 0L, planExpiryMillis = 0L)
        }
        lapsed += lostUsers.size
        lines.addAll(
            channelLines(PlayTxKind.RENEWAL, renewedUsers, { priceOf(app, it.plan) }, "renewal", "renewals", now)
        )

        // Google Ads (gold). Based on how many Free users the app has.
        var adsGross = 0L
        if (face(DiceKind.ADS) > 0) {
            val range = ADS_RATE[face(DiceKind.ADS) - 1]
            val rate = random.nextDouble(range.first, range.second)
            adsGross = max(1L, (app.freeUsers * rate).roundToLong())
            val net = adsNetAfterGoogle(adsGross)
            lines.add(tx(PlayTxKind.ADS, "Google Ads", adsGross, adsGross - net, now))
        }

        val subsNet = lines.filter { it.kind != PlayTxKind.ADS }.sumOf { it.net }
        val adsNet = lines.filter { it.kind == PlayTxKind.ADS }.sumOf { it.net }

        // Who was active today. Uses its OWN seeded random (never [random]), so every number this roll
        // draws stays exactly the same. New users and advert users are active on the day they arrive.
        val existingUsers = users.map { changed[it.id] ?: it }
        val activeStamps = pickActiveUsers(
            appId = app.id,
            today = today,
            pool = existingUsers,
            need = max(0L, activeToday - newCount - advertTotal),
            dayStart = dayStart,
            now = now
        )
        val finalUsers = existingUsers.map { u ->
            val stamped = activeStamps[u.id]?.let { stamp -> u.copy(lastActiveMillis = stamp) } ?: u
            if (app.deployTracking && !stamped.simulated) stamped.copy(lastActiveMillis = now) else stamped
        } + newUsers.map { it.copy(lastActiveMillis = it.addedMillis) } +
            advertRows.map { it.copy(lastActiveMillis = it.addedMillis) }

        val record = RollRecord(
            dayKey = today,
            faces = faces,
            newUsers = newCount.toLong(),
            active = activeToday,
            premium = (premiumPicked.size + advertPremium).toLong(),
            pro = (proPicked.size + advertPro).toLong(),
            renewed = renewedUsers.size.toLong(),
            lapsed = lapsed.toLong(),
            subsNet = subsNet,
            adsNet = adsNet,
            campaign = campaignToday != null,
            advertUsers = advertTotal
        )

        val history = (app.activeHistory.filter { it.dayKey != today } + DayCount(today, activeToday))
            .sortedBy { it.dayKey }
            .takeLast(30)
        // New users of today (listed rows added today plus counted audience users), kept for the chart.
        val audienceAddedToday = (if (app.audienceTodayKey == today) app.audienceToday else 0L) + audienceAdded
        val newToday = finalUsers.count { it.addedMillis >= dayStart && it.addedMillis < dayStart + DAY_MILLIS }
            .toLong() + audienceAddedToday
        val newHist = (app.newHistory.filter { it.dayKey != today } + DayCount(today, newToday))
            .sortedBy { it.dayKey }
            .takeLast(30)

        // Plan history: only writes down what this roll really did. It draws no random numbers.
        val planEvents = ArrayList<PlanEvent>()
        for (u in app.users.filter { it.id in dead }) {
            planEvents.add(PlanHistory.event(u.id, PlanEventKind.EXPIRED, u.plan, now, u.planExpiryMillis))
        }
        for (u in premiumPicked + proPicked) {
            val c = changed[u.id] ?: continue
            planEvents.add(PlanHistory.event(c.id, PlanEventKind.STARTED, c.plan, c.planStartMillis, c.planExpiryMillis))
        }
        for (u in advertRows) {
            if (u.plan == Plan.FREE) continue
            planEvents.add(PlanHistory.event(u.id, PlanEventKind.STARTED, u.plan, u.planStartMillis, u.planExpiryMillis))
        }
        for (u in renewedUsers) {
            val c = changed[u.id] ?: continue
            planEvents.add(PlanHistory.event(c.id, PlanEventKind.RENEWED, c.plan, now, c.planExpiryMillis))
        }
        for (u in lostUsers) {
            planEvents.add(PlanHistory.event(u.id, PlanEventKind.EXPIRED, u.plan, now, u.planExpiryMillis))
        }

        val updated = app.copy(
            planHistory = PlanHistory.merge(app.planHistory, planEvents),
            users = finalUsers,
            activeUsers = activeToday,
            activeUsersDayKey = today,
            activeHistory = history,
            newHistory = newHist,
            lastRollDayKey = today,
            rolls = (app.rolls + record).takeLast(30),
            playTx = (app.playTx + lines.filter { it.channel == Channel.PLAY }).takeLast(200),
            playSubsBalance = app.playSubsBalance +
                lines.filter { it.channel == Channel.PLAY && it.kind != PlayTxKind.ADS }.sumOf { it.net },
            playAdsBalance = app.playAdsBalance + adsNet,
            appleTx = (app.appleTx + lines.filter { it.channel == Channel.APPLE }).takeLast(200),
            appleSubsBalance = app.appleSubsBalance + lines.filter { it.channel == Channel.APPLE }.sumOf { it.net },
            paystackTx = (app.paystackTx + lines.filter { it.channel == Channel.WEBSITE }).takeLast(200),
            paystackSubsBalance = app.paystackSubsBalance + lines.filter { it.channel == Channel.WEBSITE }.sumOf { it.net },
            adverts = delivery.updatedAdverts,
            audienceApple = app.audienceApple + audienceApple,
            audienceWebsite = app.audienceWebsite + audienceWebsite,
            audienceFree = app.audienceFree + audienceAdded,
            audienceTodayKey = if (audienceAdded > 0L) today else app.audienceTodayKey,
            audienceToday = if (audienceAdded > 0L) {
                (if (app.audienceTodayKey == today) app.audienceToday else 0L) + audienceAdded
            } else {
                app.audienceToday
            }
        )
        // Storage grows by itself: the backend's share of the day of what the users and sites could fill.
        val grown = growStorage(updated, today)
        val rolled = ledger.copy(apps = ledger.apps.map { if (it.id == appId) grown else it })

        // A customer case can only be born from something real that this roll did.
        val facts = RollFacts(
            renewedUsers = renewedUsers,
            lapsedUsers = lostUsers,
            upgradedUsers = (premiumPicked + proPicked).mapNotNull { changed[it.id] },
            newUserCount = newCount,
            adsEarned = adsGross > 0L
        )
        return Pair(rolled, facts)
    }

    // -----------------------------------------------------------------------------------------
    // Which listed users were active today (Last seen)
    // -----------------------------------------------------------------------------------------

    /** A stable 64-bit number from a text (the first 8 bytes of its SHA-256). Same text, same number. */
    private fun stableSeed(text: String): Long {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        var seed = 0L
        for (i in 0 until 8) seed = (seed shl 8) or (digest[i].toLong() and 0xffL)
        return seed
    }

    /**
     * Chooses which of [pool] (the listed users that existed before this roll) were active today, and
     * when. Returns user id to the time they were active. Draws from its own seeded random, so it never
     * changes any number the rest of the roll produces, and the same app and day give the same answer.
     * A user who is already active today (added by you today) counts toward [need] and is not picked again.
     * If [need] is more than the pool, the difference stays unlisted audience users.
     */
    private fun pickActiveUsers(
        appId: String,
        today: String,
        pool: List<AppUser>,
        need: Long,
        dayStart: Long,
        now: Long
    ): Map<String, Long> {
        if (need <= 0L || pool.isEmpty()) return emptyMap()
        val alreadyToday = pool.count { dayKey(it.lastSeenMillis()) == today }
        val stillNeeded = need - alreadyToday
        if (stillNeeded <= 0L) return emptyMap()
        val rng = Random(stableSeed("active|$appId|$today"))
        val candidates = pool
            .filter { dayKey(it.lastSeenMillis()) != today }
            .sortedBy { it.id }
            .shuffled(rng)
            .take(min(stillNeeded, pool.size.toLong()).toInt())
        val result = HashMap<String, Long>()
        for (u in candidates) {
            val moment = dayStart + rng.nextLong(max(1L, now - dayStart))
            result[u.id] = max(moment, u.addedMillis)
        }
        return result
    }

    // -----------------------------------------------------------------------------------------
    // Withdrawing from the Google Play Balance
    // -----------------------------------------------------------------------------------------

    /** Moves one app's whole Google Play Balance into that app's income and writes it in the history. */
    fun withdrawApp(ledger: Ledger, appId: String, now: Long): Ledger {
        val app = ledger.apps.firstOrNull { it.id == appId } ?: return ledger
        if (!app.googlePlayEnabled) return ledger
        val amount = app.playBalance
        if (amount <= 0L) return ledger
        val record = Withdrawal(dateMillis = now, amount = amount, label = app.name)
        return ledger.copy(
            apps = ledger.apps.map { if (it.id == appId) it.withdrawn(now) else it },
            withdrawals = (listOf(record) + ledger.withdrawals).take(200)
        )
    }

    /** Moves every app's Google Play Balance into profit at once and writes one line in the history. */
    fun withdrawAll(ledger: Ledger, now: Long): Ledger {
        val paying = ledger.apps.filter { it.googlePlayEnabled && it.playBalance > 0L }
        if (paying.isEmpty()) return ledger
        val total = paying.sumOf { it.playBalance }
        val ids = paying.map { it.id }.toSet()
        val note = paying.joinToString(", ") { it.name + " " + "₦" + it.playBalance }
        val record = Withdrawal(dateMillis = now, amount = total, label = "All apps", note = note)
        return ledger.copy(
            apps = ledger.apps.map { if (it.id in ids) it.withdrawn(now) else it },
            withdrawals = (listOf(record) + ledger.withdrawals).take(200)
        )
    }

    /** Adds the day's storage growth (the backend's answer) to the app, never beyond what the users and sites could fill. */
    private fun growStorage(app: AppData, today: String): AppData {
        val rolls = app.dayRolls ?: return app
        if (rolls.dayKey != today || rolls.storageBp <= 0) return app
        val capacityKb = app.storageCapacityMb * 1024L
        if (capacityKb <= 0L) return app
        val grown = app.storageUsedKb + (capacityKb * rolls.storageBp.toLong() + 5_000L) / 10_000L
        return app.copy(storageUsedKb = min(grown, capacityKb))
    }

    // -----------------------------------------------------------------------------------------
    // Apple Store Balance and Paystack Balance
    // -----------------------------------------------------------------------------------------

    /** Moves one app's Apple Store Balance or Paystack Balance into the app's income. */
    fun withdrawChannelApp(ledger: Ledger, appId: String, channel: Channel, now: Long): Ledger {
        if (channel == Channel.PLAY) return withdrawApp(ledger, appId, now)
        val app = ledger.apps.firstOrNull { it.id == appId } ?: return ledger
        if (!app.googlePlayEnabled) return ledger
        val amount = if (channel == Channel.APPLE) app.appleBalance else app.paystackBalance
        if (amount <= 0L) return ledger
        val record = Withdrawal(dateMillis = now, amount = amount, label = app.name, channel = channel)
        return ledger.copy(
            apps = ledger.apps.map { if (it.id == appId) it.withdrawnChannel(channel, now) else it },
            withdrawals = (listOf(record) + ledger.withdrawals).take(200)
        )
    }

    /** Moves every app's Apple Store Balance or Paystack Balance into profit at once. */
    fun withdrawChannelAll(ledger: Ledger, channel: Channel, now: Long): Ledger {
        if (channel == Channel.PLAY) return withdrawAll(ledger, now)
        fun balanceOf(app: AppData): Long = if (channel == Channel.APPLE) app.appleBalance else app.paystackBalance
        val paying = ledger.apps.filter { it.googlePlayEnabled && balanceOf(it) > 0L }
        if (paying.isEmpty()) return ledger
        val total = paying.sumOf { balanceOf(it) }
        val ids = paying.map { it.id }.toSet()
        val note = paying.joinToString(", ") { it.name + " " + "₦" + balanceOf(it) }
        val record = Withdrawal(dateMillis = now, amount = total, label = "All apps", note = note, channel = channel)
        return ledger.copy(
            apps = ledger.apps.map { if (it.id in ids) it.withdrawnChannel(channel, now) else it },
            withdrawals = (listOf(record) + ledger.withdrawals).take(200)
        )
    }

    // -----------------------------------------------------------------------------------------
    // Campaigns
    // -----------------------------------------------------------------------------------------

    /** Null when the campaign is allowed, otherwise the reason it is not. */
    fun campaignError(app: AppData, startInDays: Int, lastsDays: Int, now: Long): String? {
        if (!app.googlePlayEnabled) return "Campaigns are only for Google Play apps."
        if (startInDays < 0) return "A campaign cannot start in the past."
        if (startInDays > 30) return "A campaign must start within the next 30 days."
        if (lastsDays < 1) return "A campaign lasts at least 1 day."
        if (lastsDays > MAX_CAMPAIGN_DAYS) return "A campaign lasts at most $MAX_CAMPAIGN_DAYS days."
        val existing = app.campaign
        if (existing != null) {
            val neverRan = existing.stoppedDayKey.isNotEmpty() &&
                existing.stoppedDayKey <= existing.startDayKey
            if (existing.endDayKey >= dayKey(now) && !neverRan) {
                return "This app already has a campaign. It can only have one at a time."
            }
        }
        return null
    }

    /** Starts a campaign. Returns the app unchanged when it is not allowed. It costs no money. */
    fun startCampaign(
        app: AppData,
        size: CampaignSize,
        startInDays: Int,
        lastsDays: Int,
        now: Long
    ): AppData {
        if (!app.googlePlayEnabled) return app
        if (campaignError(app, startInDays, lastsDays, now) != null) return app
        val today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
        val start = today.plusDays(startInDays.toLong())
        val end = start.plusDays((lastsDays - 1).toLong())
        return app.copy(
            campaign = Campaign(
                sizeName = size.name,
                startDayKey = start.toString(),
                endDayKey = end.toString()
            )
        )
    }

    /** Stops a campaign early. It can never be made longer. */
    fun stopCampaign(app: AppData, now: Long): AppData {
        if (!app.googlePlayEnabled) return app
        val campaign = app.campaign ?: return app
        if (campaign.stoppedDayKey.isNotEmpty() || campaign.endDayKey < dayKey(now)) return app
        return app.copy(campaign = campaign.copy(stoppedDayKey = dayKey(now)))
    }
}

/** Makes believable Nigerian names, emails and phone numbers that never repeat. */
object IdentityGenerator {

    private val FIRST = listOf(
        "Chinedu", "Emeka", "Ngozi", "Adaeze", "Oluwaseun", "Tunde", "Bola", "Funke", "Ibrahim", "Aisha",
        "Musa", "Fatima", "Yusuf", "Zainab", "Chioma", "Obinna", "Ifeanyi", "Kelechi", "Nneka", "Uche",
        "Tobi", "Damilola", "Segun", "Yetunde", "Kemi", "Femi", "Sade", "Ayo", "Dayo", "Kayode",
        "Babatunde", "Folake", "Temitope", "Adebayo", "Olamide", "Ebube", "Chidera", "Amara", "Ikenna",
        "Somto", "Tochukwu", "Eniola", "Hauwa", "Abdullahi", "Aminu", "Sani", "Halima", "Ejiro", "Efe",
        "Tamara", "Peace", "Blessing", "Favour", "Gift", "Godwin", "Joy", "Precious", "Samuel", "David",
        "Daniel", "Grace", "Esther", "Ruth", "Michael", "John", "Mary", "Sarah", "Emmanuel", "Joseph",
        "Stella", "Victoria", "Patience", "Faith", "Ifeoma", "Onyinye", "Nkechi", "Ugochukwu", "Ebuka",
        "Nonso", "Kosi", "Zara", "Hadiza", "Bukola", "Titilayo", "Rotimi", "Gbenga", "Wale", "Lanre",
        "Taiwo", "Kehinde", "Ayomide", "Oluwatobi", "Temi", "Chukwuemeka", "Amaka", "Ifunanya", "Sola",
        "Bisi", "Jide", "Kunle", "Dele", "Lola", "Tayo", "Yemi", "Sunday", "Monday", "Friday"
    )

    private val LAST = listOf(
        "Okafor", "Adeyemi", "Balogun", "Ibrahim", "Musa", "Okeke", "Eze", "Nwosu", "Obi", "Okonkwo",
        "Adebayo", "Ogunleye", "Afolabi", "Bello", "Danjuma", "Abubakar", "Suleiman", "Lawal", "Garba",
        "Yakubu", "Okoro", "Nwachukwu", "Igwe", "Chukwu", "Onyeka", "Ajayi", "Akinwale", "Olawale",
        "Fashola", "Ogundipe", "Adewale", "Oyelaran", "Salami", "Alabi", "Odunsi", "Ekwueme", "Umeh",
        "Anyanwu", "Madu", "Nnamdi", "Ezeh", "Agu", "Uzor", "Okpara", "Ojo", "Adeleke", "Ogbonna",
        "Mohammed", "Aliyu", "Hassan", "Usman", "Sadiq", "Tijani", "Bakare", "Oladipo", "Olaniyan",
        "Famuyiwa", "Adegoke", "Ilori", "Onwudiwe", "Nwankwo", "Ibekwe", "Emenike", "Uchenna", "Eboh",
        "Ibe", "Nwoye", "Obiora", "Ogbu", "Okoye", "Oparaji", "Dike", "Egbuna", "Chukwuma", "Ofoegbu",
        "Ayodele", "Akande", "Ogunbanjo", "Olaoye", "Oyewole", "Adesina", "Adeniyi", "Ajibola",
        "Osagie", "Idowu", "Edet", "Effiong", "Bassey", "Etim", "Udoh", "Okon", "Ekanem", "Inyang",
        "Yahaya", "Shehu", "Waziri", "Dantata", "Kolo", "Maiduguri", "Zubairu", "Sanusi", "Gambo"
    )

    private val DOMAINS = listOf(
        "gmail.com", "gmail.com", "gmail.com", "gmail.com", "yahoo.com", "yahoo.com",
        "outlook.com", "hotmail.com", "icloud.com"
    )

    // Real Nigerian mobile starts (MTN, Airtel, Glo, 9mobile).
    private val PREFIXES = listOf(
        "0803", "0806", "0810", "0813", "0814", "0816", "0703", "0706", "0802", "0805", "0807",
        "0808", "0811", "0812", "0815", "0701", "0708", "0809", "0817", "0818", "0901", "0902",
        "0903", "0904", "0905", "0906", "0907", "0908", "0909", "0912"
    )

    private fun digitsOf(text: String): String = text.filter { it.isDigit() }

    private fun uniqueName(taken: Set<String>, random: Random): String {
        repeat(60) {
            val name = FIRST.random(random) + " " + LAST.random(random)
            if (name.lowercase() !in taken) return name
        }
        for (letter in 'A'..'Z') {
            repeat(30) {
                val name = FIRST.random(random) + " " + letter + ". " + LAST.random(random)
                if (name.lowercase() !in taken) return name
            }
        }
        var number = 2
        var candidate: String
        do {
            candidate = FIRST.random(random) + " " + LAST.random(random) + " " + number
            number += 1
        } while (candidate.lowercase() in taken)
        return candidate
    }

    private fun uniqueEmail(name: String, taken: Set<String>, random: Random): String {
        val parts = name.lowercase().split(" ")
            .map { part -> part.filter { it.isLetter() } }
            .filter { it.length > 1 }
        val first = parts.firstOrNull() ?: "user"
        val last = parts.lastOrNull() ?: "name"
        repeat(40) {
            val local = when (random.nextInt(4)) {
                0 -> "$first.$last"
                1 -> "$first$last"
                2 -> "$last.$first"
                else -> "$first.$last" + random.nextInt(10, 100)
            }
            val email = local + "@" + DOMAINS.random(random)
            if (email !in taken) return email
        }
        var candidate: String
        do {
            candidate = "$first.$last" + random.nextInt(100, 10000) + "@" + DOMAINS.random(random)
        } while (candidate in taken)
        return candidate
    }

    private fun uniquePhone(taken: Set<String>, random: Random): String {
        var candidate: String
        do {
            candidate = PREFIXES.random(random) + random.nextInt(0, 10_000_000).toString().padStart(7, '0')
        } while (candidate in taken)
        return candidate
    }

    /**
     * Makes [count] new Free users. Names, emails and phone numbers are checked against every user
     * in every app, and against each other, so none repeats.
     */
    fun make(
        count: Int,
        ledger: Ledger,
        now: Long,
        dayStart: Long,
        random: Random,
        extra: List<AppUser> = emptyList()
    ): List<AppUser> {
        val names = HashSet<String>()
        val emails = HashSet<String>()
        val phones = HashSet<String>()
        for (app in ledger.apps) {
            for (user in app.users) {
                names.add(user.name.trim().lowercase())
                emails.add(user.email.trim().lowercase())
                val digits = digitsOf(user.phone)
                if (digits.isNotEmpty()) phones.add(digits)
            }
        }
        for (user in extra) {
            names.add(user.name.trim().lowercase())
            emails.add(user.email.trim().lowercase())
            val digits = digitsOf(user.phone)
            if (digits.isNotEmpty()) phones.add(digits)
        }
        val result = ArrayList<AppUser>()
        repeat(count) {
            val name = uniqueName(names, random)
            names.add(name.lowercase())
            val email = uniqueEmail(name, emails, random)
            emails.add(email)
            val phone = uniquePhone(phones, random)
            phones.add(phone)
            result.add(
                AppUser(
                    name = name,
                    email = email,
                    phone = phone,
                    plan = Plan.FREE,
                    addedMillis = dayStart + random.nextLong(max(1L, now - dayStart)),
                    simulated = true
                )
            )
        }
        return result
    }

    // -----------------------------------------------------------------------------------------
    // Names, emails and phone numbers that Claude wrote (Phase 4B). Always checked before use.
    // -----------------------------------------------------------------------------------------

    /** The only email domains Claude may use until the owner changes them in Settings. */
    val DEFAULT_AI_DOMAINS: List<String> = listOf("example.com", "example.org", "example.net")

    private val EMAIL_REGEX = Regex("^[a-z0-9._-]+@[a-z0-9.-]+$")

    /** The last 10 digits of a phone number, so +234 and 0 forms of the same number match. */
    fun phoneKey(text: String): String {
        val digits = digitsOf(text)
        return if (digits.length >= 10) digits.takeLast(10) else digits
    }

    /**
     * Cleans one candidate from Claude. Returns the cleaned identity, or null when the name or email
     * is not acceptable. A bad phone number does not reject the person: the phone becomes empty.
     * [takenNames] and [takenEmails] are lowercase; [takenPhones] holds [phoneKey] values.
     */
    fun acceptAiIdentity(
        candidate: PresetIdentity,
        takenNames: Set<String>,
        takenEmails: Set<String>,
        takenPhones: Set<String>,
        allowedDomains: List<String>
    ): PresetIdentity? {
        val name = candidate.name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
        if (name.length < 3 || name.length > 60) return null
        if (!name.all { it.isLetter() || it == ' ' || it == '\'' || it == '-' || it == '.' }) return null
        if (!name.any { it.isLetter() }) return null

        val email = candidate.email.trim()
        if (email.length > 80 || email != email.lowercase()) return null
        if (!EMAIL_REGEX.matches(email)) return null
        val domain = email.substringAfter('@')
        val domains = allowedDomains.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        if (domains.none { it == domain }) return null

        if (name.lowercase() in takenNames) return null
        if (email in takenEmails) return null

        val phone = cleanPhone(candidate.phone)
        if (phone.isNotEmpty() && phoneKey(phone) in takenPhones) return null
        return PresetIdentity(name, email, phone)
    }

    /** "0XXXXXXXXXX" (11 digits) for a good Nigerian mobile number, otherwise an empty text. */
    private fun cleanPhone(raw: String): String {
        val compact = raw.trim().filter { it != ' ' && it != '-' && it != '(' && it != ')' }
        val body = when {
            compact.startsWith("+234") -> compact.substring(4)
            compact.startsWith("234") && compact.length == 13 -> compact.substring(3)
            compact.startsWith("0") -> compact.substring(1)
            else -> return ""
        }
        if (body.length != 10 || !body.all { it in '0'..'9' }) return ""
        if (body[0] != '7' && body[0] != '8' && body[0] != '9') return ""
        return "0$body"
    }

    /**
     * Turns up to [limit] of Claude's candidates into Free dice users. A candidate that fails the
     * checks (or repeats a name, email or phone of any user, or of an earlier candidate) is skipped.
     */
    fun acceptAiIdentities(
        candidates: List<PresetIdentity>,
        limit: Int,
        ledger: Ledger,
        now: Long,
        dayStart: Long,
        random: Random,
        allowedDomains: List<String>
    ): List<AppUser> {
        val names = HashSet<String>()
        val emails = HashSet<String>()
        val phones = HashSet<String>()
        for (app in ledger.apps) {
            for (user in app.users) {
                names.add(user.name.trim().lowercase())
                emails.add(user.email.trim().lowercase())
                val key = phoneKey(user.phone)
                if (key.isNotEmpty()) phones.add(key)
            }
        }
        val result = ArrayList<AppUser>()
        for (candidate in candidates) {
            if (result.size >= limit) break
            val ok = acceptAiIdentity(candidate, names, emails, phones, allowedDomains) ?: continue
            names.add(ok.name.lowercase())
            emails.add(ok.email)
            if (ok.phone.isNotEmpty()) phones.add(phoneKey(ok.phone))
            result.add(
                AppUser(
                    name = ok.name,
                    email = ok.email,
                    phone = ok.phone,
                    plan = Plan.FREE,
                    addedMillis = dayStart + random.nextLong(max(1L, now - dayStart)),
                    simulated = true
                )
            )
        }
        return result
    }
}
