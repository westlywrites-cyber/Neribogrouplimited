package com.westly.neribogroup.data

/** Where a user stands with payment in an app. */
enum class PaymentStatus(val label: String) {
    /** On the Free plan (only in an app that has one). */
    FREE("Free"),

    /** Has not paid, and the app has no Free plan, so the user has no access. */
    NOT_PAID("Not paid"),

    /** A paid plan that is running. */
    ACTIVE("Active"),

    /** A paid plan that ends in 7 days or fewer. */
    ENDING_SOON("Ending soon"),

    /** A paid plan whose end date has passed. */
    EXPIRED("Expired"),

    /** Paid once and unlocked for good (One-time payment apps). */
    UNLOCKED("Unlocked")
}

/**
 * The one place that says what each payment model means. The screens and the dice ask here instead of each
 * deciding for themselves, so a change of model reaches every part of the app the same way.
 *
 * How the three models are stored (no new plan was added):
 * - Free, Pro & Premium: Free, Premium and Pro, as before.
 * - Pro & Premium only: the same plans, but a user on "Free" has not paid and has no access ("Not paid").
 * - One-time payment: a user who paid is stored as Premium with no end date and shown as "Unlocked". A user who
 *   has not paid is stored as Free and shown as "Not paid". Pro does not exist.
 */
object PaymentRules {

    /** The name of [plan] in an app that uses [model]. */
    fun planName(model: PaymentModel, plan: Plan): String = when (model) {
        PaymentModel.ONE_TIME -> if (plan == Plan.FREE) "Not paid" else "Unlocked"
        PaymentModel.PRO_PREMIUM_ONLY -> when (plan) {
            Plan.FREE -> "Not paid"
            Plan.PREMIUM -> "Premium"
            Plan.PRO -> "Pro"
        }
        PaymentModel.FREE_PRO_PREMIUM -> when (plan) {
            Plan.FREE -> "Free"
            Plan.PREMIUM -> "Premium"
            Plan.PRO -> "Pro"
        }
    }

    /** The payment status of [user] on the day of [now]. */
    fun statusOf(app: AppData, user: AppUser, now: Long): PaymentStatus {
        if (user.plan == Plan.FREE) {
            return if (app.freeAllowed) PaymentStatus.FREE else PaymentStatus.NOT_PAID
        }
        if (app.isOneTime) return PaymentStatus.UNLOCKED
        val left = user.planDaysLeft(now) ?: return PaymentStatus.ACTIVE
        return when {
            left < 0L -> PaymentStatus.EXPIRED
            left <= 7L -> PaymentStatus.ENDING_SOON
            else -> PaymentStatus.ACTIVE
        }
    }

    /**
     * Whether [user] may use the app. A Free plan always has access. Without a Free plan, only a user whose
     * payment is in force has access. A finished paid plan falls back to Free access when the app has Free.
     */
    fun hasAccess(app: AppData, user: AppUser, now: Long): Boolean =
        when (statusOf(app, user, now)) {
            PaymentStatus.FREE -> true
            PaymentStatus.NOT_PAID -> false
            PaymentStatus.EXPIRED -> app.freeAllowed
            PaymentStatus.ACTIVE -> true
            PaymentStatus.ENDING_SOON -> true
            PaymentStatus.UNLOCKED -> true
        }

    /**
     * How many users have access today and how many do not, as (with access, without access). Listed users are
     * judged one by one; the users that are only counted (audience) and the typed numbers of a Manual app are
     * judged by their plan.
     */
    fun accessCounts(app: AppData, now: Long): Pair<Long, Long> {
        if (!app.googlePlayEnabled) {
            val paid = app.premiumUsers + app.proUsers
            return if (app.freeAllowed) Pair(app.totalUsers, 0L) else Pair(paid, app.freeUsers)
        }
        var yes = 0L
        var no = 0L
        for (user in app.users) {
            if (hasAccess(app, user, now)) yes += 1L else no += 1L
        }
        // Counted-only users: those on a paid period that has not ended have access. The rest are Free (with a Free
        // plan everybody has access) or Not paid / on an ended period (no access without a Free plan).
        val today = dayKey(now)
        val audiencePaid = minOf(app.audienceFree, app.audiencePremium + app.audiencePro)
        val audienceNotPaid = maxOf(0L, app.audienceFree - audiencePaid)
        val audienceEnded = minOf(audiencePaid, app.audienceBatches.filter { it.endedOn(today) }.sumOf { it.premium + it.pro })
        if (app.freeAllowed) {
            yes += app.audienceFree
        } else {
            yes += audiencePaid - audienceEnded
            no += audienceNotPaid + audienceEnded
        }
        return Pair(yes, no)
    }

