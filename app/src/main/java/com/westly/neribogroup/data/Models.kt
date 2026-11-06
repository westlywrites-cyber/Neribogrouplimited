package com.westly.neribogroup.data

import java.util.UUID

const val DEFAULT_COMPANY_NAME = "Neribo Group Limited"

/** One money line: either income or an expense. Amounts are whole naira. */
data class Entry(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val amount: Long,
    val isIncome: Boolean,
    val dateMillis: Long
)

/** One app dashboard, for example "Neribo Vault". */
data class AppData(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val totalUsers: Long = 0,
    val premiumUsers: Long = 0,
    val proUsers: Long = 0,
    val dailyNewUsers: Long = 0,
    val entries: List<Entry> = emptyList()
) {
    val income: Long get() = entries.filter { it.isIncome }.sumOf { it.amount }
    val expenses: Long get() = entries.filter { !it.isIncome }.sumOf { it.amount }
    val profit: Long get() = income - expenses
}

/** Company-level costs such as salaries. These live only on the main dashboard. */
data class Company(
    val name: String = DEFAULT_COMPANY_NAME,
    val expenses: List<Entry> = emptyList()
) {
    val totalExpenses: Long get() = expenses.sumOf { it.amount }
}

/** Everything the app stores. The main dashboard totals are calculated from this. */
data class Ledger(
    val company: Company = Company(),
    val apps: List<AppData> = emptyList()
) {
    val totalIncome: Long get() = apps.sumOf { it.income }
    val appExpenses: Long get() = apps.sumOf { it.expenses }
    val companyExpenses: Long get() = company.totalExpenses
    val totalExpenses: Long get() = appExpenses + companyExpenses
    val profit: Long get() = totalIncome - totalExpenses

    val totalUsers: Long get() = apps.sumOf { it.totalUsers }
    val premiumUsers: Long get() = apps.sumOf { it.premiumUsers }
    val proUsers: Long get() = apps.sumOf { it.proUsers }
}
