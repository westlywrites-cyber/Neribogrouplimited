package com.westly.opay.feature.statement

import java.util.concurrent.TimeUnit

/** The list rules of the download records. Pure, so they can be unit tested without Android classes. */
object StatementRecordLists {
    val KEEP_MILLIS: Long = TimeUnit.DAYS.toMillis(30)

    /** The records no older than 30 days, newest first. */
    fun fresh(records: List<StatementRecord>, nowMillis: Long): List<StatementRecord> =
        records.filter { nowMillis - it.requestedAtMillis <= KEEP_MILLIS }
            .sortedByDescending { it.requestedAtMillis }

    /** The records that are older than 30 days and must be deleted with their files. */
    fun expired(records: List<StatementRecord>, nowMillis: Long): List<StatementRecord> =
        records.filter { nowMillis - it.requestedAtMillis > KEEP_MILLIS }

    /** [record] added, or replacing the record with the same id; newest first. */
    fun upsert(records: List<StatementRecord>, record: StatementRecord): List<StatementRecord> =
        (records.filter { it.id != record.id } + record).sortedByDescending { it.requestedAtMillis }
}
