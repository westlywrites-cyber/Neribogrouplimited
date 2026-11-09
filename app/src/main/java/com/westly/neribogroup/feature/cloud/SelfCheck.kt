package com.westly.neribogroup.feature.cloud

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.westly.neribogroup.cloud.ApiResult
import com.westly.neribogroup.cloud.CloudStatus
import com.westly.neribogroup.cloud.DocState
import com.westly.neribogroup.cloud.LedgerSplitter
import com.westly.neribogroup.cloud.PendingBatch
import com.westly.neribogroup.cloud.PendingChange
import com.westly.neribogroup.cloud.SplitDoc
import com.westly.neribogroup.cloud.SupabaseApi
import com.westly.neribogroup.cloud.SyncConflict
import com.westly.neribogroup.cloud.SyncData
import com.westly.neribogroup.cloud.SyncDecision
import com.westly.neribogroup.cloud.SyncEngine
import com.westly.neribogroup.cloud.SyncRules
import com.westly.neribogroup.cloud.SyncStore
import com.westly.neribogroup.cloud.sha256Hex
import com.westly.neribogroup.cloud.AiJobRules
import com.westly.neribogroup.data.AppData
import com.westly.neribogroup.data.AppUser
import com.westly.neribogroup.data.DAY_MILLIS
import com.westly.neribogroup.data.DiceEngine
import com.westly.neribogroup.data.DiceKind
import com.westly.neribogroup.data.IdentityGenerator
import com.westly.neribogroup.data.Ledger
import com.westly.neribogroup.data.Plan
import com.westly.neribogroup.data.PresetIdentity
import com.westly.neribogroup.data.PresetRoll
import com.westly.neribogroup.data.SOFTWARE_APP_ID
import com.westly.neribogroup.data.LedgerStorage
import com.westly.neribogroup.feature.common.DialogActions
import com.westly.neribogroup.feature.common.NeriboDialog
import com.westly.neribogroup.feature.common.TintedButton
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/**
 * One line of the self-check: what was tested, PASS or FAIL, and a short detail.
 * A [skipped] line (needs internet) counts as neither: it is shown as SKIPPED and is never a failure.
 */
data class CheckLine(val name: String, val pass: Boolean, val detail: String, val skipped: Boolean = false)

data class SelfCheckResult(val lines: List<CheckLine>, val largestDocBytes: Int)

/**
 * Runs the checks on the real ledger. Items 1 to 6 and 9 to 10 use no network; 7, 8 and 11 only when [online].
 * [syncStatus] is how the Sync now that ran just before the check ended (null when none ran).
 */
object SelfCheck {

    /** SHA-256 of the UTF-8 bytes of "₦ test", computed with Python's hashlib (see the Decisions). */
    const val EXPECTED_NAIRA_TEST_SHA =
        "9fe2998e95bc699699bb9f53c2b746af8b5dc52da77c2c5c284224d64d25d8d2"

