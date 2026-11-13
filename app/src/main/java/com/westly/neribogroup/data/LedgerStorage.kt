package com.westly.neribogroup.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Saves and loads everything as one JSON file inside the app's private storage. */
object LedgerStorage {

    private const val FILE_NAME = "ledger.json"

    fun load(context: Context): Ledger {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return Ledger().withSoftwareApp().withHostingApp().withSocialsSeeded(System.currentTimeMillis())
        return try {
            fromJson(file.readText())
        } catch (e: Exception) {
            // Keep the unreadable file instead of overwriting it.
            runCatching { file.renameTo(File(context.filesDir, "ledger.corrupt.json")) }
            Ledger().withSoftwareApp().withHostingApp().withSocialsSeeded(System.currentTimeMillis())
        }
    }

    fun save(context: Context, ledger: Ledger) {
        runCatching { writeAtomic(context, toJson(ledger)) }
    }

    /** Saves ledger text exactly as given, with the same atomic write as [save]. True when it was written. */
    fun saveRawText(context: Context, text: String): Boolean {
        return runCatching { writeAtomic(context, text) }.isSuccess
    }

    private fun writeAtomic(context: Context, text: String) {
        val file = File(context.filesDir, FILE_NAME)
        val temp = File(context.filesDir, "$FILE_NAME.tmp")
        temp.writeText(text)
        if (!temp.renameTo(file)) {
            file.writeText(temp.readText())
            temp.delete()
        }
    }

    fun toJson(ledger: Ledger): String {
        val root = JSONObject()
        root.put("version", 6)
        root.put("companyName", ledger.company.name)
        root.put("companyExpenses", entriesToJson(ledger.company.expenses))

        val apps = JSONArray()
        for (app in ledger.apps) {
            val o = JSONObject()
            o.put("id", app.id)
            o.put("name", app.name)
            o.put("premiumPrice", app.premiumPrice)
            o.put("proPrice", app.proPrice)
            o.put("entries", entriesToJson(app.entries))
            o.put("users", usersToJson(app.users))
            o.put("premiumBenefits", app.premiumBenefits)
            o.put("proBenefits", app.proBenefits)
            o.put("freeBenefits", app.freeBenefits)
            o.put("activeUsers", app.activeUsers)
            o.put("activeUsersDayKey", app.activeUsersDayKey)
            o.put("activeHistory", historyToJson(app.activeHistory))
            o.put("newHistory", historyToJson(app.newHistory))
            o.put("adsEnabled", app.adsEnabled)
            o.put("lastRollDayKey", app.lastRollDayKey)
            o.put("playSubsBalance", app.playSubsBalance)
            o.put("playAdsBalance", app.playAdsBalance)
            o.put("rolls", rollsToJson(app.rolls))
            o.put("playTx", playTxToJson(app.playTx))
            o.put("planHistory", planHistoryToJson(app.planHistory))
            o.put("googlePlayEnabled", app.googlePlayEnabled)
            o.put("googlePlaySinceMillis", app.googlePlaySinceMillis)
            o.put("manualTotalUsers", app.manualTotalUsers)
            o.put("manualPremiumUsers", app.manualPremiumUsers)
            o.put("manualProUsers", app.manualProUsers)
            o.put("manualDailyNewUsers", app.manualDailyNewUsers)
            o.put("manualNewUsersDayKey", app.manualNewUsersDayKey)
            o.put("manualPremiumToday", app.manualPremiumToday)
            o.put("manualProToday", app.manualProToday)
            o.put("manualTodayKey", app.manualTodayKey)
            o.put("adverts", advertsToJson(app.adverts))
            o.put("audienceFree", app.audienceFree)
            o.put("audienceTodayKey", app.audienceTodayKey)
            o.put("audienceToday", app.audienceToday)
            o.put("playOn", app.playOn)
            o.put("appleOn", app.appleOn)
            o.put("websiteOn", app.websiteOn)
            o.put("appleSubsBalance", app.appleSubsBalance)
            o.put("paystackSubsBalance", app.paystackSubsBalance)
            o.put("appleTx", playTxToJson(app.appleTx))
            o.put("paystackTx", playTxToJson(app.paystackTx))
            o.put("audienceApple", app.audienceApple)
            o.put("audienceWebsite", app.audienceWebsite)
            val dr = app.dayRolls
            if (dr != null) {
                o.put(
                    "dayRolls",
                    JSONObject().put("day", dr.dayKey).put("play", dr.playBp).put("apple", dr.appleBp)
                        .put("website", dr.websiteBp).put("sites", dr.sitesTenths).put("storage", dr.storageBp)
                )
            }
            o.put("deployTracking", app.deployTracking)
            o.put("storageFreeMb", app.storageFreeMb)
            o.put("storageProMb", app.storageProMb)
            o.put("storagePremiumMb", app.storagePremiumMb)
            o.put("storageSiteMb", app.storageSiteMb)
            o.put("storageUsedKb", app.storageUsedKb)
            o.put("storageShareMb", app.storageShareMb)
            val campaign = app.campaign
            if (campaign != null) {
                val co = JSONObject()
                co.put("size", campaign.sizeName)
                co.put("start", campaign.startDayKey)
                co.put("end", campaign.endDayKey)
                co.put("stopped", campaign.stoppedDayKey)
                o.put("campaign", co)
            }
            apps.put(o)
        }
        root.put("apps", apps)

        val staffArray = JSONArray()
        for (s in ledger.staff) {
            val o = JSONObject()
            o.put("id", s.id)
            o.put("name", s.name)
            o.put("role", s.role)
            o.put("email", s.email)
            o.put("phone", s.phone)
            o.put("bankName", s.bankName)
            o.put("accountNumber", s.accountNumber)
            o.put("monthlySalary", s.monthlySalary)

            val allowances = JSONArray()
            for (a in s.allowances) {
                val ao = JSONObject()
                ao.put("id", a.id)
                ao.put("name", a.name)
                ao.put("amount", a.amount)
                allowances.put(ao)
            }
            o.put("allowances", allowances)

            val payments = JSONArray()
            for (p in s.payments) {
                val po = JSONObject()
                po.put("id", p.id)
                po.put("date", p.dateMillis)
                po.put("amount", p.amount)
                po.put("item", p.itemKey)
                po.put("itemName", p.itemName)
                payments.put(po)
            }
            o.put("payments", payments)

            // Photos: only the small description (the picture files are not inside a backup).
            if (s.images.isNotEmpty()) {
                val imagesArray = JSONArray()
                for (img in s.images.take(MAX_STAFF_IMAGES)) {
                    val io = JSONObject()
                    io.put("id", img.id)
                    io.put("added", img.addedMillis)
                    io.put("size", img.sizeBytes)
                    io.put("sha256", img.sha256)
                    imagesArray.put(io)
                }
                o.put("images", imagesArray)
            }

            staffArray.put(o)
        }
        root.put("staff", staffArray)

        val renewalsArray = JSONArray()
        for (r in ledger.renewals) {
            val o = JSONObject()
            o.put("id", r.id)
            o.put("name", r.name)
            o.put("amount", r.amount)
            o.put("durationDays", r.durationDays)
            o.put("paid", r.paidMillis)
            o.put("expiry", r.expiryMillis)
            val days = JSONArray()
            for (d in r.reminderDays) days.put(d)
            o.put("reminderDays", days)
            renewalsArray.put(o)
        }
        root.put("renewals", renewalsArray)

        val withdrawalsArray = JSONArray()
        for (w in ledger.withdrawals) {
            val o = JSONObject()
            o.put("id", w.id)
            o.put("date", w.dateMillis)
            o.put("amount", w.amount)
            o.put("label", w.label)
            if (w.channel != Channel.PLAY) o.put("channel", w.channel.name)
            o.put("note", w.note)
            withdrawalsArray.put(o)
        }
        root.put("withdrawals", withdrawalsArray)

        val casesArray = JSONArray()
        for (c in ledger.cases) casesArray.put(caseToJson(c))
        root.put("cases", casesArray)
        root.put("nextCaseNumber", ledger.nextCaseNumber)
        root.put("casesClockMillis", ledger.casesClockMillis)
        root.put("software", SoftwareJson.toJson(ledger.software))
        root.put("socials", SocialsJson.toJson(ledger.socials))
        root.put("assets", assetsToJson(ledger.assets))
        root.put("hosting", HostingJson.toJson(ledger.hosting))

        return root.toString(2)
    }

