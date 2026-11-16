package com.westly.opay.feature.notifications

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.westly.opay.OPayApplication
import com.westly.opay.data.model.AppNotification
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NotificationsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as OPayApplication).repository

    val notifications: StateFlow<List<AppNotification>> = repository.notifications

    private val _newIds = MutableStateFlow<Set<String>>(emptySet())

    /** The notifications that were unread when they appeared on this screen. They keep their dot until the screen closes. */
    val newIds: StateFlow<Set<String>> = _newIds.asStateFlow()

    private var watching: Job? = null

    /** Called when the screen opens: every unread notification is marked read on the server, also those that arrive while it is open. */
    fun open() {
        if (watching != null) return
        watching = viewModelScope.launch {
            repository.notifications
                .map { list -> list.filter { !it.read }.map { it.id }.toSet() }
                .filter { it.isNotEmpty() }
                .distinctUntilChanged()
                .collect { unread ->
                    _newIds.update { it + unread }
                    repository.markNotificationsRead()
                }
        }
    }

    /** Called when the screen closes. */
    fun close() {
        watching?.cancel()
        watching = null
        _newIds.value = emptySet()
    }
}
