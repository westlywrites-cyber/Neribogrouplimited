package com.westly.neribogroup.data

import java.time.Instant
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sqrt
import kotlin.random.Random

/** The seven dice. The order here is the order of [RollRecord.faces]. */
enum class DiceKind { NEW, ACTIVE, PREMIUM, PRO, RENEW, LOST, ADS }

enum class RollBlock { OK, ALREADY_ROLLED, DATE_WENT_BACK }

/**
 * The daily dice. Every dice shows 0 to 5. 0 always means nothing happens.
 * 1 to 5 are bigger and bigger ranges that depend on how big the app is.
 * Nothing here needs the internet or AI.
 */
object DiceEngine {

    const val MAX_CAMPAIGN_DAYS = 14
    private const val MAX_NEW_PER_ROLL = 400

    // Dice 1 to 5 for new users: a range of people per day for an app with 1,000 users.
    private val NEW_RANGES = arrayOf(1 to 5, 6 to 12, 13 to 20, 21 to 30, 31 to 45)

    // Dice 1 to 5 as a share of a group of people (low, high).
    private val ACTIVE_SHARE = arrayOf(
        0.03 to 0.08, 0.08 to 0.15, 0.15 to 0.25, 0.25 to 0.35, 0.35 to 0.50
    )
    private val UPGRADE_SHARE = arrayOf(
        0.0005 to 0.0015, 0.0015 to 0.003, 0.003 to 0.006, 0.006 to 0.010, 0.010 to 0.018
    )
    private val RENEW_SHARE = arrayOf(
        0.20 to 0.35, 0.35 to 0.50, 0.50 to 0.65, 0.65 to 0.80, 0.80 to 0.95
    )
    private val LOST_SHARE = arrayOf(
        0.05 to 0.10, 0.10 to 0.20, 0.20 to 0.30, 0.30 to 0.45, 0.45 to 0.60
    )

    // Dice 1 to 5 for ads: naira per free user per day before Google's share (low, high).
    private val ADS_RATE = arrayOf(
        0.3 to 0.8, 0.8 to 1.6, 1.6 to 2.8, 2.8 to 4.2, 4.2 to 6.5
    )

    private fun priceOf(app: AppData, plan: Plan): Long = when (plan) {
        Plan.PREMIUM -> app.premiumPrice
        Plan.PRO -> app.proPrice
        Plan.FREE -> 0L
    }

    /** Dice users on the Free plan. Only these can upgrade. */
    private fun upgradePool(app: AppData): List<AppUser> =
        app.users.filter { it.simulated && it.plan == Plan.FREE }

    /** Dice users whose paid plan has ended and whose plan still exists (has a price). */
    private fun renewalPool(app: AppData, now: Long): List<AppUser> =
        app.users.filter { user ->
            user.simulated && user.plan != Plan.FREE &&
                (user.planDaysLeft(now) ?: 1L) <= 0L && priceOf(app, user.plan) > 0L
        }

    /** Whether the dice can be rolled now. One roll per calendar day, by the phone's date. */
    fun rollBlock(app: AppData, now: Long): RollBlock {
        val today = dayKey(now)
        val last = app.lastRollDayKey
        return when {
            last.isEmpty() -> RollBlock.OK
            last == today -> RollBlock.ALREADY_ROLLED
            last > today -> RollBlock.DATE_WENT_BACK
            else -> RollBlock.OK
        }
    }

    /** Only the dice that can bring something real are shown. */
    fun visibleDice(app: AppData, now: Long): List<DiceKind> {
        val list = ArrayList<DiceKind>()
        list.add(DiceKind.NEW)
        if (app.totalUsers >= 1L) list.add(DiceKind.ACTIVE)
        val canUpgrade = upgradePool(app).isNotEmpty()
        if (app.premiumPrice > 0L && canUpgrade) list.add(DiceKind.PREMIUM)
        if (app.proPrice > 0L && canUpgrade) list.add(DiceKind.PRO)
        if (renewalPool(app, now).isNotEmpty()) {
            list.add(DiceKind.RENEW)
            list.add(DiceKind.LOST)
        }
        if (app.adsEnabled && app.freeUsers >= 1L) list.add(DiceKind.ADS)
        return list
    }

