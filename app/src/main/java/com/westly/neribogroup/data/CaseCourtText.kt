package com.westly.neribogroup.data

import kotlin.random.Random

/**
 * Wording for regulator letters, company responses and court entries. A simulation in plain
 * language: it follows the usual steps but is not legal advice. Names, numbers and courts are made up.
 * Placeholders: {name} {first} {app} {plan} {amount} {date} {ref} {regulator} {regref} {court} {suit}
 * {claim} {award} {days} {reason} {payby}.
 */
object CaseCourtText {

    private fun <T> List<T>.pick(random: Random): T = this[random.nextInt(size)]

    private val LETTERS = listOf(
        "We acknowledge receipt of a complaint from {name} against Neribo Group Limited concerning {app}. Your complaint reference at this office is {regref}. The company is required to send its written response within 14 days of this letter. Failure to respond may lead to further action.\n\nComplaints Desk\n{regulator}",
        "This office has received a complaint by {name} about the services of Neribo Group Limited. The matter is registered as {regref}. Neribo Group Limited should reply in writing within 14 days and attach any record that supports its position.\n\nConsumer Complaints Unit\n{regulator}",
        "Please be informed that a complaint (ref. {regref}) has been lodged with us by {name}. We invite Neribo Group Limited to respond in writing within 14 days. Your response will be shared with the complainant.\n\nCase Officer\n{regulator}"
    )

    fun regulatorLetter(random: Random): String = LETTERS.pick(random)

    private val COMPANY = listOf(
        "Neribo Group Limited acknowledges your letter ref. {regref}. We have reviewed case {ref}. Our records show that the customer was served through Google Play and our support team replied within the stated time. We remain willing to resolve the matter and have attached our correspondence.\n\nLegal and Compliance, Neribo Group Limited",
        "We write in response to {regref}. Neribo Group Limited takes the complaint seriously. We explained the charge and offered to ask Google Play for a review. We respectfully ask that you consider our attached records.\n\nLegal and Compliance, Neribo Group Limited",
        "Thank you for your letter ref. {regref}. Please find our full response and the history of case {ref}. Neribo Group Limited has acted in line with its published terms and is ready to cooperate with this office.\n\nLegal and Compliance, Neribo Group Limited"
    )

    fun companyResponse(random: Random): String = COMPANY.pick(random)

    private val CLOSED = listOf(
        "After reviewing the complaint and the company's response, this office finds no further action is needed and the file is closed. Ref. {regref}.",
        "The commission has considered the response of Neribo Group Limited and the records provided. The matter is closed with no further action. Ref. {regref}.",
        "Having reviewed both sides, this office considers that the company has dealt with the issue adequately. The complaint is closed. Ref. {regref}."
    )

    fun decisionClosed(random: Random): String = CLOSED.pick(random)

    private val REFUND = listOf(
        "This office directs Neribo Group Limited to ensure that the complainant is refunded the amount in dispute through Google Play and to confirm to us when this is done. Ref. {regref}.",
        "After review, the company is directed to refund the complainant through Google Play and to improve its reminders before renewals. Ref. {regref}."
    )

    fun decisionRefund(random: Random): String = REFUND.pick(random)

    private val PENALTY = listOf(
        "Following its review, the commission finds that Neribo Group Limited did not meet its data protection duties in this matter. A penalty of {amount} is imposed, to be paid within 30 days, that is by {payby}. Ref. {regref}.",
        "The commission has concluded that the company failed to handle the complainant's personal data properly. A penalty of {amount} is imposed and must be paid by {payby}. Ref. {regref}."
    )

    fun decisionPenalty(random: Random): String = PENALTY.pick(random)

    private val UNRESOLVED = listOf(
        "The complaint could not be resolved at this office. The complainant is free to take the matter to court. Ref. {regref}.",
        "Both sides remain apart on this matter. This office has closed its review without a settlement and the complainant may pursue the claim in court. Ref. {regref}."
    )

    fun decisionUnresolved(random: Random): String = UNRESOLVED.pick(random)

