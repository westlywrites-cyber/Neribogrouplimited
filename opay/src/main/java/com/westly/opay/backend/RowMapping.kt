package com.westly.opay.backend

import com.westly.opay.data.model.AppNotification
import com.westly.opay.data.model.Bank
import com.westly.opay.data.model.Beneficiary
import com.westly.opay.data.model.Transaction
import com.westly.opay.data.model.TxType
import com.westly.opay.data.model.UserProfile
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

const val STATUS_ACTIVE_VALUE = "active"
const val FREE_TRANSFERS_PER_DAY = 5
const val MAX_RECENTS = 10

/** The repository's values after the rows are mapped to the app models. */
data class MappedData(
    val active: Boolean,
    val profile: UserProfile,
    val balanceKobo: Long,
    val businessNumber: String? = null,
    val freeTransfersLeft: Int,
    val transactions: List<Transaction>,
    val notifications: List<AppNotification>,
    val beneficiaries: List<Beneficiary>,
    val banks: List<Bank>,
    val savings: List<SavingsPositionRow>
)

/** Pure row to model mapping, kept free of Android types so it can be unit tested. */
object RowMapping {
    private val dobPattern = Regex("""\d{4}-\d{2}-\d{2}""")

    fun map(snapshot: RemoteSnapshot, today: LocalDate): MappedData {
        return MappedData(
            active = snapshot.profile.status == STATUS_ACTIVE_VALUE,
            profile = profile(snapshot.profile),
            balanceKobo = snapshot.account?.balanceKobo ?: 0L,
            businessNumber = snapshot.account?.businessNumber,
            freeTransfersLeft = snapshot.account?.let { freeTransfersLeft(it, today) } ?: FREE_TRANSFERS_PER_DAY,
            transactions = snapshot.transactions.mapNotNull(::transaction),
            notifications = snapshot.notifications.map(::notification),
            beneficiaries = beneficiaries(snapshot.beneficiaries),
            banks = sortBanks(snapshot.banks).map(::bank),
            savings = snapshot.savings
        )
    }

    fun profile(row: ProfileRow): UserProfile = UserProfile(
        id = row.id,
        fullName = displayName(row).uppercase(),
        nickname = row.nickname?.trim().orEmpty().ifEmpty { row.firstName?.trim().orEmpty() },
        phone = "0" + row.phone,
        accountNumber = row.phone,
        tier = row.tier,
        emailMasked = "",
        gender = row.gender?.trim().orEmpty(),
        dobMasked = maskedDob(row.dob)
    )

    /** First, middle (if any) and surname joined by single spaces. */
    fun displayName(row: ProfileRow): String =
        listOf(row.firstName, row.middleName, row.surname)
            .mapNotNull { it?.trim()?.takeIf { part -> part.isNotEmpty() } }
            .joinToString(" ")

    fun maskedDob(dob: String?): String {
        val text = dob?.trim().orEmpty()
        return if (dobPattern.matches(text)) "**-**-" + text.take(4) else ""
    }

    /** Null when the type is not one the app knows, so the row is left out instead of shown wrongly. */
    fun transaction(row: TransactionRow): Transaction? {
        val type = TxType.entries.firstOrNull { it.name == row.type } ?: return null
        return Transaction(
            id = row.id,
            type = type,
            title = row.title,
            counterpartyName = row.counterpartyName,
            counterpartyBank = row.counterpartyBank,
            counterpartyAccount = row.counterpartyAccount,
            amountKobo = row.amountKobo,
            timestampMillis = epochMillis(row.createdAt),
            status = row.status,
            txNo = row.txNo,
            sessionId = row.sessionId,
            category = row.category,
            remark = row.remark
        )
    }

    fun notification(row: NotificationRow): AppNotification = AppNotification(
        id = row.id,
        title = row.title,
        body = row.body,
        timestampMillis = epochMillis(row.createdAt),
        tab = row.tab,
        read = row.read
    )

    fun beneficiary(row: BeneficiaryRow): Beneficiary = Beneficiary(
        name = row.name,
        accountNumber = row.accountNumber,
        bankName = row.bankName,
        isFavourite = row.isFavourite,
        bankCode = row.bankCode
    )

    /** Every saved recipient, the one used most recently first. Rows with the same time keep their order. */
    fun beneficiaries(rows: List<BeneficiaryRow>): List<Beneficiary> =
        rows.sortedByDescending { epochMillis(it.lastUsedAt) }.map(::beneficiary)

    /** The internal OPay bank first, then by `sort`, then by name. */
    fun sortBanks(rows: List<BankRow>): List<BankRow> =
        rows.sortedWith(
            compareByDescending<BankRow> { it.isInternal }
                .thenBy { it.sort }
                .thenBy { it.name.lowercase() }
        )

    fun bank(row: BankRow): Bank = Bank(
        code = row.code,
        name = row.name,
        successRate = row.successRate,
        isMfb = row.isMfb,
        isInternal = row.isInternal
    )

    /** The server resets the counter on the first transfer of a new Lagos day, so an older day means a full allowance. */
    fun freeTransfersLeft(row: AccountRow, today: LocalDate): Int {
        val day = try {
            LocalDate.parse(row.freeTransfersDay)
        } catch (e: DateTimeParseException) {
            null
        }
        return if (day == today) row.freeTransfersLeft.coerceAtLeast(0) else FREE_TRANSFERS_PER_DAY
    }

    /** Postgres timestamps such as `2026-10-29T10:44:46.123456+00:00`. Zero when the text cannot be read. */
    fun epochMillis(text: String): Long = try {
        OffsetDateTime.parse(text).toInstant().toEpochMilli()
    } catch (e: DateTimeParseException) {
        0L
    }
}
