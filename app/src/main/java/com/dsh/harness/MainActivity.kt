package com.dsh.harness

import android.os.Bundle
import android.util.Base64
import android.widget.Toast
import android.content.Context
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import android.graphics.BitmapFactory
import com.dsh.harness.data.FileItem
import com.dsh.harness.data.MessageItem
import com.dsh.harness.data.PendingImage
import com.dsh.harness.data.QuestionItem
import com.dsh.harness.data.WorkspaceItem
import com.dsh.harness.ui.LocalHarness
import com.dsh.harness.ui.ThemeMode
import com.dsh.harness.ui.ThemePrefs
import com.dsh.harness.ui.effectiveDark
import com.dsh.harness.ui.qrBitmap
import com.dsh.harness.ui.themeColorScheme
import com.dsh.harness.ui.tokens
import com.mikepenz.markdown.m3.Markdown
import kotlinx.coroutines.launch

private val Dark = darkColorScheme(
    primary = Color(0xFF8AB4FF),
    background = Color(0xFF0F1115),
    surface = Color(0xFF171A1F),
    onSurface = Color(0xFFE6E6E6),
    onBackground = Color(0xFFE6E6E6)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ThemePrefs.load(this)
        // deep link: harness://open?url=<encoded-address>
        val initialUrl = intent?.data?.getQueryParameter("url")
        setContent {
            val mode = ThemePrefs.mode.value
            val isDark = effectiveDark(mode)
            val t = tokens(isDark)
            MaterialTheme(colorScheme = themeColorScheme(isDark)) {
                CompositionLocalProvider(LocalHarness provides t) {
                    HarnessApp(initialUrl = initialUrl)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HarnessApp(vm: HarnessViewModel = viewModel(), initialUrl: String? = null) {
    var connected by remember { mutableStateOf(false) }
    var openSessionId by remember { mutableStateOf<String?>(null) }
    var openTitle by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf(0) } // 0=conversations 1=workspace
    var showSettings by remember { mutableStateOf(false) }
    val sessions by vm.sessions.collectAsState()
    val workspaces by vm.workspaces.collectAsState()
    val update by vm.update.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    BackHandler(enabled = openSessionId != null) { openSessionId = null; openTitle = null }
    BackHandler(enabled = openSessionId == null && showSettings) { showSettings = false }

    Box(Modifier.fillMaxSize()) {
        when {
            !connected -> ConnectScreen(vm, initialUrl = initialUrl, onConnected = {
                connected = true
                vm.refreshSessions()
                vm.loadWorkspaces()
                vm.checkForUpdate(UpdateManager.installedCode(context))
            })
            openSessionId != null -> ChatScreen(
                vm,
                sessionId = openSessionId!!,
                title = openTitle,
                onBack = { openSessionId = null; openTitle = null }
            )
            else -> Column(Modifier.fillMaxSize()) {
                update?.let { u ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF26314A)),
                        modifier = Modifier.fillMaxWidth().padding(8.dp)
                    ) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Versiune ${u.versionName} disponibilă", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Button(onClick = {
                                scope.launch {
                                    if (UpdateManager.needsInstallPermission(context)) {
                                        UpdateManager.openInstallPermissionSettings(context)
                                    } else {
                                        val file = java.io.File(context.cacheDir, "harness-update.apk")
                                        val size = UpdateManager.download(u.downloadUrl, file)
                                        if (size > 5_000_000) {
                                            UpdateManager.install(context, file)
                                        } else {
                                            Toast.makeText(context, "Descărcare incompletă ($size B)", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            }) { Text("Actualizează", fontSize = 12.sp) }
                        }
                    }
                }
                Scaffold(
                    bottomBar = {
                        Row(
                            Modifier.fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .navigationBarsPadding()
                                .padding(6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(onClick = { tab = 0; showSettings = false }, modifier = Modifier.weight(1f)) {
                                Text("Conversații", fontSize = 13.sp)
                            }
                            OutlinedButton(onClick = { tab = 1; showSettings = false }, modifier = Modifier.weight(1f)) {
                                Text("Workspace", fontSize = 13.sp)
                            }
                            OutlinedButton(onClick = { showSettings = !showSettings }, modifier = Modifier.weight(0.7f)) {
                                Text("⚙", fontSize = 14.sp)
                            }
                        }
                    }
                ) { pad ->
                    if (showSettings) {
                        SettingsScreen(context = context, baseUrl = vm.currentBase())
                    } else if (tab == 0) {
                        SessionsScreen(
                            vm, sessions = sessions, workspaces = workspaces,
                            onOpen = {
                                vm.openSession(it)
                                openSessionId = it.sessionId
                                openTitle = it.title
                                showSettings = false
                            },
                            contentPadding = pad
                        )
                    } else {
                        WorkspaceScreen(vm, workspaces = workspaces, contentPadding = pad)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(context: Context, baseUrl: String) {
    val t = LocalHarness.current
    val mode = ThemePrefs.mode.value
    val options = listOf(
        ThemeMode.SYSTEM to "Automat (urmează sistemul)",
        ThemeMode.LIGHT to "Luminos",
        ThemeMode.DARK to "Întunecat"
    )
    val qrLink = if (baseUrl.isNotBlank()) "harness://open?url=" + Uri.encode(baseUrl) else ""
    val qr = remember(qrLink) { if (qrLink.isNotBlank()) qrBitmap(qrLink) else null }
    Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())) {
        Text("SETĂRI", fontFamily = FontFamily.Monospace, fontSize = 12.sp,
            letterSpacing = 3.sp, color = t.accentText, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("Temă", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        options.forEach { (m, label) ->
            Card(
                onClick = { ThemePrefs.save(context, m) },
                colors = CardDefaults.cardColors(
                    containerColor = if (mode == m) t.accentSoft else t.surface
                ),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (mode == m) "●" else "○",
                        color = if (mode == m) t.accent else t.muted,
                        fontSize = 16.sp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("Configurare rapidă", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        if (baseUrl.isNotBlank()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                qr?.let {
                    Image(bitmap = it.asImageBitmap(), contentDescription = "QR configurare",
                        modifier = Modifier.size(200.dp).background(Color.White))
                }
                Spacer(Modifier.height(8.dp))
                Text("Scanează QR-ul cu un cititor — se deschide app-ul cu adresa\n$baseUrl\nprecompletată.",
                    style = MaterialTheme.typography.bodySmall, color = t.muted, lineHeight = 16.sp)
            }
        } else {
            Text("Conectează-te întâi ca să generezi QR-ul de configurare.",
                style = MaterialTheme.typography.bodySmall, color = t.muted)
        }
        Spacer(Modifier.height(24.dp))
        Text("Despre", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("Harness · client nativ DeepSeek Harness pe Tailscale.\nSesiuni, model, workspace și fișiere în direct.",
            style = MaterialTheme.typography.bodySmall, color = t.muted, lineHeight = 18.sp)
    }
}

@Composable
private fun ConnectScreen(vm: HarnessViewModel, initialUrl: String? = null, onConnected: () -> Unit) {
    val t = LocalHarness.current
    var url by remember(initialUrl) { mutableStateOf(initialUrl ?: "") }
    var showHelp by remember { mutableStateOf(false) }
    val status by vm.status.collectAsState()
    val steps = listOf(
        "Instalează Tailscale pe PC și telefon, conectate la același tailnet.",
        "Pe PC, rulează: powershell -File setup-dsh-remote.ps1 (o dată).",
        "Repornește DSH (Ctrl+C, apoi 'ollama launch dsh').",
        "Introdu mai jos adresa afișată de script (ex. http://nume.ts.net:3080)."
    )
    Column(
        Modifier.fillMaxSize().padding(24.dp).imePadding().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Text("HARNESS", fontFamily = FontFamily.Monospace, fontSize = 34.sp,
            letterSpacing = 6.sp, fontWeight = FontWeight.Bold, color = t.accent)
        Text("DeepSeek Harness pe telefon", style = MaterialTheme.typography.bodyMedium, color = t.muted)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Adresă DSH") },
            placeholder = { Text("http://<tailnet-ip>:3080") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Text("Adresa instanței tale DSH din Tailscale (ex. http://100.x.y.z:3080 sau http://nume.ts.net:3080)",
            style = MaterialTheme.typography.bodySmall, color = t.muted)
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { vm.setBase(url); vm.connect(onSuccess = { onConnected() }) },
            enabled = url.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Conectează-te") }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { showHelp = !showHelp }) {
            Text(if (showHelp) "Ascunde ajutorul" else "Cum mă conectez?")
        }
        if (showHelp) {
            Card(
                colors = CardDefaults.cardColors(containerColor = t.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    steps.forEachIndexed { i, s ->
                        Text("${i + 1}. $s", style = MaterialTheme.typography.bodySmall, lineHeight = 18.sp)
                    }
                }
            }
        }
        if (status != "Neconectat") {
            Spacer(Modifier.height(12.dp))
            Text(status, style = MaterialTheme.typography.bodySmall, color = t.muted)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionsScreen(
    vm: HarnessViewModel,
    sessions: List<com.dsh.harness.data.SessionItem>,
    workspaces: List<WorkspaceItem>,
    onOpen: (com.dsh.harness.data.SessionItem) -> Unit,
    contentPadding: PaddingValues
) {
    val t = LocalHarness.current

    // Group sessions by workspace (workspaces in registry order, then "neatribuite").
    val rows: List<Any> = remember(workspaces, sessions) {
        val result = ArrayList<Any>()
        for (w in workspaces) {
            val list = sessions.filter { it.sessionId in w.sessionIds }
            if (list.isNotEmpty()) {
                result.add(w.title)
                result.addAll(list)
            }
        }
        val ungrouped = sessions.filter { s -> workspaces.none { it.sessionIds.contains(s.sessionId) } }
        if (ungrouped.isNotEmpty()) {
            result.add("Fără workspace")
            result.addAll(ungrouped)
        }
        result
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("HARNESS", fontFamily = FontFamily.Monospace, fontSize = 18.sp,
                        letterSpacing = 4.sp, fontWeight = FontWeight.Bold, color = t.accent)
                },
                actions = {
                    IconButton(onClick = { vm.newSession() }) {
                        Icon(Icons.Filled.Add, contentDescription = "Sesiune nouă", tint = t.accentText)
                    }
                }
            )
        }
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rows.size) { idx ->
                val row = rows[idx]
                if (row is String) {
                    Text(row.uppercase(), fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                        letterSpacing = 2.sp, fontWeight = FontWeight.Bold, color = t.accentText,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
                } else if (row is com.dsh.harness.data.SessionItem) {
                    val s = row
                    Card(
                        onClick = { onOpen(s) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (s.running) {
                                Box(Modifier.width(3.dp).height(34.dp).background(t.accent, RoundedCornerShape(2)))
                                Spacer(Modifier.width(10.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(s.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                Text(s.timeText(), style = MaterialTheme.typography.bodySmall, color = t.muted)
                            }
                            if (s.running) {
                                Spacer(Modifier.width(8.dp))
                                Box(Modifier.size(8.dp).background(t.running, RoundedCornerShape(50)))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkspaceScreen(
    vm: HarnessViewModel,
    workspaces: List<WorkspaceItem>,
    contentPadding: PaddingValues
) {
    val files by vm.files.collectAsState()
    val currentDir by vm.currentDir.collectAsState()
    val t = LocalHarness.current

    // Open the project folder automatically the first time the tab is shown.
    LaunchedEffect(workspaces) {
        if (currentDir == null && workspaces.isNotEmpty()) {
            vm.listDir(workspaces.first().path)
        }
    }

    val displayName = currentDir?.substringAfterLast('\\')?.substringAfterLast('/') ?: "Workspace"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    if (currentDir != null) {
                        IconButton(onClick = { vm.listDir(workspaces.firstOrNull()?.path) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Rădăcina workspace")
                        }
                    }
                },
                actions = {
                    if (workspaces.isNotEmpty()) {
                        IconButton(onClick = { vm.listDir(workspaces.first().path) }) {
                            Icon(Icons.Filled.Folder, contentDescription = "Deschide workspace", tint = t.accentText)
                        }
                    }
                }
            )
        }
    ) { pad ->
        if (files.isEmpty() && currentDir == null && workspaces.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text("Niciun workspace disponibil.", color = t.muted)
            }
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(12.dp)) {
            if (workspaces.isNotEmpty() && currentDir != null) {
                item {
                    Text(workspaces.first().title, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                        letterSpacing = 2.sp, fontWeight = FontWeight.Bold, color = t.accentText,
                        modifier = Modifier.padding(bottom = 6.dp))
                }
            }
            items(files) { f ->
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (f.hidden) Color(0x221F1F1F) else Color.Transparent)
                        .clickable(role = androidx.compose.ui.semantics.Role.Button) {
                            if (f.name.endsWith("/") || f.name.isEmpty().not() && looksLikeDir(f.name)) {
                                vm.listDir(f.path)
                            }
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = Color.Gray
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(f.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                }
            }
        }
    }
}

private fun looksLikeDir(name: String): Boolean = !name.contains(".") || name.endsWith("/")

private fun com.dsh.harness.data.SessionItem.timeText(): String {
    val now = System.currentTimeMillis()
    val d = now - updatedAt
    return when {
        d < 60_000 -> "chiar acum"
        d < 3_600_000 -> "${d / 60_000} min în urmă"
        d < 86_400_000 -> "${d / 3_600_000} h în urmă"
        else -> "${d / 86_400_000} z în urmă"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreen(vm: HarnessViewModel, sessionId: String, title: String?, onBack: () -> Unit) {
    val messages by vm.messages.collectAsState()
    val question by vm.pendingQuestion.collectAsState()
    val models by vm.models.collectAsState()
    val currentModel by vm.currentModel.collectAsState()
    val pendingImage by vm.pendingImage.collectAsState()
    val canLoadOlder by vm.canLoadOlder.collectAsState()
    var input by remember { mutableStateOf("") }
    var modelMenu by remember { mutableStateOf(false) }
    var justOpened by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val context = LocalContext.current

    // Open at the newest messages; also follow when the user sends a message.
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && (justOpened || messages.last().role == "user")) {
            listState.scrollToItem(messages.lastIndex)
            justOpened = false
        }
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                val ins = context.contentResolver.openInputStream(uri)
                val bytes = ins?.readBytes()
                ins?.close()
                if (bytes != null) {
                    val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                    vm.setPendingImage(PendingImage(Base64.encodeToString(bytes, Base64.NO_WRAP), mime, "image"))
                }
            } catch (_: Exception) {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(title ?: "Conversație", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Înapoi") }
                },
                actions = {
                    Box {
                        OutlinedButton(
                            onClick = { modelMenu = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(currentModel?.model ?: "model", fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        DropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
                            models.forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(m.model, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    onClick = {
                                        modelMenu = false
                                        vm.selectModel(m.provider, m.model)
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).imePadding()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (canLoadOlder) {
                    item {
                        OutlinedButton(onClick = { vm.loadOlder() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Încarcă mesaje mai vechi", fontSize = 13.sp)
                        }
                    }
                }
                items(messages) { m -> MessageBubble(m) }
                question?.let { q -> item { QuestionCard(q) { id, sel, custom -> vm.answer(id, sel, custom) } } }
            }
            pendingImage?.let {
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🖼 imagine atașată", style = MaterialTheme.typography.bodySmall, color = Color(0xFF9AC7FF))
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { vm.setPendingImage(null) }, modifier = Modifier.size(28.dp)) {
                        Text("✕", fontSize = 14.sp)
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { pickImage.launch("image/*") }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = "Atașează imagine")
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Scrie…") },
                    maxLines = 4
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        val t = input.trim()
                        if (t.startsWith("/model", ignoreCase = true)) {
                            modelMenu = true
                            input = ""
                        } else if (t.isNotEmpty() || pendingImage != null) {
                            vm.send(t); input = ""; scope.launch { listState.scrollToItem(Int.MAX_VALUE) }
                        }
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Trimite")
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(m: MessageItem) {
    val t = LocalHarness.current
    val mine = m.role == "user"
    val bg = if (mine) t.accentSoft else t.surfaceAlt
    val role = when (m.role) {
        "user" -> "TU"
        "assistant" -> "AI"
        "tool" -> "TOOL"
        else -> "SYS"
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        // ledger rail: role + accent tick
        Column(
            Modifier.width(44.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(role, fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                letterSpacing = 1.sp, color = if (mine) t.accentText else t.muted)
            Spacer(Modifier.height(4.dp))
            Box(Modifier.width(2.dp).height(18.dp).background(if (mine) t.accent else t.hairline))
        }
        Card(
            colors = CardDefaults.cardColors(
                containerColor = bg,
                contentColor = if (mine) Color.White else t.text
            ),
            modifier = Modifier.weight(1f)
        ) {
            Column(Modifier.padding(12.dp)) {
                if (!m.tool.isNullOrBlank()) {
                    Text(m.tool, style = MaterialTheme.typography.bodySmall, color = if (mine) Color(0xFFDCE4FF) else t.accentText)
                }
                if (m.reasoning.isNotBlank()) {
                    Text("💭", style = MaterialTheme.typography.bodySmall, color = if (mine) Color(0xFFCFD8FF) else t.muted)
                }
                if (mine) {
                    Text(m.text.ifEmpty { "…" }, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Markdown(
                        content = m.text.ifEmpty { "…" },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun QuestionCard(q: QuestionItem, onAnswer: (String, List<String>, String) -> Unit) {
    var selected by remember { mutableStateOf<String?>(null) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF26314A))
    ) {
        Column(Modifier.padding(14.dp)) {
            if (q.header.isNotBlank()) Text(q.header, style = MaterialTheme.typography.labelLarge, color = Color(0xFF9AC7FF))
            Text(q.question, style = MaterialTheme.typography.bodyLarge)
            if (q.detail.isNotBlank()) Text(q.detail, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Spacer(Modifier.height(10.dp))
            q.options.forEach { opt ->
                OutlinedButton(
                    onClick = { selected = opt },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                ) {
                    Text(opt)
                }
            }
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = { selected?.let { onAnswer(q.id, listOf(it), "") } },
                enabled = selected != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Răspunde")
            }
        }
    }
}