    // ---------------------------------------------------------------------------------------
    // Court
    // ---------------------------------------------------------------------------------------

    private val FILED = listOf(
        "{name} (the claimant) filed a writ of summons at the {court} against Neribo Group Limited (the defendant) under suit number {suit}. The claimant asks for a refund, general damages and costs, in total {claim}.",
        "A civil claim was filed at the {court} by {name} against Neribo Group Limited, suit number {suit}. The total amount claimed is {claim}, made up of a refund, damages and costs.",
        "The {court} registered suit {suit}. The claimant, {name}, sues Neribo Group Limited for {claim}, being refund, damages and costs arising from case {ref}."
    )

    fun filed(random: Random): String = FILED.pick(random)

    private val SERVICE = listOf(
        "The court bailiff served the originating process in suit {suit} on Neribo Group Limited at its registered office in Lagos. The company has been told to enter an appearance and file its defence.",
        "Neribo Group Limited was served with the writ and the hearing notice in suit {suit}. The company's counsel was instructed to enter appearance."
    )

    fun service(random: Random): String = SERVICE.pick(random)

    private val FIRST_HEARING = listOf(
        "The matter came up for first hearing in suit {suit}. Counsel for the claimant and counsel for Neribo Group Limited were in court. The court took the appearances and directed both sides to file their documents.",
        "At the first hearing of suit {suit}, the claimant appeared in person and Neribo Group Limited was represented by its counsel. The court fixed a date for the next stage."
    )

    fun firstHearing(random: Random): String = FIRST_HEARING.pick(random)

    private val ADJOURN_REASONS = listOf(
        "Counsel for the claimant was absent, so the court adjourned the matter in suit {suit}.",
        "The court did not sit on the day, so suit {suit} was adjourned to the next available date.",
        "Parties told the court they were exploring settlement, and suit {suit} was adjourned to allow them to talk."
    )

    fun adjourned(random: Random): String = ADJOURN_REASONS.pick(random)

    private val FINAL_ADDRESSES = listOf(
        "Both sides adopted their final written addresses in suit {suit}. Counsel for the claimant asked for the full claim of {claim}, and counsel for Neribo Group Limited asked the court to dismiss it.",
        "Trial ended in suit {suit}. The parties adopted their final addresses and the court asked them to wait for a date for judgment."
    )

    fun finalAddresses(random: Random): String = FINAL_ADDRESSES.pick(random)

    private val RESERVED = listOf(
        "The court reserved judgment in suit {suit} and will give a date for delivery.",
        "Judgment was reserved in suit {suit}. The parties will be told when the court will sit to deliver it."
    )

    fun reserved(random: Random): String = RESERVED.pick(random)

    private val DISMISSED = listOf(
        "The court delivered judgment in suit {suit}. It held that the claimant did not prove the claim against Neribo Group Limited and the claim is dismissed. No order is made against the company.",
        "In its judgment in suit {suit}, the {court} found that the company acted within its terms and the claimant failed to prove the case. The claim is dismissed."
    )

    fun judgmentDismissed(random: Random): String = DISMISSED.pick(random)

    private val CLAIMANT_WON = listOf(
        "The court delivered judgment in suit {suit} in favour of the claimant. It held that Neribo Group Limited must refund the amount charged, pay general damages and costs, a total of {award}, within 30 days, that is by {payby}.",
        "In its judgment in suit {suit}, the {court} found for the claimant and ordered Neribo Group Limited to pay a refund, general damages and costs, in total {award}, by {payby}."
    )

    fun judgmentClaimant(random: Random): String = CLAIMANT_WON.pick(random)

    private val SETTLEMENT = listOf(
        "The parties told the court in suit {suit} that they have agreed terms of settlement. Neribo Group Limited will pay {award} by {payby} and the claim is withdrawn. Neither side is declared the winner.",
        "Terms of settlement were filed in suit {suit}. The company agreed to pay {award} by {payby}, and the claimant agreed to end the case."
    )

    fun settlement(random: Random): String = SETTLEMENT.pick(random)
}
