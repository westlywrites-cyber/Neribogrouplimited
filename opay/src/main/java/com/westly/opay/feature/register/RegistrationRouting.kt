package com.westly.opay.feature.register

import com.westly.opay.backend.RegistrationState
import com.westly.opay.navigation.Routes

/** Decides where a user goes after the code is accepted. */
object RegistrationRouting {
    fun nextRoute(state: RegistrationState): String = when {
        state.status == STATUS_ACTIVE -> Routes.Home
        state.status == STATUS_PENDING || state.status == STATUS_SUSPENDED -> Routes.Waiting
        !state.hasKyc -> Routes.RegisterAddress
        else -> Routes.RegisterPin
    }

    /** The "Phone number verified" dialog is shown only to a user who still has the whole form ahead. */
    fun showsVerifiedDialog(state: RegistrationState): Boolean =
        state.status == STATUS_REGISTERING && !state.hasKyc

    private const val STATUS_REGISTERING = "registering"
    private const val STATUS_PENDING = "pending"
    private const val STATUS_ACTIVE = "active"
    private const val STATUS_SUSPENDED = "suspended"
}