    /** Throws if the text is not a valid backup. Older backups without staff still load. */
    fun fromJson(text: String): Ledger {
        val root = JSONObject(text)
        if (!root.has("apps") && !root.has("companyName")) {
            throw IllegalArgumentException("This is not a Neribo Group backup")
        }

        val company = Company(
            name = root.optString("companyName", DEFAULT_COMPANY_NAME).ifBlank { DEFAULT_COMPANY_NAME },
            expenses = jsonToEntries(root.optJSONArray("companyExpenses"))
        )

        val appsArray = root.optJSONArray("apps") ?: JSONArray()
        val apps = ArrayList<AppData>()
        for (i in 0 until appsArray.length()) {
            val o = appsArray.getJSONObject(i)
            apps.add(appFromJson(o))
        }

        val staffArray = root.optJSONArray("staff") ?: JSONArray()
        val staff = ArrayList<Staff>()
        for (i in 0 until staffArray.length()) {
            val o = staffArray.getJSONObject(i)

            val allowances = ArrayList<Allowance>()
            val allowancesArray = o.optJSONArray("allowances") ?: JSONArray()
            for (j in 0 until allowancesArray.length()) {
                val ao = allowancesArray.getJSONObject(j)
                allowances.add(
                    Allowance(
                        id = ao.optString("id").ifBlank { UUID.randomUUID().toString() },
                        name = ao.optString("name"),
                        amount = ao.optLong("amount")
                    )
                )
            }

            val payments = ArrayList<Payment>()
            val paymentsArray = o.optJSONArray("payments") ?: JSONArray()
            for (j in 0 until paymentsArray.length()) {
                val po = paymentsArray.getJSONObject(j)
                payments.add(
                    Payment(
                        id = po.optString("id").ifBlank { UUID.randomUUID().toString() },
                        dateMillis = po.optLong("date"),
                        amount = po.optLong("amount"),
                        itemKey = po.optString("item"),
                        itemName = po.optString("itemName")
                    )
                )
            }

            // Photos: optional. Bad ids are skipped and anything beyond the limit is ignored (and logged).
            val images = ArrayList<StaffImage>()
            val imagesArray = o.optJSONArray("images")
            if (imagesArray != null) {
                for (j in 0 until imagesArray.length()) {
                    val io = imagesArray.optJSONObject(j) ?: continue
                    val imageId = io.optString("id")
                    if (!isValidMediaId(imageId)) continue
                    if (images.any { it.id == imageId }) continue
                    if (images.size >= MAX_STAFF_IMAGES) {
                        Log.w("LedgerStorage", "A staff member has more than $MAX_STAFF_IMAGES photos; the extra ones are ignored.")
                        break
                    }
                    images.add(
                        StaffImage(
                            id = imageId,
                            addedMillis = io.optLong("added"),
                            sizeBytes = io.optLong("size"),
                            sha256 = io.optString("sha256")
                        )
                    )
                }
            }

            staff.add(
                Staff(
                    id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                    name = o.optString("name"),
                    role = o.optString("role"),
                    email = o.optString("email"),
                    phone = o.optString("phone"),
                    bankName = o.optString("bankName"),
                    accountNumber = o.optString("accountNumber"),
                    monthlySalary = o.optLong("monthlySalary"),
                    allowances = allowances,
                    payments = payments,
                    images = images
                )
            )
        }

        val renewalsArray = root.optJSONArray("renewals") ?: JSONArray()
        val renewals = ArrayList<Renewal>()
        for (i in 0 until renewalsArray.length()) {
            val o = renewalsArray.getJSONObject(i)
            val reminders = ArrayList<Int>()
            val daysArray = o.optJSONArray("reminderDays")
            if (daysArray != null) {
                for (j in 0 until daysArray.length()) reminders.add(daysArray.optInt(j))
            }
            renewals.add(
                Renewal(
                    id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                    name = o.optString("name"),
                    amount = o.optLong("amount"),
                    durationDays = o.optInt("durationDays", 30),
                    paidMillis = o.optLong("paid"),
                    expiryMillis = o.optLong("expiry"),
                    reminderDays = reminders
                )
            )
        }

        val withdrawals = ArrayList<Withdrawal>()
        val withdrawalsArray = root.optJSONArray("withdrawals") ?: JSONArray()
        for (i in 0 until withdrawalsArray.length()) {
            val o = withdrawalsArray.optJSONObject(i) ?: continue
            withdrawals.add(
                Withdrawal(
                    id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                    dateMillis = o.optLong("date"),
                    amount = o.optLong("amount"),
                    label = o.optString("label"),
                    note = o.optString("note"),
                    channel = runCatching { Channel.valueOf(o.optString("channel", "PLAY")) }.getOrDefault(Channel.PLAY)
                )
            )
        }

        val cases = ArrayList<ComplaintCase>()
        val casesArray = root.optJSONArray("cases") ?: JSONArray()
        for (i in 0 until casesArray.length()) {
            val o = casesArray.optJSONObject(i) ?: continue
            val parsed = runCatching { jsonToCase(o) }.getOrNull() ?: continue
            cases.add(parsed)
        }

        // Older backups and older cloud data have no "socials": the four first accounts are made once.
        val socialsObject = root.optJSONObject("socials")
        val loaded = Ledger(
            company = company,
            apps = apps,
            staff = staff,
            renewals = renewals,
            withdrawals = withdrawals,
            cases = cases,
            nextCaseNumber = maxOf(root.optInt("nextCaseNumber", 1), cases.size + 1, 1),
            casesClockMillis = root.optLong("casesClockMillis"),
            software = SoftwareJson.fromJson(root.optJSONObject("software")),
            socials = if (socialsObject != null) SocialsJson.fromJson(socialsObject) else SocialsData(),
            assets = jsonToAssets(root.optJSONObject("assets")),
            hosting = HostingJson.fromJson(root.optJSONObject("hosting"))
        ).withSoftwareApp().withHostingApp().withoutDuplicateLegalExpenses()
        return if (socialsObject == null) loaded.withSocialsSeeded(System.currentTimeMillis()) else loaded
    }

