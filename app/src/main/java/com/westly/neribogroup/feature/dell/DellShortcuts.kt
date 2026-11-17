package com.westly.neribogroup.feature.dell

import androidx.annotation.DrawableRes
import com.westly.neribogroup.R
import com.westly.neribogroup.Section
import com.westly.neribogroup.data.Ledger
import java.io.File

/** Icons per desktop screen. Only this many icons are on one screen; swipe (or use the arrow keys) for the next one. */
const val DELL_PER_PAGE = 5

/** The key of the first icon, Neribo Group (the home page: profit and renewals only). */
const val DELL_HOME_KEY = "ng"

/** The key of the OPay icon (NCO only). Its window works with or without Wi-Fi. */
const val DELL_OPAY_KEY = "opay"

/**
 * One icon on the CEO office desktop. [key] says what it opens: "ng" (home), "sec:<SECTION NAME>", "app:<app id>"
 * or "social:<account id>". [iconFile] is the picture the owner chose when he added an app (it may not exist);
 * [icon] is the built-in picture used otherwise.
 */
data class DellShortcut(
    val key: String,
    val name: String,
    @DrawableRes val icon: Int,
    val iconFile: File? = null
)

private fun socialIcon(name: String): Int = when (name.trim().lowercase()) {
    "instagram" -> R.drawable.dell_ic_instagram
    "facebook" -> R.drawable.dell_ic_facebook
    "x" -> R.drawable.dell_ic_x
    "tiktok" -> R.drawable.dell_ic_tiktok
    else -> R.drawable.dell_ic_default
}

/**
 * Every icon of the desktop, in order: Neribo Group, the sections, one icon for each social platform, then every app.
 * Nothing is typed here: the social accounts and the apps come from the ledger, so an app you add shows up by itself.
 */
fun dellShortcuts(ledger: Ledger, appIconFile: (String) -> File): List<DellShortcut> {
    val list = ArrayList<DellShortcut>()
    list.add(DellShortcut(DELL_HOME_KEY, "Neribo Group", R.drawable.dell_ic_default))
    list.add(DellShortcut("sec:" + Section.APPS.name, "Apps", R.drawable.dell_ic_apps))
    list.add(DellShortcut("sec:" + Section.RENEWALS.name, "Renewals", R.drawable.dell_ic_renewals))
    list.add(DellShortcut("sec:" + Section.STAFF.name, "Staff", R.drawable.dell_ic_staff))
    list.add(DellShortcut("sec:" + Section.COSTS.name, "Company costs", R.drawable.dell_ic_costs))
    list.add(DellShortcut("sec:" + Section.PLAY.name, "Google Play Balance", R.drawable.dell_ic_play))
    list.add(DellShortcut("sec:" + Section.APPLE.name, "Apple Store Balance", R.drawable.dell_ic_apple))
    list.add(DellShortcut("sec:" + Section.PAYSTACK.name, "Paystack Balance", R.drawable.dell_ic_paystack))
    list.add(DellShortcut("sec:" + Section.CASES.name, "Customer Service & Complaints", R.drawable.dell_ic_service))
    list.add(DellShortcut("sec:" + Section.USD.name, "\$ Account", R.drawable.dell_ic_usd))
    for (account in ledger.socials.accounts) {
        list.add(DellShortcut("social:" + account.id, account.name, socialIcon(account.name)))
    }
    list.add(DellShortcut("sec:" + Section.DOCS.name, "Company documents", R.drawable.dell_ic_docs))
    list.add(DellShortcut("sec:" + Section.TOOLS.name, "Equipment", R.drawable.dell_ic_equipment))
    // The Neribo Deploy sites page belongs to the Neribo Deploy app only, so its icon is there only when that app is.
    if (ledger.apps.any { it.googlePlayEnabled && it.isDeployApp }) {
        list.add(DellShortcut("sec:" + Section.DEPLOY.name, "Neribo Deploy sites", R.drawable.dell_ic_deploy))
    }
    list.add(DellShortcut("sec:" + Section.STORAGE.name, "Storage", R.drawable.dell_ic_storage))
    list.add(DellShortcut("sec:" + Section.STORAGE_ROOM.name, "Storage room", R.drawable.dell_ic_room))
    for (app in ledger.apps) {
        list.add(
            DellShortcut(
                key = "app:" + app.id,
                name = app.name,
                icon = R.drawable.dell_ic_default,
                iconFile = if (app.hasIcon) appIconFile(app.id) else null
            )
        )
    }
    return list
}
