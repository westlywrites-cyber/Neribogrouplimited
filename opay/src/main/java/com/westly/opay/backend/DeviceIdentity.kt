package com.westly.opay.backend

import android.os.Build
import java.util.UUID

class DeviceIdentity(private val store: SecureStore) {

    /** A random UUID created once per install and kept in [SecureStore]. */
    @Synchronized
    fun deviceId(): String {
        val existing = store.getString(SecureKeys.DEVICE_ID)
        if (!existing.isNullOrBlank()) return existing
        val created = UUID.randomUUID().toString()
        store.putString(SecureKeys.DEVICE_ID, created)
        return created
    }

    fun deviceName(): String = Build.MANUFACTURER + " " + Build.MODEL
}
