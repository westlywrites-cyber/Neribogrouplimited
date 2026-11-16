package com.westly.opay.feature.register

/** Choices on the "Select occupation" sheet (reference B12). */
val OCCUPATIONS: List<String> = listOf(
    "Student",
    "Self-employed",
    "Trader",
    "Civil servant",
    "Private sector employee",
    "Artisan",
    "Farmer",
    "Driver",
    "Teacher",
    "Healthcare worker",
    "Engineer",
    "Unemployed",
    "Retired",
    "Other"
)

/** Choices on the "Select yearly income range" sheet (reference B13). The label text is what is stored. */
val INCOME_RANGES: List<String> = listOf(
    "Less than N50,000",
    "N51,000 - N250,000",
    "N251,000 - N500,000",
    "N501,000 - Less than N1 million",
    "N1 million - Less than N5 million",
    "N5 million - Less than N10 million",
    "N10 million and above"
)

/** Choices on the "Select your other source of income" sheet (reference B14). */
val OTHER_INCOME_SOURCES: List<String> = listOf(
    "Salary and earnings",
    "Savings",
    "Sales of financial assets",
    "Sales of real estates",
    "Reimbursements",
    "Inheritance/donation"
)

/** Choices for the expected annual income of the other source (reference B14). */
val EXPECTED_ANNUAL_INCOMES: List<String> = listOf(
    "Less than N1 million",
    "N1 million - Less than N5 million",
    "N5 million - Less than N20 million",
    "N20 million and above"
)

/** The income half of the registration form (reference B12 to B14). */
data class IncomeForm(
    val occupation: String = "",
    val incomeRange: String = "",
    val otherIncome: Boolean? = null,
    val otherSource: String = "",
    val expectedAnnual: String = ""
) {
    val isValid: Boolean
        get() = occupation.isNotEmpty() && incomeRange.isNotEmpty() && when (otherIncome) {
            null -> false
            false -> true
            true -> otherSource.isNotEmpty() && expectedAnnual.isNotEmpty()
        }
}
