package com.westly.neribogroup.data

import java.util.UUID

/** Customer Service & Complaints. Everything here is a simulation. Nothing is legal advice. */

enum class CaseCategory { BILLING_RENEWAL, BILLING_UPGRADE, PLAN_ACCESS, DATA_PRIVACY, ADS_CONTENT }

enum class CaseStatus { OPEN, PENDING, RESOLVED, ESCALATED, IN_COURT, CLOSED, PAYMENT_DUE, PAID }

enum class CaseActor { CUSTOMER, SUPPORT, REGULATOR, COURT, COMPANY, OWNER, SYSTEM }

enum class CaseOutcome {
    NONE, RESOLVED_BY_SUPPORT, REGULATOR_CLOSED, REGULATOR_REFUND, REGULATOR_PENALTY,
    SETTLED, COMPANY_WON, CUSTOMER_WON
}

enum class CaseEventKind {
    CUSTOMER_EMAIL, SUPPORT_REPLY, CUSTOMER_REPLY, RESOLUTION, CUSTOMER_ESCALATION, REGULATOR_LETTER,
    COMPANY_RESPONSE, REGULATOR_DECISION, COURT_FILED, COURT_SERVICE, COURT_HEARING, COURT_ADJOURNED,
    COURT_JUDGMENT, SETTLEMENT, PAYMENT_DEMAND, PAID, NOTE
}

/** One step of a case. [dueMillis] is when it becomes visible. */
data class CaseEvent(
    val id: String = UUID.randomUUID().toString(),
    val dueMillis: Long,
    val kind: CaseEventKind,
    val actor: CaseActor,
    val title: String,
    val body: String,
    /** Null = this event does not change the status. */
    val statusAfter: CaseStatus? = null
)

data class ComplaintCase(
    val id: String = UUID.randomUUID().toString(),
    /** For example "NG-CS-2026-0007". */
    val ref: String,
    val appId: String,
    val appName: String,
    /** The real AppUser this case is about. */
    val userId: String,
    val customerName: String,
    val customerEmail: String,
    val customerPhone: String,
    /** Plan at creation. */
    val plan: Plan,
    val category: CaseCategory,
    val subject: String,
    val createdMillis: Long,
    /** The WHOLE planned timeline, decided once at creation. */
    val events: List<CaseEvent>,
    /** Decided at creation, shown only when its event is visible. */
    val outcome: CaseOutcome,
    /** Empty when never escalated, for example "FCCPC". */
    val regulator: String = "",
    val regulatorRef: String = "",
    /** Empty when there is no lawsuit. */
    val court: String = "",
    val suitNo: String = "",
    val claimAmount: Long = 0,
    /** 0 unless the outcome obliges the company to pay. */
    val amountDue: Long = 0,
    val dueReason: String = "",
    val payByMillis: Long = 0,
    /** 0 = not paid. */
    val paidMillis: Long = 0,
    /** Empty until paid. Then the id of the company expense. */
    val expenseId: String = "",
    /** Owner notes (kind NOTE, actor OWNER, dueMillis = when written). */
    val notes: List<CaseEvent> = emptyList()
) {
    val hasRegulator: Boolean get() = regulator.isNotBlank()
    val hasSuit: Boolean get() = suitNo.isNotBlank() || court.isNotBlank()
}

/** Pure functions: the same ledger always shows the same result. Nothing is re-rolled here. */
object CaseEngine {

    /** Events (and owner notes) that are visible at [now], oldest first. */
    fun visibleEvents(c: ComplaintCase, now: Long): List<CaseEvent> =
        (c.events + c.notes).filter { it.dueMillis <= now }.sortedBy { it.dueMillis }

    /** Only the planned events (no notes) that are visible, oldest first. */
    private fun visiblePlanned(c: ComplaintCase, now: Long): List<CaseEvent> =
        c.events.filter { it.dueMillis <= now }.sortedBy { it.dueMillis }

