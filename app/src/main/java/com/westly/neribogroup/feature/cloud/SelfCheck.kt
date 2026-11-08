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
import com.westly.neribogroup.cloud.LedgerSplitter
import com.westly.neribogroup.cloud.PendingBatch
import com.westly.neribogroup.cloud.PendingChange
import com.westly.neribogroup.cloud.SplitDoc
import com.westly.neribogroup.cloud.SupabaseApi
import com.westly.neribogroup.cloud.SyncData
import com.westly.neribogroup.cloud.SyncEngine
import com.westly.neribogroup.cloud.SyncStore
import com.westly.neribogroup.cloud.sha256Hex
import com.westly.neribogroup.data.Ledger
import com.westly.neribogroup.data.LedgerStorage
import com.westly.neribogroup.feature.common.DialogActions
import com.westly.neribogroup.feature.common.NeriboDialog
import com.westly.neribogroup.feature.common.TintedButton
import org.json.JSONArray
import org.json.JSONObject

/** One line of the self-check: what was tested, PASS or FAIL, and a short detail. */
data class CheckLine(val name: String, val pass: Boolean, val detail: String)

data class SelfCheckResult(val lines: List<CheckLine>, val largestDocBytes: Int)

/** Runs the 3A checks on the real ledger. Items 1 to 6 use no network; 7 and 8 only when [online]. */
object SelfCheck {

    /** SHA-256 of the UTF-8 bytes of "₦ test", computed with Python's hashlib (see the Decisions). */
    const val EXPECTED_NAIRA_TEST_SHA =
        "9fe2998e95bc699699bb9f53c2b746af8b5dc52da77c2c5c284224d64d25d8d2"

    fun run(ledger: Ledger, api: SupabaseApi?, sync: SyncData?, online: Boolean): SelfCheckResult {
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
        return SelfCheckResult(lines, largest)
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
    NeriboDialog(
        onDismiss = onClose,
        title = "Self-check",
        subtitle = if (failures == 0) "Everything passed." else "$failures check(s) failed."
    ) {
        for (line in lines) {
            Row(modifier = Modifier.padding(vertical = 4.dp)) {
                Text(
                    text = if (line.pass) "PASS" else "FAIL",
                    fontWeight = FontWeight.Bold,
                    color = if (line.pass) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
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
