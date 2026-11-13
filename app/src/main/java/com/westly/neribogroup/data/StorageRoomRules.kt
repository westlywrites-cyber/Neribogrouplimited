package com.westly.neribogroup.data

import kotlin.math.roundToLong

/**
 * The answer of a check made before a change in the Storage room: [refused] is the plain message when the change
 * is not allowed (nothing is changed), and [needsPin] is true when the change lowers a limit and so needs the PIN.
 */
class RoomVerdict(val refused: String?, val needsPin: Boolean)

/**
 * The checks of the Storage room. They do not keep a second copy of the numbers: every sum, every size text and
 * every "over the total" figure comes from [StorageRules], the same functions the Storage page uses, so both
 * places always agree.
 */
object StorageRoomRules {

    const val FREE = 0
    const val PRO = 1
    const val PREMIUM = 2
    const val CUSTOM = 3

    val PLAN_NAMES: List<String> = listOf("Free", "Pro", "Premium", "Custom")

    /** The apps the room lists: the same ones the Storage page lists. */
    fun roomApps(apps: List<AppData>): List<AppData> =
        apps.filter { it.googlePlayEnabled || it.id != SOFTWARE_APP_ID }

    /** KB rounded up to whole MB. */
    fun mbUp(kb: Long): Long = (maxOf(0L, kb) + 1023L) / 1024L

    /** A typed number such as "1.5" in a unit, as MB. Null when nothing usable was typed. Same cap as the Storage page. */
    fun typedToMb(buf: String, unit: StorageUnit): Long? {
        val value = buf.toDoubleOrNull() ?: return null
        if (value.isNaN() || value.isInfinite() || value < 0.0) return null
        val capped = minOf(value, StorageRules.MAX_TYPED.toDouble())
        return (capped * unit.mb.toDouble()).roundToLong()
    }

    /** A typed whole number of users. Null when nothing usable was typed. */
    fun typedToCount(buf: String): Long? {
        val value = buf.toLongOrNull() ?: return null
        return value.coerceIn(0L, 99_999_999L)
    }

    // ---- What one plan row of an app holds ----

    fun planUsers(app: AppData, row: Int): Long = when (row) {
        FREE -> app.plainFreeUsers
        PRO -> app.proUsers
        PREMIUM -> app.premiumUsers
        else -> app.customStorageUsers.size.toLong()
    }

    /** The size each user of the plan gets, in MB. For Custom it is the total of the Customize users (each has its own). */
    fun planSizeMb(app: AppData, row: Int): Long = when (row) {
        FREE -> app.storageFreeMb
        PRO -> app.storageProMb
        PREMIUM -> app.storagePremiumMb
        else -> app.customStorageUsers.sumOf { it.customStorageMb }
    }

    /** What the whole plan row holds, in MB. */
    fun planTotalMb(app: AppData, row: Int): Long =
        if (row == CUSTOM) planSizeMb(app, row) else planUsers(app, row) * planSizeMb(app, row)

    fun sizeEditable(row: Int): Boolean = row in FREE..PREMIUM

    /** Only a Manual app has typed user numbers. In a live app the numbers come from its real user list. */
    fun usersEditable(app: AppData, row: Int): Boolean = !app.googlePlayEnabled && row in FREE..PREMIUM

    fun withSize(app: AppData, row: Int, mb: Long): AppData = when (row) {
        FREE -> app.copy(storageFreeMb = mb)
        PRO -> app.copy(storageProMb = mb)
        PREMIUM -> app.copy(storagePremiumMb = mb)
        else -> app
    }

    /** A Manual app with [count] users on one plan. The other two plans keep their numbers. */
    fun withUsers(app: AppData, row: Int, count: Long): AppData {
        val free = app.freeUsers
        val pro = app.proUsers
        val premium = app.premiumUsers
        return when (row) {
            FREE -> app.copy(manualTotalUsers = count + pro + premium)
            PRO -> app.copy(manualProUsers = count, manualTotalUsers = free + count + premium)
            PREMIUM -> app.copy(manualPremiumUsers = count, manualTotalUsers = free + pro + count)
            else -> app
        }
    }

