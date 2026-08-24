package com.dsh.harness.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

class RpcException(val code: String, override val message: String) : Exception(message)

class DshApi(private val base: String) {
    private val json = "application/json".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS) // long reads (history), prompt returns quickly
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    fun rpc(method: String, payload: JSONObject = JSONObject()): JSONObject {
        val env = JSONObject()
            .put("type", "client-request")
            .put("rpcId", UUID.randomUUID().toString())
            .put("method", method)
            .put("payload", payload)
        val req = Request.Builder().url("$base/api/$method").post(env.toString().toRequestBody(json)).build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: throw RpcException("transport", "empty response ${resp.code}")
            val server = JSONObject(text)
            val result = server.optJSONObject("result") ?: throw RpcException("transport", "missing result")
            if (!result.optBoolean("ok")) {
                val e = result.optJSONObject("error")
                throw RpcException(e?.optString("code") ?: "error", e?.optString("message") ?: "rpc failed")
            }
            return result.optJSONObject("value") ?: JSONObject()
        }
    }

    /** Answer a server request (a pending question) by echoing its rpcId. */
    fun respond(rpcId: String, value: JSONObject): Response? {
        val env = JSONObject()
            .put("type", "client-response")
            .put("rpcId", rpcId)
            .put("result", JSONObject().put("ok", true).put("value", value))
        val req = Request.Builder().url("$base/api/respond").post(env.toString().toRequestBody(json)).build()
        return client.newCall(req).execute()
    }

    /** Plain GET returning the raw body text (for the version manifest). */
    fun get(path: String): String {
        val req = Request.Builder().url("$base$path").build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw RpcException("http", "HTTP ${resp.code}")
            return resp.body?.string() ?: ""
        }
    }

    /** Downlink-only events stream: server pushes ServerRequest frames as text. */
    fun openMux(onFrame: (JSONObject) -> Unit, onFailure: (String) -> Unit): WebSocket {
        val req = Request.Builder().url("$base/api/events.mux").build()
        val listener = object : WebSocketListener() {
            override fun onMessage(ws: WebSocket, text: String) {
                try { onFrame(JSONObject(text)) } catch (_: Exception) {}
            }
            override fun onFailure(ws: WebSocket, t: Throwable, resp: Response?) {
                onFailure(t.message ?: "mux failure")
            }
        }
        return client.newWebSocket(req, listener)
    }
}
