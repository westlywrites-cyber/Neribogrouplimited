package com.westly.opay.feature.statement

import android.graphics.BitmapFactory
import com.westly.opay.OPayApplication
import com.westly.opay.R
import com.westly.opay.backend.BackendCodes
import com.westly.opay.backend.BackendError
import com.westly.opay.backend.BackendResult
import com.westly.opay.backend.PostgrestStatementGateway
import com.westly.opay.backend.SendStatementRequest
import com.westly.opay.backend.SessionState
import java.io.File
import java.io.IOException
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** A failure whose message is already the text to show. */
private class StatementFailure(val text: String) : Exception(text)

/**
 * Builds and emails the statements. It lives as long as the app process, so leaving a screen never stops a statement
 * that is being made or sent. It keeps the records of the signed-in user and deletes them, with their files and the
 * saved statement email, on sign-out.
 */
class StatementEngine private constructor(private val app: OPayApplication) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val store = StatementStore(app)
    private val gateway = PostgrestStatementGateway(app.backend.provider, app.backend::isOnline)
    private val mutex = Mutex()

    /** The email each user chose for statements. Reads and writes the encrypted store: not for the main thread. */
    val emails = StatementEmailStore(app.backend.secureStore)

    private val _records = MutableStateFlow<List<StatementRecord>>(emptyList())
    val records: StateFlow<List<StatementRecord>> = _records.asStateFlow()

    private val _currentId = MutableStateFlow<String?>(null)

    /** The request the Generating page follows. */
    val currentId: StateFlow<String?> = _currentId.asStateFlow()

    private val _failures = MutableStateFlow<Map<String, String>>(emptyMap())

    /** The message of each request that failed during this run of the app, by record id. */
    val failures: StateFlow<Map<String, String>> = _failures.asStateFlow()

    private val _resending = MutableStateFlow<Set<String>>(emptySet())

    /** The ids of the records whose email is being sent again right now. */
    val resending: StateFlow<Set<String>> = _resending.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** One short message each time a "Resend Email" ends. */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    @Volatile
    private var userId = ""

    init {
        scope.launch {
            app.backend.session.sessionState.collect { state ->
                when (state) {
                    is SessionState.SignedIn -> signedIn(state.userId)
                    is SessionState.SignedOut -> signedOut()
                    is SessionState.Loading -> Unit
                }
            }
        }
    }

    private suspend fun signedIn(id: String) = mutex.withLock {
        if (id.isEmpty() || id == userId) return@withLock
        userId = id
        val stored = store.load(id).map {
            if (it.status == StatementStatus.Processing) it.copy(status = StatementStatus.Failed) else it
        }
        _records.value = stored
        pruneLocked()
    }

    private suspend fun signedOut() = mutex.withLock {
        val old = userId
        if (old.isEmpty()) return@withLock
        userId = ""
        _records.value = emptyList()
        _currentId.value = null
        _failures.value = emptyMap()
        _resending.value = emptySet()
        emails.clear(old)
        store.deleteUser(old)
    }

    /** Deletes the records older than 30 days with their files. */
    fun purge() {
        scope.launch { mutex.withLock { pruneLocked() } }
    }

    private suspend fun pruneLocked() {
        val user = userId
        if (user.isEmpty()) return
        val now = System.currentTimeMillis()
        val all = _records.value
        StatementRecordLists.expired(all, now).forEach { if (it.filePath.isNotEmpty()) File(it.filePath).delete() }
        _records.value = StatementRecordLists.fresh(all, now)
        store.save(user, _records.value)
    }

    private fun currentUser(): String = userId.ifEmpty { app.repository.profile.value.id }

    /** Keeps [email] as the address statements are sent to. It finishes even if the screen is left at once. */
    fun saveEmail(email: String) {
        val user = currentUser()
        scope.launch { emails.save(user, email) }
    }

    /** Starts one statement and returns the id of its record. The work continues when the screen is left. */
    fun start(request: StatementRequest): String {
        val id = UUID.randomUUID().toString()
        val user = currentUser()
        val record = StatementRecord(
            id = id,
            accountNumber = request.accountNumber,
            startDate = request.period.start.toString(),
            endDate = request.period.end.toString(),
            requestedAtMillis = request.requestedAtMillis,
            status = StatementStatus.Processing,
            fileName = StatementFormat.fileName(request.holderName, request.accountNumber, request.requestedAtMillis),
            sendTo = StatementFormat.recordsEmail(request.email),
            sendToSteps = StatementFormat.stepsEmail(request.email)
        )
        _currentId.value = id
        _records.update { StatementRecordLists.upsert(it, record) }
        scope.launch {
            persist(user)
            val finished = process(user, request, record)
            if (userId != user) {
                if (finished.filePath.isNotEmpty()) File(finished.filePath).delete()
                return@launch
            }
            _records.update { StatementRecordLists.upsert(it, finished) }
            persist(user)
        }
        return id
    }

    /**
     * Sends the statement of [recordId] again, to the email saved now, with the PDF kept on the phone. It runs on the
     * engine's own scope, so it finishes when the screen is left. The result arrives on [messages].
     */
    fun resend(recordId: String) {
        if (recordId in _resending.value) return
        val user = currentUser()
        _resending.update { it + recordId }
        scope.launch {
            val problem = try {
                redeliver(user, recordId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                StatementErrors.SEND_FAILED
            } finally {
                _resending.update { it - recordId }
            }
            _messages.tryEmit(problem ?: StatementErrors.RESENT)
        }
    }

    private suspend fun persist(user: String) = mutex.withLock { store.save(user, _records.value) }

    /** Builds the PDF and emails it. The record that comes back is Sent, or Failed with its PDF kept when it exists. */
    private suspend fun process(user: String, request: StatementRequest, record: StatementRecord): StatementRecord {
        var current = record
        return try {
            current = record.copy(filePath = generate(user, request, record))
            val file = File(current.filePath)
            when {
                file.length() > MAX_PDF_BYTES -> failed(current, StatementErrors.TOO_LARGE)
                !gateway.isOnline() -> failed(current, StatementErrors.NO_INTERNET)
                else -> deliver(request.email, current, file)?.let { failed(current, it) }
                    ?: current.copy(status = StatementStatus.Sent)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: StatementFailure) {
            failed(current, e.text)
        } catch (e: BackendError) {
            failed(current, if (e.code == BackendCodes.NETWORK) StatementErrors.NO_INTERNET else StatementErrors.GENERATE_FAILED)
        } catch (e: Exception) {
            failed(current, StatementErrors.GENERATE_FAILED)
        }
    }

    private fun failed(record: StatementRecord, text: String): StatementRecord {
        _failures.update { it + (record.id to text) }
        return record.copy(status = StatementStatus.Failed)
    }

    /** Builds the PDF in the user's folder and returns its path. */
    private suspend fun generate(user: String, request: StatementRequest, record: StatementRecord): String {
        if (!gateway.isOnline()) throw StatementFailure(StatementErrors.NO_INTERNET)
        val zone = StatementFormat.ZONE
        val from = request.period.start.atStartOfDay(zone).toInstant()
        val to = request.period.end.plusDays(1).atStartOfDay(zone).toInstant()
        val rows = when (val result = gateway.rowsBetween(from, to)) {
            is BackendResult.Success -> result.value
            is BackendResult.Failure -> throw result.error
        }
        val source = StatementSource(
            inPeriod = rows.inPeriod.mapNotNull(StatementBuilder::toStatementTx),
            before = rows.before?.let(StatementBuilder::toStatementTx),
            after = rows.after?.let(StatementBuilder::toStatementTx),
            currentBalanceKobo = app.repository.balanceKobo.value
        )
        val data = StatementBuilder.build(request, source, System.currentTimeMillis())
        val dir = store.userDir(user)
        if (!dir.exists() && !dir.mkdirs()) throw IOException("storage")
        val target = File(dir, record.fileName)
        val part = File(dir, record.fileName + ".part")
        val logo = BitmapFactory.decodeResource(app.resources, R.drawable.opay_logo_full)
        try {
            StatementPdfWriter(logo).write(data, part)
            if (!part.renameTo(target)) throw IOException("storage")
        } finally {
            logo?.recycle()
            part.delete()
        }
        return target.absolutePath
    }

    /** Emails the PDF through `send-statement`. Returns null when it was sent, otherwise the message to show. */
    private suspend fun deliver(email: String, record: StatementRecord, file: File): String? {
        val pdf = withContext(Dispatchers.IO) { Base64.getEncoder().encodeToString(file.readBytes()) }
        val request = SendStatementRequest(
            email = email,
            pdfBase64 = pdf,
            periodLabel = StatementFormat.periodText(record.period)
        )
        return when (val result = app.backend.edge.sendStatement(request)) {
            is BackendResult.Success -> null
            is BackendResult.Failure -> StatementErrors.messageFor(result.error)
        }
    }

    /** The work of [resend]: null when the email was sent and the record is Sent, otherwise the message to show. */
    private suspend fun redeliver(user: String, recordId: String): String? {
        val record = _records.value.firstOrNull { it.id == recordId } ?: return StatementErrors.FILE_MISSING
        val file = File(record.filePath)
        if (record.filePath.isEmpty() || !file.exists()) return StatementErrors.FILE_MISSING
        val email = withContext(Dispatchers.IO) { emails.get(user) }
        if (!StatementEmail.isValid(email)) return StatementErrors.INVALID_EMAIL
        if (file.length() > MAX_PDF_BYTES) return StatementErrors.TOO_LARGE
        if (!gateway.isOnline()) return StatementErrors.NO_INTERNET
        val problem = deliver(email, record, file)
        if (problem != null) return problem
        if (userId == user) {
            val sent = record.copy(
                status = StatementStatus.Sent,
                sendTo = StatementFormat.recordsEmail(email),
                sendToSteps = StatementFormat.stepsEmail(email)
            )
            _records.update { StatementRecordLists.upsert(it, sent) }
            persist(user)
        }
        return null
    }

    companion object {
        /** A PDF larger than this is never sent: the function accepts at most 5 MB. */
        private const val MAX_PDF_BYTES = 4L * 1024 * 1024

        @Volatile
        private var instance: StatementEngine? = null

        fun get(app: OPayApplication): StatementEngine =
            instance ?: synchronized(this) { instance ?: StatementEngine(app).also { instance = it } }
    }
}