    fun run(
        ledger: Ledger,
        api: SupabaseApi?,
        sync: SyncData?,
        online: Boolean,
        syncStatus: CloudStatus? = null
    ): SelfCheckResult {
        val lines = ArrayList<CheckLine>()
        var largest = 0

        var text = ""
        var docs: List<SplitDoc> = emptyList()
        var joinedLedger: Ledger? = null
        try {
            text = LedgerStorage.toJson(ledger)
            docs = LedgerSplitter.split(text)
            for (d in docs) {
                val size = d.text.toByteArray(Charsets.UTF_8).size
                if (size > largest) largest = size
            }
            val joinedText = LedgerSplitter.join(docs.associate { it.key to it.text })
            joinedLedger = LedgerStorage.fromJson(joinedText)
        } catch (e: Throwable) {
            // Items below report the failure.
        }
        val textNow = text
        val docsNow = docs
        val back = joinedLedger
        val largestNow = largest

        // 1
        lines.add(
            runCheck("1. Split, join and save again gives the same ledger") {
                if (back == null) {
                    Pair(false, "Could not split and join the ledger")
                } else {
                    val again = LedgerStorage.toJson(back)
                    Pair(again == textNow, if (again == textNow) "Identical" else "The texts differ")
                }
            }
        )

        // 2
        lines.add(
            runCheck("2. Splitting twice gives the same hashes") {
                val second = LedgerSplitter.split(textNow)
                val same = second.size == docsNow.size &&
                    second.indices.all { second[it].key == docsNow[it].key && second[it].sha256 == docsNow[it].sha256 }
                Pair(same, docsNow.size.toString() + " documents")
            }
        )

        // 3
        lines.add(
            runCheck("3. Document names and JSON are valid") {
                var bad = 0
                for (d in docsNow) {
                    if (!LedgerSplitter.KEY_REGEX.matches(d.key)) bad++
                    try {
                        JSONObject(d.text)
                    } catch (e: Exception) {
                        bad++
                    }
                }
                val kb = (largestNow + 1023) / 1024
                Pair(bad == 0 && docsNow.isNotEmpty(), "Largest document: " + kb + " KB")
            }
        )

        // 4
        lines.add(
            runCheck("4. SHA-256 of a known text") {
                val got = sha256Hex("₦ test")
                Pair(got == EXPECTED_NAIRA_TEST_SHA, if (got == EXPECTED_NAIRA_TEST_SHA) "Matches" else "Different")
            }
        )

        // 5
        lines.add(
            runCheck("5. Outbox batch survives save and load") {
                val payload = docsNow.firstOrNull()?.text ?: "{\"x\":\"₦ 🎉\"}"
                val extra = "{\"note\":\"₦ 🎉 5000000000 12.5\"}"
                val batch = PendingBatch(
                    opId = "00000000-0000-0000-0000-000000000001",
                    mode = "initial",
                    changes = listOf(
                        PendingChange("company", 0, payload, sha256Hex(payload), false),
                        PendingChange("test", 0, extra, sha256Hex(extra), false)
                    ),
                    createdMillis = 1L
                )
                val round = SyncStore.fromJson(SyncStore.toJson(SyncData(deviceId = "test", pending = batch)))
                val p = round.pending
                val same = p != null && p.opId == batch.opId && p.mode == batch.mode &&
                    p.changes.size == batch.changes.size &&
                    p.changes.indices.all {
                        p.changes[it].payloadSha256 == batch.changes[it].payloadSha256 &&
                            p.changes[it].payload == batch.changes[it].payload &&
                            sha256Hex(p.changes[it].payload) == p.changes[it].payloadSha256
                    }
                Pair(same, if (same) "Same opId and same hashes" else "Different after reload")
            }
        )

        // 6
        lines.add(
            runCheck("6. Counts match after join") {
                if (back == null) {
                    Pair(false, "Could not join")
                } else {
                    val a = counts(ledger)
                    val b = counts(back)
                    Pair(a == b, "apps/users/cases/offers/staff: " + a + (if (a == b) "" else " vs " + b))
                }
            }
        )

        if (online && api != null && sync != null) {
            // 7
            lines.add(
                runCheck("7. The cloud answers") {
                    when (val r = api.rpc(SyncEngine.Contract.RPC_STATUS, JSONObject())) {
                        is ApiResult.Ok -> Pair(true, "initialized=" + r.json.optBoolean("initialized", false))
                        ApiResult.Offline -> Pair(false, "No internet")
                        ApiResult.AuthExpired -> Pair(false, "Please sign in again")
                        is ApiResult.Http -> Pair(false, "Cloud problem (code " + r.code + ")")
                        is ApiResult.AppError -> Pair(false, "Cloud problem (" + r.code + ")")
                    }
                }
            )
            // 8
            lines.add(
                runCheck("8. The cloud holds what was uploaded") {
                    val expect = JSONArray()
                    var count = 0
                    for ((key, state) in sync.docs) {
                        if (state.deleted) continue
                        val o = JSONObject()
                        o.put("doc_key", key)
                        o.put("version", state.version)
                        o.put("payload_sha256", state.sha256)
                        expect.put(o)
                        count++
                    }
                    if (count == 0) {
                        Pair(false, "This phone has no uploaded documents yet")
                    } else {
                        val body = JSONObject()
                        body.put(SyncEngine.Contract.P_EXPECT, expect)
                        when (val r = api.rpc(SyncEngine.Contract.RPC_VERIFY, body, true)) {
                            is ApiResult.Ok -> {
                                val ok = r.json.optBoolean("all_match", false)
                                Pair(ok, if (ok) count.toString() + " documents match" else "Some documents do not match")
                            }
                            ApiResult.Offline -> Pair(false, "No internet")
                            ApiResult.AuthExpired -> Pair(false, "Please sign in again")
                            is ApiResult.Http -> Pair(false, "Cloud problem (code " + r.code + ")")
                            is ApiResult.AppError -> Pair(false, "Cloud problem (" + r.code + ")")
                        }
                    }
                }
            )
            // Information only: edits made after the first upload wait for the next update.
            val changed = docsNow.count { d ->
                val known = sync.docs[d.key]
                known == null || known.sha256 != d.sha256
            }
            lines.add(
                CheckLine(
                    "Changes since the first upload",
                    true,
                    if (changed == 0) "None" else changed.toString() + " document(s) will be copied by the next update"
                )
            )
        }

        // ---- Phase 3B items ----

        // 9a, 9b, 9c: the conflict rule, on in-memory numbers
        lines.add(
            runCheck("9a. Phone changed and cloud newer: it asks you") {
                val d = SyncRules.decide(true, 3, 4)
                Pair(d == SyncDecision.CONFLICT, "Result: " + d.name)
            }
        )
        lines.add(
            runCheck("9b. Phone not changed and cloud newer: it takes the cloud") {
                val d = SyncRules.decide(false, 3, 4)
                Pair(d == SyncDecision.APPLY, "Result: " + d.name)
            }
        )
        lines.add(
            runCheck("9c. Phone not changed and same version: it does nothing") {
                val d = SyncRules.decide(false, 4, 4)
                Pair(d == SyncDecision.NOTHING, "Result: " + d.name)
            }
        )

        // 10: unsent changes are still the same after the app is updated and restarted
        lines.add(
            runCheck("10. Unsent changes survive an app update") {
                val p1 = "{\"a\":\"₦ 🎉 5000000000 12.5\"}"
                val p2 = "{\"deleted\":true}"
                val batch = PendingBatch(
                    opId = "00000000-0000-0000-0000-0000000000a2",
                    mode = "normal",
                    changes = listOf(
                        PendingChange("company", 4, p1, sha256Hex(p1), false),
                        PendingChange("app:test", 2, p2, sha256Hex(p2), true)
                    ),
                    createdMillis = 2L
                )
                val data = SyncData(
                    deviceId = "test",
                    lastSeq = 7L,
                    initialized = true,
                    docs = mapOf("company" to DocState(4, sha256Hex(p1))),
                    pending = batch,
                    conflicts = listOf("{\"key\":\"cases\"}")
                )
                val written = SyncStore.toJson(data)
                val read = SyncStore.fromJson(written)
                val p = read.pending
                val same = p != null && p.opId == batch.opId && p.mode == batch.mode &&
                    p.changes.size == batch.changes.size &&
                    p.changes.indices.all { i ->
                        p.changes[i].docKey == batch.changes[i].docKey &&
                            p.changes[i].baseVersion == batch.changes[i].baseVersion &&
                            p.changes[i].deleted == batch.changes[i].deleted &&
                            p.changes[i].payloadSha256 == batch.changes[i].payloadSha256 &&
                            sha256Hex(p.changes[i].payload) == batch.changes[i].payloadSha256
                    } &&
                    read.lastSeq == 7L && SyncStore.toJson(read) == written
                Pair(same, if (same) "Same opId, same payload hashes" else "Different after reload")
            }
        )

        // 11: after Sync now, the cloud proves it holds every current document (internet needed)
        val verifyName = "11. After Sync now, the cloud holds every document"
        if (online && api != null && sync != null && syncStatus == CloudStatus.UP_TO_DATE) {
            val open = HashSet<String>()
            for (t in sync.conflicts) {
                val c = SyncConflict.fromJsonText(t)
                if (c != null && !c.handled) open.add(c.key)
            }
            val expect = JSONArray()
            var count = 0
            for (d in docsNow) {
                if (open.contains(d.key)) continue
                val state = sync.docs[d.key] ?: continue
                // A document edited after the sync, or removed, is not compared.
                if (state.deleted || state.sha256 != d.sha256) continue
                val o = JSONObject()
                o.put("doc_key", d.key)
                o.put("version", state.version)
                o.put("payload_sha256", d.sha256)
                expect.put(o)
                count++
            }
            if (count == 0) {
                lines.add(CheckLine(verifyName, true, "(no synced documents to compare yet)", skipped = true))
            } else {
                lines.add(
                    runCheck(verifyName) {
                        val body = JSONObject()
                        body.put(SyncEngine.Contract.P_EXPECT, expect)
                        when (val r = api.rpc(SyncEngine.Contract.RPC_VERIFY, body, true)) {
                            is ApiResult.Ok -> {
                                val ok = r.json.optBoolean("all_match", false)
                                Pair(ok, if (ok) count.toString() + " documents match" else "Some documents do not match")
                            }
                            ApiResult.Offline -> Pair(false, "No internet")
                            ApiResult.AuthExpired -> Pair(false, "Please sign in again")
                            is ApiResult.Http -> Pair(false, "Cloud problem (code " + r.code + ")")
                            is ApiResult.AppError -> Pair(false, "Cloud problem (" + r.code + ")")
                        }
                    }
                )
            }
        } else {
            val reason = if (online && syncStatus != null && syncStatus != CloudStatus.WAITING_INTERNET) {
                "(the sync did not finish)"
            } else {
                "(needs internet)"
            }
            lines.add(CheckLine(verifyName, true, reason, skipped = true))
        }

        lines.addAll(aiChecks(ledger))
        lines.addAll(CaseSelfCheck.run())
        lines.addAll(SoftwareSelfCheck.run())
        return SelfCheckResult(lines, largest)
    }

