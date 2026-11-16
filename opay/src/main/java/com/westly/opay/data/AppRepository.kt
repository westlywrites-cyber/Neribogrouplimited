package com.westly.opay.data

import com.westly.opay.backend.AccountResolver
import com.westly.opay.backend.BackendCodes
import com.westly.opay.backend.BackendError
import com.westly.opay.backend.BackendResult
import com.westly.opay.backend.BeneficiaryLists
import com.westly.opay.backend.FavouriteGateway
import com.westly.opay.backend.NOTIFICATIONS_LIMIT
import com.westly.opay.backend.NetworkMonitor
import com.westly.opay.backend.NotificationGateway
import com.westly.opay.backend.PayAuth
import com.westly.opay.backend.ProfileGateway
import com.westly.opay.backend.ProfileRow
import com.westly.opay.backend.PurchaseGateway
import com.westly.opay.backend.RealtimeEvent
import com.westly.opay.backend.RealtimeSource
import com.westly.opay.backend.RemoteReader
import com.westly.opay.backend.RowMapping
import com.westly.opay.backend.STATUS_ACTIVE_VALUE
import com.westly.opay.backend.SavingsGateway
import com.westly.opay.backend.SavingsPositionRow
import com.westly.opay.backend.SessionState
import com.westly.opay.backend.TRANSACTIONS_LIMIT
import com.westly.opay.backend.TransactionRow
import com.westly.opay.backend.TransferGateway
import com.westly.opay.backend.TransferRequest
import com.westly.opay.core.theme.ThemeMode
import com.westly.opay.data.model.AppNotification
import com.westly.opay.data.model.Bank
import com.westly.opay.data.model.Beneficiary
import com.westly.opay.data.model.ResolvedAccount
import com.westly.opay.data.model.Transaction
import com.westly.opay.data.model.TransferDraft
import com.westly.opay.data.model.TxType
import com.westly.opay.data.model.UserProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** The `source` the lookup answers with for a real OPay account that is not registered in this app. */
private const val SOURCE_REAL_LOOKUP = "paystack"

/**
 * Holds what the screens show. Account data is read from Supabase: once after every sign-in and again on
 * [refresh]. While signed in, [realtime] pushes changes into the same values and [network] tells when the
 * connection is lost or back. The last known values are cached with the local settings. The nickname, the theme,
 * hide-balance and the small app settings are saved in the profile: a change shows at once, is sent through
 * [profileGateway] and is put back when the save fails. After every sign-in the saved values replace the local ones.
 */
