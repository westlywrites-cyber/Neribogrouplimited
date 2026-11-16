package com.westly.opay.data

import com.westly.opay.backend.BackendError

/** Where the first read of the signed-in user's data stands. */
sealed interface LoadState {
    /** Nothing has been read yet, or the account is not active yet. */
    data object Idle : LoadState

    /** The first read is in flight. */
    data object Loading : LoadState

    /** Data is on screen. Later refreshes keep this state. */
    data object Loaded : LoadState

    /** The first read failed and there is nothing to show. */
    data class Failed(val error: BackendError) : LoadState
}