    // -----------------------------------------------------------------------------------------
    // Turning a dice face into a real number
    // -----------------------------------------------------------------------------------------

    private fun newUsersFor(face: Int, total: Long, factor: Double, random: Random): Int {
        if (face <= 0) return 0
        val scale = max(0.1, sqrt(total / 1000.0))
        val range = NEW_RANGES[face - 1]
        val low = max(1, (range.first * scale).roundToInt())
        val high = max(low, (range.second * scale).roundToInt())
        val base = random.nextInt(low, high + 1)
        return min(MAX_NEW_PER_ROLL, max(1, (base * factor).roundToInt()))
    }

    private fun activeFor(face: Int, total: Long, factor: Double, random: Random): Long {
        if (face <= 0 || total <= 0L) return 0L
        val range = ACTIVE_SHARE[face - 1]
        val boost = 1.0 + (factor - 1.0) / 2.0
        val share = random.nextDouble(range.first, range.second) * boost
        return min(total, (total * share).roundToLong())
    }

    /** How many people out of [pool] a dice face picks. At least 1 for a face above 0, never more than [pool]. */
    private fun countFromShare(
        pool: Int,
        face: Int,
        table: Array<Pair<Double, Double>>,
        factor: Double,
        weight: Double,
        random: Random
    ): Int {
        if (face <= 0 || pool <= 0) return 0
        val range = table[face - 1]
        val share = random.nextDouble(range.first, range.second) * weight * factor
        return min(pool, max(1, (pool * share).roundToInt()))
    }

    private fun tx(kind: PlayTxKind, label: String, gross: Long, fee: Long, now: Long): PlayTx =
        PlayTx(kind = kind, label = label, gross = gross, fee = fee, net = gross - fee, dateMillis = now)

    private fun plural(count: Int, one: String, many: String): String =
        if (count == 1) "1 $one" else "$count $many"

