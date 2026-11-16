package com.westly.opay.feature.register

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

const val NATIONALITY_NIGERIA = "Nigeria"
const val AREA_OTHER = "Other"
const val ADDRESS_MIN_LENGTH = 10
const val ADDRESS_MAX_LENGTH = 200
const val LANDMARK_MAX_LENGTH = 100

/** The checks shown under the "Full Address" box. */
object AddressRules {
    fun longEnough(text: String): Boolean = text.trim().length >= ADDRESS_MIN_LENGTH

    fun notPurelyNumbers(text: String): Boolean = text.any { it.isLetter() }

    fun houseAndStreet(text: String): Boolean =
        text.trim().split(WHITESPACE).count { it.isNotEmpty() } >= 2

    fun isValid(text: String): Boolean =
        text.length <= ADDRESS_MAX_LENGTH && longEnough(text) && notPurelyNumbers(text) && houseAndStreet(text)

    private val WHITESPACE = Regex("\\s+")
}

/** The address half of the registration form. Held by [RegistrationViewModel] until the form is submitted. */
data class AddressForm(
    val state: String = "",
    val lga: String = "",
    val area: String = "",
    val fullAddress: String = "",
    val landmark: String = "",
    val permanentSame: Boolean = true,
    val permanentState: String = "",
    val permanentLga: String = "",
    val permanentArea: String = "",
    val permanentFullAddress: String = ""
) {
    val residentialValid: Boolean
        get() = state.isNotEmpty() && lga.isNotEmpty() && area.isNotEmpty() && AddressRules.isValid(fullAddress)

    val permanentValid: Boolean
        get() = permanentSame ||
            (permanentState.isNotEmpty() && permanentLga.isNotEmpty() &&
                permanentArea.isNotEmpty() && AddressRules.isValid(permanentFullAddress))

    val isValid: Boolean get() = residentialValid && permanentValid

    /** True once the person has typed or chosen anything on the address screen. */
    val hasProgress: Boolean
        get() = state.isNotEmpty() || lga.isNotEmpty() || area.isNotEmpty() ||
            fullAddress.isNotEmpty() || landmark.isNotEmpty() || !permanentSame ||
            permanentState.isNotEmpty() || permanentLga.isNotEmpty() ||
            permanentArea.isNotEmpty() || permanentFullAddress.isNotEmpty()

    /** The address fields under the names of the `submit_kyc_form` contract. */
    fun toContractFields(nationality: String): JsonObject {
        fun text(value: String): JsonPrimitive = JsonPrimitive(value.trim())
        fun permanent(value: String) = if (permanentSame) JsonNull else JsonPrimitive(value.trim())
        return JsonObject(
            mapOf(
                "state" to text(state),
                "lga" to text(lga),
                "area" to text(area),
                "full_address" to text(fullAddress),
                "landmark" to if (landmark.isBlank()) JsonNull else text(landmark),
                "permanent_same" to JsonPrimitive(permanentSame),
                "permanent_state" to permanent(permanentState),
                "permanent_lga" to permanent(permanentLga),
                "permanent_area" to permanent(permanentArea),
                "permanent_full_address" to permanent(permanentFullAddress),
                "nationality" to JsonPrimitive(nationality)
            )
        )
    }
}