    // ---- Phase 4B items (no network) ----

    private val UUID_TEXT = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

    private fun aiChecks(ledger: Ledger): List<CheckLine> {
        val lines = ArrayList<CheckLine>()
        val now = System.currentTimeMillis()
        val real = ledger.apps.firstOrNull { it.googlePlayEnabled && it.id != SOFTWARE_APP_ID }
        val testApp: AppData = real?.copy(lastRollDayKey = "") ?: AppData(
            id = "selfcheck-app",
            name = "Test app",
            premiumPrice = 1000L,
            proPrice = 3000L,
            adsEnabled = true,
            googlePlayEnabled = true,
            users = (1..40).map { i ->
                AppUser(
                    id = "t$i",
                    name = "Test User $i",
                    email = "test$i@example.com",
                    plan = if (i > 30) Plan.PREMIUM else Plan.FREE,
                    addedMillis = now - 10L * DAY_MILLIS,
                    planExpiryMillis = if (i > 30) now - 2L * DAY_MILLIS else 0L,
                    planStartMillis = if (i > 30) now - 32L * DAY_MILLIS else 0L,
                    simulated = true
                )
            }
        )
        val base: Ledger = if (real != null) {
            ledger.copy(apps = ledger.apps.map { if (it.id == real.id) testApp else it })
        } else {
            Ledger(apps = listOf(testApp))
        }

        lines.add(
            runCheck("12. A roll with Claude's faces equals the normal roll") {
                val visible = DiceEngine.visibleDice(testApp, now)
                val plain = DiceEngine.roll(base, testApp.id, now, Random(42), null)
                val drawn = DiceEngine.drawFaces(Random(42), visible)
                val preset = PresetRoll(IntArray(DiceKind.values().size) { drawn[it] }, emptyList())
                val withPreset = DiceEngine.roll(base, testApp.id, now, Random(42), preset)
                val a = UUID_TEXT.replace(LedgerStorage.toJson(plain), "ID")
                val b = UUID_TEXT.replace(LedgerStorage.toJson(withPreset), "ID")
                val rolled = plain !== base
                Pair(rolled && a == b, if (a == b) (if (rolled) "Identical" else "Roll was not possible") else "The texts differ")
            }
        )

        lines.add(
            runCheck("13. Names from Claude are checked") {
                val domains = IdentityGenerator.DEFAULT_AI_DOMAINS
                val none = emptySet<String>()
                fun accept(n: String, e: String, p: String, names: Set<String> = none): PresetIdentity? =
                    IdentityGenerator.acceptAiIdentity(PresetIdentity(n, e, p), names, none, none, domains)
                val good = accept("Chinedu Okafor", "chinedu.okafor@example.com", "+2348031234567")
                val goodOk = good != null && good.phone == "08031234567"
                val digits = accept("Chinedu 2", "chinedu@example.com", "08031234567") == null
                val upper = accept("Chinedu Okafor", "Chinedu@example.com", "08031234567") == null
                val domain = accept("Chinedu Okafor", "chinedu@gmail.com", "08031234567") == null
                val dup = accept("Chinedu Okafor", "chinedu@example.com", "08031234567", setOf("chinedu okafor")) == null
                val badPhone = accept("Ngozi Eze", "ngozi@example.com", "12345")?.phone == ""
                val longName = accept("A".repeat(70), "a@example.com", "") == null
                val longEmail = accept("Ngozi Eze", "a".repeat(90) + "@example.com", "") == null
                val all = goodOk && digits && upper && domain && dup && badPhone && longName && longEmail
                Pair(all, if (all) "8 of 8 cases right" else "A case failed")
            }
        )

        lines.add(
            runCheck("14. A group of jobs is ready at the right time") {
                val ok = AiJobRules.groupReady(listOf("submitted", "submitted")) &&
                    !AiJobRules.groupReady(listOf("submitted", "pending")) &&
                    AiJobRules.groupReady(listOf("submitted", "rejected")) &&
                    !AiJobRules.groupReady(listOf("claimed"))
                Pair(ok, if (ok) "All four cases right" else "A case failed")
            }
        )

        lines.add(
            runCheck("15. The dice jobs are well formed") {
                val jobs = AiJobRules.buildJobs(testApp, 0, now)
                var problems = 0
                for (job in jobs) {
                    val ctx = JSONObject(job.getJSONObject("context").toString())
                    val schema = JSONObject(job.getJSONObject("result_schema").toString())
                    if (ctx.toString().toByteArray(Charsets.UTF_8).size > 6000) problems++
                    if (job.getString("dedupe_key").isEmpty()) problems++
                    if (ctx.getJSONArray("faceTable").length() != 6) problems++
                    if (job.getJSONObject("context").getJSONObject("dice").getString("kind") == "NEW") {
                        val max = schema.getJSONObject("properties").getJSONObject("users").getInt("maxItems")
                        if (max < 1 || max > 25) problems++
                    }
                }
                Pair(problems == 0 && jobs.isNotEmpty(), jobs.size.toString() + " jobs checked")
            }
        )
        return lines
    }

