package com.dsh.harness

import android.os.Bundle
import android.os.Environment
import android.util.Base64
import android.widget.Toast
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import java.io.File
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
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
import com.dsh.harness.ui.HarnessTokens
import com.dsh.harness.ui.LocalHarness
import com.dsh.harness.ui.ServerPrefs
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
    var tab by remember { mutableStateOf(0) } // 0=chats 1=workspace 2=settings
    var showShared by remember { mutableStateOf(false) }
    val sessions by vm.sessions.collectAsState()
    val workspaces by vm.workspaces.collectAsState()
    val update by vm.update.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    BackHandler(enabled = openSessionId != null) { openSessionId = null; openTitle = null }
    BackHandler(enabled = showShared) { showShared = false }
    val t = LocalHarness.current

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
                            Text("Version ${u.versionName} available", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
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
                                            Toast.makeText(context, "Incomplete download ($size B)", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            }) { Text("Update", fontSize = 12.sp) }
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
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { tab = 0; showShared = false }) {
                                Icon(Icons.Filled.Chat, contentDescription = "Chats", tint = if (tab == 0) t.accent else t.muted)
                            }
                            IconButton(onClick = { tab = 1; showShared = false }) {
                                Icon(Icons.Filled.Folder, contentDescription = "Workspace", tint = if (tab == 1) t.accent else t.muted)
                            }
                            IconButton(onClick = { tab = 2; showShared = false }) {
                                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = if (tab == 2) t.accent else t.muted)
                            }
                        }
                    }
                ) { pad ->
                    if (showShared) {
                        SharedScreen(vm, onBack = { showShared = false }, contentPadding = pad)
                    } else if (tab == 0) {
                        SessionsScreen(
                            vm, sessions = sessions, workspaces = workspaces,
                            onOpen = {
                                vm.openSession(it)
                                openSessionId = it.sessionId
                                openTitle = it.title
                            },
                            contentPadding = pad
                        )
                    } else if (tab == 1) {
                        WorkspaceScreen(vm, workspaces = workspaces, onShared = { showShared = true }, contentPadding = pad)
                    } else {
                        SettingsScreen(vm = vm, context = context, baseUrl = vm.currentBase(), onDisconnect = { connected = false; tab = 0 })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(vm: HarnessViewModel, context: Context, baseUrl: String, onDisconnect: () -> Unit) {
    val t = LocalHarness.current
    val mode = ThemePrefs.mode.value
    val options = listOf(
        ThemeMode.SYSTEM to "System (follow device)",
        ThemeMode.LIGHT to "Light",
        ThemeMode.DARK to "Dark"
    )
    val qrLink = if (baseUrl.isNotBlank()) "harness://open?url=" + Uri.encode(baseUrl) else ""
    val qr = remember(qrLink) { if (qrLink.isNotBlank()) qrBitmap(qrLink) else null }
    val githubUpdate by vm.githubUpdate.collectAsState()
    val checking by vm.checking.collectAsState()
    val checkMsg by vm.checkMsg.collectAsState()
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())) {
        Text("SETTINGS", fontFamily = FontFamily.Monospace, fontSize = 12.sp,
            letterSpacing = 3.sp, color = t.accentText, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text("Server", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (baseUrl.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(baseUrl, style = MaterialTheme.typography.bodyMedium, color = t.accentText,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(8.dp))
        qr?.let {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(bitmap = it.asImageBitmap(), contentDescription = "setup QR",
                    modifier = Modifier.size(160.dp).background(Color.White))
                Spacer(Modifier.height(4.dp))
                Text("Scan to connect another device", style = MaterialTheme.typography.bodySmall, color = t.muted)
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) {
            Text("Change server / reconnect")
        }
        Spacer(Modifier.height(20.dp))
        Text("Updates", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        githubUpdate?.let { g ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Version ${g.versionName} available (GitHub)", style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f))
                Button(onClick = {
                    scope.launch {
                        val file = java.io.File(context.cacheDir, "harness-update.apk")
                        val size = UpdateManager.download(g.downloadUrl, file)
                        if (size > 5_000_000) UpdateManager.install(context, file)
                        else Toast.makeText(context, "Incomplete download ($size B)", Toast.LENGTH_LONG).show()
                    }
                }) { Text("⬇ Update", fontSize = 12.sp) }
            }
        } ?: run {
            Text("Installed: v${UpdateManager.installedVersionName(context)}",
                style = MaterialTheme.typography.bodySmall, color = t.muted)
        }
        OutlinedButton(
            onClick = { vm.checkGitHubUpdate(UpdateManager.installedVersionName(context)) },
            enabled = !checking,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
        ) { Text(if (checking) "Checking…" else "Check for updates (GitHub)") }
        checkMsg?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = t.accentText)
        }
        Spacer(Modifier.height(20.dp))
        Text("Theme", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
        Spacer(Modifier.height(24.dp))
        Text("About", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("Harness · a native client for DeepSeek Harness over Tailscale.\nSessions, model, workspace and files, live.",
            style = MaterialTheme.typography.bodySmall, color = t.muted, lineHeight = 18.sp)
    }
}

@Composable
private fun ConnectScreen(vm: HarnessViewModel, initialUrl: String? = null, onConnected: () -> Unit) {
    val t = LocalHarness.current
    val context = LocalContext.current
    var url by remember(initialUrl) { mutableStateOf(initialUrl ?: ServerPrefs.load(context)) }
    var showHelp by remember { mutableStateOf(false) }
    val status by vm.status.collectAsState()
    val steps = listOf(
        "Install Tailscale on your PC and phone, signed into the same tailnet.",
        "On the PC, run once: powershell -File setup-dsh-remote.ps1",
        "Restart DSH (Ctrl+C, then 'ollama launch dsh').",
        "Enter the address shown by the script below (e.g. http://host.ts.net:3080)."
    )
    Column(
        Modifier.fillMaxSize().padding(24.dp).imePadding().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Text("HARNESS", fontFamily = FontFamily.Monospace, fontSize = 34.sp,
            letterSpacing = 6.sp, fontWeight = FontWeight.Bold, color = t.accent)
        Text("DeepSeek Harness on your phone", style = MaterialTheme.typography.bodyMedium, color = t.muted)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("DSH address") },
            placeholder = { Text("http://<tailnet-ip>:3080") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Text("Your DSH address from Tailscale (e.g. http://100.x.y.z:3080 or http://host.ts.net:3080)",
            style = MaterialTheme.typography.bodySmall, color = t.muted)
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { ServerPrefs.save(context, url); vm.setBase(url); vm.connect(onSuccess = { onConnected() }) },
            enabled = url.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Connect") }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { showHelp = !showHelp }) {
            Text(if (showHelp) "Hide help" else "How do I connect?")
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
        if (status != "Disconnected") {
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
            result.add("No workspace")
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
                        Icon(Icons.Filled.Add, contentDescription = "New session", tint = t.accentText)
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
    onShared: () -> Unit,
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
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Workspace root")
                        }
                    }
                },
                actions = {
                    if (workspaces.isNotEmpty()) {
                        IconButton(onClick = { vm.listDir(workspaces.first().path) }) {
                            Icon(Icons.Filled.Folder, contentDescription = "Open workspace", tint = t.accentText)
                        }
                    }
                    IconButton(onClick = { onShared() }) {
                        Icon(Icons.Filled.Chat, contentDescription = "Shared files", tint = t.accentText)
                    }
                }
            )
        }
    ) { pad ->
        if (files.isEmpty() && currentDir == null && workspaces.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text("No workspace available.", color = t.muted)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedScreen(vm: HarnessViewModel, onBack: () -> Unit, contentPadding: PaddingValues) {
    val t = LocalHarness.current
    val files by vm.sharedFiles.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { vm.loadSharedFiles() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shared files") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { pad ->
        if (files.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text("No shared files. Files in <workspace>\\shared appear here.", color = t.muted)
            }
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(files) { f -> SharedFileCard(vm, f.name, t, context, scope) }
        }
    }
}

@Composable
private fun SharedFileCard(
    vm: HarnessViewModel, name: String, t: HarnessTokens, context: Context, scope: kotlinx.coroutines.CoroutineScope
) {
    val isImage = name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") ||
        name.endsWith(".gif") || name.endsWith(".webp")
    var bmp by remember(name) { mutableStateOf<Bitmap?>(null) }
    if (isImage && bmp == null) {
        LaunchedEffect(name) {
            val b = vm.fetchSharedFile(name)
            bmp = b?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull() }
        }
    }
    Card(colors = CardDefaults.cardColors(containerColor = t.surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Button(onClick = {
                    scope.launch {
                        val bytes = vm.fetchSharedFile(name)
                        if (bytes != null) {
                            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "shared")
                            dir.mkdirs()
                            val file = File(dir, name)
                            file.writeBytes(bytes)
                            try {
                                val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
                                val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mimeOf(name))
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                Toast.makeText(context, "Saved to ${file.absolutePath}", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            Toast.makeText(context, "Download failed", Toast.LENGTH_LONG).show()
                        }
                    }
                }) { Text("⬇", fontSize = 14.sp) }
            }
            if (bmp != null) {
                Image(bitmap = bmp!!.asImageBitmap(), contentDescription = name,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(10.dp)))
            } else if (isImage) {
                Text("loading preview…", style = MaterialTheme.typography.bodySmall, color = t.muted)
            }
        }
    }
}

