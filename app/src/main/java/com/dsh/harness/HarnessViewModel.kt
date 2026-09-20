package com.dsh.harness

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dsh.harness.data.DshApi
import com.dsh.harness.data.FileItem
import com.dsh.harness.data.MessageItem
import com.dsh.harness.data.ModelOption
import com.dsh.harness.data.Parse
import com.dsh.harness.data.PendingImage
import com.dsh.harness.data.QuestionItem
import com.dsh.harness.data.SessionItem
import com.dsh.harness.data.UpdateInfo
import com.dsh.harness.data.WorkspaceItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class HarnessViewModel : ViewModel() {

    private var api: DshApi? = null
    private var baseUrl: String = ""
    private var authToken: String? = null
    private var hostCwd: String? = null
    private var mux: okhttp3.WebSocket? = null
    private var followStreamId: String? = null

    private val _sessions = MutableStateFlow<List<SessionItem>>(emptyList())
    val sessions: StateFlow<List<SessionItem>> = _sessions.asStateFlow()

    private val _messages = MutableStateFlow<List<MessageItem>>(emptyList())
    val messages: StateFlow<List<MessageItem>> = _messages.asStateFlow()

    private val _pendingQuestion = MutableStateFlow<QuestionItem?>(null)
    val pendingQuestion: StateFlow<QuestionItem?> = _pendingQuestion.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _status = MutableStateFlow("Disconnected")
    val status: StateFlow<String> = _status.asStateFlow()

    // model switcher
    private val _models = MutableStateFlow<List<ModelOption>>(emptyList())
    val models: StateFlow<List<ModelOption>> = _models.asStateFlow()
    private val _currentModel = MutableStateFlow<ModelOption?>(null)
    val currentModel: StateFlow<ModelOption?> = _currentModel.asStateFlow()

    // workspace / file browser
    private val _workspaces = MutableStateFlow<List<WorkspaceItem>>(emptyList())
    val workspaces: StateFlow<List<WorkspaceItem>> = _workspaces.asStateFlow()
    private val _files = MutableStateFlow<List<FileItem>>(emptyList())
    val files: StateFlow<List<FileItem>> = _files.asStateFlow()
    private val _currentDir = MutableStateFlow<String?>(null)
    val currentDir: StateFlow<String?> = _currentDir.asStateFlow()

    // attachment
    private val _pendingImage = MutableStateFlow<PendingImage?>(null)
    val pendingImage: StateFlow<PendingImage?> = _pendingImage.asStateFlow()

    // loaded chat images keyed by attachmentId -> base64
    private val _images = MutableStateFlow<Map<String, String>>(emptyMap())
    val images: StateFlow<Map<String, String>> = _images.asStateFlow()

    fun loadImage(attachmentId: String) {
        val a = api ?: return
        val sessionId = currentSessionId ?: return
        if (_images.value.containsKey(attachmentId)) return
        viewModelScope.launch {
            try {
                // session.attachment has no counterpart in DSH 0.1.5 yet.
                val unused = sessionId + attachmentId
            } catch (_: Exception) {}
        }
    }

    // ---- Shared files (download/preview over Tailscale) ----
    val sharedPath: String? get() = hostCwd?.let { "$it\\shared" }

    private val _sharedFiles = MutableStateFlow<List<FileItem>>(emptyList())
    val sharedFiles: StateFlow<List<FileItem>> = _sharedFiles.asStateFlow()

    fun loadSharedFiles() {
        val a = api ?: return
        val path = sharedPath ?: return
        viewModelScope.launch {
            try {
                // Listing needs host.listDirectory, absent in DSH 0.1.5.
                val unused = path
                _sharedFiles.value = emptyList()
            } catch (_: Exception) {}
        }
    }

    fun sharedFileUrl(name: String): String = "$baseUrl/shared/$name"

    /** Fetches a shared file's bytes over Tailscale. */
    suspend fun fetchSharedFile(name: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val req = okhttp3.Request.Builder().url(sharedFileUrl(name)).build()
            okhttp3.OkHttpClient().newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.bytes() else null
            }
        } catch (_: Exception) { null }
    }

    // auto-update
    private val _update = MutableStateFlow<UpdateInfo?>(null)
    val update: StateFlow<UpdateInfo?> = _update.asStateFlow()

    private var currentSessionId: String? = null
    private var streaming: MutableList<String> = ArrayList() // deltas of current assistant msg
    private var reasoningStream: MutableList<String> = ArrayList()
    private var earliestSeq = 0

    private val _thinking = MutableStateFlow(false)
    val thinking: StateFlow<Boolean> = _thinking.asStateFlow()

    private val _canLoadOlder = MutableStateFlow(false)
    val canLoadOlder: StateFlow<Boolean> = _canLoadOlder.asStateFlow()

    fun checkForUpdate(installedCode: Int) {
        val a = api ?: return
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) { a.get("/harness-version.json") }
                val j = JSONObject(text)
                val code = j.optInt("versionCode")
                if (code > installedCode) {
                    val rel = j.optString("downloadUrl", "/harness.apk")
                    _update.value = UpdateInfo(code, j.optString("versionName", "$code"), baseUrl + rel)
                } else {
                    _update.value = null
                }
            } catch (_: Exception) {
                _update.value = null
            }
        }
    }

    // latest GitHub release available (checked from Settings)
    private val _githubUpdate = MutableStateFlow<UpdateInfo?>(null)
    val githubUpdate: StateFlow<UpdateInfo?> = _githubUpdate.asStateFlow()
    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()
    private val _checkMsg = MutableStateFlow<String?>(null)
    val checkMsg: StateFlow<String?> = _checkMsg.asStateFlow()

    fun checkGitHubUpdate(installedVersionName: String) {
        _checking.value = true
        _checkMsg.value = null
        viewModelScope.launch {
            try {
                val url = "https://api.github.com/repos/morlocul/deepseek-harness-mobile/releases/latest"
                val text = withContext(Dispatchers.IO) {
                    okhttp3.OkHttpClient.Builder()
                        .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                        .newCall(okhttp3.Request.Builder().url(url)
                        .header("Accept", "application/vnd.github+json").build())
                        .execute().use { resp -> if (resp.isSuccessful) resp.body?.string() else null }
                }
                if (text == null) { _checkMsg.value = "Check failed (network?)"; return@launch }
                val j = JSONObject(text)
                val tag = j.optString("tag_name").removePrefix("v")
                val assets = j.optJSONArray("assets")
                var apkUrl = ""
                for (i in 0 until (assets?.length() ?: 0)) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk")) { apkUrl = a.optString("browser_download_url"); break }
                }
                if (compareVersion(tag, installedVersionName) > 0 && apkUrl.isNotBlank()) {
                    _githubUpdate.value = UpdateInfo(0, tag, apkUrl)
                    _checkMsg.value = "Update available: v$tag"
                } else {
                    _githubUpdate.value = null
                    _checkMsg.value = "You're up to date (v$installedVersionName)"
                }
            } catch (e: Exception) {
                _githubUpdate.value = null
                _checkMsg.value = "Check failed: ${e.message}"
            } finally {
                _checking.value = false
            }
        }
    }

    private fun compareVersion(a: String, b: String): Int {
        val as_ = a.split(".").mapNotNull { it.toIntOrNull() }
        val bs = b.split(".").mapNotNull { it.toIntOrNull() }
        val n = maxOf(as_.size, bs.size)
        for (i in 0 until n) {
            val x = if (i < as_.size) as_[i] else 0
            val y = if (i < bs.size) bs[i] else 0
            if (x != y) return if (x > y) 1 else -1
        }
        return 0
    }

    /**
     * Accepts either a bare address or the full link DSH prints on start
     * (http://host:3080/?token=XYZ). The token is split off here so the user has
     * nothing extra to type: paste the whole line and it works.
     */
    fun setBase(url: String) {
        val raw = url.trim()
        var addr = raw
        var tok: String? = null
        val q = raw.indexOf('?')
        if (q >= 0) {
            addr = raw.substring(0, q)
            for (part in raw.substring(q + 1).split('&')) {
                val kv = part.split('=', limit = 2)
                if (kv.size == 2 && kv[0] == "token" && kv[1].isNotBlank()) tok = kv[1]
            }
        }
        baseUrl = addr.trimEnd('/')
        authToken = tok
        api = DshApi(baseUrl, tok)
    }

    fun currentBase(): String = baseUrl

    fun connect(onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        val a = api ?: return
        viewModelScope.launch {
            _status.value = "Connecting…"
            try {
                withContext(Dispatchers.IO) { a.authenticate() }
                startMux()
                _status.value = "Connected"
                loadWorkspaces()
                refreshSessions(onError)
                onSuccess()
            } catch (e: Exception) {
                _status.value = "Eroare: ${e.message}"
                onError(e.message ?: "eroare")
            }
        }
    }

    fun refreshSessions(onError: (String) -> Unit = {}) {
        val a = api ?: return
        viewModelScope.launch {
            try {
                val json = withContext(Dispatchers.IO) { a.sessionList() }
                _sessions.value = Parse.sessionList(json)
            } catch (e: Exception) {
                onError(e.message ?: "eroare listare")
            }
        }
    }

    fun openSession(item: SessionItem) {
        val a = api ?: return
        currentSessionId = item.sessionId
        _messages.value = emptyList()
        _pendingQuestion.value = null
        startMux()
        loadModels()
        refreshSessions()
        // One stream carries the history AND every later event for this session.
        followStreamId?.let { old -> try { a.closeStream(old) } catch (_: Exception) {} }
        _busy.value = true
        try {
            followStreamId = a.followSession(item.sessionId, 50) { value ->
                when (value.optString("type")) {
                    "event" -> handleEvent(value.optJSONObject("event"))
                    "projection" -> { /* title / permissions / turnOutline: not rendered directly */ }
                }
                _busy.value = false
            }
        } catch (e: Exception) {
            _busy.value = false
            _messages.value = listOf(MessageItem("e", "system", "Error reading: ${e.message}", "", "", 0))
        }
        _canLoadOlder.value = false
    }

    /** Loads an earlier page (older messages) prepended to the current list. */
    fun loadOlder() {
        val a = api ?: return
        val sessionId = currentSessionId ?: return
        // DSH 0.1.5 has no history paging endpoint; session/follow delivers a
        // fixed window. Older pages are unavailable until one is reinstated.
        _canLoadOlder.value = false
        if (true) return
        viewModelScope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    a.rpc("session.history", JSONObject()
                        .put("sessionId", sessionId)
                        .put("beforeSeq", earliestSeq)
                        .put("maxMessages", 40))
                }
                val older = Parse.historyMessages(json)
                if (older.isEmpty()) { _canLoadOlder.value = false; return@launch }
                _messages.value = older + _messages.value
                val newEarliest = Parse.firstSeq(json)
                if (newEarliest > 0 && newEarliest < earliestSeq) earliestSeq = newEarliest
                _canLoadOlder.value = Parse.hasMore(json) && Parse.firstSeq(json) > 0
            } catch (_: Exception) {}
        }
    }

    fun newSession() {
        val a = api ?: return
        viewModelScope.launch {
            try {
                val wsId = _workspaces.value.firstOrNull()?.workspaceId
                    ?: throw IllegalStateException("no workspace yet")
                val json = withContext(Dispatchers.IO) { a.createSession(wsId) }
                val id = json.optString("sessionId")
                openSession(SessionItem(id, "New conversation", System.currentTimeMillis(), false))
                refreshSessions()
            } catch (e: Exception) {
                _status.value = "Error creating: ${e.message}"
            }
        }
    }

    fun send(text: String) {
        val a = api ?: return
        val sessionId = currentSessionId ?: return
        val img = _pendingImage.value
        if (text.isBlank() && img == null) return
        // optimistic user message
        val list = _messages.value.toMutableList()
        list.add(MessageItem("local-${System.currentTimeMillis()}", "user", text.ifBlank { "🖼" }, "", "", System.currentTimeMillis()))
        _messages.value = list
        _pendingImage.value = null
        streaming = ArrayList()
        reasoningStream = ArrayList()
        _thinking.value = true
        viewModelScope.launch {
            try {
                val content = org.json.JSONArray()
                img?.let {
                    content.put(JSONObject()
                        .put("type", "image")
                        .put("mediaType", it.mime)
                        .put("data", it.base64)
                        .put("name", it.name))
                }
                if (text.isNotBlank()) {
                    content.put(JSONObject().put("type", "text").put("text", text))
                }
                val tz = java.util.TimeZone.getDefault().id
                withContext(Dispatchers.IO) { a.prompt(sessionId, text, tz) }
                refreshSessions()
            } catch (e: Exception) {
                val l = _messages.value.toMutableList()
                l.add(MessageItem("err", "system", "Error sending: ${e.message}", "", "", System.currentTimeMillis()))
                _messages.value = l
            }
        }
    }

    fun setPendingImage(img: PendingImage?) { _pendingImage.value = img }

    fun loadModels() {
        val a = api ?: return
        val sessionId = currentSessionId ?: return
        viewModelScope.launch {
            try {
                val json = withContext(Dispatchers.IO) { a.modelCatalog() }
                val (opts, cur) = Parse.modelOptions(json)
                _models.value = opts
                _currentModel.value = cur
            } catch (_: Exception) {}
        }
    }

    fun selectModel(provider: String, model: String) {
        val a = api ?: return
        val sessionId = currentSessionId ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    a.rpc("session/selectModel", JSONObject().put("request", JSONObject()
                        .put("sessionId", sessionId)
                        .put("provider", provider)
                        .put("model", model)))
                }
                _currentModel.value = ModelOption(provider, model)
                _status.value = "Model: $model"
            } catch (e: Exception) {
                _status.value = "Error selecting model: ${e.message}"
            }
        }
    }

    fun loadWorkspaces() {
        val a = api ?: return
        viewModelScope.launch {
            try {
                a.followWorkspaces { value ->
                    val t = value.optString("type")
                    if (t == "baseline") {
                        value.optJSONObject("value")?.let { _workspaces.value = Parse.workspaces(it) }
                        _workspaces.value.firstOrNull()?.let { hostCwd = it.path }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun listDir(path: String?) {
        val a = api ?: return
        viewModelScope.launch {
            try {
                // host.listDirectory has no counterpart in DSH 0.1.5 yet.
                _files.value = emptyList()
                _currentDir.value = path
                _status.value = "File browsing is unavailable on this DSH version"

            } catch (e: Exception) {
                _status.value = "Error listing: ${e.message}"
            }
        }
    }

    fun answer(questionId: String, selected: List<String>, custom: String) {
        val a = api ?: return
        val q = _pendingQuestion.value ?: return
        val answerObj = JSONObject()
            .put("sessionId", currentSessionId ?: "")
            .put("answer", JSONObject()
                .put("answers", org.json.JSONArray().put(JSONObject()
                    .put("id", questionId)
                    .put("selected", org.json.JSONArray().also { sel -> selected.forEach { sel.put(it) } })
                    .apply { if (custom.isNotBlank()) put("custom", custom) })))
        viewModelScope.launch {
            try {
                // The ask_user round-trip is not ported to the mux protocol yet.
                withContext(Dispatchers.IO) { val unused = a.toString() + answerObj.toString() + q.rpcId }
            } catch (e: Exception) {
            } finally {
                _pendingQuestion.value = null
            }
        }
    }

    private fun startMux() {
        val a = api ?: return
        if (mux != null) return
        mux = a.connectMux { msg -> _status.value = "Stream lost: $msg" }
        a.followEvents { value ->
            if (value.optString("type") == "emit" && value.optString("event").startsWith("api-session/")) {
                refreshSessions()
            }
        }
    }

    private fun handleFrame(frame: JSONObject) {
        val sessionId = frame.optJSONObject("payload")?.optString("sessionId")
        if (sessionId != currentSessionId) return
        val payload = frame.optJSONObject("payload") ?: return
        when (payload.optString("type")) {
            "session/event" -> handleEvent(payload.optJSONObject("event"))
            "question/requested" -> {
                val qs = Parse.question(frame.optString("rpcId"), payload)
                _pendingQuestion.value = qs.firstOrNull()
            }
            "question/resolved" -> _pendingQuestion.value = null
        }
    }

    private fun handleEvent(event: JSONObject?) {
        if (event == null) return
        val type = event.optString("type")
        val list = _messages.value.toMutableList()
        when (type) {
            "user/message" -> {
                val m = Parse.userMessage(event)
                if (m != null) {
                    if (m.text.isNotBlank()) {
                        // replace the optimistic local message with the authoritative one
                        list.removeAll { it.id.startsWith("local-") }
                        list.add(m)
                    }
                    // else: keep the optimistic message (it already has the user's text)
                }
            }
            "assistant/chunk" -> {
                val chunk = event.optJSONObject("data")?.optJSONObject("chunk")
                val ctype = chunk?.optString("type")
                if (ctype == "text-delta") {
                    _thinking.value = true
                    streaming.add(chunk.optString("text"))
                    upsertStreaming(list, streaming.joinToString(""), reasoningStream.joinToString(""))
                } else if (ctype == "reasoning-delta") {
                    _thinking.value = true
                    reasoningStream.add(chunk.optString("text"))
                    upsertStreaming(list, streaming.joinToString(""), reasoningStream.joinToString(""))
                }
            }
            "assistant/message" -> {
                val m = Parse.assistantMessage(event)
                _thinking.value = false
                if (m != null) {
                    streaming = ArrayList()
                    reasoningStream = ArrayList()
                    // drop the streaming placeholder if present
                    if (list.isNotEmpty() && list.last().id.startsWith("stream-")) list.removeAt(list.lastIndex)
                    list.add(m)
                }
            }
        }
        _messages.value = list
    }

    private fun upsertStreaming(list: MutableList<MessageItem>, text: String, reasoning: String) {
        if (list.isNotEmpty() && list.last().id.startsWith("stream-")) {
            list[list.lastIndex] = list.last().copy(text = text, reasoning = reasoning)
        } else {
            list.add(MessageItem("stream-${System.currentTimeMillis()}", "assistant", text, reasoning, "", System.currentTimeMillis()))
        }
    }
}
