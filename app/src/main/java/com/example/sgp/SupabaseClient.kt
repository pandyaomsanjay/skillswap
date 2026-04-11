package com.example.sgp

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.auth.Auth

object SupabaseClient {
    val client = createSupabaseClient(
        supabaseUrl = "https://ghrxltlstncjcizyyqfo.supabase.co",
        supabaseKey = "sb_publishable_gqJk7xQe8lHqHKKtsCxb7g_txjduIKv"
    ) {
        install(Storage)
        install(Postgrest)
        install(Auth)
    }
}