    /**
     * Reads one app. Older files have no googlePlayEnabled: an app that has any sign of the dice
     * (a roll, Play Balance money, dice users, ads or a campaign) stays a Google Play app. Every other
     * app becomes a Manual app and keeps its users counted. A saved true is never lowered.
     */
    private fun appFromJson(o: JSONObject): AppData {
        val users = jsonToUsers(o.optJSONArray("users"))
        val rolls = jsonToRolls(o.optJSONArray("rolls"))
        val playTx = jsonToPlayTx(o.optJSONArray("playTx"))
        val campaign = jsonToCampaign(o.optJSONObject("campaign"))
        val adverts = jsonToAdverts(o.optJSONArray("adverts"))
        val lastRoll = o.optString("lastRollDayKey")
        val subs = o.optLong("playSubsBalance")
        val ads = o.optLong("playAdsBalance")
        val adsOn = o.optBoolean("adsEnabled")

        val gp = if (o.has("googlePlayEnabled")) {
            o.optBoolean("googlePlayEnabled")
        } else {
            lastRoll.isNotBlank() || rolls.isNotEmpty() || playTx.isNotEmpty() || subs > 0L || ads > 0L ||
                users.any { it.simulated } || adsOn || campaign != null || adverts.isNotEmpty()
        }

        val hasManual = o.has("manualTotalUsers")
        val manualTotal = if (hasManual) o.optLong("manualTotalUsers") else users.size.toLong()
        val manualPremium = if (hasManual) o.optLong("manualPremiumUsers")
        else users.count { it.plan == Plan.PREMIUM }.toLong()
        val manualPro = if (hasManual) o.optLong("manualProUsers")
        else users.count { it.plan == Plan.PRO }.toLong()

        return AppData(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            name = o.optString("name"),
            premiumPrice = o.optLong("premiumPrice"),
            proPrice = o.optLong("proPrice"),
            entries = jsonToEntries(o.optJSONArray("entries")),
            users = users,
            premiumBenefits = o.optString("premiumBenefits"),
            proBenefits = o.optString("proBenefits"),
            freeBenefits = o.optString("freeBenefits"),
            activeUsers = o.optLong("activeUsers"),
            activeUsersDayKey = o.optString("activeUsersDayKey"),
            activeHistory = jsonToHistory(o.optJSONArray("activeHistory")),
            newHistory = jsonToHistory(o.optJSONArray("newHistory")),
            adsEnabled = adsOn,
            lastRollDayKey = lastRoll,
            rolls = rolls,
            playTx = playTx,
            playSubsBalance = subs,
            playAdsBalance = ads,
            campaign = campaign,
            googlePlayEnabled = gp,
            googlePlaySinceMillis = o.optLong("googlePlaySinceMillis"),
            manualTotalUsers = if (gp) 0L else manualTotal,
            manualPremiumUsers = if (gp) 0L else manualPremium,
            manualProUsers = if (gp) 0L else manualPro,
            manualDailyNewUsers = o.optLong("manualDailyNewUsers"),
            manualNewUsersDayKey = o.optString("manualNewUsersDayKey"),
            manualPremiumToday = o.optLong("manualPremiumToday"),
            manualProToday = o.optLong("manualProToday"),
            manualTodayKey = o.optString("manualTodayKey"),
            planHistory = jsonToPlanHistory(o.optJSONArray("planHistory")),
            adverts = adverts,
            audienceFree = o.optLong("audienceFree"),
            audienceTodayKey = o.optString("audienceTodayKey"),
            audienceToday = o.optLong("audienceToday"),
            // Older files have no channels: the app stays a Google Play app.
            playOn = if (o.has("playOn")) o.optBoolean("playOn") else true,
            appleOn = o.optBoolean("appleOn"),
            websiteOn = o.optBoolean("websiteOn"),
            appleSubsBalance = o.optLong("appleSubsBalance"),
            paystackSubsBalance = o.optLong("paystackSubsBalance"),
            appleTx = jsonToPlayTx(o.optJSONArray("appleTx")),
            paystackTx = jsonToPlayTx(o.optJSONArray("paystackTx")),
            audienceApple = o.optLong("audienceApple"),
            audienceWebsite = o.optLong("audienceWebsite"),
            dayRolls = o.optJSONObject("dayRolls")?.let { d ->
                DayRolls(
                    dayKey = d.optString("day"),
                    playBp = d.optInt("play"),
                    appleBp = d.optInt("apple"),
                    websiteBp = d.optInt("website"),
                    sitesTenths = d.optInt("sites"),
                    storageBp = d.optInt("storage")
                )
            },
            // Only the Neribo Deploy app has users with sites; any other app is kept off, whatever an older file says.
            deployTracking = o.optBoolean("deployTracking") && o.optString("name").contains("deploy", ignoreCase = true),
            storageFreeMb = o.optLong("storageFreeMb"),
            storageProMb = o.optLong("storageProMb"),
            storagePremiumMb = o.optLong("storagePremiumMb"),
            storageSiteMb = o.optLong("storageSiteMb"),
            storageUsedKb = o.optLong("storageUsedKb"),
            storageShareMb = maxOf(0L, o.optLong("storageShareMb"))
        )
    }