    /**
     * Rolls every dice of one app for today and returns the new ledger.
     * Returns the ledger unchanged if the app is missing or today's roll is not allowed.
     * Money goes only into the app's Google Play Balance, never straight into profit.
     */
    fun roll(ledger: Ledger, appId: String, now: Long, random: Random = Random.Default): Ledger {
        val app = ledger.apps.firstOrNull { it.id == appId } ?: return ledger
        if (rollBlock(app, now) != RollBlock.OK) return ledger

        val today = dayKey(now)
        val dayStart = dayStartMillis(now)
        val campaignToday = app.campaign?.takeIf { it.isActiveOn(today) }
        val factor = campaignToday?.size?.factor ?: 1.0

        val visible = visibleDice(app, now)
        val faces = DiceKind.values().map { kind ->
            if (kind in visible) random.nextInt(0, 6) else -1
        }
        fun face(kind: DiceKind): Int = faces[kind.ordinal]
        fun timeToday(): Long = dayStart + random.nextLong(max(1L, now - dayStart))

        var users: List<AppUser> = app.users
        var lapsed = 0

        // A paid plan that no longer exists (price 0) lapses by itself when it ends.
        val dead = users.filter { user ->
            user.simulated && user.plan != Plan.FREE &&
                (user.planDaysLeft(now) ?: 1L) <= 0L && priceOf(app, user.plan) <= 0L
        }.map { it.id }.toSet()
        if (dead.isNotEmpty()) {
            users = users.map {
                if (it.id in dead) it.copy(plan = Plan.FREE, planStartMillis = 0L, planExpiryMillis = 0L) else it
            }
            lapsed += dead.size
        }

        val lines = ArrayList<PlayTx>()

        // New users (green). They are Free users with a made-up name, email and phone.
        val newCount = newUsersFor(face(DiceKind.NEW), app.totalUsers, factor, random)
        val newUsers = if (newCount > 0) {
            IdentityGenerator.make(newCount, ledger, now, dayStart, random)
        } else {
            emptyList()
        }

        // Active users (blue). Never more than all users, never fewer than today's new users.
        val totalAfter = app.totalUsers + newCount
        val fromDice = activeFor(face(DiceKind.ACTIVE), totalAfter, factor, random)
        val activeToday = min(totalAfter, max(fromDice, newCount.toLong()))

        // Upgrades (yellow and purple). Only dice users who were Free before this roll.
        val freePool = upgradePool(app).shuffled(random)
        val premiumPicked = if (app.premiumPrice > 0L) {
            freePool.take(
                countFromShare(freePool.size, face(DiceKind.PREMIUM), UPGRADE_SHARE, factor, 1.0, random)
            )
        } else {
            emptyList()
        }
        val proPicked = if (app.proPrice > 0L) {
            val rest = freePool.drop(premiumPicked.size)
            rest.take(countFromShare(rest.size, face(DiceKind.PRO), UPGRADE_SHARE, factor, 0.5, random))
        } else {
            emptyList()
        }
        val changed = HashMap<String, AppUser>()
        for (u in premiumPicked) {
            val start = timeToday()
            changed[u.id] = u.copy(
                plan = Plan.PREMIUM,
                planStartMillis = start,
                planExpiryMillis = start + PLAN_DAYS * DAY_MILLIS
            )
        }
        for (u in proPicked) {
            val start = timeToday()
            changed[u.id] = u.copy(
                plan = Plan.PRO,
                planStartMillis = start,
                planExpiryMillis = start + PLAN_DAYS * DAY_MILLIS
            )
        }
        if (premiumPicked.isNotEmpty()) {
            val gross = premiumPicked.size * app.premiumPrice
            lines.add(
                tx(
                    PlayTxKind.SUBSCRIPTION,
                    plural(premiumPicked.size, "new Premium subscription", "new Premium subscriptions"),
                    gross, playSubscriptionFee(gross), now
                )
            )
        }
        if (proPicked.isNotEmpty()) {
            val gross = proPicked.size * app.proPrice
            lines.add(
                tx(
                    PlayTxKind.SUBSCRIPTION,
                    plural(proPicked.size, "new Pro subscription", "new Pro subscriptions"),
                    gross, playSubscriptionFee(gross), now
                )
            )
        }

        // Renewals (orange) and cancellations (red). Both take people from the same group
        // (dice users whose plan has ended), so nobody is counted twice.
        val endedPool = renewalPool(app, now).shuffled(random)
        val renewCount = countFromShare(endedPool.size, face(DiceKind.RENEW), RENEW_SHARE, 1.0, 1.0, random)
        val renewedUsers = endedPool.take(renewCount)
        val leftOver = endedPool.drop(renewCount)
        val lostCount = min(
            leftOver.size,
            countFromShare(endedPool.size, face(DiceKind.LOST), LOST_SHARE, 1.0, 1.0, random)
        )
        val lostUsers = leftOver.take(lostCount)
        for (u in renewedUsers) {
            val base = max(now, u.planExpiryMillis)
            changed[u.id] = u.copy(planExpiryMillis = base + PLAN_DAYS * DAY_MILLIS)
        }
        for (u in lostUsers) {
            changed[u.id] = u.copy(plan = Plan.FREE, planStartMillis = 0L, planExpiryMillis = 0L)
        }
        lapsed += lostUsers.size
        if (renewedUsers.isNotEmpty()) {
            val gross = renewedUsers.sumOf { priceOf(app, it.plan) }
            lines.add(
                tx(
                    PlayTxKind.RENEWAL,
                    plural(renewedUsers.size, "renewal", "renewals"),
                    gross, playSubscriptionFee(gross), now
                )
            )
        }

        // Google Ads (gold). Based on how many Free users the app has.
        var adsGross = 0L
        if (face(DiceKind.ADS) > 0) {
            val range = ADS_RATE[face(DiceKind.ADS) - 1]
            val rate = random.nextDouble(range.first, range.second)
            adsGross = max(1L, (app.freeUsers * rate).roundToLong())
            val net = adsNetAfterGoogle(adsGross)
            lines.add(tx(PlayTxKind.ADS, "Google Ads", adsGross, adsGross - net, now))
        }

        val subsNet = lines.filter { it.kind != PlayTxKind.ADS }.sumOf { it.net }
        val adsNet = lines.filter { it.kind == PlayTxKind.ADS }.sumOf { it.net }

        val finalUsers = users.map { changed[it.id] ?: it } + newUsers

        val record = RollRecord(
            dayKey = today,
            faces = faces,
            newUsers = newCount.toLong(),
            active = activeToday,
            premium = premiumPicked.size.toLong(),
            pro = proPicked.size.toLong(),
            renewed = renewedUsers.size.toLong(),
            lapsed = lapsed.toLong(),
            subsNet = subsNet,
            adsNet = adsNet,
            campaign = campaignToday != null
        )

        val history = (app.activeHistory.filter { it.dayKey != today } + DayCount(today, activeToday))
            .sortedBy { it.dayKey }
            .takeLast(30)

        val updated = app.copy(
            users = finalUsers,
            activeUsers = activeToday,
            activeUsersDayKey = today,
            activeHistory = history,
            lastRollDayKey = today,
            rolls = (app.rolls + record).takeLast(30),
            playTx = (app.playTx + lines).takeLast(200),
            playSubsBalance = app.playSubsBalance + subsNet,
            playAdsBalance = app.playAdsBalance + adsNet
        )
        return ledger.copy(apps = ledger.apps.map { if (it.id == appId) updated else it })
    }