    /** Out of every 100 people who join an app that has Pro and Premium but no Free plan, this many start on Pro. */
    const val PRO_ARRIVAL_PERCENT = 30

    /**
     * True when everybody who joins pays on the day they arrive: the app has no Free plan (Pro & Premium only, or
     * One-time payment) and at least one paid price is set. Then the Premium and Pro upgrade dice are not needed.
     */
    fun paysOnArrival(app: AppData): Boolean =
        !app.freeAllowed && (app.effectivePremiumPrice > 0L || app.effectiveProPrice > 0L)

    /**
     * The share (0.0 to 1.0) of the people who join that start on Pro. 30% when both plans have a price, all of
     * them when only Pro has a price, none when only Premium has a price. A One-time app has no Pro: everybody
     * is Unlocked (stored as Premium).
     */
    fun arrivalProShare(app: AppData): Double = when {
        app.effectiveProPrice <= 0L -> 0.0
        app.effectivePremiumPrice <= 0L -> 1.0
        else -> PRO_ARRIVAL_PERCENT / 100.0
    }

    /** The plans you can give a user in this app: only those that exist, in the order shown on the chips. */
    fun givablePlans(app: AppData): List<Plan> {
        val list = ArrayList<Plan>()
        if (app.freeAllowed) list.add(Plan.FREE)
        if (app.effectivePremiumPrice > 0L) list.add(Plan.PREMIUM)
        if (app.effectiveProPrice > 0L) list.add(Plan.PRO)
        return list
    }

    /** The plan a new user you add starts on. */
    fun defaultPlan(app: AppData): Plan = givablePlans(app).firstOrNull() ?: Plan.FREE

    /** "Free, Premium or Pro", "Premium or Pro" or "Unlocked": what you can pick for a new user. */
    fun choiceText(model: PaymentModel): String = when (model) {
        PaymentModel.FREE_PRO_PREMIUM -> "Free, Premium or Pro"
        PaymentModel.PRO_PREMIUM_ONLY -> "Premium or Pro"
        PaymentModel.ONE_TIME -> "Unlocked"
    }

    /** What is still missing before users can pay in this app, or null when the prices are set. */
    fun setupNote(app: AppData): String? = when (app.paymentModel) {
        PaymentModel.ONE_TIME ->
            if (app.oneTimePrice <= 0L) "Set the one-time price in Edit so users can unlock this app." else null
        PaymentModel.PRO_PREMIUM_ONLY ->
            if (app.premiumPrice <= 0L && app.proPrice <= 0L) {
                "Set a Premium or Pro price in Edit. Until then nobody can get access to this app."
            } else {
                null
            }
        PaymentModel.FREE_PRO_PREMIUM -> null
    }

    /** What changes for the users when the owner moves an app from [from] to [to]. Nobody is deleted. */
    fun changeWarning(from: PaymentModel, to: PaymentModel): String {
        val unlocked = if (from == PaymentModel.ONE_TIME) {
            " Users who unlocked the app keep access as Premium with no end date."
        } else {
            ""
        }
        return when (to) {
            PaymentModel.ONE_TIME ->
                "Everyone who has paid keeps access as Unlocked (Pro users become Premium with no end date). " +
                    "There are no renewals any more. People who have not paid have no access until they pay once."
            PaymentModel.PRO_PREMIUM_ONLY ->
                "The Free plan is turned off. People on Free become Not paid and have no access until " +
                    "they buy Pro or Premium." + unlocked
            PaymentModel.FREE_PRO_PREMIUM ->
                "The Free plan is turned on. People who have not paid get free access." + unlocked
        }
    }
}