    /** True when the event that creates the payment is visible and there is money to pay. */
    fun paymentVisible(c: ComplaintCase, now: Long): Boolean =
        c.amountDue > 0L && c.events.any { it.dueMillis <= now && it.statusAfter == CaseStatus.PAYMENT_DUE }

    fun statusAt(c: ComplaintCase, now: Long): CaseStatus {
        if (c.paidMillis > 0L) return CaseStatus.PAID
        if (paymentVisible(c, now)) return CaseStatus.PAYMENT_DUE
        val last = visiblePlanned(c, now).lastOrNull { it.statusAfter != null && it.statusAfter != CaseStatus.PAYMENT_DUE }
        return last?.statusAfter ?: CaseStatus.OPEN
    }

    /** The amount the company still has to pay now. 0 when nothing is due or it is already paid. */
    fun amountDueAt(c: ComplaintCase, now: Long): Long =
        if (c.paidMillis > 0L) 0L
        else if (paymentVisible(c, now)) c.amountDue
        else 0L

    /** Final = RESOLVED, CLOSED or PAID. Everything else still needs attention. */
    fun isFinal(status: CaseStatus): Boolean =
        status == CaseStatus.RESOLVED || status == CaseStatus.CLOSED || status == CaseStatus.PAID

    /** True once the escalation to a regulator has become visible. */
    fun escalatedAt(c: ComplaintCase, now: Long): Boolean =
        c.regulator.isNotBlank() && c.events.any {
            it.dueMillis <= now &&
                (it.kind == CaseEventKind.CUSTOMER_ESCALATION || it.kind == CaseEventKind.REGULATOR_LETTER)
        }

    /** True once the lawsuit has been filed (visible). */
    fun inCourtAt(c: ComplaintCase, now: Long): Boolean =
        c.court.isNotBlank() && c.events.any { it.dueMillis <= now && it.kind == CaseEventKind.COURT_FILED }

    /** The date of the latest visible event (or the creation date). */
    fun latestMillis(c: ComplaintCase, now: Long): Long =
        visibleEvents(c, now).lastOrNull()?.dueMillis ?: c.createdMillis

    /** The next hearing that has not happened yet, or 0. */
    fun nextHearingMillis(c: ComplaintCase, now: Long): Long =
        c.events.filter { it.kind == CaseEventKind.COURT_HEARING && it.dueMillis > now }
            .minOfOrNull { it.dueMillis } ?: 0L

    /** The stage of a lawsuit in plain words. */
    fun courtStage(c: ComplaintCase, now: Long): String {
        val visible = visiblePlanned(c, now).filter {
            it.kind == CaseEventKind.COURT_FILED || it.kind == CaseEventKind.COURT_SERVICE ||
                it.kind == CaseEventKind.COURT_HEARING || it.kind == CaseEventKind.COURT_ADJOURNED ||
                it.kind == CaseEventKind.COURT_JUDGMENT || it.kind == CaseEventKind.SETTLEMENT
        }
        val last = visible.lastOrNull() ?: return "Not filed yet"
        return when (last.kind) {
            CaseEventKind.COURT_FILED -> "Suit filed"
            CaseEventKind.COURT_SERVICE -> "Company served"
            CaseEventKind.COURT_HEARING -> last.title
            CaseEventKind.COURT_ADJOURNED -> "Adjourned"
            CaseEventKind.COURT_JUDGMENT -> "Judgment delivered"
            CaseEventKind.SETTLEMENT -> "Settled out of court"
            else -> "In progress"
        }
    }

    /** "Customer", "Neribo Group (company)", "Settled — no winner" or null while undecided. */
    fun winnerAt(c: ComplaintCase, now: Long): String? {
        val decided = c.events.any {
            it.dueMillis <= now &&
                (it.kind == CaseEventKind.COURT_JUDGMENT || it.kind == CaseEventKind.SETTLEMENT)
        }
        if (!decided) return null
        return when (c.outcome) {
            CaseOutcome.CUSTOMER_WON -> "Customer"
            CaseOutcome.COMPANY_WON -> "Neribo Group (company)"
            CaseOutcome.SETTLED -> "Settled — no winner"
            else -> null
        }
    }
}