    // -----------------------------------------------------------------------------------------
    // Withdrawing from the Google Play Balance
    // -----------------------------------------------------------------------------------------

    /** Moves one app's whole Google Play Balance into that app's income and writes it in the history. */
    fun withdrawApp(ledger: Ledger, appId: String, now: Long): Ledger {
        val app = ledger.apps.firstOrNull { it.id == appId } ?: return ledger
        val amount = app.playBalance
        if (amount <= 0L) return ledger
        val record = Withdrawal(dateMillis = now, amount = amount, label = app.name)
        return ledger.copy(
            apps = ledger.apps.map { if (it.id == appId) it.withdrawn(now) else it },
            withdrawals = (listOf(record) + ledger.withdrawals).take(200)
        )
    }

    /** Moves every app's Google Play Balance into profit at once and writes one line in the history. */
    fun withdrawAll(ledger: Ledger, now: Long): Ledger {
        val paying = ledger.apps.filter { it.playBalance > 0L }
        if (paying.isEmpty()) return ledger
        val total = paying.sumOf { it.playBalance }
        val ids = paying.map { it.id }.toSet()
        val note = paying.joinToString(", ") { it.name + " " + "₦" + it.playBalance }
        val record = Withdrawal(dateMillis = now, amount = total, label = "All apps", note = note)
        return ledger.copy(
            apps = ledger.apps.map { if (it.id in ids) it.withdrawn(now) else it },
            withdrawals = (listOf(record) + ledger.withdrawals).take(200)
        )
    }

    // -----------------------------------------------------------------------------------------
    // Campaigns
    // -----------------------------------------------------------------------------------------

    /** Null when the campaign is allowed, otherwise the reason it is not. */
    fun campaignError(app: AppData, startInDays: Int, lastsDays: Int, now: Long): String? {
        if (startInDays < 0) return "A campaign cannot start in the past."
        if (startInDays > 30) return "A campaign must start within the next 30 days."
        if (lastsDays < 1) return "A campaign lasts at least 1 day."
        if (lastsDays > MAX_CAMPAIGN_DAYS) return "A campaign lasts at most $MAX_CAMPAIGN_DAYS days."
        val existing = app.campaign
        if (existing != null) {
            val neverRan = existing.stoppedDayKey.isNotEmpty() &&
                existing.stoppedDayKey <= existing.startDayKey
            if (existing.endDayKey >= dayKey(now) && !neverRan) {
                return "This app already has a campaign. It can only have one at a time."
            }
        }
        return null
    }