    private fun counts(l: Ledger): String {
        val users = l.apps.sumOf { it.users.size }
        return l.apps.size.toString() + "/" + users + "/" + l.cases.size + "/" +
            l.software.offers.size + "/" + l.staff.size
    }

    private fun runCheck(name: String, block: () -> Pair<Boolean, String>): CheckLine {
        return try {
            val result = block()
            CheckLine(name, result.first, result.second)
        } catch (e: Throwable) {
            CheckLine(name, false, "Error: " + e.javaClass.simpleName)
        }
    }
}

/** The result dialog: one PASS / FAIL line per check. */
@Composable
fun SelfCheckDialog(lines: List<CheckLine>, onClose: () -> Unit) {
    val failures = lines.count { !it.pass }
    val skipped = lines.count { it.skipped }
    NeriboDialog(
        onDismiss = onClose,
        title = "Self-check",
        subtitle = if (failures > 0) {
            "$failures check(s) failed."
        } else if (skipped > 0) {
            "Everything that could run passed ($skipped skipped)."
        } else {
            "Everything passed."
        }
    ) {
        for (line in lines) {
            Row(modifier = Modifier.padding(vertical = 4.dp)) {
                Text(
                    text = if (line.skipped) "SKIPPED" else if (line.pass) "PASS" else "FAIL",
                    fontWeight = FontWeight.Bold,
                    color = if (line.skipped) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else if (line.pass) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text = line.name, style = MaterialTheme.typography.bodyMedium)
                    if (line.detail.isNotEmpty()) {
                        Text(
                            text = line.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        DialogActions {
            TintedButton(text = "Close", onClick = onClose)
        }
    }
}
