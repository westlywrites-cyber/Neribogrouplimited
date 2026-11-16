package com.westly.opay.feature.register

import java.time.DateTimeException
import java.time.LocalDate

/** Choices on the "Select political office" sheet (reference B11). The first five are the recorded rows. */
val PEP_OFFICES: List<String> = listOf(
    "Local Government Chairmen",
    "President of a foreign government",
    "Head of international organization, such as the United Nations",
    "International Monetary Fund (IMF), or World Health Organization (WHO)",
    "Minister",
    "President",
    "Governor",
    "Senator",
    "House of Representatives member",
    "Commissioner",
    "Judge",
    "Military or police officer (senior)",
    "Head of a state-owned company",
    "Other"
)

object PepRules {
    private const val EARLIEST_YEAR = 1900

    /** Reads a date written as yyyy-MM-dd; anything else gives null. */
    fun parseDate(iso: String): LocalDate? =
        try {
            LocalDate.parse(iso)
        } catch (e: DateTimeException) {
            null
        }

    /** A start date must be a real date, not after [today] and not before 1900. */
    fun startDateValid(iso: String, today: LocalDate): Boolean {
        val date = parseDate(iso) ?: return false
        return !date.isAfter(today) && date.year >= EARLIEST_YEAR
    }
}

/**
 * The politically-exposed-person half of the registration form (reference B8 to B11).
 * [answered] is the first question: null = not asked yet, false = "No, I'm not", true = confirmed "Yes, I am".
 * The other fields only count when [answered] is true.
 */
data class PepForm(
    val answered: Boolean? = null,
    val holder: Boolean = true,
    val office: String = "",
    val startDate: String = "",
    val holdsPosition: Boolean = false,
    val related: Boolean = false
) {
    /** The `is_pep` field: the person holds or held an office themselves. */
    val isPep: Boolean get() = answered == true && holder

    /** The `pep_related` field. */
    val isRelated: Boolean get() = answered == true && related

    /** True when the details page (B10) holds everything it needs. */
    fun detailsValid(today: LocalDate): Boolean =
        answered == true && (!holder || (office.isNotEmpty() && PepRules.startDateValid(startDate, today)))

    /** True when the whole PEP part of the form can be submitted. */
    fun isValid(today: LocalDate): Boolean = when (answered) {
        null -> false
        false -> true
        true -> detailsValid(today)
    }
}
