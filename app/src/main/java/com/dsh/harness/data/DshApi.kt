package com.dsh.harness.data

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class RpcException(val code: String, override val message: String) : Exception(message)

/**
 * Keeps the `dsh-auth-*` cookie the server mints in exchange for the one-shot
 * `?token=` link. Without a jar OkHttp drops every Set-Cookie, so /api answers
 * `unauthorized` as plain text and the old code died parsing it as JSON.
 */
private class MemoryCookieJar : CookieJar {
    private val store = ConcurrentHashMap<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val list = store.getOrPut(url.host) { mutableListOf() }
        synchronized(list) {
            for (c in cookies) {
                list.removeAll { it.name == c.name }
                list.add(c)
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val list = store[url.host] ?: return emptyList()
        val now = System.currentTimeMillis()
        synchronized(list) {
            list.removeAll { it.expiresAt < now }
            return list.filter { it.matches(url) }
        }
    }
}

/**
 * Talks to DSH 0.1.5+.
 *
 * Three things changed from the 0.1.2 protocol this app was written against:
 *  - endpoints use `/` instead of `.`   (`session.list` -> `session/list`)
 *  - every call wraps its arguments     (`"payload":{"args":{...}}`)
 *  - the server authenticates by cookie, traded for the one-shot `?token=` link
 *
 * Streaming moved too: instead of one downlink socket, there is a multiplexed
 * socket at /api/remote.mux carrying independent streams keyed by streamId.
 *
 * @param base  server root, no trailing slash and no query
 * @param token value from the `?token=` link DSH prints on start; null if the
 *              server predates auth
 */
class DshApi(private val base: String, private val token: String? = null) {
    private val json = "application/json".toMediaType()
    private val cookies = MemoryCookieJar()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .cookieJar(cookies)
        .followRedirects(true)
        .build()

    // ---------------------------------------------------------------- auth

    /**
     * Trades the one-shot token for the session cookie. DSH answers the token
     * link with 303 + Set-Cookie; the jar then carries it on every later call,
     * the mux socket included.
     */
    fun authenticate() {
        val url = if (token.isNullOrBlank()) base else "$base/?token=$token"
        try {
            client.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
                if (resp.code == 401 || resp.code == 403) throw RpcException(
                    "auth",
                    if (token.isNullOrBlank())
                        "serverul cere un token; lipește adresa completă, cu ?token=…"
                    else
                        "token respins (HTTP ${resp.code}) — probabil a expirat, repornește DSH și ia linkul nou"
                )
            }
        } catch (e: RpcException) {
            throw e
        } catch (e: Exception) {
            throw RpcException("transport", e.message ?: "nu pot contacta serverul")
        }
    }

    // ---------------------------------------------------------------- rest

    private fun parseOrExplain(text: String, code: Int): JSONObject {
        val trimmed = text.trim()
        if (!trimmed.startsWith("{")) {
            val hint = when {
                trimmed.equals("unauthorized", true) || code == 401 || code == 403 ->
                    "neautentificat — adresa trebuie să includă ?token=… (se schimbă la fiecare pornire a DSH)"
                trimmed.isEmpty() -> "răspuns gol (HTTP $code)"
                else -> "răspuns neașteptat (HTTP $code): ${trimmed.take(120)}"
            }
            throw RpcException(if (code == 401 || code == 403) "auth" else "transport", hint)
        }
        return JSONObject(trimmed)
    }

