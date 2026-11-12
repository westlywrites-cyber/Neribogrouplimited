package com.westly.neribogroup.data

import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale
import java.util.UUID

const val DEFAULT_COMPANY_NAME = "Neribo Group Limited"

/** Turns a time into a month label like "2026-11", used to tell which month a payment belongs to. */
fun monthKey(millis: Long): String =
    SimpleDateFormat("yyyy-MM", Locale.US).format(Date(millis))

/** Turns a time into a day label like "2026-11-06", used for the "today" counters. */
fun dayKey(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(millis))

/** How long one Premium or Pro subscription lasts. */
const val PLAN_DAYS = 30

/** Whole days from today to the day of [target]. Negative means the day has passed. */
fun calendarDaysLeft(now: Long, target: Long): Long {
    val zone = ZoneId.systemDefault()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val end = Instant.ofEpochMilli(target).atZone(zone).toLocalDate()
    return ChronoUnit.DAYS.between(today, end)
}

/** The number of active users that was recorded for one day. */
data class DayCount(
    val dayKey: String,
    val count: Long
)

/** One money line: either income or an expense. Amounts are whole naira. */
data class Entry(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val amount: Long,
    val isIncome: Boolean,
    val dateMillis: Long,
    /** True when the app created this line itself from a Premium or Pro subscription change. */
    val auto: Boolean = false,
    /** Links an expense to a legal case (Customer Service & Complaints). Empty when not linked. */
    val caseId: String = "",
    /** Links a company expense to the Google Play app that charged it. Empty when not linked. */
    val appId: String = "",
    /** Links a company expense to a software client project. Empty when not linked. */
    val projectId: String = ""
)

/** The plan a user of an app is on. */
enum class Plan { FREE, PREMIUM, PRO }

/** One user of an app, with the plan they are on. */
data class AppUser(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val email: String,
    /** Empty when no phone number was given. */
    val phone: String = "",
    val plan: Plan = Plan.FREE,
    val addedMillis: Long,
    /** When the paid plan ends. 0 for Free users. */
    val planExpiryMillis: Long = 0,
    /** When the current paid plan started. 0 for Free users. */
    val planStartMillis: Long = 0,
    /** True for users made by the dice. Users you add yourself are false. */
    val simulated: Boolean = false,
    /** The last moment this user was active. 0 means unknown (then [addedMillis] is used). */
    val lastActiveMillis: Long = 0,
    /** Where this user came from (Google Play, Apple Store or Website). */
    val channel: Channel = Channel.PLAY,
    /** Neribo Deploy style apps: how many sites this user has deployed. 0 = not set (counts as 1). */
    val sites: Int = 0,
    /**
     * Only for users you add yourself ("Customize"): the storage you typed for this user, in MB. -1 = none, the
     * user gets what the app offers to their plan. A Customize user is on the Free plan for money.
     */
    val customStorageMb: Long = -1L
) {
    /** The effective last active time: [lastActiveMillis] when known, otherwise the day the user was added. */
    fun lastSeenMillis(): Long = if (lastActiveMillis > 0L) lastActiveMillis else addedMillis

    /** Whole calendar days since this user was last active. 0 = today (or the clock went back). */
    fun daysSinceSeen(now: Long): Long = maxOf(0L, -calendarDaysLeft(now, lastSeenMillis()))

    /** True when this user was active on the day of [now]. */
    fun isActiveToday(now: Long): Boolean = daysSinceSeen(now) == 0L

    /** Whole days until the paid plan ends. Null for Free users and users with no end date. */
    fun planDaysLeft(now: Long): Long? =
        if (plan == Plan.FREE || planExpiryMillis <= 0L) null
        else calendarDaysLeft(now, planExpiryMillis)
}

