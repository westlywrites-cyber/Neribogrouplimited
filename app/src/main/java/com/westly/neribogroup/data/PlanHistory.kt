package com.westly.neribogroup.data

/** What happened to a user's paid plan. */
enum class PlanEventKind { STARTED, RENEWED, EXPIRED, MOVED_TO_FREE }

/**
 * One line of a user's plan history. [id] is made from the other fields, so the same event is never
 * kept twice when two copies of an app are merged.
 */
data class PlanEvent(
    val id: String,
    val userId: String,
    val kind: PlanEventKind,
    val plan: Plan,
    val atMillis: Long,
    /** The plan end date after a start or renewal; the end date it had when it expired. 0 if none. */
    val endMillis: Long = 0L
)

object PlanHistory {
    /** Only the newest events of one app are kept. */
    const val MAX_EVENTS = 2000

    fun event(
        userId: String,
        kind: PlanEventKind,
        plan: Plan,
        atMillis: Long,
        endMillis: Long = 0L
    ): PlanEvent = PlanEvent(
        id = userId + "|" + kind.name + "|" + plan.name + "|" + atMillis,
        userId = userId,
        kind = kind,
        plan = plan,
        atMillis = atMillis,
        endMillis = endMillis
    )

    /** Adds [added] to [history] without duplicates, oldest first, keeping only the newest MAX_EVENTS. */
    fun merge(history: List<PlanEvent>, added: List<PlanEvent>): List<PlanEvent> {
        if (added.isEmpty()) return history
        val seen = HashSet<String>()
        val all = ArrayList<PlanEvent>(history.size + added.size)
        for (e in history) if (seen.add(e.id)) all.add(e)
        for (e in added) if (seen.add(e.id)) all.add(e)
        all.sortBy { it.atMillis }
        return if (all.size > MAX_EVENTS) all.subList(all.size - MAX_EVENTS, all.size).toList() else all
    }

    /** The events of one user, newest first. */
    fun forUser(history: List<PlanEvent>, userId: String): List<PlanEvent> =
        history.filter { it.userId == userId }.sortedByDescending { it.atMillis }

    /** Short text for one event, for example "Started Premium". */
    fun label(e: PlanEvent): String {
        val p = when (e.plan) {
            Plan.PREMIUM -> "Premium"
            Plan.PRO -> "Pro"
            Plan.FREE -> "Free"
        }
        return when (e.kind) {
            PlanEventKind.STARTED -> "Started $p"
            PlanEventKind.RENEWED -> "Renewed $p"
            PlanEventKind.EXPIRED -> "$p ended"
            PlanEventKind.MOVED_TO_FREE -> "Moved from $p to Free"
        }
    }
}
