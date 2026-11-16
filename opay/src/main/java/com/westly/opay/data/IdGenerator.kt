package com.westly.opay.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.random.Random

object IdGenerator {
    private val zone: ZoneId = ZoneId.of("Africa/Lagos")
    private val txStamp: DateTimeFormatter = DateTimeFormatter.ofPattern("MMddHHmmss")
    private val sessionStamp: DateTimeFormatter = DateTimeFormatter.ofPattern("yyMMddHHmmss")

    fun txNumber(nowMillis: Long = System.currentTimeMillis(), random: Random = Random.Default): String {
        val stamp = txStamp.format(Instant.ofEpochMilli(nowMillis).atZone(zone))
        return "26" + stamp + randomDigits(12, random)
    }

    fun sessionId(nowMillis: Long = System.currentTimeMillis(), random: Random = Random.Default): String {
        val stamp = sessionStamp.format(Instant.ofEpochMilli(nowMillis).atZone(zone))
        return "100004" + stamp + randomDigits(12, random)
    }

    fun shortId(): String = UUID.randomUUID().toString().replace("-", "").take(12)

    private fun randomDigits(count: Int, random: Random): String {
        val builder = StringBuilder(count)
        repeat(count) { builder.append(random.nextInt(10)) }
        return builder.toString()
    }
}
