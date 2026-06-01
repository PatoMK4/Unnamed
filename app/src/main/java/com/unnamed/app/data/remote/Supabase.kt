package com.unnamed.app.data.remote

import com.unnamed.app.BuildConfig
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

/**
 * Supabase client (auth + postgrest). The publishable key is public by design;
 * Row-Level Security protects all data. See supabase/README.md.
 *
 * Not yet used for syncing — the local Room DB is the source of truth for now.
 * This is wired so the sync layer can be added without restructuring.
 */
object Supabase {
    val client by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_KEY,
        ) {
            install(Auth)
            install(Postgrest)
        }
    }
}