/** One app dashboard, for example "Neribo Vault". */
data class AppData(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    /** Price of one Premium subscription, in naira. 0 means Premium does not exist in this app. */
    val premiumPrice: Long = 0,
    /** Price of one Pro subscription, in naira. 0 means Pro does not exist in this app. */
    val proPrice: Long = 0,
    val entries: List<Entry> = emptyList(),
    val users: List<AppUser> = emptyList(),
    val premiumBenefits: String = "",
    val proBenefits: String = "",
    val freeBenefits: String = "",
    /** Active users of the day named by [activeUsersDayKey]. Set by the daily roll. */
    val activeUsers: Long = 0,
    val activeUsersDayKey: String = "",
    /** Active users of earlier days (the last 30 are kept). */
    val activeHistory: List<DayCount> = emptyList(),
    /** New users of earlier days (the last 30 are kept). */
    val newHistory: List<DayCount> = emptyList(),
    /** True when Google Ads is switched on for this app. */
    val adsEnabled: Boolean = false,
    /** The last day the dice were rolled, "yyyy-MM-dd". Empty when never rolled. */
    val lastRollDayKey: String = "",
    /** What the last rolls did (the last 30 are kept). */
    val rolls: List<RollRecord> = emptyList(),
    /** The latest Google Play Balance lines (the last 200 are kept). */
    val playTx: List<PlayTx> = emptyList(),
    /** Subscription and renewal money in the Google Play Balance, after Google's 15%. */
    val playSubsBalance: Long = 0,
    /** Ad money in the Google Play Balance, after Google's share. */
    val playAdsBalance: Long = 0,
    val campaign: Campaign? = null,
    /** False = Manual app (typed numbers). True = Google Play app (dice). Once true it never goes back. */
    val googlePlayEnabled: Boolean = false,
    /** When Google Play was turned on. 0 for a Manual app. */
    val googlePlaySinceMillis: Long = 0,
    // Stored values used ONLY while googlePlayEnabled == false.
    val manualTotalUsers: Long = 0,
    val manualPremiumUsers: Long = 0,
    val manualProUsers: Long = 0,
    val manualDailyNewUsers: Long = 0,
    val manualNewUsersDayKey: String = "",
    val manualPremiumToday: Long = 0,
    val manualProToday: Long = 0,
    val manualTodayKey: String = "",
    /** What happened to users' paid plans (started, renewed, ended). The newest 2000 are kept. */
    val planHistory: List<PlanEvent> = emptyList(),
    /** Adverts of this app (Google Play apps only). Running, upcoming and the newest finished ones. */
    val adverts: List<Advert> = emptyList(),
    /** Free users brought by adverts that are counted but not stored as individual rows. */
    val audienceFree: Long = 0,
    /** How many audience users were added on the day named by [audienceTodayKey]. */
    val audienceTodayKey: String = "",
    val audienceToday: Long = 0,
    /** The channels this app is on (only meaningful for a live app, see googlePlayEnabled). Google Play is the old default. */
    val playOn: Boolean = true,
    val appleOn: Boolean = false,
    val websiteOn: Boolean = false,
    /** Subscription and renewal money in the Apple Store Balance, after Apple's share. */
    val appleSubsBalance: Long = 0,
    /** Subscription and renewal money in the Paystack Balance, after Paystack's fees. */
    val paystackSubsBalance: Long = 0,
    val appleTx: List<PlayTx> = emptyList(),
    val paystackTx: List<PlayTx> = emptyList(),
    /** Of [audienceFree]: how many belong to the Apple Store and to the Website (the rest are Google Play). */
    val audienceApple: Long = 0,
    val audienceWebsite: Long = 0,
    /** What the backend decided for the last day it was asked about. */
    val dayRolls: DayRolls? = null,
    /** True for a Neribo Deploy style app: users have sites. */
    val deployTracking: Boolean = false,
    /** What each plan offers, in MB per user, and per deployed site. 0 = nothing offered. */
    val storageFreeMb: Long = 0,
    val storageProMb: Long = 0,
    val storagePremiumMb: Long = 0,
    val storageSiteMb: Long = 0,
    /** Old value from the time storage grew by dice. Not used any more: storage is worked out from the users. */
    val storageUsedKb: Long = 0,
    /** This app's share of the company's total storage, in MB. 0 = no share set yet. */
    val storageShareMb: Long = 0L
) {
    val income: Long get() = entries.filter { it.isIncome }.sumOf { it.amount }
    val expenses: Long get() = entries.filter { !it.isIncome }.sumOf { it.amount }
    val profit: Long get() = income - expenses

    /** Google Play apps count from the user list. Manual apps use the numbers typed in Edit. */
    val totalUsers: Long get() = if (googlePlayEnabled) users.size.toLong() + audienceFree else manualTotalUsers
    val premiumUsers: Long
        get() = if (googlePlayEnabled) users.count { it.plan == Plan.PREMIUM }.toLong() else manualPremiumUsers
    val proUsers: Long
        get() = if (googlePlayEnabled) users.count { it.plan == Plan.PRO }.toLong() else manualProUsers
    val freeUsers: Long
        get() = if (googlePlayEnabled) users.count { it.plan == Plan.FREE }.toLong() + audienceFree
        else maxOf(0L, manualTotalUsers - manualPremiumUsers - manualProUsers)

    /** Everything waiting in the Google Play Balance. It is not profit until it is withdrawn. */
    val playBalance: Long get() = playSubsBalance + playAdsBalance

    /** Waiting in the Apple Store Balance and in the Paystack Balance. Not profit until withdrawn. */
    val appleBalance: Long get() = appleSubsBalance
    val paystackBalance: Long get() = paystackSubsBalance

    /** Users that came through one channel: the listed users plus the counted audience of that channel. */
    fun usersOfChannel(channel: Channel): Long {
        if (!googlePlayEnabled) return 0L
        val rows = users.count { it.channel == channel }.toLong()
        val audience = when (channel) {
            Channel.APPLE -> audienceApple
            Channel.WEBSITE -> audienceWebsite
            Channel.PLAY -> maxOf(0L, audienceFree - audienceApple - audienceWebsite)
        }
        return rows + audience
    }

    /** Neribo Deploy: all sites. Everybody has at least 1 site, so this is never less than the user count. */
    val totalSites: Long
        get() = users.sumOf { maxOf(1, it.sites).toLong() } + audienceFree

    /** True for the Neribo Deploy app only: only it has users with sites. */
    val isDeployApp: Boolean get() = name.contains("deploy", ignoreCase = true)

    /** Users you added yourself with a typed storage amount (Customize). They are Free users for money. */
    val customStorageUsers: List<AppUser>
        get() = if (googlePlayEnabled) users.filter { it.customStorageMb >= 0L && it.plan == Plan.FREE } else emptyList()

    /**
     * The storage given to every user of this app, in MB: what the app offers to each plan for the users of that
     * plan, and the typed amount for each Customize user. Sites share the account's storage: they add nothing.
     * Worked out from the users every time, so it is always up to date.
     */
    val storageAllocatedMb: Long
        get() {
            val custom = customStorageUsers
            val planFree = maxOf(0L, freeUsers - custom.size.toLong())
            return planFree * storageFreeMb + proUsers * storageProMb + premiumUsers * storagePremiumMb +
                custom.sumOf { it.customStorageMb }
        }

    val storageAllocatedKb: Long get() = storageAllocatedMb * 1024L

    /** Kept for older code: the same as [storageAllocatedMb]. */
    val storageCapacityMb: Long get() = storageAllocatedMb

    val storageUsedMb: Long get() = storageAllocatedMb

    fun newUsersToday(now: Long): Long {
        if (!googlePlayEnabled) {
            return if (manualNewUsersDayKey == dayKey(now)) manualDailyNewUsers else 0L
        }
        val start = dayStartMillis(now)
        val end = start + DAY_MILLIS
        val rows = users.count { it.addedMillis >= start && it.addedMillis < end }.toLong()
        return rows + (if (audienceTodayKey == dayKey(now)) audienceToday else 0L)
    }

    fun premiumTodayAt(now: Long): Long {
        if (!googlePlayEnabled) {
            return if (manualTodayKey == dayKey(now)) manualPremiumToday else 0L
        }
        val start = dayStartMillis(now)
        val end = start + DAY_MILLIS
        return users.count {
            it.plan == Plan.PREMIUM && it.planStartMillis >= start && it.planStartMillis < end
        }.toLong()
    }

    fun proTodayAt(now: Long): Long {
        if (!googlePlayEnabled) {
            return if (manualTodayKey == dayKey(now)) manualProToday else 0L
        }
        val start = dayStartMillis(now)
        val end = start + DAY_MILLIS
        return users.count {
            it.plan == Plan.PRO && it.planStartMillis >= start && it.planStartMillis < end
        }.toLong()
    }

    /** What reached the Google Play Balance today (subscriptions, renewals and ads), after Google's cut. */
    fun playEarningsToday(now: Long): Long {
        val start = dayStartMillis(now)
        val end = start + DAY_MILLIS
        return playTx.filter { it.dateMillis >= start && it.dateMillis < end }.sumOf { it.net }
    }

    /** Active users of today. Goes back to 0 on a new day. */
    fun activeUsersAt(now: Long): Long =
        if (activeUsersDayKey == dayKey(now)) activeUsers else 0L

    /** Active users on the day of [millis]: today's number, or the number saved for an earlier day. */
    fun activeUsersOn(millis: Long, now: Long): Long {
        val key = dayKey(millis)
        if (key == dayKey(now)) return activeUsersAt(now)
        return activeHistory.firstOrNull { it.dayKey == key }?.count ?: 0L
    }

    /**
     * New users on the day of [millis]: today's number, the number saved for an earlier day, or (for a
     * Google Play app with nothing saved) the listed users added on that day. Otherwise 0.
     */
    fun newUsersOn(millis: Long, now: Long): Long {
        val key = dayKey(millis)
        if (key == dayKey(now)) return newUsersToday(now)
        val saved = newHistory.firstOrNull { it.dayKey == key }
        if (saved != null) return saved.count
        if (!googlePlayEnabled) return 0L
        val start = dayStartMillis(millis)
        val end = start + DAY_MILLIS
        return users.count { it.addedMillis >= start && it.addedMillis < end }.toLong()
    }

    /** Listed users who were active today. */
    fun listedActiveToday(now: Long): Long = users.count { it.isActiveToday(now) }.toLong()

    /** Listed users last seen 7 or more days ago. */
    fun notSeenSevenDays(now: Long): Long = users.count { it.daysSinceSeen(now) >= 7L }.toLong()

    private fun withPlanCount(plan: Plan, delta: Long): AppData {
        if (googlePlayEnabled) return this
        return when (plan) {
            Plan.PREMIUM -> copy(manualPremiumUsers = maxOf(0L, manualPremiumUsers + delta))
            Plan.PRO -> copy(manualProUsers = maxOf(0L, manualProUsers + delta))
            Plan.FREE -> this
        }
    }

    /**
     * Adds a user you typed in. A Premium or Pro user gets a start time and an end date PLAN_DAYS later.
     * In a Manual app a Premium or Pro user also raises that plan's count by 1 (Total users is not changed).
     */
    fun withUserAdded(user: AppUser): AppData {
        val fixed = if (user.plan != Plan.FREE) {
            val start = if (user.planStartMillis > 0L) user.planStartMillis else user.addedMillis
            user.copy(
                planStartMillis = start,
                planExpiryMillis = start + PLAN_DAYS * DAY_MILLIS
            )
        } else {
            user.copy(planStartMillis = 0L, planExpiryMillis = 0L)
        }
        val started = if (fixed.plan != Plan.FREE) {
            listOf(PlanHistory.event(fixed.id, PlanEventKind.STARTED, fixed.plan, fixed.planStartMillis, fixed.planExpiryMillis))
        } else {
            emptyList()
        }
        return copy(users = users + fixed, planHistory = PlanHistory.merge(planHistory, started))
            .withPlanCount(fixed.plan, 1L)
    }

    /** Replaces a user you added (matched by id). Users made by the dice cannot be edited. */
    fun withUserUpdated(updated: AppUser, now: Long = System.currentTimeMillis()): AppData {
        val old = users.firstOrNull { it.id == updated.id } ?: return this
        if (old.simulated) return this
        // A new paid plan starts a new period. Free has no dates.
        val fixed = when {
            updated.plan == Plan.FREE ->
                updated.copy(planStartMillis = 0L, planExpiryMillis = 0L, simulated = false)
            old.plan != updated.plan ->
                updated.copy(
                    planStartMillis = now,
                    planExpiryMillis = now + PLAN_DAYS * DAY_MILLIS,
                    simulated = false
                )
            else -> updated.copy(simulated = false)
        }
        var result = copy(users = users.map { if (it.id == fixed.id) fixed else it })
        if (old.plan != fixed.plan) {
            result = result.withPlanCount(old.plan, -1L).withPlanCount(fixed.plan, 1L)
            val ev = if (fixed.plan == Plan.FREE) {
                PlanHistory.event(fixed.id, PlanEventKind.MOVED_TO_FREE, old.plan, now, old.planExpiryMillis)
            } else {
                PlanHistory.event(fixed.id, PlanEventKind.STARTED, fixed.plan, fixed.planStartMillis, fixed.planExpiryMillis)
            }
            result = result.copy(planHistory = PlanHistory.merge(result.planHistory, listOf(ev)))
        }
        return result
    }

    /**
     * A user you added renewed: adds PLAN_DAYS to the plan end date (from today if it already ended).
     * In a Manual app the plan price is recorded as app income. In a Google Play app no money is
     * recorded, because users you add yourself do not earn money.
     */
    fun withUserRenewed(userId: String, now: Long): AppData {
        val user = users.firstOrNull { it.id == userId } ?: return this
        if (user.plan == Plan.FREE || user.simulated) return this
        val base = maxOf(now, user.planExpiryMillis)
        val renewed = user.copy(planExpiryMillis = base + PLAN_DAYS * DAY_MILLIS)
        var result = copy(
            users = users.map { if (it.id == userId) renewed else it },
            planHistory = PlanHistory.merge(
                planHistory,
                listOf(PlanHistory.event(userId, PlanEventKind.RENEWED, user.plan, now, renewed.planExpiryMillis))
            )
        )
        if (!googlePlayEnabled) {
            val price = if (user.plan == Plan.PREMIUM) premiumPrice else proPrice
            if (price > 0L) {
                val label = if (user.plan == Plan.PREMIUM) "Premium" else "Pro"
                result = result.copy(
                    entries = result.entries + Entry(
                        title = user.name + " renewed " + label,
                        amount = price,
                        isIncome = true,
                        dateMillis = now,
                        auto = true
                    )
                )
            }
        }
        return result
    }

    /** A user you added did not renew: moves them to Free (and lowers that plan's count in a Manual app). */
    fun withPlanLapsed(userId: String, now: Long = System.currentTimeMillis()): AppData {
        val user = users.firstOrNull { it.id == userId } ?: return this
        if (user.plan == Plan.FREE || user.simulated) return this
        return copy(
            planHistory = PlanHistory.merge(
                planHistory,
                listOf(PlanHistory.event(userId, PlanEventKind.EXPIRED, user.plan, now, user.planExpiryMillis))
            ),
            users = users.map {
                if (it.id == userId) it.copy(plan = Plan.FREE, planStartMillis = 0L, planExpiryMillis = 0L)
                else it
            }
        ).withPlanCount(user.plan, -1L)
    }

    /** Removes a user you added. Users made by the dice cannot be removed. */
    fun withUserRemoved(userId: String): AppData {
        val old = users.firstOrNull { it.id == userId } ?: return this
        if (old.simulated) return this
        return copy(
            users = users.filter { it.id != userId },
            planHistory = planHistory.filter { it.userId != userId }
        ).withPlanCount(old.plan, -1L)
    }

    /**
     * Used when saving an edit of a Google Play app: keeps everything the dice own (their users, roll
     * history, Play Balance, active users and campaign) exactly as it is in [current], and takes only
     * the settings and the users you added from this copy.
     */
    fun keepingDiceData(current: AppData): AppData = copy(
        users = current.users.filter { it.simulated } + users.filter { !it.simulated }.map { mine ->
            // A roll may have marked one of your users active while you were editing: keep the later time.
            val saved = current.users.firstOrNull { it.id == mine.id }
            if (saved != null && saved.lastActiveMillis > mine.lastActiveMillis) {
                mine.copy(lastActiveMillis = saved.lastActiveMillis)
            } else {
                mine
            }
        },
        activeUsers = current.activeUsers,
        activeUsersDayKey = current.activeUsersDayKey,
        activeHistory = current.activeHistory,
        newHistory = current.newHistory,
        lastRollDayKey = current.lastRollDayKey,
        rolls = current.rolls,
        playTx = current.playTx,
        playSubsBalance = current.playSubsBalance,
        playAdsBalance = current.playAdsBalance,
        campaign = current.campaign,
        adverts = current.adverts,
        audienceFree = current.audienceFree,
        audienceTodayKey = current.audienceTodayKey,
        audienceToday = current.audienceToday,
        appleSubsBalance = current.appleSubsBalance,
        paystackSubsBalance = current.paystackSubsBalance,
        appleTx = current.appleTx,
        paystackTx = current.paystackTx,
        audienceApple = current.audienceApple,
        audienceWebsite = current.audienceWebsite,
        dayRolls = current.dayRolls,
        storageUsedKb = current.storageUsedKb,
        planHistory = PlanHistory.merge(current.planHistory, planHistory)
    )

    /** The copy used while editing a Manual app: today's typed numbers shown as they are right now. */
    fun forEditing(now: Long): AppData =
        if (googlePlayEnabled) this
        else copy(
            manualDailyNewUsers = newUsersToday(now),
            manualNewUsersDayKey = dayKey(now),
            activeUsers = activeUsersAt(now),
            activeUsersDayKey = dayKey(now)
        )

    /**
     * Saves an edit of a Manual app. Compares with [previous] (the saved app) to date today's numbers
     * and to record income for new Premium and Pro subscriptions (once, at Save).
     */
    fun finalizeSave(previous: AppData, now: Long): AppData {
        val today = dayKey(now)
        var result = copy(
            googlePlayEnabled = previous.googlePlayEnabled,
            googlePlaySinceMillis = previous.googlePlaySinceMillis
        )
        if (result.googlePlayEnabled) return result

        if (result.manualDailyNewUsers != previous.newUsersToday(now)) {
            val newHist = (result.newHistory.filter { it.dayKey != today } +
                DayCount(today, result.manualDailyNewUsers))
                .sortedBy { it.dayKey }
                .takeLast(30)
            result = result.copy(manualNewUsersDayKey = today, newHistory = newHist)
        } else {
            result = result.copy(
                manualDailyNewUsers = previous.manualDailyNewUsers,
                manualNewUsersDayKey = previous.manualNewUsersDayKey,
                newHistory = previous.newHistory
            )
        }

        if (result.activeUsers != previous.activeUsersAt(now)) {
            val history = (result.activeHistory.filter { it.dayKey != today } +
                DayCount(today, result.activeUsers))
                .sortedBy { it.dayKey }
                .takeLast(30)
            result = result.copy(activeUsersDayKey = today, activeHistory = history)
        } else {
            result = result.copy(
                activeUsers = previous.activeUsers,
                activeUsersDayKey = previous.activeUsersDayKey,
                activeHistory = previous.activeHistory
            )
        }

        val premiumAdded = maxOf(0L, result.manualPremiumUsers - previous.manualPremiumUsers)
        val proAdded = maxOf(0L, result.manualProUsers - previous.manualProUsers)
        if (premiumAdded == 0L && proAdded == 0L) return result

        val newEntries = ArrayList<Entry>()
        if (premiumAdded > 0L && result.premiumPrice > 0L) {
            newEntries.add(
                Entry(
                    title = premiumAdded.toString() + " new Premium subscription" +
                        (if (premiumAdded > 1L) "s" else ""),
                    amount = premiumAdded * result.premiumPrice,
                    isIncome = true,
                    dateMillis = now,
                    auto = true
                )
            )
        }
        if (proAdded > 0L && result.proPrice > 0L) {
            newEntries.add(
                Entry(
                    title = proAdded.toString() + " new Pro subscription" +
                        (if (proAdded > 1L) "s" else ""),
                    amount = proAdded * result.proPrice,
                    isIncome = true,
                    dateMillis = now,
                    auto = true
                )
            )
        }
        val sameDay = previous.manualTodayKey == today
        return result.copy(
            entries = result.entries + newEntries,
            manualPremiumToday = (if (sameDay) previous.manualPremiumToday else 0L) + premiumAdded,
            manualProToday = (if (sameDay) previous.manualProToday else 0L) + proAdded,
            manualTodayKey = today
        )
    }

    /** Turns Google Play on for good: the typed numbers are cleared and the user list takes over. */
    fun withGooglePlayOn(now: Long): AppData {
        if (googlePlayEnabled) return this
        return copy(
            googlePlayEnabled = true,
            googlePlaySinceMillis = now,
            manualTotalUsers = 0L,
            manualPremiumUsers = 0L,
            manualProUsers = 0L,
            manualDailyNewUsers = 0L,
            manualNewUsersDayKey = "",
            manualPremiumToday = 0L,
            manualProToday = 0L,
            manualTodayKey = ""
        )
    }

    /** Moves the whole Apple Store Balance or Paystack Balance into this app's income. */
    fun withdrawnChannel(channel: Channel, now: Long): AppData {
        val amount = if (channel == Channel.APPLE) appleBalance else paystackBalance
        if (amount <= 0L || channel == Channel.PLAY) return this
        val title = if (channel == Channel.APPLE) "Apple Store withdrawal" else "Paystack withdrawal"
        return copy(
            appleSubsBalance = if (channel == Channel.APPLE) 0L else appleSubsBalance,
            paystackSubsBalance = if (channel == Channel.WEBSITE) 0L else paystackSubsBalance,
            entries = entries + Entry(title = title, amount = amount, isIncome = true, dateMillis = now, auto = true)
        )
    }

    /** Moves the whole Google Play Balance into this app's income. */
    fun withdrawn(now: Long): AppData {
        val amount = playBalance
        if (amount <= 0L) return this
        return copy(
            playSubsBalance = 0L,
            playAdsBalance = 0L,
            entries = entries + Entry(
                title = "Google Play withdrawal",
                amount = amount,
                isIncome = true,
                dateMillis = now,
                auto = true
            )
        )
    }
}

