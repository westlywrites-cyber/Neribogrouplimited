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
    val auto: Boolean = false
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
    val simulated: Boolean = false
) {
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
    val campaign: Campaign? = null
) {
    val income: Long get() = entries.filter { it.isIncome }.sumOf { it.amount }
    val expenses: Long get() = entries.filter { !it.isIncome }.sumOf { it.amount }
    val profit: Long get() = income - expenses

    /** Users, Premium and Pro are counted from the user list. */
    val totalUsers: Long get() = users.size.toLong()
    val premiumUsers: Long get() = users.count { it.plan == Plan.PREMIUM }.toLong()
    val proUsers: Long get() = users.count { it.plan == Plan.PRO }.toLong()
    val freeUsers: Long get() = users.count { it.plan == Plan.FREE }.toLong()

    /** Everything waiting in the Google Play Balance. It is not profit until it is withdrawn. */
    val playBalance: Long get() = playSubsBalance + playAdsBalance

    fun newUsersToday(now: Long): Long {
        val start = dayStartMillis(now)
        val end = start + DAY_MILLIS
        return users.count { it.addedMillis >= start && it.addedMillis < end }.toLong()
    }

    fun premiumTodayAt(now: Long): Long {
        val start = dayStartMillis(now)
        val end = start + DAY_MILLIS
        return users.count {
            it.plan == Plan.PREMIUM && it.planStartMillis >= start && it.planStartMillis < end
        }.toLong()
    }

    fun proTodayAt(now: Long): Long {
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

    /** Adds a user you typed in. A Premium or Pro user gets a start time and an end date PLAN_DAYS later. */
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
        return copy(users = users + fixed)
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
        return copy(users = users.map { if (it.id == fixed.id) fixed else it })
    }

    /**
     * A user you added renewed: adds PLAN_DAYS to the plan end date (from today if it already ended).
     * No money is recorded, because users you add yourself do not earn money.
     */
    fun withUserRenewed(userId: String, now: Long): AppData {
        val user = users.firstOrNull { it.id == userId } ?: return this
        if (user.plan == Plan.FREE || user.simulated) return this
        val base = maxOf(now, user.planExpiryMillis)
        val renewed = user.copy(planExpiryMillis = base + PLAN_DAYS * DAY_MILLIS)
        return copy(users = users.map { if (it.id == userId) renewed else it })
    }

    /** A user you added did not renew: moves them to Free. */
    fun withPlanLapsed(userId: String): AppData {
        val user = users.firstOrNull { it.id == userId } ?: return this
        if (user.plan == Plan.FREE || user.simulated) return this
        return copy(
            users = users.map {
                if (it.id == userId) it.copy(plan = Plan.FREE, planStartMillis = 0L, planExpiryMillis = 0L)
                else it
            }
        )
    }

    /** Removes a user you added. Users made by the dice cannot be removed. */
    fun withUserRemoved(userId: String): AppData {
        val old = users.firstOrNull { it.id == userId } ?: return this
        if (old.simulated) return this
        return copy(users = users.filter { it.id != userId })
    }

    /**
     * Used when saving an edit: keeps everything the dice own (their users, roll history, Play Balance,
     * active users and campaign) exactly as it is in [current], and takes only the settings and the
     * users you added from this copy.
     */
    fun keepingDiceData(current: AppData): AppData = copy(
        users = current.users.filter { it.simulated } + users.filter { !it.simulated },
        activeUsers = current.activeUsers,
        activeUsersDayKey = current.activeUsersDayKey,
        activeHistory = current.activeHistory,
        lastRollDayKey = current.lastRollDayKey,
        rolls = current.rolls,
        playTx = current.playTx,
        playSubsBalance = current.playSubsBalance,
        playAdsBalance = current.playAdsBalance,
        campaign = current.campaign
    )

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
    val payments: List<Payment> = emptyList()
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
    val withdrawals: List<Withdrawal> = emptyList()
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