    /**
     * One request/response call. [endpoint] uses the new slash form and [args]
     * is the arguments object itself — the `{"args": …}` envelope is added here.
     */
    fun rpc(endpoint: String, args: JSONObject = JSONObject()): JSONObject {
        val env = JSONObject()
            .put("type", "client-request")
            .put("rpcId", UUID.randomUUID().toString())
            .put("method", endpoint)
            .put("payload", JSONObject().put("args", args))
        val req = Request.Builder().url("$base/api/$endpoint")
            .post(env.toString().toRequestBody(json)).build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: throw RpcException("transport", "răspuns gol ${resp.code}")
            val server = parseOrExplain(text, resp.code)
            val result = server.optJSONObject("result") ?: throw RpcException("transport", "lipsește result")
            if (!result.optBoolean("ok")) {
                val e = result.optJSONObject("error")
                throw RpcException(e?.optString("code") ?: "error", e?.optString("message") ?: "apel eșuat")
            }
            return result.optJSONObject("value") ?: JSONObject()
        }
    }

    /** Plain GET returning the raw body text (for the version manifest). */
    fun get(path: String): String {
        val req = Request.Builder().url("$base$path").build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw RpcException("http", "HTTP ${resp.code}")
            return resp.body?.string() ?: ""
        }
    }

    // ----------------------------------------------------------------- mux

    private var mux: WebSocket? = null
    private val handlers = ConcurrentHashMap<String, (JSONObject) -> Unit>()

    /**
     * Opens the single multiplexed socket. Frames arrive as
     * `{"type":"item","streamId":…,"value":{…}}` and are routed to whoever
     * opened that stream.
     */
    fun connectMux(onFailure: (String) -> Unit): WebSocket {
        mux?.let { return it }
        val req = Request.Builder().url("$base/api/remote.mux").build()
        val ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onMessage(ws: WebSocket, text: String) {
                try {
                    val f = JSONObject(text)
                    val sid = f.optString("streamId")
                    val value = f.optJSONObject("value") ?: return
                    handlers[sid]?.invoke(value)
                } catch (_: Exception) {
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, resp: Response?) {
                mux = null
                onFailure(t.message ?: "mux întrerupt")
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                mux = null
            }
        })
        mux = ws
        return ws
    }

    /**
     * Opens one logical stream on the mux and routes its items to [onItem].
     * Returns the streamId so the caller can close it later.
     */
    fun openStream(endpoint: String, args: JSONObject = JSONObject(), onItem: (JSONObject) -> Unit): String {
        val ws = mux ?: throw RpcException("transport", "mux neconectat")
        val sid = UUID.randomUUID().toString()
        handlers[sid] = onItem
        val frame = JSONObject()
            .put("type", "open")
            .put("streamId", sid)
            .put("endpoint", endpoint)
            .put("payload", JSONObject().put("args", args))
        ws.send(frame.toString())
        return sid
    }

    fun closeStream(streamId: String) {
        handlers.remove(streamId)
        mux?.send(JSONObject().put("type", "close").put("streamId", streamId).toString())
    }

    fun shutdown() {
        handlers.clear()
        try { mux?.close(1000, null) } catch (_: Exception) {}
        mux = null
    }

    // ------------------------------------------------------- named helpers

    fun sessionList(): JSONObject = rpc("session/list", JSONObject().put("_request", JSONObject()))

    fun settingsDescribe(): JSONObject = rpc("settings/describe")

    fun modelCatalog(): JSONObject = rpc("session/modelCatalog")

    fun createSession(workspaceId: String): JSONObject =
        rpc("session/create", JSONObject().put("request", JSONObject().put("workspaceId", workspaceId)))

    /** Queues a prompt. The answer arrives on the session/follow stream. */
    fun prompt(sessionId: String, text: String, timeZone: String): JSONObject {
        val content = org.json.JSONArray().put(JSONObject().put("type", "text").put("text", text))
        val request = JSONObject()
            .put("requestId", UUID.randomUUID().toString())
            .put("sessionId", sessionId)
            .put("mode", "queue")
            .put("content", content)
            .put("clientTimeZone", timeZone)
        return rpc("session/prompt", JSONObject().put("request", request))
    }

    /** History AND the live continuation, on one stream. */
    fun followSession(sessionId: String, maxMessages: Int = 50, onItem: (JSONObject) -> Unit): String {
        val request = JSONObject()
            .put("address", JSONObject().put("kind", "session").put("sessionId", sessionId))
            .put("maxMessages", maxMessages)
            .put("assistantStream", true)
        return openStream("session/follow", JSONObject().put("request", request), onItem)
    }

    fun followWorkspaces(onItem: (JSONObject) -> Unit): String = openStream("workspace/follow", JSONObject(), onItem)

    fun followEvents(onItem: (JSONObject) -> Unit): String = openStream("\$events", JSONObject(), onItem)
}
