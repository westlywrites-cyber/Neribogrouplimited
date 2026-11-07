package com.westly.neribogroup.data

/** The id and name of the built-in "Software Development Clients" app. */
const val SOFTWARE_APP_ID = "software-clients"
const val SOFTWARE_APP_NAME = "Software Development Clients"

/** True for the one built-in software app. It has its own screen and can never be deleted. */
val AppData.isSoftware: Boolean get() = id == SOFTWARE_APP_ID

/**
 * Makes sure the software app exists. It is added at the END of the apps list when missing.
 * When more than one app has its id, only the first is kept. Returns the same ledger when nothing changes.
 */
fun Ledger.withSoftwareApp(): Ledger {
    val matches = apps.count { it.id == SOFTWARE_APP_ID }
    if (matches == 0) {
        return copy(apps = apps + AppData(id = SOFTWARE_APP_ID, name = SOFTWARE_APP_NAME))
    }
    if (matches > 1) {
        var seen = false
        val cleaned = apps.filter { app ->
            if (app.id != SOFTWARE_APP_ID) {
                true
            } else if (!seen) {
                seen = true
                true
            } else {
                false
            }
        }
        return copy(apps = cleaned)
    }
    return this
}

// ---------------------------------------------------------------------------
// Enums
// ---------------------------------------------------------------------------

enum class OfferKind { WEBSITE, WEB_APP, ANDROID_APP, CODE_PURCHASE }

/** EXPIRED is derived from the time. It is never stored. */
enum class OfferStatus { PENDING, ACCEPTED, DECLINED, EXPIRED }

enum class ClientType { COMPANY, INDIVIDUAL }

enum class ProjectStage { ACCEPTED, IN_PROGRESS, TESTING, DELIVERED }

enum class PaymentPurpose { UPFRONT, FINAL, MAINTENANCE }

enum class UsdTxKind { PAYMENT_IN, CONVERSION_OUT }

enum class RateSource { DEFAULT, LIVE, MANUAL }

// ---------------------------------------------------------------------------
// Currencies and rates
// ---------------------------------------------------------------------------

/** The supported currency codes, in the order they are shown. */
val SOFTWARE_CURRENCIES: List<String> =
    listOf("NGN", "USD", "GBP", "EUR", "CAD", "AUD", "AED", "ZAR", "GHS", "KES")

/** Units of each currency per 1 USD. Used only until the first successful live refresh. */
val DEFAULT_RATES: Map<String, RateInfo> = mapOf(
    "NGN" to RateInfo(1500.0, 0L, RateSource.DEFAULT),
    "USD" to RateInfo(1.0, 0L, RateSource.DEFAULT),
    "GBP" to RateInfo(0.78, 0L, RateSource.DEFAULT),
    "EUR" to RateInfo(0.92, 0L, RateSource.DEFAULT),
    "CAD" to RateInfo(1.37, 0L, RateSource.DEFAULT),
    "AUD" to RateInfo(1.52, 0L, RateSource.DEFAULT),
    "AED" to RateInfo(3.6725, 0L, RateSource.DEFAULT),
    "ZAR" to RateInfo(18.0, 0L, RateSource.DEFAULT),
    "GHS" to RateInfo(12.0, 0L, RateSource.DEFAULT),
    "KES" to RateInfo(129.0, 0L, RateSource.DEFAULT)
)

/** The naira rate must always stay inside this range (live, typed or converted). */
const val NGN_RATE_MIN = 300.0
const val NGN_RATE_MAX = 10_000.0

// ---------------------------------------------------------------------------
// Classes
// ---------------------------------------------------------------------------

