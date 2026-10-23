package com.example.firstlink.data

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.json.JSONObject

/**
 * The Supabase client, wrapped for Java: plain callbacks on the main thread instead of
 * coroutines. Rows travel as JSON text / org.json; FirstLinkRepository maps them to models.
 *
 * Every call signs in anonymously first if needed. Writes go through one queue, so they
 * land in the order they were made — the event row exists before the organizer's users
 * row points at it, the way Firestore ordered one client's writes.
 */
object Backend {

    private const val URL = "https://wnevnenryvvsmjjecysn.supabase.co"

    /** Public by design: row level security in supabase/schema.sql is what protects data. */
    private const val ANON_KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InduZXZuZW5yeXZ2c21qamVjeXNuIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTI3MjA4NTEsImV4cCI6MjEwODI5Njg1MX0.Z5jvz5wtvjqFsUev_jliiZ6f0dhiilQrRZoXL7tEJkM"

    /** Every table holding one event's state. Realtime watches all of them. */
    private val TABLES = arrayOf("events", "users", "emergencies", "help_requests", "contacts")

    private val client = createSupabaseClient(URL, ANON_KEY) {
        install(Auth)
        install(Postgrest)
        install(Realtime)
    }

    private val scope = MainScope()
    private val signInLock = Mutex()
    private val writes = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        scope.launch { for (w in writes) w() }
    }

    /** value is null on failure; error is null on success. Always called on the main thread. */
    fun interface Callback<T> {
        fun onResult(value: T?, error: String?)
    }

    /** This device's anonymous auth uid, or "" before sign-in completes. */
    @JvmStatic
    fun uid(): String = client.auth.currentUserOrNull()?.id ?: ""

    /** Restores the saved session, or signs in anonymously. Locked so two callers can't make two users. */
    private suspend fun signedIn() = signInLock.withLock {
        client.auth.awaitInitialization()
        if (client.auth.currentSessionOrNull() == null) client.auth.signInAnonymously()
    }

    private fun <T> call(cb: Callback<T>, block: suspend () -> T) {
        scope.launch { finish(cb) { block() } }
    }

    private fun <T> write(cb: Callback<T>, block: suspend () -> T) {
        writes.trySend { finish(cb) { withTimeout(15_000) { block() } } }
    }

    private suspend fun <T> finish(cb: Callback<T>, block: suspend () -> T) {
        val r = withContext(Dispatchers.IO) { runCatching { signedIn(); block() } }
        r.exceptionOrNull()?.let { cb.onResult(null, it.message ?: it.toString()) }
            ?: cb.onResult(r.getOrNull(), null)
    }

    private fun json(row: JSONObject): JsonObject = Json.parseToJsonElement(row.toString()).jsonObject

    @JvmStatic
    fun signIn(cb: Callback<String>) = call(cb) { uid() }

    /** Rows of [table] where [column] = [value], as a JSON array. */
    @JvmStatic
    fun select(table: String, column: String, value: String, cb: Callback<String>) = call(cb) {
        client.from(table).select { filter { eq(column, value) } }.data
    }

    /** Read-only RPC (redeem_code), returning its JSON result. */
    @JvmStatic
    fun rpc(function: String, params: JSONObject, cb: Callback<String>) = call(cb) {
        client.postgrest.rpc(function, json(params)).data
    }

    /** RPC that changes data (accept_*): queued with the other writes. */
    @JvmStatic
    fun rpcWrite(function: String, params: JSONObject, cb: Callback<String>) = write(cb) {
        client.postgrest.rpc(function, json(params)).data
    }

    @JvmStatic
    fun insert(table: String, row: JSONObject, cb: Callback<String>) = write(cb) {
        client.from(table).insert(json(row)).data
    }

    @JvmStatic
    fun upsert(table: String, row: JSONObject, cb: Callback<String>) = write(cb) {
        client.from(table).upsert(json(row)).data
    }

    @JvmStatic
    fun update(table: String, id: String, patch: JSONObject, cb: Callback<String>) = write(cb) {
        client.from(table).update(json(patch)) { filter { eq("id", id) } }.data
    }

    /**
     * Calls [onChange] with a table name whenever one of this event's rows changes, and
     * with every table each time the channel (re)subscribes — so the caller's refetch
     * doubles as the initial load and as catch-up after a dropped connection. Subscribes
     * outside the write queue, so one hung write cannot block it.
     * Returns a handle for [stop].
     */
    @JvmStatic
    fun listen(eventId: String, onChange: Callback<String>): Any {
        val channel = client.channel("event-$eventId-${System.nanoTime()}")
        val job = Job()
        for (t in TABLES) {
            channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = t
                filter(if (t == "events") "id" else "event_id", FilterOperator.EQ, eventId)
            }.onEach { onChange.onResult(t, null) }.launchIn(scope + job)
        }
        channel.status.onEach {
            if (it == RealtimeChannel.Status.SUBSCRIBED) TABLES.forEach { t -> onChange.onResult(t, null) }
        }.launchIn(scope + job)
        call<Unit>({ _, err -> if (err != null) onChange.onResult(null, err) }) { channel.subscribe() }
        return channel to job
    }

    @JvmStatic
    fun stop(handle: Any?) {
        @Suppress("UNCHECKED_CAST")
        val (channel, job) = handle as? Pair<RealtimeChannel, Job> ?: return
        job.cancel()
        scope.launch { runCatching { client.realtime.removeChannel(channel) } }
    }
}