    /** Starts a campaign. Returns the app unchanged when it is not allowed. It costs no money. */
    fun startCampaign(
        app: AppData,
        size: CampaignSize,
        startInDays: Int,
        lastsDays: Int,
        now: Long
    ): AppData {
        if (campaignError(app, startInDays, lastsDays, now) != null) return app
        val today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
        val start = today.plusDays(startInDays.toLong())
        val end = start.plusDays((lastsDays - 1).toLong())
        return app.copy(
            campaign = Campaign(
                sizeName = size.name,
                startDayKey = start.toString(),
                endDayKey = end.toString()
            )
        )
    }

    /** Stops a campaign early. It can never be made longer. */
    fun stopCampaign(app: AppData, now: Long): AppData {
        val campaign = app.campaign ?: return app
        if (campaign.stoppedDayKey.isNotEmpty() || campaign.endDayKey < dayKey(now)) return app
        return app.copy(campaign = campaign.copy(stoppedDayKey = dayKey(now)))
    }
}

/** Makes believable Nigerian names, emails and phone numbers that never repeat. */
object IdentityGenerator {

    private val FIRST = listOf(
        "Chinedu", "Emeka", "Ngozi", "Adaeze", "Oluwaseun", "Tunde", "Bola", "Funke", "Ibrahim", "Aisha",
        "Musa", "Fatima", "Yusuf", "Zainab", "Chioma", "Obinna", "Ifeanyi", "Kelechi", "Nneka", "Uche",
        "Tobi", "Damilola", "Segun", "Yetunde", "Kemi", "Femi", "Sade", "Ayo", "Dayo", "Kayode",
        "Babatunde", "Folake", "Temitope", "Adebayo", "Olamide", "Ebube", "Chidera", "Amara", "Ikenna",
        "Somto", "Tochukwu", "Eniola", "Hauwa", "Abdullahi", "Aminu", "Sani", "Halima", "Ejiro", "Efe",
        "Tamara", "Peace", "Blessing", "Favour", "Gift", "Godwin", "Joy", "Precious", "Samuel", "David",
        "Daniel", "Grace", "Esther", "Ruth", "Michael", "John", "Mary", "Sarah", "Emmanuel", "Joseph",
        "Stella", "Victoria", "Patience", "Faith", "Ifeoma", "Onyinye", "Nkechi", "Ugochukwu", "Ebuka",
        "Nonso", "Kosi", "Zara", "Hadiza", "Bukola", "Titilayo", "Rotimi", "Gbenga", "Wale", "Lanre",
        "Taiwo", "Kehinde", "Ayomide", "Oluwatobi", "Temi", "Chukwuemeka", "Amaka", "Ifunanya", "Sola",
        "Bisi", "Jide", "Kunle", "Dele", "Lola", "Tayo", "Yemi", "Sunday", "Monday", "Friday"
    )

    private val LAST = listOf(
        "Okafor", "Adeyemi", "Balogun", "Ibrahim", "Musa", "Okeke", "Eze", "Nwosu", "Obi", "Okonkwo",
        "Adebayo", "Ogunleye", "Afolabi", "Bello", "Danjuma", "Abubakar", "Suleiman", "Lawal", "Garba",
        "Yakubu", "Okoro", "Nwachukwu", "Igwe", "Chukwu", "Onyeka", "Ajayi", "Akinwale", "Olawale",
        "Fashola", "Ogundipe", "Adewale", "Oyelaran", "Salami", "Alabi", "Odunsi", "Ekwueme", "Umeh",
        "Anyanwu", "Madu", "Nnamdi", "Ezeh", "Agu", "Uzor", "Okpara", "Ojo", "Adeleke", "Ogbonna",
        "Mohammed", "Aliyu", "Hassan", "Usman", "Sadiq", "Tijani", "Bakare", "Oladipo", "Olaniyan",
        "Famuyiwa", "Adegoke", "Ilori", "Onwudiwe", "Nwankwo", "Ibekwe", "Emenike", "Uchenna", "Eboh",
        "Ibe", "Nwoye", "Obiora", "Ogbu", "Okoye", "Oparaji", "Dike", "Egbuna", "Chukwuma", "Ofoegbu",
        "Ayodele", "Akande", "Ogunbanjo", "Olaoye", "Oyewole", "Adesina", "Adeniyi", "Ajibola",
        "Osagie", "Idowu", "Edet", "Effiong", "Bassey", "Etim", "Udoh", "Okon", "Ekanem", "Inyang",
        "Yahaya", "Shehu", "Waziri", "Dantata", "Kolo", "Maiduguri", "Zubairu", "Sanusi", "Gambo"
    )

