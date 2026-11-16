package com.westly.opay.backend

import com.westly.opay.data.model.Bank
import com.westly.opay.data.model.Beneficiary

/**
 * The lists the transfer screens show, all derived from one list of saved recipients that is already ordered with
 * the most recently used first. Pure functions, so they can be unit tested.
 */
object BeneficiaryLists {
    /** The latest [MAX_RECENTS] recipients. */
    fun recents(all: List<Beneficiary>): List<Beneficiary> = all.take(MAX_RECENTS)

    fun favourites(all: List<Beneficiary>): List<Beneficiary> = all.filter { it.isFavourite }

    /** True when [item] is saved at [bank]. Older saved values without a bank code are matched by bank name. */
    fun isAt(item: Beneficiary, bank: Bank?): Boolean =
        bank != null && if (item.bankCode.isNotEmpty()) item.bankCode == bank.code else item.bankName == bank.name

    /** Only the recipients saved at [bank]. Empty while [bank] is not known. */
    fun atBank(all: List<Beneficiary>, bank: Bank?): List<Beneficiary> = all.filter { isAt(it, bank) }
}