/**
 * Records a case payment as ONE company expense and marks the case Paid, in one ledger update.
 * Returns the ledger unchanged when the case is not payable, is already paid, or already has an expense.
 */
fun Ledger.markCasePaid(caseId: String, now: Long): Ledger {
    val c = cases.firstOrNull { it.id == caseId } ?: return this
    val effectiveNow = maxOf(now, casesClockMillis)
    val expenseKey = "legal-$caseId"
    if (CaseEngine.statusAt(c, effectiveNow) != CaseStatus.PAYMENT_DUE) return this
    if (c.paidMillis > 0L || c.expenseId.isNotEmpty() || c.amountDue <= 0L) return this
    if (company.expenses.any { it.caseId == c.id || it.id == expenseKey }) return this

    val suit = if (c.suitNo.isNotBlank()) " (" + c.suitNo + ")" else ""
    val expense = Entry(
        id = expenseKey,
        title = c.dueReason + " — case " + c.ref + suit,
        amount = c.amountDue,
        isIncome = false,
        dateMillis = effectiveNow,
        auto = true,
        caseId = c.id,
        appId = c.appId
    )
    val note = CaseEvent(
        dueMillis = effectiveNow,
        kind = CaseEventKind.PAID,
        actor = CaseActor.SYSTEM,
        title = "Payment recorded",
        body = "Payment of ₦" + java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(c.amountDue) +
            " recorded as a company expense.",
        statusAfter = null
    )
    val paid = c.copy(
        paidMillis = effectiveNow,
        expenseId = expenseKey,
        notes = c.notes + note
    )
    return copy(
        company = company.copy(expenses = company.expenses + expense),
        cases = cases.map { if (it.id == caseId) paid else it },
        casesClockMillis = effectiveNow
    )
}

/** Adds an owner note to a case. Notes never change status or money. */
fun Ledger.withCaseNote(caseId: String, text: String, now: Long): Ledger {
    val clean = text.trim()
    if (clean.isEmpty()) return this
    val c = cases.firstOrNull { it.id == caseId } ?: return this
    val effectiveNow = maxOf(now, casesClockMillis)
    val note = CaseEvent(
        dueMillis = effectiveNow,
        kind = CaseEventKind.NOTE,
        actor = CaseActor.OWNER,
        title = "Your note",
        body = clean.take(500),
        statusAfter = null
    )
    return copy(
        cases = cases.map { if (it.id == caseId) c.copy(notes = c.notes + note) else it },
        casesClockMillis = effectiveNow
    )
}

/** Raises the saved clock to [now] when it is higher. Returns the same ledger when nothing changes. */
fun Ledger.withClockAtLeast(now: Long): Ledger =
    if (now > casesClockMillis) copy(casesClockMillis = now) else this

/** Self-healing on load: keeps the first company expense of each case and drops repeats. */
fun Ledger.withoutDuplicateLegalExpenses(): Ledger {
    val seen = HashSet<String>()
    val cleaned = company.expenses.filter { e ->
        if (e.caseId.isEmpty()) true else seen.add(e.caseId)
    }
    return if (cleaned.size == company.expenses.size) this
    else copy(company = company.copy(expenses = cleaned))
}

/** Adds a new case, numbers it, and keeps at most 300 (dropping the oldest finished ones only). */
fun Ledger.withCaseAdded(c: ComplaintCase, now: Long): Ledger {
    val added = cases + c
    var list = added
    if (list.size > MAX_CASES) {
        val removable = list
            .filter {
                val s = CaseEngine.statusAt(it, maxOf(now, casesClockMillis))
                s == CaseStatus.RESOLVED || s == CaseStatus.CLOSED || s == CaseStatus.PAID
            }
            .sortedBy { it.createdMillis }
        val dropIds = removable.take(list.size - MAX_CASES).map { it.id }.toSet()
        list = list.filter { it.id !in dropIds }
    }
    return copy(cases = list, nextCaseNumber = nextCaseNumber + 1)
}

const val MAX_CASES = 300
