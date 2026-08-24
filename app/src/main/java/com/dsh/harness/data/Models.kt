package com.dsh.harness.data

import org.json.JSONArray
import org.json.JSONObject

data class SessionItem(
    val sessionId: String,
    val title: String,
    val updatedAt: Long,
    val running: Boolean
)

/** A single renderable chat row. */
data class MessageItem(
    val id: String,
    val role: String,        // "user" | "assistant" | "tool" | "system"
    val text: String,
    val reasoning: String,
    val tool: String?,       // tool name + brief args for a tool call row
    val time: Long
)

/** A pending ask_user question awaiting an answer. */
data class QuestionItem(
    val rpcId: String,
    val id: String,
    val header: String,
    val question: String,
    val detail: String,
    val options: List<String>,
    val multiSelect: Boolean
)

/** One workspace (a directory with an ordered session account). */
data class WorkspaceItem(
    val workspaceId: String,
    val path: String,
    val title: String,
    val sessionIds: List<String>
)

/** One file/dir row from host.listDirectory. */
data class FileItem(
    val name: String,
    val path: String,
    val hidden: Boolean
)

/** Model picker option. */
data class ModelOption(
    val provider: String,
    val model: String
)

/** An image chosen by the user, ready to attach to the next prompt. */
data class PendingImage(
    val base64: String,
    val mime: String,
    val name: String
)

/** Version information advertised by the DSH host for the mobile app. */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String
)

object Parse {

    fun workspaces(json: JSONObject): List<WorkspaceItem> {
        val items = json.optJSONArray("items") ?: JSONArray()
        val out = ArrayList<WorkspaceItem>(items.length())
        for (i in 0 until items.length()) {
            val it = items.getJSONObject(i)
            val ids = it.optJSONArray("sessionIds") ?: JSONArray()
            val list = ArrayList<String>(ids.length())
            for (j in 0 until ids.length()) list.add(ids.getString(j))
            out.add(WorkspaceItem(it.optString("workspaceId"), it.optString("path"), it.optString("title"), list))
        }
        return out
    }

    fun files(json: JSONObject): List<FileItem> {
        val entries = json.optJSONArray("entries") ?: JSONArray()
        val out = ArrayList<FileItem>(entries.length())
        for (i in 0 until entries.length()) {
            val it = entries.getJSONObject(i)
            out.add(FileItem(it.optString("name"), it.optString("path"), it.optBoolean("hidden")))
        }
        out.sortBy { it.name.lowercase() }
        return out
    }

    fun modelOptions(json: JSONObject): Pair<List<ModelOption>, ModelOption?> {
        val groups = json.optJSONArray("groups") ?: JSONArray()
        val out = ArrayList<ModelOption>()
        val cur = json.optJSONObject("current")
        val current = if (cur != null) ModelOption(cur.optString("provider"), cur.optString("model")) else null
        for (i in 0 until groups.length()) {
            val g = groups.getJSONObject(i)
            val provider = g.optString("id")
            val models = g.optJSONArray("models") ?: JSONArray()
            for (j in 0 until models.length()) {
                val m = models.getJSONObject(j)
                out.add(ModelOption(provider, m.optString("id")))
            }
        }
        return Pair(out, current)
    }

    fun sessionList(json: JSONObject): List<SessionItem> {
        val items = json.optJSONArray("items") ?: JSONArray()
        val out = ArrayList<SessionItem>(items.length())
        for (i in 0 until items.length()) {
            val it = items.getJSONObject(i)
            val projections = it.optJSONObject("projections")
            val title = projections?.optJSONObject("values")?.optJSONObject("title")?.optString("value")
                ?: projections?.optJSONObject("values")?.optString("title")
                ?: "(untitled)"
            out.add(
                SessionItem(
                    sessionId = it.optString("sessionId"),
                    title = title,
                    updatedAt = it.optLong("updatedAt"),
                    running = it.optBoolean("running")
                )
            )
        }
        out.sortByDescending { it.updatedAt }
        return out
    }

