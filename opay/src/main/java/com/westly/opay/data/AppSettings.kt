package com.westly.opay.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/** The toast shown when a setting could not be saved and was put back. */
const val SETTING_SAVE_FAILED_TEXT = "Could not save setting"

/**
 * The keys of `profiles.settings`. Every key is flat and holds a boolean, so a shallow merge is enough.
 * The keys are part of the backend contract (section "App settings keys"); never rename them.
 */
object SettingsKeys {
    const val SMS_CREDIT = "sms.credit"
    const val SMS_CARD = "sms.card"
    const val SMS_TRANSFER = "sms.transfer"
    const val SMS_OTHER = "sms.other"

    /** The four SMS alert options in the order the SMS Alert Settings screen lists them. */
    val SMS_ALL: List<String> = listOf(SMS_CREDIT, SMS_CARD, SMS_TRANSFER, SMS_OTHER)

    const val SAVINGS_AUTOMATIC_INVESTMENT = "savings.automatic_investment"
    const val SAVINGS_INTEREST = "savings.interest"

    const val SECURITY_QUESTIONS_SET = "security_questions_set"
}

/** Pure helpers for the settings JSON, kept free of Android types so they can be unit tested. */
object SettingsMerge {

    /** [base] with every entry of [changes] written over it. [base] itself is not changed. */
    fun merge(base: JsonObject, changes: Map<String, Boolean>): JsonObject =
        JsonObject(base + changes.mapValues { JsonPrimitive(it.value) })

    /** [changes] as the JSON object `update_profile` receives. */
    fun toJson(changes: Map<String, Boolean>): JsonObject =
        JsonObject(changes.mapValues { JsonPrimitive(it.value) })

    /** The boolean stored under [key], or [default] when it is missing or not a boolean. */
    fun flag(settings: JsonObject, key: String, default: Boolean = false): Boolean =
        (settings[key] as? JsonPrimitive)?.booleanOrNull ?: default

    /** True when [changes] would leave [settings] as it is. */
    fun isNoOp(settings: JsonObject, changes: Map<String, Boolean>): Boolean =
        changes.all { (key, value) -> (settings[key] as? JsonPrimitive)?.booleanOrNull == value }

    /**
     * Undoes [changes] after a failed save: each key goes back to what [before] held (or is removed when it was
     * missing), but only where [current] still holds the value that was written. A key changed again since is left alone.
     */
    fun revert(current: JsonObject, before: JsonObject, changes: Map<String, Boolean>): JsonObject {
        val result = current.toMutableMap()
        changes.forEach { (key, written) ->
            if ((current[key] as? JsonPrimitive)?.booleanOrNull == written) {
                val old = before[key]
                if (old == null) result.remove(key) else result[key] = old
            }
        }
        return JsonObject(result)
    }
}
