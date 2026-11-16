package com.westly.opay.data

import com.westly.opay.backend.BackendCodes
import com.westly.opay.backend.BackendError
import com.westly.opay.backend.RowMapping
import com.westly.opay.backend.SavingsPositionRow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

const val SAVINGS_STATUS_ACTIVE = "active"

/** The five kinds of `savings_positions.kind`. [wire] is the value the server stores. */
enum class SavingsKind(val wire: String, val label: String) {
    SafeBox("safebox", "SafeBox"),
    Target("target", "Targets"),
    Fixed("fixed", "Fixed"),
    OWealth("owealth", "OWealth"),
    SpendSave("spendsave", "Spend & Save");

    companion object {
        fun fromWire(value: String): SavingsKind? = entries.firstOrNull { it.wire == value }
    }
}

/** One active savings position as the screens use it. */
data class SavingsPosition(
    val id: String,
    val kind: SavingsKind,
    val name: String,
    val principalKobo: Long,
    val accruedKobo: Long,
    val targetKobo: Long?,
    val rateBps: Int,
    val startsAtMillis: Long,
    val maturesAtMillis: Long?,
    val spendSavePercent: Int?
) {
    val totalKobo: Long
        get() = principalKobo + accruedKobo
}

data class KindTotals(val principalKobo: Long, val accruedKobo: Long) {
    val totalKobo: Long
        get() = principalKobo + accruedKobo
}

data class SavingsSummary(
    val totalKobo: Long,
    val interestKobo: Long,
    val perKind: Map<SavingsKind, KindTotals>
) {
    fun of(kind: SavingsKind): KindTotals = perKind[kind] ?: KindTotals(0L, 0L)
}

/** Display rates shown before a position exists. The server sets the real rate inside `savings_create`. */
object SavingsRates {
    const val OWEALTH_BPS = 1500
    const val FIXED_BPS = 1800
    const val SAFEBOX_BPS = 1000
    const val TARGET_BPS = 1000
    const val SPENDSAVE_BPS = 1000
}

/** Pure mapping and totals for savings rows. No interest is ever computed here: the server accrues it. */
object SavingsBook {
    private const val META_PERCENT = "percent"

    /** Active positions only, newest first. Rows of a kind the app does not know are left out. */
    fun positions(rows: List<SavingsPositionRow>): List<SavingsPosition> =
        rows.filter { it.status == SAVINGS_STATUS_ACTIVE }
            .mapNotNull(::position)
            .sortedByDescending { it.startsAtMillis }

    fun position(row: SavingsPositionRow): SavingsPosition? {
        val kind = SavingsKind.fromWire(row.kind) ?: return null
        return SavingsPosition(
            id = row.id,
            kind = kind,
            name = row.name,
            principalKobo = row.principalKobo,
            accruedKobo = row.accruedKobo,
            targetKobo = row.targetKobo,
            rateBps = row.rateBps,
            startsAtMillis = RowMapping.epochMillis(row.startsAt),
            maturesAtMillis = row.maturesAt?.let { RowMapping.epochMillis(it) },
            spendSavePercent = percentOf(row.meta)
        )
    }

    /** Totals are `principal_kobo + accrued_kobo` of the given positions. */
    fun summary(positions: List<SavingsPosition>): SavingsSummary {
        val perKind = positions.groupBy { it.kind }.mapValues { (_, list) ->
            KindTotals(list.sumOf { it.principalKobo }, list.sumOf { it.accruedKobo })
        }
        return SavingsSummary(
            totalKobo = positions.sumOf { it.totalKobo },
            interestKobo = positions.sumOf { it.accruedKobo },
            perKind = perKind
        )
    }

    fun ofKind(positions: List<SavingsPosition>, kind: SavingsKind): List<SavingsPosition> =
        positions.filter { it.kind == kind }

    /** `1500` is "15% p.a.", `1250` is "12.5% p.a.". */
    fun rateLabel(rateBps: Int): String {
        val whole = rateBps / 100
        val rest = rateBps % 100
        return if (rest == 0) "$whole% p.a." else "$whole.${(rest / 10)}% p.a."
    }

    fun percentOf(meta: JsonObject): Int? = (meta[META_PERCENT] as? JsonPrimitive)?.intOrNull

    /** Whether a fixed position can be withdrawn at [nowMillis]. The server makes the final decision. */
    fun isMatured(position: SavingsPosition, nowMillis: Long): Boolean {
        val matures = position.maturesAtMillis ?: return true
        return nowMillis >= matures
    }
}

/** The text shown for a failed savings action. */
object SavingsErrors {
    const val NOT_MATURED_TEXT = "This fixed savings has not matured yet"

    fun message(error: BackendError): String =
        if (error.code == BackendCodes.NOT_MATURED) NOT_MATURED_TEXT else error.message
}