    /** Build the visible message list from session.history events. */
    fun historyMessages(json: JSONObject): MutableList<MessageItem> {
        val events = json.optJSONArray("events") ?: JSONArray()
        val msgs = ArrayList<MessageItem>()
        for (i in 0 until events.length()) {
            val event = events.getJSONObject(i).optJSONObject("event") ?: continue
            val type = event.optString("type")
            when (type) {
                "user/message" -> userMessage(event)?.let { msgs.add(it) }
                "assistant/message" -> assistantMessage(event)?.let { msgs.add(it) }
                else -> { /* tool/call, tool/result, chunks are folded into messages */ }
            }
        }
        return msgs
    }

    /** The smallest seq among history events (the paging anchor for loadOlder). */
    fun firstSeq(json: JSONObject): Int {
        val events = json.optJSONArray("events") ?: JSONArray()
        if (events.length() == 0) return 0
        return events.getJSONObject(0).optJSONObject("event")?.optInt("seq", 0) ?: 0
    }

    fun hasMore(json: JSONObject): Boolean = json.optBoolean("hasMore")

    fun userMessage(event: JSONObject): MessageItem? {
        val data = event.optJSONObject("data") ?: return null
        val message = data.optJSONObject("message") ?: return null
        val sourceKind = message.optJSONObject("source")?.optString("kind")
        val content = message.optJSONArray("content") ?: JSONArray()
        val text = StringBuilder()
        for (i in 0 until content.length()) {
            val b = content.getJSONObject(i)
            if (b.optString("type") == "text") text.append(b.optString("text"))
        }
        if (sourceKind == "tool") {
            return MessageItem(
                id = message.optString("id"),
                role = "tool",
                text = text.toString(),
                reasoning = "",
                tool = "🔧 tool result",
                time = event.optLong("time")
            )
        }
        return MessageItem(
            id = message.optString("id"),
            role = "user",
            text = text.toString(),
            reasoning = "",
            tool = "",
            time = event.optLong("time")
        )
    }

    fun assistantMessage(event: JSONObject): MessageItem? {
        val data = event.optJSONObject("data") ?: return null
        val message = data.optJSONObject("message") ?: return null
        val content = message.optJSONArray("content") ?: JSONArray()
        val text = StringBuilder()
        val reasoning = StringBuilder()
        val tools = StringBuilder()
        for (i in 0 until content.length()) {
            val b = content.getJSONObject(i)
            when (b.optString("type")) {
                "text" -> text.append(b.optString("text"))
                "reasoning" -> reasoning.append(b.optString("text"))
                "tool-call" -> {
                    if (tools.isNotEmpty()) tools.append(", ")
                    tools.append(b.optString("name"))
                }
            }
        }
        if (text.isEmpty() && reasoning.isEmpty() && tools.isEmpty()) return null
        return MessageItem(
            id = message.optString("id"),
            role = "assistant",
            text = text.toString(),
            reasoning = reasoning.toString(),
            tool = tools.toString(),
            time = event.optLong("time")
        )
    }

    fun question(rpcId: String, payload: JSONObject): List<QuestionItem> {
        val qs = payload.optJSONArray("questions") ?: JSONArray()
        val out = ArrayList<QuestionItem>(qs.length())
        for (i in 0 until qs.length()) {
            val q = qs.getJSONObject(i)
            val opts = JSONArray()
            val optionsArr = q.optJSONArray("options") ?: JSONArray()
            for (j in 0 until optionsArr.length()) {
                opts.put(optionsArr.getJSONObject(j).optString("label"))
            }
            val optsList = List(opts.length()) { opts.getString(it) }
            out.add(
                QuestionItem(
                    rpcId = rpcId,
                    id = q.optString("id"),
                    header = q.optString("header"),
                    question = q.optString("question"),
                    detail = q.optString("detail"),
                    options = optsList,
                    multiSelect = q.optBoolean("multiSelect")
                )
            )
        }
        return out
    }
}
