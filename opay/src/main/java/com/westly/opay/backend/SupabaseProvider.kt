package com.westly.opay.backend

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime

/** Holds the one Supabase client. It is created on first use; with no URL or key configured it is never created. */
class SupabaseProvider(private val store: SecureStore) {
    val client: SupabaseClient by lazy {
        check(BackendConfig.isConfigured) { "Backend is not configured" }
        createSupabaseClient(BackendConfig.url, BackendConfig.anonKey) {
            install(Auth) {
                sessionManager = SecureSessionManager(store)
                alwaysAutoRefresh = true
            }
            install(Postgrest)
            install(Realtime)
            install(Functions)
        }
    }
}