data class SoftwareOffer(
    val id: String,
    /** For example "SW-OFR-2026-0007". */
    val ref: String,
    val createdMillis: Long,
    /** createdMillis + 7 days. */
    val expiresMillis: Long,
    /** Stored: PENDING, ACCEPTED or DECLINED only. */
    val status: OfferStatus,
    /** 0 until accepted or declined. */
    val decidedMillis: Long,
    val clientType: ClientType,
    /** The company name, or the person's full name. */
    val clientName: String,
    /** For a company: the contact person. For a person: the same as clientName. */
    val contactName: String,
    val email: String,
    val phone: String,
    val country: String,
    val currency: String,
    val kind: OfferKind,
    val title: String,
    val summary: String,
    val requirements: List<String>,
    val deliveryWeeks: Int,
    /** The offered fee, in whole units of [currency]. */
    val feeUnits: Long,
    /** The fee in naira at the rates of the creation moment (display only). */
    val ngnEstimate: Long
)

data class SoftwareClient(
    /** "cl-" + offer id. */
    val id: String,
    val type: ClientType,
    val name: String,
    val contactName: String,
    val email: String,
    val phone: String,
    val country: String,
    val currency: String,
    val sinceMillis: Long
)

data class ProjectPayment(
    /** Deterministic: see SoftwareEngine.paymentId. */
    val id: String,
    val purpose: PaymentPurpose,
    /** MAINTENANCE: 1, 2, 3 ... Otherwise 0. */
    val periodIndex: Int,
    val dateMillis: Long,
    val currency: String,
    /** The amount in whole units of [currency]. */
    val units: Long,
    /** Units of [currency] per 1 USD at receipt (NGN: the naira rate, USD: 1.0). */
    val ratePerUsd: Double,
    /** Credited to the $ account, in cents. 0 for naira payments. */
    val usdCents: Long,
    /** Naira payments: the amount itself. Foreign payments: an estimate at receipt (display only). */
    val ngnAtReceipt: Long
)

data class SoftwareProject(
    /** "prj-" + offer id. */
    val id: String,
    val offerId: String,
    val clientId: String,
    /** For example "SW-2026-0003". */
    val ref: String,
    val clientName: String,
    val title: String,
    val summary: String,
    val requirements: List<String>,
    val kind: OfferKind,
    val currency: String,
    val feeUnits: Long,
    val stage: ProjectStage,
    val createdMillis: Long,
    /** 0 until delivered. */
    val deliveredMillis: Long,
    val payments: List<ProjectPayment>,
    /** True only after delivery of a non-code project, until it is stopped. */
    val maintenanceActive: Boolean,
    /** feeUnits / 10, set at delivery. 0 for a code purchase. */
    val maintenanceUnits: Long,
    val maintenanceStoppedMillis: Long
)

data class UsdTx(
    /** A UUID. For a conversion: the request id. */
    val id: String,
    val kind: UsdTxKind,
    val dateMillis: Long,
    /** Always positive. The kind gives the direction. */
    val usdCents: Long,
    val label: String,
    // PAYMENT_IN: what was received.
    val currency: String,
    val units: Long,
    val ratePerUsd: Double,
    // CONVERSION_OUT: what was credited as naira income.
    val ngnAmount: Long,
    val ngnPerUsd: Double
)

data class RateInfo(val perUsd: Double, val updatedMillis: Long, val source: RateSource)

data class SoftwareRoll(
    val dayKey: String,
    val atMillis: Long,
    /** Faces 0..5. */
    val contactFace: Int,
    val offerFace: Int,
    val contacts: Int,
    /** Proposals actually added to the inbox. */
    val offers: Int,
    val note: String
)

data class SoftwareData(
    val offers: List<SoftwareOffer> = emptyList(),
    val projects: List<SoftwareProject> = emptyList(),
    val clients: List<SoftwareClient> = emptyList(),
    val usdBalanceCents: Long = 0,
    /** Newest first. The last 200 are kept. */
    val usdTx: List<UsdTx> = emptyList(),
    val rates: Map<String, RateInfo> = DEFAULT_RATES,
    /** The last successful live refresh. */
    val ratesFetchedMillis: Long = 0,
    val lastRollDayKey: String = "",
    /** Newest first. The last 30 are kept. */
    val rolls: List<SoftwareRoll> = emptyList(),
    val nextOfferNumber: Int = 1,
    val nextProjectNumber: Int = 1,
    /** The highest "now" ever seen by the software screens. */
    val clockMillis: Long = 0
)