/** The start of the day (12 am) that contains [millis], in the phone's time zone. */
fun dayStartMillis(millis: Long): Long {
    val zone = ZoneId.systemDefault()
    return Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        .atStartOfDay(zone).toInstant().toEpochMilli()
}

/** Extra monthly money for a staff member, for example Transport or Housing. */
data class Allowance(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val amount: Long
)

/** Key used for the salary part of a staff member's monthly pay. */
const val SALARY_KEY = "salary"

/** Key used for one allowance of a staff member's monthly pay. */
fun allowanceKey(allowanceId: String): String = "allowance:$allowanceId"

/** One part of a staff member's monthly pay that can be paid on its own: the salary or one allowance. */
data class PayItem(
    val key: String,
    val name: String,
    val amount: Long
)

/** One recorded payment. */
data class Payment(
    val id: String = UUID.randomUUID().toString(),
    val dateMillis: Long,
    val amount: Long,
    /**
     * What this payment covers: [SALARY_KEY] or an [allowanceKey]. Empty means the whole month
     * (the way payments were recorded before salary and allowances could be paid separately).
     */
    val itemKey: String = "",
    /** Readable name of what was paid, for example "Salary" or "Transport". Empty for whole-month payments. */
    val itemName: String = ""
)

/**
 * One photo of a staff member. Only this small description is saved in the ledger (and synced);
 * the picture itself lives in a file on the phone and in the cloud's media storage.
 */
