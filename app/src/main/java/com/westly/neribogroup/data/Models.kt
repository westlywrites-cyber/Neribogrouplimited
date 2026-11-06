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

/** One real user of an app, with the plan they are on. */
data class AppUser(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val email: String,
    /** Empty when no phone number was given. */
    val phone: String = "",
    val plan: Plan = Plan.FREE,
    val addedMillis: Long
)

/** One app dashboard, for example "Neribo Vault". */
data class AppData(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val totalUsers: Long = 0,
    val premiumUsers: Long = 0,
    val proUsers: Long = 0,
    val dailyNewUsers: Long = 0,
    /** Price of one Premium subscription, in naira. */
    val premiumPrice: Long = 0,
    /** Price of one Pro subscription, in naira. */
    val proPrice: Long = 0,
    /** New Premium subscriptions counted on the day named by [todayKey]. */
    val premiumToday: Long = 0,
    /** New Pro subscriptions counted on the day named by [todayKey]. */
    val proToday: Long = 0,
    val todayKey: String = "",
    /** The day [dailyNewUsers] was last typed in. Older days count as zero. */
    val newUsersDayKey: String = "",
    val entries: List<Entry> = emptyList(),
    val users: List<AppUser> = emptyList(),
    val premiumBenefits: String = "",
    val proBenefits: String = ""
) {
    val income: Long get() = entries.filter { it.isIncome }.sumOf { it.amount }
    val expenses: Long get() = entries.filter { !it.isIncome }.sumOf { it.amount }
    val profit: Long get() = income - expenses

    fun newUsersToday(now: Long): Long =
        if (newUsersDayKey == dayKey(now)) dailyNewUsers else 0L

    fun premiumTodayAt(now: Long): Long =
        if (todayKey == dayKey(now)) premiumToday else 0L

    fun proTodayAt(now: Long): Long =
        if (todayKey == dayKey(now)) proToday else 0L

    fun subscriptionIncomeToday(now: Long): Long =
        premiumTodayAt(now) * premiumPrice + proTodayAt(now) * proPrice

    /** The copy used while editing, so the "new users today" box shows today's real value. */
    fun forEditing(now: Long): AppData = copy(dailyNewUsers = newUsersToday(now))

    /** Moves the Premium or Pro count by [delta] (never below 0). FREE changes nothing. */
    private fun withPlanCount(plan: Plan, delta: Long): AppData = when (plan) {
        Plan.PREMIUM -> copy(premiumUsers = (premiumUsers + delta).coerceAtLeast(0L))
        Plan.PRO -> copy(proUsers = (proUsers + delta).coerceAtLeast(0L))
        Plan.FREE -> this
    }

    /** Adds a user and raises the Premium or Pro count if the user is on that plan. */
    fun withUserAdded(user: AppUser): AppData =
        copy(users = users + user).withPlanCount(user.plan, 1L)

    /** Replaces a user (matched by id). If the plan changed, the counts move with it. */
    fun withUserUpdated(updated: AppUser): AppData {
        val old = users.firstOrNull { it.id == updated.id } ?: return this
        val replaced = copy(users = users.map { if (it.id == updated.id) updated else it })
        return if (old.plan == updated.plan) {
            replaced
        } else {
            replaced.withPlanCount(old.plan, -1L).withPlanCount(updated.plan, 1L)
        }
    }

    /** Removes a user and lowers the count of their plan. Income already recorded stays. */
    fun withUserRemoved(userId: String): AppData {
        val old = users.firstOrNull { it.id == userId } ?: return this
        return copy(users = users.filter { it.id != userId }).withPlanCount(old.plan, -1L)
    }

    /**
     * Called when saving an edit. [previous] is the saved version before the edit.
     * If Premium or Pro users went up, adds income for the new subscriptions
     * (increase x price) and adds them to today's counters.
     */
    fun finalizeSave(previous: AppData, now: Long): AppData {
        val today = dayKey(now)
        var result = this

        if (dailyNewUsers != previous.newUsersToday(now)) {
            result = result.copy(newUsersDayKey = today)
        }

        val premiumAdded = (premiumUsers - previous.premiumUsers).coerceAtLeast(0L)
        val proAdded = (proUsers - previous.proUsers).coerceAtLeast(0L)
        if (premiumAdded == 0L && proAdded == 0L) return result

        val newEntries = ArrayList<Entry>()
        if (premiumAdded > 0L && premiumPrice > 0L) {
            newEntries.add(
                Entry(
                    title = "$premiumAdded new Premium subscription" +
                        if (premiumAdded == 1L) "" else "s",
                    amount = premiumAdded * premiumPrice,
                    isIncome = true,
                    dateMillis = now,
                    auto = true
                )
            )
        }
        if (proAdded > 0L && proPrice > 0L) {
            newEntries.add(
                Entry(
                    title = "$proAdded new Pro subscription" +
                        if (proAdded == 1L) "" else "s",
                    amount = proAdded * proPrice,
                    isIncome = true,
                    dateMillis = now,
                    auto = true
                )
            )
        }

        return result.copy(
            entries = result.entries + newEntries,
            premiumToday = result.premiumTodayAt(now) + premiumAdded,
            proToday = result.proTodayAt(now) + proAdded,
            todayKey = today
        )
    }
}

/** Extra monthly money for a staff member, for example Transport or Housing. */
data class Allowance(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val amount: Long
)

/** One recorded salary payment. */
data class Payment(
    val id: String = UUID.randomUUID().toString(),
    val dateMillis: Long,
    val amount: Long
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

    fun isPaidIn(millis: Long): Boolean {
        val key = monthKey(millis)
        return payments.any { monthKey(it.dateMillis) == key }
    }

    fun paidInMonth(millis: Long): Long {
        val key = monthKey(millis)
        return payments.filter { monthKey(it.dateMillis) == key }.sumOf { it.amount }
    }

    /** Records this month's pay. Does nothing if already paid this month or there is no pay set. */
    fun paidForMonth(millis: Long): Staff =
        if (isPaidIn(millis) || monthlyTotal <= 0L) {
            this
        } else {
            copy(payments = payments + Payment(dateMillis = millis, amount = monthlyTotal))
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
    val renewals: List<Renewal> = emptyList()
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
    fun subscriptionIncomeToday(now: Long): Long = apps.sumOf { it.subscriptionIncomeToday(now) }

    val monthlyPayroll: Long get() = staff.sumOf { it.monthlyTotal }

    fun staffPaidInMonth(millis: Long): Long = staff.sumOf { it.paidInMonth(millis) }

    /** Staff who still have to be paid this month (and have pay set). */
    fun unpaidStaff(millis: Long): List<Staff> =
        staff.filter { !it.isPaidIn(millis) && it.monthlyTotal > 0L }

    fun payStaff(staffId: String, millis: Long): Ledger =
        copy(staff = staff.map { if (it.id == staffId) it.paidForMonth(millis) else it })

    fun payAllStaff(millis: Long): Ledger =
        copy(staff = staff.map { it.paidForMonth(millis) })
}