    private fun replaced(apps: List<AppData>, updated: AppData): List<AppData> =
        apps.map { if (it.id == updated.id) updated else it }

    private fun refuse(text: String): RoomVerdict = RoomVerdict(text, false)

    private const val hostingPlanRefusal =
        "Client Hosting's storage comes from its customers. Change it on the Client Hosting page."

    // ---- The checks ----

    /** The company total. Not below the shares given, not below what the copies already use, lowering needs the PIN. */
    fun checkTotal(assets: CompanyAssets, apps: List<AppData>, newMb: Long): RoomVerdict {
        if (newMb <= 0L) return refuse("Type a total above 0.")
        val sharesMb = StorageRules.sharesMb(apps)
        if (newMb < sharesMb) {
            return refuse(
                "The apps hold " + StorageRules.textMb(sharesMb) +
                    " between them. Lower their shares first, then lower the company total."
            )
        }
        val copiesKb = StorageRules.copiesKb(assets, apps)
        if (copiesKb > newMb * 1024L) {
            return refuse(
                "Company storage would go to " + StorageRules.textMb(newMb) + ", but the copies already use " +
                    StorageRules.text(copiesKb) + ". Raise it or lower what users get."
            )
        }
        return RoomVerdict(null, newMb < assets.storageTotalMb)
    }

    /** One app's share. All shares together stay within the total, a share is not below what its users hold. */
    fun checkShare(assets: CompanyAssets, apps: List<AppData>, app: AppData, newMb: Long): RoomVerdict {
        val totalMb = assets.storageTotalMb
        if (totalMb <= 0L) return refuse("Set the company's total storage first.")
        val others = StorageRules.sharesMb(apps) - app.storageShareMb
        if (others + newMb > totalMb) {
            return refuse(
                "Only " + StorageRules.textMb(maxOf(0L, totalMb - others)) + " is left to give " + app.name +
                    ". Raise the company storage or lower another app's share first."
            )
        }
        if (newMb > 0L && newMb < mbUp(app.storageAllocatedKb)) {
            return refuse(
                app.name + "'s users already hold " + StorageRules.text(app.storageAllocatedKb) +
                    ". Lower what they get first."
            )
        }
        return RoomVerdict(null, newMb < app.storageShareMb)
    }

    /** A change that would put the copies further over the company total is refused. */
    private fun checkCopies(assets: CompanyAssets, apps: List<AppData>, updated: AppData): String? {
        val newApps = replaced(apps, updated)
        val newOver = StorageRules.companyOverKb(assets, newApps)
        val oldOver = StorageRules.companyOverKb(assets, apps)
        if (newOver > 0L && newOver > oldOver) {
            return "That would put company storage over its total by " + StorageRules.text(newOver) +
                ". Raise the total first."
        }
        return null
    }

    /** The size each user of a plan gets. Lowering it needs the PIN. */
    fun checkSize(assets: CompanyAssets, apps: List<AppData>, app: AppData, row: Int, newMb: Long): RoomVerdict {
        if (app.isHosting) return refuse(hostingPlanRefusal)
        if (!sizeEditable(row)) return refuse("Custom storage is set for each user in the app.")
        val problem = checkCopies(assets, apps, withSize(app, row, newMb))
        if (problem != null) return refuse(problem)
        return RoomVerdict(null, newMb < planSizeMb(app, row))
    }

    /** The number of users of a plan (Manual apps only). */
    fun checkUsers(assets: CompanyAssets, apps: List<AppData>, app: AppData, row: Int, newCount: Long): RoomVerdict {
        if (app.isHosting) return refuse(hostingPlanRefusal)
        if (!usersEditable(app, row)) {
            return refuse(app.name + "'s users come from its user list. Add or remove them in the app.")
        }
        val problem = checkCopies(assets, apps, withUsers(app, row, newCount))
        if (problem != null) return refuse(problem)
        return RoomVerdict(null, false)
    }

    /** The message when a new off-site copy would not fit, or null when it can be made. */
    fun offsiteRefusal(assets: CompanyAssets, apps: List<AppData>): String? {
        val over = StorageRules.offsiteWouldOverKb(assets, apps)
        return if (over > 0L) StorageRules.offsiteStoppedText(over) else null
    }
}