private fun mimeOf(name: String): String = when {
    name.endsWith(".pdf") -> "application/pdf"
    name.endsWith(".png") -> "image/png"
    name.endsWith(".jpg") || name.endsWith(".jpeg") -> "image/jpeg"
    name.endsWith(".gif") -> "image/gif"
    name.endsWith(".webp") -> "image/webp"
    name.endsWith(".html") -> "text/html"
    name.endsWith(".txt") -> "text/plain"
    name.endsWith(".docx") -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    name.endsWith(".mp4") -> "video/mp4"
    else -> "application/octet-stream"
}

private fun com.dsh.harness.data.SessionItem.timeText(): String {
    val now = System.currentTimeMillis()
    val d = now - updatedAt
    return when {
        d < 60_000 -> "just now"
        d < 3_600_000 -> "${d / 60_000} min ago"
        d < 86_400_000 -> "${d / 3_600_000} h ago"
        else -> "${d / 86_400_000} d ago"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreen(vm: HarnessViewModel, sessionId: String, title: String?, onBack: () -> Unit) {
    val messages by vm.messages.collectAsState()
    val images by vm.images.collectAsState()
    val thinking by vm.thinking.collectAsState()
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
            kotlinx.coroutines.delay(80)
            listState.animateScrollToItem(messages.lastIndex)
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
                    Text(title ?: "Conversation", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
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
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (canLoadOlder) {
                    item {
                        OutlinedButton(onClick = { vm.loadOlder() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Load older messages", fontSize = 13.sp)
                        }
                    }
                }
                items(messages) { m -> MessageBubble(m, images, vm::loadImage) }
                if (thinking) {
                    item {
                        val infinite = rememberInfiniteTransition()
                        val alpha by infinite.animateFloat(0.3f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse))
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.primary, CircleShape).graphicsLayer { this.alpha = alpha })
                            Spacer(Modifier.width(8.dp))
                            Text("thinking…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                question?.let { q -> item { QuestionCard(q) { id, sel, custom -> vm.answer(id, sel, custom) } } }
            }
            pendingImage?.let {
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🖼 image attached", style = MaterialTheme.typography.bodySmall, color = Color(0xFF9AC7FF))
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
                    Icon(Icons.Filled.Add, contentDescription = "Attach image")
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Type a message…") },
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
private fun MessageBubble(m: MessageItem, images: Map<String, String>, onLoadImage: (String) -> Unit) {
    val t = LocalHarness.current
    val clipboard = LocalClipboardManager.current
    val mine = m.role == "user"
    val bg = if (mine) t.accentSoft else t.surfaceAlt
    val role = when (m.role) {
        "user" -> "YOU"
        "assistant" -> "AI"
        "tool" -> "TOOL"
        else -> "SYS"
    }
    LaunchedEffect(m.imageIds) { m.imageIds.forEach { onLoadImage(it) } }
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
            modifier = Modifier.weight(1f).combinedClickable(
                onClick = {},
                onLongClick = { if (m.text.isNotBlank()) clipboard.setText(AnnotatedString(m.text)) }
            )
        ) {
            Column(Modifier.padding(12.dp)) {
                if (!m.tool.isNullOrBlank()) {
                    Text(m.tool, style = MaterialTheme.typography.bodySmall, color = if (mine) Color(0xFFDCE4FF) else t.accentText)
                }
                if (m.reasoning.isNotBlank()) {
                    Text("💭 " + m.reasoning.trim(), style = MaterialTheme.typography.bodySmall,
                        color = if (mine) Color(0xFFCFD8FF) else t.muted, maxLines = 8, overflow = TextOverflow.Ellipsis)
                }
                m.imageIds.forEach { id ->
                    val b64 = images[id]
                    if (!b64.isNullOrBlank()) {
                        val bmp = remember(id, b64) {
                            runCatching { BitmapFactory.decodeByteArray(Base64.decode(b64, Base64.DEFAULT), 0, 0) }.getOrNull()
                        }
                        bmp?.let {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = "image",
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(10.dp))
                            )
                        }
                    }
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
                Text("Answer")
            }
        }
    }
}