class AppRepository(
    private val store: StateStore,
    private val remote: RemoteReader,
    sessions: Flow<SessionState>,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val realtime: RealtimeSource? = null,
    private val network: NetworkMonitor? = null,
    private val resolver: AccountResolver? = null,
    private val transfers: TransferGateway? = null,
    private val favouriteGateway: FavouriteGateway? = null,
    private val notificationGateway: NotificationGateway? = null,
    private val purchaseGateway: PurchaseGateway? = null,
    private val savingsGateway: SavingsGateway? = null,
    private val profileGateway: ProfileGateway? = null,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val saveMutex = Mutex()
    private val favouriteMutex = Mutex()
    private val notificationMutex = Mutex()
    private val purchaseBusy = AtomicBoolean(false)
    private val savingsBusy = AtomicBoolean(false)
    private val settingsMutex = Mutex()
    private val pendingSettingWrites = AtomicInteger(0)
    private val lock = Any()
    private val zone: ZoneId = ZoneId.of("Africa/Lagos")

    private val loaded: PersistedState = store.loadBlocking() ?: PersistedState()

    private val _profile = MutableStateFlow(EMPTY_PROFILE)
    private val _balanceKobo = MutableStateFlow(0L)
    private val _businessNumber = MutableStateFlow<String?>(null)
    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    private val _recents = MutableStateFlow<List<Beneficiary>>(emptyList())
    private val _favourites = MutableStateFlow<List<Beneficiary>>(emptyList())
    private val _opayRecents = MutableStateFlow<List<Beneficiary>>(emptyList())
    private val _opayFavourites = MutableStateFlow<List<Beneficiary>>(emptyList())
    private val _notifications = MutableStateFlow<List<AppNotification>>(emptyList())
    private val _themeMode = MutableStateFlow(themeFromName(loaded.themeMode))
    private val _hideBalance = MutableStateFlow(loaded.hideBalance)
    private val _freeTransfersLeft = MutableStateFlow(FREE_TRANSFERS_DEFAULT)
    private val _banks = MutableStateFlow<List<Bank>>(emptyList())
    private val _banksUnavailable = MutableStateFlow(false)
    private val _savingsPositions = MutableStateFlow<List<SavingsPositionRow>>(emptyList())
    private val _appSettings = MutableStateFlow(loaded.appSettings)
    private val _settingMessages = MutableSharedFlow<String>(extraBufferCapacity = SETTING_MESSAGE_BUFFER)
    private val _loadState = MutableStateFlow<LoadState>(LoadState.Idle)
    private val _offline = MutableStateFlow(false)
    private val _incomingTransfers = MutableSharedFlow<IncomingTransfer>(extraBufferCapacity = INCOMING_BUFFER)

    private var cached: CachedAccount? = loaded.cache
    private var beneficiaries: List<Beneficiary> = emptyList()
    private var liveUser: String? = null
    private var liveJob: Job? = null
    private var liveSinceMillis = 0L
    private var sessionGeneration = 0
    private var inFlight: Deferred<BackendResult<Unit>>? = null

    val profile: StateFlow<UserProfile> = _profile.asStateFlow()
    val balanceKobo: StateFlow<Long> = _balanceKobo.asStateFlow()

    /** The signed-in user's permanent 10-digit business number from `accounts.business_number`; null until it is read. */
    val businessNumber: StateFlow<String?> = _businessNumber.asStateFlow()
    val transactions: StateFlow<List<Transaction>> = _transactions.asStateFlow()
    val recents: StateFlow<List<Beneficiary>> = _recents.asStateFlow()
    val favourites: StateFlow<List<Beneficiary>> = _favourites.asStateFlow()

    /** The latest recipients saved at the internal OPay bank. */
    val opayRecents: StateFlow<List<Beneficiary>> = _opayRecents.asStateFlow()

    /** The favourite recipients saved at the internal OPay bank. */
    val opayFavourites: StateFlow<List<Beneficiary>> = _opayFavourites.asStateFlow()
    val notifications: StateFlow<List<AppNotification>> = _notifications.asStateFlow()
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()
    val hideBalance: StateFlow<Boolean> = _hideBalance.asStateFlow()
    val freeTransfersLeft: StateFlow<Int> = _freeTransfersLeft.asStateFlow()
    val savingsPositions: StateFlow<List<SavingsPositionRow>> = _savingsPositions.asStateFlow()

    /** The small settings saved in `profiles.settings` (see [SettingsKeys]). Empty until the first read. */
    val appSettings: StateFlow<JsonObject> = _appSettings.asStateFlow()

    /** Texts to show as a toast when a setting could not be changed or saved. Shown once by the main screen. */
    val settingMessages: SharedFlow<String> = _settingMessages.asSharedFlow()
    val loadState: StateFlow<LoadState> = _loadState.asStateFlow()

    /** True while there is no connection. It turns false again when a read succeeds. */
    val offline: StateFlow<Boolean> = _offline.asStateFlow()

    /** Emits once for each transfer received while the app is open. Rows from a read never appear here. */
    val incomingTransfers: SharedFlow<IncomingTransfer> = _incomingTransfers.asSharedFlow()

    /** The banks read from the backend, the internal OPay bank first. Empty until the first read or cache is applied. */
    val banks: List<Bank>
        get() = _banks.value

    /** The same list as a flow, for screens that stay open while a read finishes. */
    val banksState: StateFlow<List<Bank>> = _banks.asStateFlow()

    /** True when the last successful read found the `banks` table empty, which means the owner has not synced it yet. */
    val banksUnavailable: StateFlow<Boolean> = _banksUnavailable.asStateFlow()

    /** The OPay bank, the one marked internal, or null while no list is loaded. */
    val internalBank: Bank?
        get() = _banks.value.firstOrNull { it.isInternal }

    init {
        scope.launch {
            sessions.collect { state ->
                when (state) {
                    is SessionState.SignedIn -> {
                        applyCache(state.userId)
                        startLive(state.userId)
                        scope.launch { refresh() }
                    }
                    is SessionState.SignedOut -> clear()
                    is SessionState.Loading -> Unit
                }
            }
        }
    }

    /**
     * Reads everything again. Calls made while a read is running share that read. On a failure the values on screen
     * stay as they were and the error is returned.
     */
    suspend fun refresh(): BackendResult<Unit> {
        val running = synchronized(lock) {
            val current = inFlight
            if (current != null && current.isActive) {
                current
            } else {
                scope.async { load() }.also { inFlight = it }
            }
        }
        return running.await()
    }

    private suspend fun load(): BackendResult<Unit> {
        val generation = synchronized(lock) {
            if (_loadState.value !is LoadState.Loaded) _loadState.value = LoadState.Loading
            sessionGeneration
        }
        return when (val result = remote.fetch()) {
            is BackendResult.Success -> {
                val mapped = RowMapping.map(result.value, LocalDate.now(zone))
                val applied = synchronized(lock) {
                    if (generation != sessionGeneration) {
                        false
                    } else {
                        _offline.value = false
                        if (pendingSettingWrites.get() == 0) applySynced(result.value.profile)
                        if (mapped.active) {
                            _profile.value = mapped.profile
                            _balanceKobo.value = mapped.balanceKobo
                            _businessNumber.value = mapped.businessNumber ?: _businessNumber.value
                            _freeTransfersLeft.value = mapped.freeTransfersLeft
                            _transactions.value = mapped.transactions
                            _notifications.value = mapped.notifications
                            _banksUnavailable.value = mapped.banks.isEmpty()
                            if (mapped.banks.isNotEmpty()) _banks.value = mapped.banks
                            setBeneficiaries(mapped.beneficiaries)
                            _savingsPositions.value = mapped.savings
                            _loadState.value = LoadState.Loaded
                            true
                        } else {
                            if (_loadState.value !is LoadState.Loaded) _loadState.value = LoadState.Idle
                            false
                        }
                    }
                }
                if (applied) persist()
                BackendResult.Success(Unit)
            }
            is BackendResult.Failure -> {
                synchronized(lock) {
                    if (generation == sessionGeneration) {
                        if (result.error.code == BackendCodes.NETWORK) _offline.value = true
                        if (_loadState.value !is LoadState.Loaded) _loadState.value = LoadState.Failed(result.error)
                    }
                }
                BackendResult.Failure(result.error)
            }
        }
    }

    /** Shows the cached values of this user, if any, until the first read has finished. */
    private fun applyCache(userId: String) {
        synchronized(lock) {
            val snapshot = cached ?: return
            if (snapshot.userId != userId) {
                cached = null
                return
            }
            if (_loadState.value is LoadState.Loaded) return
            _profile.value = snapshot.profile
            _balanceKobo.value = snapshot.balanceKobo
            _businessNumber.value = snapshot.businessNumber
            _freeTransfersLeft.value = snapshot.freeTransfersLeft
            _transactions.value = snapshot.transactions
            _notifications.value = snapshot.notifications
            _savingsPositions.value = snapshot.savings
            _banks.value = snapshot.banks
            setBeneficiaries(
                snapshot.beneficiaries.ifEmpty {
                    (snapshot.recents + snapshot.favourites).distinctBy { it.accountNumber to it.bankName }
                }
            )
            _loadState.value = LoadState.Loaded
            cached = null
        }
    }

    /** Opens the live feed for this user. Calling it again for the same user while it runs does nothing. */
    private fun startLive(userId: String) {
        synchronized(lock) {
            if (liveUser == userId && liveJob?.isActive == true) return
            liveJob?.cancel()
            liveUser = userId
            liveSinceMillis = clock()
            val generation = sessionGeneration
            liveJob = scope.launch {
                realtime?.let { source -> launch { collectRealtime(source, userId, generation) } }
                network?.let { monitor -> launch { collectNetwork(monitor, generation) } }
            }
        }
    }

    private suspend fun collectRealtime(source: RealtimeSource, userId: String, generation: Int) {
        while (true) {
            try {
                source.events(userId).collect { event -> handle(userId, generation, event) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The subscription failed or dropped; it is opened again after the pause below.
            }
            delay(RESUBSCRIBE_DELAY_MS)
        }
    }

    private suspend fun collectNetwork(monitor: NetworkMonitor, generation: Int) {
        try {
            monitor.online.collect { online ->
                if (!online) {
                    synchronized(lock) { if (generation == sessionGeneration) _offline.value = true }
                } else if (_offline.value) {
                    scope.launch { refresh() }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Without the monitor, failed reads still switch the offline flag on.
        }
    }

    private suspend fun handle(userId: String, generation: Int, event: RealtimeEvent) {
        when (event) {
            RealtimeEvent.Connected -> {
                synchronized(lock) { liveSinceMillis = clock() }
                refresh()
            }
            RealtimeEvent.Resync -> refresh()
            is RealtimeEvent.AccountChanged -> if (event.row.userId == userId) {
                applyLive(generation) {
                    _balanceKobo.value = event.row.balanceKobo
                    _businessNumber.value = event.row.businessNumber ?: _businessNumber.value
                    _freeTransfersLeft.value = RowMapping.freeTransfersLeft(event.row, LocalDate.now(zone))
                }
            }
            is RealtimeEvent.ProfileChanged -> if (event.row.id == userId) onProfile(generation, event.row)
            is RealtimeEvent.TransactionInserted -> if (event.row.userId == userId) onTransaction(generation, event.row)
            is RealtimeEvent.SavingsChanged -> if (event.row.userId == userId) {
                applyLive(generation) { mergeSavings(event.row) }
            }
            is RealtimeEvent.NotificationInserted -> if (event.row.userId == userId) {
                val notification = RowMapping.notification(event.row)
                applyLive(generation) {
                    if (_notifications.value.none { it.id == notification.id }) {
                        _notifications.value = (listOf(notification) + _notifications.value)
                            .sortedByDescending { it.timestampMillis }
                            .take(NOTIFICATIONS_LIMIT.toInt())
                    }
                }
            }
        }
    }

    /** A profile that becomes active while nothing is loaded yet is read in full; otherwise only the profile changes. */
    private suspend fun onProfile(generation: Int, row: ProfileRow) {
        val notLoaded = synchronized(lock) { generation == sessionGeneration && _loadState.value !is LoadState.Loaded }
        if (notLoaded) {
            if (row.status == STATUS_ACTIVE_VALUE) refresh()
            return
        }
        applyLive(generation) {
            if (pendingSettingWrites.get() == 0) {
                applySynced(row)
                _profile.value = RowMapping.profile(row)
            }
        }
    }

    private fun onTransaction(generation: Int, row: TransactionRow) {
        val transaction = RowMapping.transaction(row) ?: return
        var incoming: IncomingTransfer? = null
        applyLive(generation) {
            if (_transactions.value.none { it.id == transaction.id }) {
                _transactions.value = (listOf(transaction) + _transactions.value)
                    .sortedByDescending { it.timestampMillis }
                    .take(TRANSACTIONS_LIMIT.toInt())
                if (transaction.type == TxType.TransferIn &&
                    transaction.amountKobo > 0 &&
                    transaction.timestampMillis >= liveSinceMillis
                ) {
                    incoming = IncomingTransfer(transaction.amountKobo, transaction.counterpartyName)
                }
            }
        }
        incoming?.let { _incomingTransfers.tryEmit(it) }
    }

    /** Runs [change] on the values only while they belong to the current sign-in and have been loaded, then saves them. */
    private fun applyLive(generation: Int, change: () -> Unit) {
        val applied = synchronized(lock) {
            if (generation == sessionGeneration && _loadState.value is LoadState.Loaded) {
                change()
                true
            } else {
                false
            }
        }
        if (applied) persist()
    }

    /** Signed out: nobody's data may stay on screen, and a read still in flight must not fill it in again. */
    private fun clear() {
        synchronized(lock) {
            sessionGeneration += 1
            inFlight = null
            liveJob?.cancel()
            liveJob = null
            liveUser = null
            cached = null
            _offline.value = false
            _profile.value = EMPTY_PROFILE
            _balanceKobo.value = 0L
            _businessNumber.value = null
            _transactions.value = emptyList()
            setBeneficiaries(emptyList())
            _notifications.value = emptyList()
            _freeTransfersLeft.value = FREE_TRANSFERS_DEFAULT
            _banks.value = emptyList()
            _banksUnavailable.value = false
            _savingsPositions.value = emptyList()
            _appSettings.value = JsonObject(emptyMap())
            _loadState.value = LoadState.Idle
        }
        persist()
    }

    /** Accounts from the saved recipients (recent and favourite) whose number starts with [prefix]. No network. */
    fun suggestAccounts(prefix: String): List<ResolvedAccount> {
        if (!isSuggestionPrefix(prefix)) return emptyList()
        val known = banks
        return (_recents.value + _favourites.value)
            .filter { it.accountNumber.startsWith(prefix) }
            .distinctBy { it.accountNumber }
            .mapNotNull { beneficiary ->
                known.firstOrNull { it.name == beneficiary.bankName }
                    ?.let { ResolvedAccount(beneficiary.accountNumber, beneficiary.name, it) }
            }
    }

    /** Accounts from the saved OPay recipients (recent and favourite) whose number starts with [prefix]. No network. */
    fun suggestOpayAccounts(prefix: String): List<ResolvedAccount> {
        val bank = internalBank ?: return emptyList()
        if (!isSuggestionPrefix(prefix)) return emptyList()
        return (_opayRecents.value + _opayFavourites.value)
            .filter { it.accountNumber.startsWith(prefix) }
            .distinctBy { it.accountNumber }
            .map { ResolvedAccount(it.accountNumber, it.name, bank) }
    }

    private fun isSuggestionPrefix(prefix: String): Boolean = prefix.length in 1..9 && prefix.all { it in '0'..'9' }

    /**
     * Looks up the holder of [accountNumber] at the bank with [bankCode] through the backend. Throws [BackendError]
     * when the lookup fails; cancelling the caller cancels the lookup.
     */
    suspend fun resolveAccount(accountNumber: String, bankCode: String): ResolvedAccount {
        val api = resolver ?: throw BackendError(BackendCodes.UNKNOWN, BackendCodes.messageFor(BackendCodes.UNKNOWN))
        return when (val result = api.resolve(accountNumber, bankCode)) {
            is BackendResult.Failure -> throw result.error
            is BackendResult.Success -> {
                val answer = result.value
                val bank = banks.firstOrNull { it.code == answer.bankCode }
                    ?: throw BackendError(BackendCodes.LOOKUP_FAILED, BackendCodes.messageFor(BackendCodes.LOOKUP_FAILED))
                ResolvedAccount(answer.accountNumber, answer.name, bank, external = bank.isInternal && answer.source == SOURCE_REAL_LOOKUP)
            }
        }
    }

    /**
     * Sends one transfer to the server and returns the sender's new transaction. Money only moves in the server
     * function: the balance, the list and the recent recipients are updated from the row it returns, never by local
     * arithmetic. The call runs in the repository's own scope, so leaving the screen cannot cancel a payment that
     * is already on its way, and exactly one request is sent per call.
     *
     * Throws [BackendError]. [BackendCodes.NETWORK] means nothing was sent (no connection). When the request was
     * sent and its outcome is unknown (connection lost, unreadable answer) the error code is
     * [BackendCodes.UNKNOWN] and the account is read again in the background.
     */
    suspend fun submitTransfer(draft: TransferDraft, auth: PayAuth): Transaction =
        scope.async { performTransfer(draft, auth) }.await()

    private suspend fun performTransfer(draft: TransferDraft, auth: PayAuth): Transaction {
        val gateway = transfers ?: throw failure(BackendCodes.UNKNOWN)
        val recipient = draft.resolved ?: throw failure(BackendCodes.INVALID_INPUT)
        if (!gateway.isOnline()) throw failure(BackendCodes.NETWORK)
        val generation = synchronized(lock) { sessionGeneration }
        val request = TransferRequest(
            accountNumber = recipient.accountNumber,
            bankCode = recipient.bank.code,
            amountKobo = draft.amountKobo,
            remark = draft.remark.trim().takeIf { it.isNotEmpty() },
            category = draft.category,
            counterpartyName = recipient.name,
            bankName = recipient.bank.name,
            opayExternal = recipient.external
        )
        return when (val result = gateway.submit(request, auth)) {
            is BackendResult.Success -> applyTransfer(generation, recipient, result.value)
            is BackendResult.Failure -> {
                val error = result.error
                if (error.code == BackendCodes.NETWORK || error.code == BackendCodes.UNKNOWN) {
                    scope.launch { refresh() }
                }
                throw if (error.code == BackendCodes.NETWORK) failure(BackendCodes.UNKNOWN) else error
            }
        }
    }

    private fun applyTransfer(generation: Int, recipient: ResolvedAccount, row: TransactionRow): Transaction {
        val transaction = RowMapping.transaction(row)
        if (transaction == null) {
            scope.launch { refresh() }
            throw failure(BackendCodes.UNKNOWN)
        }
        synchronized(lock) {
            if (generation == sessionGeneration) {
                _balanceKobo.value = row.balanceAfterKobo
                if (_transactions.value.none { it.id == transaction.id }) {
                    _transactions.value = (listOf(transaction) + _transactions.value)
                        .sortedByDescending { it.timestampMillis }
                        .take(TRANSACTIONS_LIMIT.toInt())
                }
                val sameRecipient = { item: Beneficiary ->
                    item.accountNumber == recipient.accountNumber && BeneficiaryLists.isAt(item, recipient.bank)
                }
                val entry = Beneficiary(
                    name = transaction.counterpartyName ?: recipient.name,
                    accountNumber = recipient.accountNumber,
                    bankName = transaction.counterpartyBank ?: recipient.bank.name,
                    isFavourite = beneficiaries.any { sameRecipient(it) && it.isFavourite },
                    bankCode = recipient.bank.code
                )
                setBeneficiaries(listOf(entry) + beneficiaries.filterNot(sameRecipient))
            }
        }
        persist()
        scope.launch { refresh() }
        return transaction
    }

    private fun failure(code: String): BackendError = BackendError(code, BackendCodes.messageFor(code))

    /**
     * Sends one airtime, data or bill purchase to the server and returns the new transaction. The balance and the
     * list change only from the row the server answers with, never by local arithmetic. The call runs in the
     * repository's own scope, so leaving the screen cannot cancel a purchase that is already on its way, and only
     * one purchase runs at a time: a second call while one is running is refused without being sent.
     *
     * Throws [IllegalArgumentException] for input that is invalid on this phone. Every other failure is a
     * [BackendError] whose message is the text to show (see [PurchaseErrors]). [BackendCodes.NETWORK] means nothing
     * was sent. When the request was sent and its outcome is unknown the code is [BackendCodes.UNKNOWN] and the
     * account is read again in the background. [remark] is kept on the returned transaction for the receipt only;
     * the server does not store it.
     */
    suspend fun purchase(
        type: TxType,
        title: String,
        counterparty: String?,
        amountKobo: Long,
        remark: String? = null
    ): Transaction = scope.async { performPurchase(type, title, counterparty, amountKobo, remark) }.await()

    private suspend fun performPurchase(
        type: TxType,
        title: String,
        counterparty: String?,
        amountKobo: Long,
        remark: String?
    ): Transaction {
        if (type != TxType.Airtime && type != TxType.Data && type != TxType.Bill) {
            throw IllegalArgumentException("Unsupported purchase type")
        }
        if (title.isBlank()) throw IllegalArgumentException("A purchase needs a title")
        if (amountKobo < PurchaseErrors.MIN_KOBO) throw IllegalArgumentException(PurchaseErrors.lowText())
        if (amountKobo > PurchaseErrors.MAX_KOBO) throw IllegalArgumentException(PurchaseErrors.highText())
        val gateway = purchaseGateway ?: throw failure(BackendCodes.UNKNOWN)
        if (!gateway.isOnline()) throw failure(BackendCodes.NETWORK)
        if (!purchaseBusy.compareAndSet(false, true)) throw failure(BackendCodes.STATE_INVALID)
        try {
            val generation = synchronized(lock) { sessionGeneration }
            return when (val result = gateway.purchase(type.name, title.trim(), amountKobo, counterparty)) {
                is BackendResult.Success -> applyPurchase(generation, result.value, remark)
                is BackendResult.Failure -> {
                    val error = result.error
                    if (error.code == BackendCodes.NETWORK || error.code == BackendCodes.UNKNOWN) {
                        scope.launch { refresh() }
                    }
                    val shown = if (error.code == BackendCodes.NETWORK) failure(BackendCodes.UNKNOWN) else error
                    throw BackendError(shown.code, PurchaseErrors.message(shown), shown.data)
                }
            }
        } finally {
            purchaseBusy.set(false)
        }
    }

    private fun applyPurchase(generation: Int, row: TransactionRow, remark: String?): Transaction {
        val transaction = RowMapping.transaction(row)
        if (transaction == null) {
            scope.launch { refresh() }
            throw BackendError(BackendCodes.UNKNOWN, PurchaseErrors.message(failure(BackendCodes.UNKNOWN)))
        }
        synchronized(lock) {
            if (generation == sessionGeneration) {
                _balanceKobo.value = row.balanceAfterKobo
                if (_transactions.value.none { it.id == transaction.id }) {
                    _transactions.value = (listOf(transaction) + _transactions.value)
                        .sortedByDescending { it.timestampMillis }
                        .take(TRANSACTIONS_LIMIT.toInt())
                }
            }
        }
        persist()
        scope.launch { refresh() }
        return if (transaction.remark == null && !remark.isNullOrBlank()) transaction.copy(remark = remark) else transaction
    }

    /**
     * Creates one savings position through `savings_create`. The money moves only in the server function; the
     * position in the list comes from the row it returns and the balance and ledger are read again afterwards.
     * Throws [BackendError] whose message is the text to show (see [SavingsErrors]). [BackendCodes.NETWORK] means
     * nothing was sent. When the outcome is unknown the code is [BackendCodes.UNKNOWN] and the account is read again.
     */
    suspend fun savingsCreate(
        kind: SavingsKind,
        name: String,
        amountKobo: Long,
        targetKobo: Long? = null,
        maturesAt: String? = null,
        meta: JsonObject? = null
    ): SavingsPositionRow = scope.async {
        performSavings { it.create(kind.wire, name.trim(), amountKobo, targetKobo, maturesAt, meta) }
    }.await()

    /** Adds [amountKobo] to an active position through `savings_deposit`. Same rules as [savingsCreate]. */
    suspend fun savingsDeposit(positionId: String, amountKobo: Long): SavingsPositionRow = scope.async {
        performSavings { it.deposit(positionId, amountKobo) }
    }.await()

    /**
     * Takes [amountKobo] out of an active position through `savings_withdraw`. The server pays out all accrued
     * interest with it and closes the position when nothing is left. Same rules as [savingsCreate].
     */
    suspend fun savingsWithdraw(positionId: String, amountKobo: Long): SavingsPositionRow = scope.async {
        performSavings { it.withdraw(positionId, amountKobo) }
    }.await()

    private suspend fun performSavings(
        call: suspend (SavingsGateway) -> BackendResult<SavingsPositionRow>
    ): SavingsPositionRow {
        val gateway = savingsGateway ?: throw failure(BackendCodes.UNKNOWN)
        if (!gateway.isOnline()) throw failure(BackendCodes.NETWORK)
        if (!savingsBusy.compareAndSet(false, true)) throw failure(BackendCodes.STATE_INVALID)
        try {
            val generation = synchronized(lock) { sessionGeneration }
            return when (val result = call(gateway)) {
                is BackendResult.Success -> {
                    synchronized(lock) {
                        if (generation == sessionGeneration) mergeSavings(result.value)
                    }
                    persist()
                    scope.launch { refresh() }
                    result.value
                }
                is BackendResult.Failure -> {
                    val error = result.error
                    if (error.code == BackendCodes.NETWORK || error.code == BackendCodes.UNKNOWN) {
                        scope.launch { refresh() }
                    }
                    val shown = if (error.code == BackendCodes.NETWORK) failure(BackendCodes.UNKNOWN) else error
                    throw BackendError(shown.code, SavingsErrors.message(shown), shown.data)
                }
            }
        } finally {
            savingsBusy.set(false)
        }
    }

    /** Replaces the position with the same id, or adds it. Call while holding [lock]. */
    private fun mergeSavings(row: SavingsPositionRow) {
        val current = _savingsPositions.value
        _savingsPositions.value =
            if (current.any { it.id == row.id }) current.map { if (it.id == row.id) row else it } else current + row
    }

    /** The boolean setting stored under [key] (see [SettingsKeys]), or [default] when none is saved. */
    fun flag(key: String, default: Boolean = false): Boolean = SettingsMerge.flag(_appSettings.value, key, default)

    fun setThemeMode(mode: ThemeMode) = launchChange(SettingChange(themeMode = mode))

    fun setHideBalance(hide: Boolean) = launchChange(SettingChange(hideBalance = hide))

    fun setNickname(nickname: String) {
        val clean = nickname.trim().take(MAX_NICKNAME_LENGTH)
        if (clean.isEmpty() || _profile.value.id.isEmpty()) return
        launchChange(SettingChange(nickname = clean))
    }

    /** Changes small settings in the background. The values change at once; a failed save puts them back and shows a toast. */
    fun setSettings(flags: Map<String, Boolean>) {
        if (flags.isNotEmpty()) launchChange(SettingChange(flags = flags))
    }

    /**
     * Like [setSettings] but waits for the server and returns the result, for a screen that must react to it. The
     * values are put back on a failure; no toast is queued. [BackendCodes.NETWORK] means nothing was sent.
     */
    suspend fun saveSettings(flags: Map<String, Boolean>): BackendResult<Unit> =
        scope.async(start = CoroutineStart.UNDISPATCHED) {
            when (val begun = begin(SettingChange(flags = flags))) {
                Begin.Done -> BackendResult.Success(Unit)
                Begin.Offline -> BackendResult.Failure(failure(BackendCodes.NETWORK))
                is Begin.Started -> finish(begun)
            }
        }.await()

    private class SettingChange(
        val nickname: String? = null,
        val themeMode: ThemeMode? = null,
        val hideBalance: Boolean? = null,
        val flags: Map<String, Boolean> = emptyMap()
    )

    private class SettingsSnapshot(
        val nickname: String,
        val themeMode: ThemeMode,
        val hideBalance: Boolean,
        val settings: JsonObject
    )

    private sealed interface Begin {
        /** Nothing to send: the change was applied on this phone only, or it changes nothing. */
        data object Done : Begin

        /** The phone has no connection; nothing was changed. */
        data object Offline : Begin

        class Started(
            val gateway: ProfileGateway,
            val change: SettingChange,
            val before: SettingsSnapshot,
            val generation: Int
        ) : Begin
    }

    private fun launchChange(change: SettingChange) {
        when (val begun = begin(change)) {
            Begin.Done -> Unit
            Begin.Offline -> _settingMessages.tryEmit(BackendCodes.messageFor(BackendCodes.NETWORK))
            is Begin.Started -> scope.launch(start = CoroutineStart.UNDISPATCHED) {
                if (finish(begun) is BackendResult.Failure) _settingMessages.tryEmit(SETTING_SAVE_FAILED_TEXT)
            }
        }
    }

    /** Shows [change] at once and counts one save as running. Nothing is changed while the phone is offline. */
    private fun begin(change: SettingChange): Begin {
        if (synchronized(lock) { isUnchanged(change) }) return Begin.Done
        val gateway = profileGateway
        if (gateway != null && !gateway.isOnline()) return Begin.Offline
        val (before, generation) = synchronized(lock) {
            val snapshot = SettingsSnapshot(
                _profile.value.nickname,
                _themeMode.value,
                _hideBalance.value,
                _appSettings.value
            )
            applyChange(change)
            if (gateway != null) pendingSettingWrites.incrementAndGet()
            snapshot to sessionGeneration
        }
        persist()
        return if (gateway == null) Begin.Done else Begin.Started(gateway, change, before, generation)
    }

    /** True when [change] would leave every value as it is. Call while holding [lock]. */
    private fun isUnchanged(change: SettingChange): Boolean =
        (change.nickname == null || change.nickname == _profile.value.nickname) &&
            (change.themeMode == null || change.themeMode == _themeMode.value) &&
            (change.hideBalance == null || change.hideBalance == _hideBalance.value) &&
            SettingsMerge.isNoOp(_appSettings.value, change.flags)

    /**
     * Sends one started change. The callers start it undispatched, so saves reach the lock in the order the changes
     * were made and then run one after the other: the last change made is the last one applied.
     */
    private suspend fun finish(begun: Begin.Started): BackendResult<Unit> {
        val change = begun.change
        val result = try {
            settingsMutex.withLock {
                begun.gateway.update(
                    nickname = change.nickname,
                    themeMode = change.themeMode?.name,
                    hideBalance = change.hideBalance,
                    settings = change.flags.takeIf { it.isNotEmpty() }?.let { SettingsMerge.toJson(it) }
                )
            }
        } finally {
            pendingSettingWrites.decrementAndGet()
        }
        synchronized(lock) {
            if (begun.generation == sessionGeneration) {
                if (result is BackendResult.Success) {
                    if (pendingSettingWrites.get() == 0) {
                        applySynced(result.value)
                        if (_loadState.value is LoadState.Loaded) _profile.value = RowMapping.profile(result.value)
                    }
                } else {
                    revertChange(change, begun.before)
                }
            }
        }
        persist()
        return when (result) {
            is BackendResult.Success -> BackendResult.Success(Unit)
            is BackendResult.Failure -> {
                if (result.error.code == BackendCodes.NETWORK || result.error.code == BackendCodes.UNKNOWN) {
                    scope.launch { refresh() }
                }
                BackendResult.Failure(result.error)
            }
        }
    }

    /** Call while holding [lock]. */
    private fun applyChange(change: SettingChange) {
        change.nickname?.let { _profile.value = _profile.value.copy(nickname = it) }
        change.themeMode?.let { _themeMode.value = it }
        change.hideBalance?.let { _hideBalance.value = it }
        if (change.flags.isNotEmpty()) _appSettings.value = SettingsMerge.merge(_appSettings.value, change.flags)
    }

    /** Puts back what [change] touched, but only where nothing newer has replaced it. Call while holding [lock]. */
    private fun revertChange(change: SettingChange, before: SettingsSnapshot) {
        change.nickname?.let {
            if (_profile.value.nickname == it) _profile.value = _profile.value.copy(nickname = before.nickname)
        }
        change.themeMode?.let { if (_themeMode.value == it) _themeMode.value = before.themeMode }
        change.hideBalance?.let { if (_hideBalance.value == it) _hideBalance.value = before.hideBalance }
        if (change.flags.isNotEmpty()) {
            _appSettings.value = SettingsMerge.revert(_appSettings.value, before.settings, change.flags)
        }
    }

    /** The theme, hide-balance and small settings as the server holds them. Call while holding [lock]. */
    private fun applySynced(row: ProfileRow) {
        _themeMode.value = themeFromName(row.themeMode)
        _hideBalance.value = row.hideBalance
        _appSettings.value = row.settings
    }

    /**
     * Flips the favourite flag of one saved recipient. The star changes at once and the server is asked in the
     * repository's own scope, so leaving the screen cannot cancel the call. When the server refuses or cannot be
     * reached the star goes back to what it was and the failure is returned. One flip runs at a time.
     */
    suspend fun toggleFavourite(accountNumber: String, bankCode: String): BackendResult<Unit> =
        scope.async { favouriteMutex.withLock { performFavourite(accountNumber, bankCode) } }.await()

    private suspend fun performFavourite(accountNumber: String, bankCode: String): BackendResult<Unit> {
        val gateway = favouriteGateway ?: return BackendResult.Failure(failure(BackendCodes.UNKNOWN))
        if (!gateway.isOnline()) return BackendResult.Failure(failure(BackendCodes.NETWORK))
        val start = synchronized(lock) {
            val current = beneficiaries.firstOrNull { it.accountNumber == accountNumber && it.bankCode == bankCode }
            current?.let {
                setFavourite(accountNumber, bankCode, !it.isFavourite)
                sessionGeneration to it.isFavourite
            }
        } ?: return BackendResult.Failure(failure(BackendCodes.NOT_FOUND))
        val (generation, before) = start
        return when (val result = gateway.toggle(accountNumber, bankCode)) {
            is BackendResult.Success -> {
                finishFavourite(generation, accountNumber, bankCode, result.value)
                BackendResult.Success(Unit)
            }
            is BackendResult.Failure -> {
                finishFavourite(generation, accountNumber, bankCode, before)
                if (result.error.code == BackendCodes.UNKNOWN) scope.launch { refresh() }
                BackendResult.Failure(result.error)
            }
        }
    }

    private fun finishFavourite(generation: Int, accountNumber: String, bankCode: String, favourite: Boolean) {
        synchronized(lock) {
            if (generation == sessionGeneration) setFavourite(accountNumber, bankCode, favourite)
        }
        persist()
    }

    private fun setFavourite(accountNumber: String, bankCode: String, favourite: Boolean) {
        setBeneficiaries(
            beneficiaries.map {
                if (it.accountNumber == accountNumber && it.bankCode == bankCode) it.copy(isFavourite = favourite) else it
            }
        )
    }

    /**
     * Marks every notification read on the server, then in the list. Only the notifications that were unread when
     * the call started are changed, so one that arrives meanwhile still counts as unread. Nothing changes while the
     * phone is offline or when the server refuses.
     */
    suspend fun markNotificationsRead(): BackendResult<Unit> =
        scope.async { notificationMutex.withLock { performMarkRead() } }.await()

    private suspend fun performMarkRead(): BackendResult<Unit> {
        val gateway = notificationGateway ?: return BackendResult.Failure(failure(BackendCodes.UNKNOWN))
        val (generation, ids) = synchronized(lock) {
            sessionGeneration to _notifications.value.filter { !it.read }.map { it.id }.toSet()
        }
        if (ids.isEmpty()) return BackendResult.Success(Unit)
        if (!gateway.isOnline()) return BackendResult.Failure(failure(BackendCodes.NETWORK))
        return when (val result = gateway.markAllRead()) {
            is BackendResult.Success -> {
                synchronized(lock) {
                    if (generation == sessionGeneration) {
                        _notifications.value = _notifications.value.map { if (it.id in ids) it.copy(read = true) else it }
                    }
                }
                persist()
                BackendResult.Success(Unit)
            }
            is BackendResult.Failure -> BackendResult.Failure(result.error)
        }
    }

    /** The most recently used saved recipient with this account number. */
    fun findBeneficiary(accountNumber: String): Beneficiary? = synchronized(lock) {
        beneficiaries.firstOrNull { it.accountNumber == accountNumber }
    }

    /** Replaces the saved recipients and rebuilds every list derived from them. Call while holding [lock]. */
    private fun setBeneficiaries(all: List<Beneficiary>) {
        beneficiaries = all
        val atOpay = BeneficiaryLists.atBank(all, internalBank)
        _recents.value = BeneficiaryLists.recents(all)
        _favourites.value = BeneficiaryLists.favourites(all)
        _opayRecents.value = BeneficiaryLists.recents(atOpay)
        _opayFavourites.value = BeneficiaryLists.favourites(atOpay)
    }

    private fun persist() {
        scope.launch {
            saveMutex.withLock {
                val snapshot = synchronized(lock) {
                    PersistedState(
                        version = STATE_VERSION,
                        hideBalance = _hideBalance.value,
                        themeMode = _themeMode.value.name,
                        cache = cacheSnapshot(),
                        appSettings = _appSettings.value
                    )
                }
                store.save(snapshot)
            }
        }
    }

    /** The values to cache: the loaded ones, or the cache from the last run that has not been shown yet. */
    private fun cacheSnapshot(): CachedAccount? {
        val profile = _profile.value
        if (profile.id.isEmpty() || _loadState.value !is LoadState.Loaded) return cached
        return CachedAccount(
            userId = profile.id,
            profile = profile,
            balanceKobo = _balanceKobo.value,
            freeTransfersLeft = _freeTransfersLeft.value,
            transactions = _transactions.value,
            notifications = _notifications.value,
            recents = _recents.value,
            favourites = _favourites.value,
            savings = _savingsPositions.value,
            banks = _banks.value,
            beneficiaries = beneficiaries,
            businessNumber = _businessNumber.value
        )
    }

    private fun themeFromName(name: String): ThemeMode =
        ThemeMode.entries.firstOrNull { it.name == name } ?: ThemeMode.Dark

    private companion object {
        const val MAX_NICKNAME_LENGTH = 20
        const val FREE_TRANSFERS_DEFAULT = 5
        const val INCOMING_BUFFER = 8
        const val SETTING_MESSAGE_BUFFER = 4
        const val RESUBSCRIBE_DELAY_MS = 10_000L

        val EMPTY_PROFILE = UserProfile(
            id = "",
            fullName = "",
            nickname = "",
            phone = "",
            accountNumber = "",
            tier = 1,
            emailMasked = "",
            gender = "",
            dobMasked = ""
        )
    }
}