data class StaffImage(
    /** Lowercase UUID (letters, digits and hyphens only). Also the file name. */
    val id: String,
    val addedMillis: Long,
    val sizeBytes: Long,
    val sha256: String
)

/** One staff member with contact details, pay and payment history. */
data class Staff(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val role: String = "",
    val email: String = "",
    val phone: String = "",
    val bankName: String = "",
    val accountNumber: String = "",
    val monthlySalary: Long = 0,
    val allowances: List<Allowance> = emptyList(),
    val payments: List<Payment> = emptyList(),
    /** Up to [MAX_STAFF_IMAGES] photos of this person. */
    val images: List<StaffImage> = emptyList()
) {
    val allowancesTotal: Long get() = allowances.sumOf { it.amount }
    val monthlyTotal: Long get() = monthlySalary + allowancesTotal
    val totalPaid: Long get() = payments.sumOf { it.amount }

    private fun paymentsIn(millis: Long): List<Payment> {
        val key = monthKey(millis)
        return payments.filter { monthKey(it.dateMillis) == key }
    }

    /** The salary and every allowance that has an amount, each of which can be paid on its own. */
    fun payItems(): List<PayItem> {
        val items = ArrayList<PayItem>()
        if (monthlySalary > 0L) items.add(PayItem(SALARY_KEY, "Salary", monthlySalary))
        for (a in allowances) {
            if (a.amount > 0L) items.add(PayItem(allowanceKey(a.id), a.name, a.amount))
        }
        return items
    }

    /** The payment that covers this item in the month of [millis], or null if it is not paid yet. */
    fun itemPayment(key: String, millis: Long): Payment? {
        val inMonth = paymentsIn(millis)
        return inMonth.firstOrNull { it.itemKey == key } ?: inMonth.firstOrNull { it.itemKey.isEmpty() }
    }

    fun isItemPaid(key: String, millis: Long): Boolean = itemPayment(key, millis) != null

    /** Parts of this month's pay that are still unpaid. */
    fun outstandingItems(millis: Long): List<PayItem> =
        payItems().filter { !isItemPaid(it.key, millis) }

    /** The amount still to be paid this month. */
    fun outstandingTotal(millis: Long): Long = outstandingItems(millis).sumOf { it.amount }

    /** True when something was paid this month and nothing is left to pay. */
    fun isPaidIn(millis: Long): Boolean =
        paymentsIn(millis).isNotEmpty() && outstandingItems(millis).isEmpty()

    fun paidInMonth(millis: Long): Long = paymentsIn(millis).sumOf { it.amount }

    /** Records one part of this month's pay (the salary or one allowance). Does nothing if it is already paid. */
    fun paidItem(key: String, millis: Long): Staff {
        val item = outstandingItems(millis).firstOrNull { it.key == key } ?: return this
        return copy(
            payments = payments + Payment(
                dateMillis = millis,
                amount = item.amount,
                itemKey = item.key,
                itemName = item.name
            )
        )
    }

    /** Records every part of this month's pay that is still unpaid. Does nothing if nothing is left to pay. */
    fun paidForMonth(millis: Long): Staff {
        var result = this
        for (item in outstandingItems(millis)) {
            result = result.paidItem(item.key, millis)
        }
        return result
    }
}