    /**
     * Adverts as JSON. Only final numbers are saved: the days the backend answered (users, Premium, Pro, comment).
     * There is no conversion rate, no plan and no target in the file.
     */
    private fun advertsToJson(list: List<Advert>): JSONArray {
        val array = JSONArray()
        for (a in list) {
            val o = JSONObject()
            o.put("id", a.id)
            o.put("celebrityName", a.celebrityName)
            o.put("type", a.type.name)
            if (a.channel != Channel.PLAY) o.put("channel", a.channel.name)
            val platforms = JSONArray()
            for (p in a.platforms) {
                platforms.put(JSONObject().put("name", p.name).put("followers", p.followers))
            }
            o.put("platforms", platforms)
            o.put("totalFollowers", a.totalFollowers)
            o.put("startDayKey", a.startDayKey)
            o.put("endDayKey", a.endDayKey)
            o.put("stoppedDayKey", a.stoppedDayKey)
            val days = JSONArray()
            for (d in a.days) {
                val dobj = JSONObject()
                dobj.put("day", d.dayKey)
                dobj.put("users", d.users)
                dobj.put("premium", d.premium)
                dobj.put("pro", d.pro)
                if (d.comment.isNotEmpty()) dobj.put("comment", d.comment)
                dobj.put("delivered", d.delivered)
                days.put(dobj)
            }
            o.put("days", days)
            o.put("createdMillis", a.createdMillis)
            array.put(o)
        }
        return array
    }