    private val DOMAINS = listOf(
        "gmail.com", "gmail.com", "gmail.com", "gmail.com", "yahoo.com", "yahoo.com",
        "outlook.com", "hotmail.com", "icloud.com"
    )

    // Real Nigerian mobile starts (MTN, Airtel, Glo, 9mobile).
    private val PREFIXES = listOf(
        "0803", "0806", "0810", "0813", "0814", "0816", "0703", "0706", "0802", "0805", "0807",
        "0808", "0811", "0812", "0815", "0701", "0708", "0809", "0817", "0818", "0901", "0902",
        "0903", "0904", "0905", "0906", "0907", "0908", "0909", "0912"
    )

    private fun digitsOf(text: String): String = text.filter { it.isDigit() }

    private fun uniqueName(taken: Set<String>, random: Random): String {
        repeat(60) {
            val name = FIRST.random(random) + " " + LAST.random(random)
            if (name.lowercase() !in taken) return name
        }
        for (letter in 'A'..'Z') {
            repeat(30) {
                val name = FIRST.random(random) + " " + letter + ". " + LAST.random(random)
                if (name.lowercase() !in taken) return name
            }
        }
        var number = 2
        var candidate: String
        do {
            candidate = FIRST.random(random) + " " + LAST.random(random) + " " + number
            number += 1
        } while (candidate.lowercase() in taken)
        return candidate
    }

    private fun uniqueEmail(name: String, taken: Set<String>, random: Random): String {
        val parts = name.lowercase().split(" ")
            .map { part -> part.filter { it.isLetter() } }
            .filter { it.length > 1 }
        val first = parts.firstOrNull() ?: "user"
        val last = parts.lastOrNull() ?: "name"
        repeat(40) {
            val local = when (random.nextInt(4)) {
                0 -> "$first.$last"
                1 -> "$first$last"
                2 -> "$last.$first"
                else -> "$first.$last" + random.nextInt(10, 100)
            }
            val email = local + "@" + DOMAINS.random(random)
            if (email !in taken) return email
        }
        var candidate: String
        do {
            candidate = "$first.$last" + random.nextInt(100, 10000) + "@" + DOMAINS.random(random)
        } while (candidate in taken)
        return candidate
    }

    private fun uniquePhone(taken: Set<String>, random: Random): String {
        var candidate: String
        do {
            candidate = PREFIXES.random(random) + random.nextInt(0, 10_000_000).toString().padStart(7, '0')
        } while (candidate in taken)
        return candidate
    }

    /**
     * Makes [count] new Free users. Names, emails and phone numbers are checked against every user
     * in every app, and against each other, so none repeats.
     */
    fun make(count: Int, ledger: Ledger, now: Long, dayStart: Long, random: Random): List<AppUser> {
        val names = HashSet<String>()
        val emails = HashSet<String>()
        val phones = HashSet<String>()
        for (app in ledger.apps) {
            for (user in app.users) {
                names.add(user.name.trim().lowercase())
                emails.add(user.email.trim().lowercase())
                val digits = digitsOf(user.phone)
                if (digits.isNotEmpty()) phones.add(digits)
            }
        }
        val result = ArrayList<AppUser>()
        repeat(count) {
            val name = uniqueName(names, random)
            names.add(name.lowercase())
            val email = uniqueEmail(name, emails, random)
            emails.add(email)
            val phone = uniquePhone(phones, random)
            phones.add(phone)
            result.add(
                AppUser(
                    name = name,
                    email = email,
                    phone = phone,
                    plan = Plan.FREE,
                    addedMillis = dayStart + random.nextLong(max(1L, now - dayStart)),
                    simulated = true
                )
            )
        }
        return result
    }
}
