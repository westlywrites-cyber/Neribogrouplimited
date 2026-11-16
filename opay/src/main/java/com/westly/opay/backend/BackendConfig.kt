package com.westly.opay.backend

import com.westly.opay.BuildConfig

object BackendConfig {
    val url: String get() = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
    val anonKey: String get() = BuildConfig.SUPABASE_ANON_KEY.trim()
    val isConfigured: Boolean get() = url.isNotEmpty() && anonKey.isNotEmpty()
}