const val DAY_MILLIS = 86_400_000L

/** Something paid for that runs out, for example a domain, hosting or a tool subscription. */
data class Renewal(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val amount: Long = 0,
    val durationDays: Int,
    val paidMillis: Long,
    val expiryMillis: Long,
    /** Days before expiry when the app starts warning, for example 30, 7 and 2. */
    val reminderDays: List<Int> = listOf(30, 7, 2)
) {
    /** Whole days from today to the expiry day. Negative means already expired. */
    fun daysLeft(now: Long): Long {
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val end = Instant.ofEpochMilli(expiryMillis).atZone(zone).toLocalDate()
        return ChronoUnit.DAYS.between(today, end)
    }

    /** 0 = fine, 1 = inside a reminder stage, 2 = inside the last stage or expired. */
    fun alertLevel(now: Long): Int {
        val left = daysLeft(now)
        if (left < 0L) return 2
        val stages = reminderDays.sorted()
        if (stages.isEmpty()) return if (left == 0L) 2 else 0
        if (left <= stages.first()) return 2
        if (left <= stages.last()) return 1
        return 0
    }

    /** Pays for another period. If it has not run out yet, the new period starts after the current one. */
    fun renewed(now: Long): Renewal {
        val base = maxOf(now, expiryMillis)
        return copy(paidMillis = now, expiryMillis = base + durationDays * DAY_MILLIS)
    }
}