    /**
     * Reads adverts. Older files have none. An advert saved by an older version (it has a "plan" and no "days")
     * keeps the days it had already delivered; its other days are answered by the backend like any new day.
     * Nothing from the old conversion (rate, target, plan) is kept.
     */
    private fun jsonToAdverts(array: JSONArray?): List<Advert> {
        if (array == null) return emptyList()
        val list = ArrayList<Advert>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val start = o.optString("startDayKey")
            val end = o.optString("endDayKey")
            if (start.isBlank() || end.isBlank()) continue
            val platforms = ArrayList<AdvertPlatform>()
            val platformArray = o.optJSONArray("platforms")
            if (platformArray != null) {
                for (j in 0 until platformArray.length()) {
                    val po = platformArray.optJSONObject(j) ?: continue
                    val name = po.optString("name")
                    if (name.isBlank()) continue
                    platforms.add(AdvertPlatform(name, po.optLong("followers")))
                }
            }
            val days = ArrayList<AdvertDay>()
            val dayArray = o.optJSONArray("days")
            if (dayArray != null) {
                for (j in 0 until dayArray.length()) {
                    val d = dayArray.optJSONObject(j) ?: continue
                    val key = d.optString("day")
                    if (key.isBlank()) continue
                    days.add(
                        AdvertDay(
                            dayKey = key,
                            users = d.optLong("users"),
                            premium = d.optLong("premium"),
                            pro = d.optLong("pro"),
                            comment = d.optString("comment", ""),
                            delivered = d.optBoolean("delivered", false)
                        )
                    )
                }
            } else {
                // Older file: the days that were already delivered stay delivered (they must never arrive twice).
                val planArray = o.optJSONArray("plan")
                val delivered = o.optInt("deliveredDays")
                val first = runCatching { java.time.LocalDate.parse(start) }.getOrNull()
                if (planArray != null && first != null) {
                    for (j in 0 until minOf(delivered, planArray.length())) {
                        days.add(
                            AdvertDay(
                                dayKey = first.plusDays(j.toLong()).toString(),
                                users = planArray.optLong(j),
                                premium = 0L,
                                pro = 0L,
                                comment = "",
                                delivered = true
                            )
                        )
                    }
                }
            }
            val type = runCatching { AdvertType.valueOf(o.optString("type", "CELEBRITY")) }
                .getOrDefault(AdvertType.CELEBRITY)
            list.add(
                Advert(
                    id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                    celebrityName = o.optString("celebrityName"),
                    type = type,
                    channel = runCatching { Channel.valueOf(o.optString("channel", "PLAY")) }.getOrDefault(Channel.PLAY),
                    platforms = platforms,
                    totalFollowers = o.optLong("totalFollowers"),
                    startDayKey = start,
                    endDayKey = end,
                    stoppedDayKey = o.optString("stoppedDayKey"),
                    days = days.sortedBy { it.dayKey },
                    createdMillis = o.optLong("createdMillis")
                )
            )
        }
        return list
    }

    private fun planHistoryToJson(list: List<PlanEvent>): JSONArray {
        val array = JSONArray()
        for (e in list) {
            val o = JSONObject()
            o.put("id", e.id)
            o.put("userId", e.userId)
            o.put("kind", e.kind.name)
            o.put("plan", e.plan.name)
            o.put("atMillis", e.atMillis)
            o.put("endMillis", e.endMillis)
            array.put(o)
        }
        return array
    }

    private fun jsonToPlanHistory(array: JSONArray?): List<PlanEvent> {
        if (array == null) return emptyList()
        val list = ArrayList<PlanEvent>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val kind = PlanEventKind.values().firstOrNull { it.name == o.optString("kind") } ?: continue
            val plan = Plan.values().firstOrNull { it.name == o.optString("plan") } ?: continue
            val userId = o.optString("userId")
            if (userId.isBlank()) continue
            val at = o.optLong("atMillis")
            list.add(
                PlanEvent(
                    id = o.optString("id").ifBlank { userId + "|" + kind.name + "|" + plan.name + "|" + at },
                    userId = userId,
                    kind = kind,
                    plan = plan,
                    atMillis = at,
                    endMillis = o.optLong("endMillis")
                )
            )
        }
        return list
    }

    // ---- Cases ----

    private fun eventToJson(e: CaseEvent): JSONObject {
        val o = JSONObject()
        o.put("id", e.id)
        o.put("due", e.dueMillis)
        o.put("kind", e.kind.name)
        o.put("actor", e.actor.name)
        o.put("title", e.title)
        o.put("body", e.body)
        o.put("statusAfter", e.statusAfter?.name ?: "")
        return o
    }

    private fun jsonToEvent(o: JSONObject): CaseEvent? {
        val kind = runCatching { CaseEventKind.valueOf(o.optString("kind")) }.getOrNull() ?: return null
        val actor = runCatching { CaseActor.valueOf(o.optString("actor")) }.getOrDefault(CaseActor.SYSTEM)
        val status = runCatching { CaseStatus.valueOf(o.optString("statusAfter")) }.getOrNull()
        return CaseEvent(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            dueMillis = o.optLong("due"),
            kind = kind,
            actor = actor,
            title = o.optString("title"),
            body = o.optString("body"),
            statusAfter = status
        )
    }

    private fun eventsToJson(list: List<CaseEvent>): JSONArray {
        val array = JSONArray()
        for (e in list) array.put(eventToJson(e))
        return array
    }

    private fun jsonToEvents(array: JSONArray?): List<CaseEvent> {
        if (array == null) return emptyList()
        val list = ArrayList<CaseEvent>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val e = jsonToEvent(o) ?: continue
            list.add(e)
        }
        return list
    }

    private fun caseToJson(c: ComplaintCase): JSONObject {
        val o = JSONObject()
        o.put("id", c.id)
        o.put("ref", c.ref)
        o.put("appId", c.appId)
        o.put("appName", c.appName)
        o.put("userId", c.userId)
        o.put("customerName", c.customerName)
        o.put("customerEmail", c.customerEmail)
        o.put("customerPhone", c.customerPhone)
        o.put("plan", c.plan.name)
        o.put("category", c.category.name)
        o.put("subject", c.subject)
        o.put("created", c.createdMillis)
        o.put("events", eventsToJson(c.events))
        o.put("outcome", c.outcome.name)
        o.put("regulator", c.regulator)
        o.put("regulatorRef", c.regulatorRef)
        o.put("court", c.court)
        o.put("suitNo", c.suitNo)
        o.put("claimAmount", c.claimAmount)
        o.put("amountDue", c.amountDue)
        o.put("dueReason", c.dueReason)
        o.put("payBy", c.payByMillis)
        o.put("paid", c.paidMillis)
        o.put("expenseId", c.expenseId)
        o.put("notes", eventsToJson(c.notes))
        if (c.writer.isNotEmpty()) o.put("writer", c.writer)
        return o
    }

    private fun jsonToCase(o: JSONObject): ComplaintCase? {
        val category = runCatching { CaseCategory.valueOf(o.optString("category")) }.getOrNull() ?: return null
        val outcome = runCatching { CaseOutcome.valueOf(o.optString("outcome")) }.getOrDefault(CaseOutcome.NONE)
        val plan = runCatching { Plan.valueOf(o.optString("plan")) }.getOrDefault(Plan.FREE)
        val ref = o.optString("ref")
        if (ref.isBlank()) return null
        return ComplaintCase(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            ref = ref,
            appId = o.optString("appId"),
            appName = o.optString("appName"),
            userId = o.optString("userId"),
            customerName = o.optString("customerName"),
            customerEmail = o.optString("customerEmail"),
            customerPhone = o.optString("customerPhone"),
            plan = plan,
            category = category,
            subject = o.optString("subject"),
            createdMillis = o.optLong("created"),
            events = jsonToEvents(o.optJSONArray("events")),
            outcome = outcome,
            regulator = o.optString("regulator"),
            regulatorRef = o.optString("regulatorRef"),
            court = o.optString("court"),
            suitNo = o.optString("suitNo"),
            claimAmount = o.optLong("claimAmount"),
            amountDue = o.optLong("amountDue"),
            dueReason = o.optString("dueReason"),
            payByMillis = o.optLong("payBy"),
            paidMillis = o.optLong("paid"),
            expenseId = o.optString("expenseId"),
            notes = jsonToEvents(o.optJSONArray("notes")),
            writer = o.optString("writer", "")
        )
    }

    private fun assetsToJson(assets: CompanyAssets): JSONObject {
        val o = JSONObject()
        o.put("storageTotalMb", assets.storageTotalMb)
        if (assets.pinHash.isNotEmpty()) o.put("pinHash", assets.pinHash).put("pinSalt", assets.pinSalt)
        if (assets.offsiteMillis > 0L) o.put("offsiteKb", assets.offsiteKb).put("offsiteMillis", assets.offsiteMillis)
        val log = JSONArray()
        for (line in assets.storageLog.take(ChannelRules.MAX_STORAGE_LOG)) {
            log.put(JSONObject().put("t", line.millis).put("x", line.text))
        }
        o.put("storageLog", log)
        val docs = JSONArray()
        for (d in assets.documents) {
            docs.put(
                JSONObject().put("id", d.id).put("title", d.title).put("kind", d.kind).put("note", d.note)
                    .put("added", d.addedMillis).put("photo", d.hasPhoto)
            )
        }
        o.put("documents", docs)
        val tools = JSONArray()
        for (t in assets.tools) {
            tools.put(
                JSONObject().put("id", t.id).put("name", t.name).put("qty", t.quantity).put("note", t.note)
                    .put("added", t.addedMillis).put("photo", t.hasPhoto)
                    .put("cat", t.category).put("brand", t.brand).put("model", t.model).put("specs", t.specs)
                    .put("dept", t.department).put("to", t.assignedTo).put("use", t.usedFor).put("photos", t.photos)
            )
        }
        o.put("tools", tools)
        return o
    }

    private fun jsonToAssets(o: JSONObject?): CompanyAssets {
        if (o == null) return CompanyAssets()
        val docs = ArrayList<CompanyDoc>()
        val da = o.optJSONArray("documents")
        if (da != null) {
            for (i in 0 until da.length()) {
                val d = da.optJSONObject(i) ?: continue
                val title = d.optString("title").trim()
                if (title.isEmpty()) continue
                docs.add(
                    CompanyDoc(
                        id = d.optString("id").ifBlank { UUID.randomUUID().toString() },
                        title = title,
                        kind = d.optString("kind"),
                        note = d.optString("note"),
                        addedMillis = d.optLong("added"),
                        hasPhoto = d.optBoolean("photo")
                    )
                )
            }
        }
        val tools = ArrayList<ToolItem>()
        val ta = o.optJSONArray("tools")
        if (ta != null) {
            for (i in 0 until ta.length()) {
                val t = ta.optJSONObject(i) ?: continue
                val name = t.optString("name").trim()
                if (name.isEmpty()) continue
                tools.add(
                    ToolItem(
                        id = t.optString("id").ifBlank { UUID.randomUUID().toString() },
                        name = name,
                        quantity = maxOf(1, t.optInt("qty", 1)),
                        note = t.optString("note"),
                        addedMillis = t.optLong("added"),
                        hasPhoto = t.optBoolean("photo"),
                        category = t.optString("cat"),
                        brand = t.optString("brand"),
                        model = t.optString("model"),
                        specs = t.optString("specs"),
                        department = t.optString("dept"),
                        assignedTo = t.optString("to"),
                        usedFor = t.optString("use"),
                        photos = t.optInt("photos").coerceIn(0, ToolItem.MAX_PHOTOS)
                    )
                )
            }
        }
        val logLines = ArrayList<StorageLogLine>()
        val la = o.optJSONArray("storageLog")
        if (la != null) {
            for (i in 0 until la.length()) {
                val l = la.optJSONObject(i) ?: continue
                val text = l.optString("x")
                if (text.isEmpty()) continue
                logLines.add(StorageLogLine(l.optLong("t"), text))
            }
        }
        return CompanyAssets(
            documents = docs,
            tools = tools,
            storageTotalMb = maxOf(0L, o.optLong("storageTotalMb")),
            pinHash = o.optString("pinHash"),
            pinSalt = o.optString("pinSalt"),
            storageLog = logLines.take(ChannelRules.MAX_STORAGE_LOG),
            offsiteKb = maxOf(0L, o.optLong("offsiteKb")),
            offsiteMillis = maxOf(0L, o.optLong("offsiteMillis"))
        )
    }

    private fun usersToJson(users: List<AppUser>): JSONArray {
        val array = JSONArray()
        for (u in users) {
            val o = JSONObject()
            o.put("id", u.id)
            o.put("name", u.name)
            o.put("email", u.email)
            o.put("phone", u.phone)
            o.put("plan", u.plan.name)
            o.put("addedMillis", u.addedMillis)
            o.put("planExpiry", u.planExpiryMillis)
            o.put("planStart", u.planStartMillis)
            o.put("simulated", u.simulated)
            // Short key, left out when unknown, so every user row stays small.
            if (u.lastActiveMillis > 0L) o.put("la", u.lastActiveMillis)
            if (u.channel != Channel.PLAY) o.put("ch", u.channel.name)
            if (u.sites > 0) o.put("st", u.sites)
            if (u.customStorageMb >= 0L) o.put("cs", u.customStorageMb)
            array.put(o)
        }
        return array
    }

    /** Skips any user without a name. An unknown plan becomes Free. */
    private fun jsonToUsers(array: JSONArray?): List<AppUser> {
        if (array == null) return emptyList()
        val list = ArrayList<AppUser>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val name = o.optString("name").trim()
            if (name.isEmpty()) continue
            val plan = runCatching { Plan.valueOf(o.optString("plan")) }.getOrDefault(Plan.FREE)
            list.add(
                AppUser(
                    id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                    name = name,
                    email = o.optString("email"),
                    phone = o.optString("phone"),
                    plan = plan,
                    addedMillis = o.optLong("addedMillis"),
                    planExpiryMillis = o.optLong("planExpiry"),
                    planStartMillis = o.optLong("planStart"),
                    simulated = o.optBoolean("simulated"),
                    lastActiveMillis = o.optLong("la"),
                    channel = runCatching { Channel.valueOf(o.optString("ch", "PLAY")) }.getOrDefault(Channel.PLAY),
                    sites = o.optInt("st"),
                    customStorageMb = if (o.has("cs")) maxOf(0L, o.optLong("cs")) else -1L
                )
            )
        }
        return list
    }

    private fun rollsToJson(rolls: List<RollRecord>): JSONArray {
        val array = JSONArray()
        for (r in rolls) {
            val o = JSONObject()
            o.put("day", r.dayKey)
            val faces = JSONArray()
            for (f in r.faces) faces.put(f)
            o.put("faces", faces)
            o.put("newUsers", r.newUsers)
            o.put("active", r.active)
            o.put("premium", r.premium)
            o.put("pro", r.pro)
            o.put("renewed", r.renewed)
            o.put("lapsed", r.lapsed)
            o.put("subsNet", r.subsNet)
            o.put("adsNet", r.adsNet)
            o.put("campaign", r.campaign)
            o.put("advertUsers", r.advertUsers)
            array.put(o)
        }
        return array
    }

    private fun jsonToRolls(array: JSONArray?): List<RollRecord> {
        if (array == null) return emptyList()
        val list = ArrayList<RollRecord>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val day = o.optString("day")
            if (day.isBlank()) continue
            val faces = ArrayList<Int>()
            val facesArray = o.optJSONArray("faces")
            if (facesArray != null) {
                for (j in 0 until facesArray.length()) faces.add(facesArray.optInt(j, -1))
            }
            while (faces.size < DiceKind.values().size) faces.add(-1)
            list.add(
                RollRecord(
                    dayKey = day,
                    faces = faces,
                    newUsers = o.optLong("newUsers"),
                    active = o.optLong("active"),
                    premium = o.optLong("premium"),
                    pro = o.optLong("pro"),
                    renewed = o.optLong("renewed"),
                    lapsed = o.optLong("lapsed"),
                    subsNet = o.optLong("subsNet"),
                    adsNet = o.optLong("adsNet"),
                    campaign = o.optBoolean("campaign"),
                    advertUsers = o.optLong("advertUsers")
                )
            )
        }
        return list
    }

    private fun playTxToJson(list: List<PlayTx>): JSONArray {
        val array = JSONArray()
        for (t in list) {
            val o = JSONObject()
            o.put("id", t.id)
            o.put("kind", t.kind.name)
            o.put("label", t.label)
            o.put("gross", t.gross)
            o.put("fee", t.fee)
            o.put("net", t.net)
            o.put("date", t.dateMillis)
            array.put(o)
        }
        return array
    }

    private fun jsonToPlayTx(array: JSONArray?): List<PlayTx> {
        if (array == null) return emptyList()
        val list = ArrayList<PlayTx>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val kind = runCatching { PlayTxKind.valueOf(o.optString("kind")) }.getOrNull() ?: continue
            list.add(
                PlayTx(
                    id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                    kind = kind,
                    label = o.optString("label"),
                    gross = o.optLong("gross"),
                    fee = o.optLong("fee"),
                    net = o.optLong("net"),
                    dateMillis = o.optLong("date")
                )
            )
        }
        return list
    }

    private fun jsonToCampaign(o: JSONObject?): Campaign? {
        if (o == null) return null
        val start = o.optString("start")
        val end = o.optString("end")
        if (start.isBlank() || end.isBlank()) return null
        return Campaign(
            sizeName = o.optString("size"),
            startDayKey = start,
            endDayKey = end,
            stoppedDayKey = o.optString("stopped")
        )
    }

    private fun historyToJson(history: List<DayCount>): JSONArray {
        val array = JSONArray()
        for (h in history) {
            val o = JSONObject()
            o.put("day", h.dayKey)
            o.put("count", h.count)
            array.put(o)
        }
        return array
    }

    private fun jsonToHistory(array: JSONArray?): List<DayCount> {
        if (array == null) return emptyList()
        val list = ArrayList<DayCount>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val day = o.optString("day")
            if (day.isBlank()) continue
            list.add(DayCount(dayKey = day, count = o.optLong("count")))
        }
        return list
    }

    private fun entriesToJson(entries: List<Entry>): JSONArray {
        val array = JSONArray()
        for (e in entries) {
            val o = JSONObject()
            o.put("id", e.id)
            o.put("title", e.title)
            o.put("amount", e.amount)
            o.put("isIncome", e.isIncome)
            o.put("date", e.dateMillis)
            o.put("auto", e.auto)
            o.put("caseId", e.caseId)
            o.put("appId", e.appId)
            o.put("projectId", e.projectId)
            array.put(o)
        }
        return array
    }

    private fun jsonToEntries(array: JSONArray?): List<Entry> {
        if (array == null) return emptyList()
        val list = ArrayList<Entry>()
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            list.add(
                Entry(
                    id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                    title = o.optString("title"),
                    amount = o.optLong("amount"),
                    isIncome = o.optBoolean("isIncome"),
                    dateMillis = o.optLong("date"),
                    auto = o.optBoolean("auto"),
                    caseId = o.optString("caseId"),
                    appId = o.optString("appId"),
                    projectId = o.optString("projectId")
                )
            )
        }
        return list
    }
}
