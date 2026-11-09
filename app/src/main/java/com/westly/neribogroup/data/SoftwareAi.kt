package com.westly.neribogroup.data

/**
 * Phase 6: what the app decides for ONE proposal before Claude writes it. Claude never changes these.
 * (The existing generator never brings back an old client, so a slot never names a repeat client.)
 */
data class ProposalSlot(
    val index: Int,
    val kind: OfferKind,
    /** The project type (a [SoftwareType] code); used by the built-in text when Claude does not answer. */
    val typeCode: String,
    val clientType: ClientType,
    val country: String,
    val currency: String,
    val minUnits: Long,
    val maxUnits: Long,
    val stepUnits: Long,
    val minWeeks: Int,
    val maxWeeks: Int,
    val minRequirements: Int = 5,
    val maxRequirements: Int = 7
)

/** What Claude wrote for one proposal. It is checked again before it is used. */
data class AiProposal(
    val clientName: String,
    val contactName: String,
    val email: String,
    val phone: String,
    val country: String,
    val title: String,
    val summary: String,
    val requirements: List<String>,
    val deliveryWeeks: Int,
    val feeUnits: Long
)

/**
 * The answers of Claude for one software roll. [faces] = contacts dice, proposals dice (-1 = not given, the
 * random face is used). [slots] and [proposals] are by proposal number; a missing or null entry means the
 * built-in generator writes that proposal.
 */
data class SoftwarePreset(
    val faces: List<Int> = emptyList(),
    val slots: List<ProposalSlot?> = emptyList(),
    val proposals: List<AiProposal?> = emptyList(),
    /** The email domains the owner allows. Null = not checked here (the cloud already checks them). */
    val allowedDomains: List<String>? = null
)