/** Company-level costs such as rent. These live only on the main dashboard. */
data class Company(
    val name: String = DEFAULT_COMPANY_NAME,
    val expenses: List<Entry> = emptyList()
) {
    val totalExpenses: Long get() = expenses.sumOf { it.amount }
}

/** Everything the app stores. The main dashboard totals are calculated from this. */
data class Ledger(
    val company: Company = Company(),
    val apps: List<AppData> = emptyList(),
    val staff: List<Staff> = emptyList(),
    val renewals: List<Renewal> = emptyList(),
    /** Every withdrawal from the Google Play Balance, newest first (the last 200 are kept). */
    val withdrawals: List<Withdrawal> = emptyList(),
    /** Customer Service & Complaints cases created by the daily roll of Google Play apps. */
    val cases: List<ComplaintCase> = emptyList(),
    val nextCaseNumber: Int = 1,
    /** The highest "now" ever seen by the Cases screens. Protects against setting the phone clock back. */
    val casesClockMillis: Long = 0,
    /** Software Development Clients: proposals, projects, clients, the $ account and exchange rates. */
    val software: SoftwareData = SoftwareData(),
    /** Socials: the company's social accounts and their followers (made only by the social dice). */
    val socials: SocialsData = SocialsData(),
    /** Company documents, tools and the total storage the company has. */
    val assets: CompanyAssets = CompanyAssets()
) {
    /** Renewals that are inside a reminder stage or expired, soonest first. */
    fun renewalsNeedingAttention(now: Long): List<Renewal> =
        renewals.filter { it.alertLevel(now) > 0 }.sortedBy { it.daysLeft(now) }

    /** Renews one item and records its amount as a company cost. */
    fun renew(renewalId: String, now: Long): Ledger {
        val item = renewals.firstOrNull { it.id == renewalId } ?: return this
        val updatedList = renewals.map { if (it.id == renewalId) it.renewed(now) else it }
        val newExpenses = if (item.amount > 0L) {
            company.expenses + Entry(
                title = "Renewal: " + item.name,
                amount = item.amount,
                isIncome = false,
                dateMillis = now,
                auto = true
            )
        } else {
            company.expenses
        }
        return copy(renewals = updatedList, company = company.copy(expenses = newExpenses))
    }

    val totalIncome: Long get() = apps.sumOf { it.income }
    val appExpenses: Long get() = apps.sumOf { it.expenses }
    val companyExpenses: Long get() = company.totalExpenses
    val staffPaid: Long get() = staff.sumOf { it.totalPaid }
    val totalExpenses: Long get() = appExpenses + companyExpenses + staffPaid
    val profit: Long get() = totalIncome - totalExpenses

    val totalUsers: Long get() = apps.sumOf { it.totalUsers }
    val premiumUsers: Long get() = apps.sumOf { it.premiumUsers }
    val proUsers: Long get() = apps.sumOf { it.proUsers }

    fun newUsersToday(now: Long): Long = apps.sumOf { it.newUsersToday(now) }
    fun premiumToday(now: Long): Long = apps.sumOf { it.premiumTodayAt(now) }
    fun proToday(now: Long): Long = apps.sumOf { it.proTodayAt(now) }
    /** Money waiting in every app's Google Play Balance. It is not in profit until it is withdrawn. */
    val playBalanceTotal: Long get() = apps.sumOf { it.playBalance }

    /** Money waiting in every app's Apple Store Balance and Paystack Balance. */
    val appleBalanceTotal: Long get() = apps.sumOf { it.appleBalance }
    val paystackBalanceTotal: Long get() = apps.sumOf { it.paystackBalance }

    fun playEarningsToday(now: Long): Long = apps.sumOf { it.playEarningsToday(now) }

    fun activeUsersToday(now: Long): Long = apps.sumOf { it.activeUsersAt(now) }

    /** Paid plans that end within [days] days, or have already ended. */
    fun plansExpiringSoon(now: Long, days: Long = 7L): Int =
        apps.sumOf { app ->
            app.users.count { user ->
                val left = user.planDaysLeft(now)
                left != null && left <= days
            }
        }

    val monthlyPayroll: Long get() = staff.sumOf { it.monthlyTotal }

    fun staffPaidInMonth(millis: Long): Long = staff.sumOf { it.paidInMonth(millis) }

    /** Staff who still have to be paid this month (and have pay set). */
    fun unpaidStaff(millis: Long): List<Staff> =
        staff.filter { it.outstandingItems(millis).isNotEmpty() }

    fun payStaff(staffId: String, millis: Long): Ledger =
        copy(staff = staff.map { if (it.id == staffId) it.paidForMonth(millis) else it })

    fun payAllStaff(millis: Long): Ledger =
        copy(staff = staff.map { it.paidForMonth(millis) })
}
