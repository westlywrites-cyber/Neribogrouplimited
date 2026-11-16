package com.westly.opay.feature.statement

import com.westly.opay.core.util.maskAccount
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Text helpers of the statement feature. Pure, so they can be unit tested. */
object StatementFormat {
    val ZONE: ZoneId = ZoneId.of("Africa/Lagos")

    private val tableTime = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss", Locale.US)
    private val tableDate = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.US)
    private val fieldDate = DateTimeFormatter.ofPattern("dd MMM, yyyy", Locale.US)
    private val monthName = DateTimeFormatter.ofPattern("MMM", Locale.US)
    private val requestReceivedTime = DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm:ss", Locale.US)
    private val clockTime = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US)
    private val fileStamp = DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.US)
    private val nonAlphanumeric = Regex("[^A-Za-z0-9]+")

    /** `801****678`: first three digits, stars, last three digits. */
    fun maskedAccount(account: String): String = maskAccount(account)

    fun ordinalDay(day: Int): String {
        val suffix = if (day % 100 in 11..13) {
            "th"
        } else {
            when (day % 10) {
                1 -> "st"
                2 -> "nd"
                3 -> "rd"
                else -> "th"
            }
        }
        return "$day$suffix"
    }

    private fun zoned(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZONE)

    /** `04 Jan 2026 23:27:24`, used for Trans. Time and for "Generated on". */
    fun tableTime(millis: Long): String = zoned(millis).format(tableTime)

    /** `04 Jan 2026`, used for Value Date. */
    fun tableDate(millis: Long): String = zoned(millis).format(tableDate)

    /** `04 Oct, 2026`, shown in the date fields. */
    fun fieldDate(date: LocalDate): String = date.format(fieldDate)

    /** `Nov 4, 2026 19:29:29`, shown as "Request Received". */
    fun requestReceived(millis: Long): String = zoned(millis).format(requestReceivedTime)

    /** `Nov 4th, 2026 19:29:29`, shown as "Requested on". */
    fun requestedOn(millis: Long): String {
        val time = zoned(millis)
        return time.format(monthName) + " " + ordinalDay(time.dayOfMonth) + ", " + time.year + " " +
            time.format(clockTime)
    }

    /** `01 Jan 2026 - 03 Nov 2026`. */
    fun periodText(period: StatementPeriod): String =
        period.start.format(tableDate) + " - " + period.end.format(tableDate)

    /** `1,161,965.89`: the amount without the naira sign. */
    fun plainAmount(kobo: Long): String {
        val absolute = if (kobo < 0) -kobo else kobo
        return String.format(Locale.US, "%,d", absolute / 100) + "." +
            String.format(Locale.US, "%02d", absolute % 100)
    }

    private fun splitEmail(email: String): Pair<String, String>? {
        val at = email.indexOf('@')
        return if (at <= 0) null else email.substring(0, at) to email.substring(at)
    }

    /** Card style, `t*@example.com`: the first character, one star, then the domain. */
    fun cardEmail(email: String): String =
        splitEmail(email)?.let { (name, domain) -> name.take(1) + "*" + domain } ?: email

    /** Records style, `tes***@example.com`: up to three characters, three stars, then the domain. */
    fun recordsEmail(email: String): String =
        splitEmail(email)?.let { (name, domain) -> name.take(3) + "***" + domain } ?: email

    /**
     * Steps style, `test******ser@example.com`: for a name of eight or more characters the first four, six stars and
     * the last three; a shorter name uses the records style.
     */
    fun stepsEmail(email: String): String {
        val parts = splitEmail(email) ?: return email
        val (name, domain) = parts
        return if (name.length >= 8) name.take(4) + "******" + name.takeLast(3) + domain else recordsEmail(email)
    }

    /** `JOHN_DAVID_JOHNSON_8012345678_20261104192930.pdf`: capitals, digits and underscores only. */
    fun fileName(fullName: String, accountNumber: String, millis: Long): String {
        val name = fullName.uppercase().replace(nonAlphanumeric, "_").trim('_').ifEmpty { "STATEMENT" }
        val account = accountNumber.replace(nonAlphanumeric, "")
        return name + "_" + account + "_" + zoned(millis).format(fileStamp) + ".pdf"
    }
}
