package com.westly.opay.feature.register

import com.westly.opay.backend.KycForm
import java.time.LocalDate

/** Puts the three parts of the registration form into the one request `submit_kyc_form` takes. */
object KycFormBuilder {

    /** Returns null while any part of the form is missing or invalid. */
    fun build(
        address: AddressForm,
        nationality: String,
        pep: PepForm,
        income: IncomeForm,
        today: LocalDate
    ): KycForm? {
        if (!address.isValid || !pep.isValid(today) || !income.isValid) return null
        val isPep = pep.isPep
        val hasOtherIncome = income.otherIncome == true
        val permanentSame = address.permanentSame
        return KycForm(
            state = address.state.trim(),
            lga = address.lga.trim(),
            area = address.area.trim(),
            fullAddress = address.fullAddress.trim(),
            landmark = address.landmark.trim().ifEmpty { null },
            permanentSame = permanentSame,
            permanentState = if (permanentSame) null else address.permanentState.trim(),
            permanentLga = if (permanentSame) null else address.permanentLga.trim(),
            permanentArea = if (permanentSame) null else address.permanentArea.trim(),
            permanentFullAddress = if (permanentSame) null else address.permanentFullAddress.trim(),
            nationality = nationality,
            isPep = isPep,
            pepOffice = if (isPep) pep.office else null,
            pepStartDate = if (isPep) pep.startDate else null,
            pepHoldsPosition = if (isPep) pep.holdsPosition else null,
            pepRelated = pep.isRelated,
            occupation = income.occupation,
            incomeRange = income.incomeRange,
            otherIncome = hasOtherIncome,
            otherIncomeSource = if (hasOtherIncome) income.otherSource else null,
            expectedAnnualIncome = if (hasOtherIncome) income.expectedAnnual else null
        )
    }
}
