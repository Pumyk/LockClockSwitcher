package com.example.lockclock

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val ADB_CMD =
    "adb shell pm grant com.example.lockclock android.permission.WRITE_SECURE_SETTINGS"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) { App() }
            }
        }
    }
}

/** Looks for res/drawable/preview_<templateid>_<style> first, then preview_<templateid>. */
private fun previewRes(ctx: Context, p: ClockPreset): Int {
    val id = p.templateId.lowercase()
    val a = ctx.resources.getIdentifier("preview_${id}_${p.style}", "drawable", ctx.packageName)
    return if (a != 0) a else ctx.resources.getIdentifier("preview_$id", "drawable", ctx.packageName)
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val presets = remember { loadPresets(ctx) }

    var current by remember { mutableStateOf(ClockSettings.read(ctx)) }
    var granted by remember { mutableStateOf(ClockSettings.canWrite(ctx)) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var cStyle by remember { mutableStateOf("") }
    var cTemplate by remember { mutableStateOf("") }
    var pairPort by remember { mutableStateOf("") }
    var pairCode by remember { mutableStateOf("") }

    fun refresh() { current = ClockSettings.read(ctx); granted = ClockSettings.canWrite(ctx) }
    fun report(r: Result<String>, ok: String) {
        status = r.fold({ ok }, { it.message ?: "Failed" })
        refresh()
    }
    fun runAdb(block: () -> Result<String>) {
        scope.launch {
            busy = true
            status = "Working…"
            val r = withContext(Dispatchers.IO) { block() }
            status = r.fold({ it }, { it.message ?: it.toString() })
            busy = false
            refresh()
        }
    }

    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) PairingService.start(ctx)
    }
    fun startPairingNotification() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        else PairingService.start(ctx)
    }

    // Auto-fill the pairing port while the "Pair with pairing code" dialog is open.
    DisposableEffect(Unit) {
        val finder = PairingPortFinder(ctx) { port -> if (port != null) pairPort = port.toString() }
        finder.start()
        onDispose { finder.stop() }
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("Lock Clock Switcher", style = MaterialTheme.typography.headlineSmall) }

        // ---------- Permission / wireless debugging ----------
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (granted) "Permission: granted" else "Permission: NOT granted",
                        style = MaterialTheme.typography.titleSmall
                    )
                    if (!granted) {
                        Text("Grant it from this phone using Wireless debugging:", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "1. Connect to Wi-Fi, then enable Developer options → Wireless debugging.\n" +
                                "2. Tap \"Start pairing notification\" below, then open \"Pair device with pairing code\".\n" +
                                "3. Reply to this app notification with the 6-digit code — no split screen needed.\n" +
                                "(Manual fallback: the port fills in below; split screen works too.)",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(onClick = { startPairingNotification() }) { Text("Start pairing notification") }
                        OutlinedTextField(
                            pairPort, { pairPort = it.filter(Char::isDigit) },
                            label = { Text("pairing port (auto-detected)") }, singleLine = true
                        )
                        OutlinedTextField(
                            pairCode, { pairCode = it.filter(Char::isDigit).take(6) },
                            label = { Text("6-digit pairing code") }, singleLine = true
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                enabled = !busy && pairCode.length == 6 && pairPort.isNotEmpty(),
                                onClick = { runAdb { AdbSetup.pairAndGrant(ctx, pairPort.toIntOrNull(), pairCode) } }
                            ) { Text("Pair & grant") }
                            OutlinedButton(
                                enabled = !busy,
                                onClick = { runAdb { AdbSetup.connectAndGrant(ctx) } }
                            ) { Text("Already paired: connect") }
                        }
                        HorizontalDivider()
                        Text("Or from a computer:", style = MaterialTheme.typography.bodySmall)
                        SelectionContainer { Text(ADB_CMD, style = MaterialTheme.typography.bodySmall) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("adb", ADB_CMD))
                                status = "Command copied"
                            }) { Text("Copy command") }
                            OutlinedButton(onClick = { refresh() }) { Text("Re-check") }
                        }
                    }
                }
            }
        }

        // ---------- Current value ----------
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Current value", style = MaterialTheme.typography.titleSmall)
                    SelectionContainer {
                        Text(current ?: "(key not found)", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { refresh() }) { Text("Refresh") }
                        OutlinedButton(
                            enabled = granted && ClockSettings.hasOriginalBackup(ctx),
                            onClick = { report(ClockSettings.restore(ctx), "Original restored") }
                        ) { Text("Restore original") }
                    }
                }
            }
        }

        if (status.isNotEmpty()) item { Text(status, color = MaterialTheme.colorScheme.primary) }

        // ---------- Presets (from assets/presets.json) ----------
        items(presets) { p ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val res = remember(p) { previewRes(ctx, p) }
                    if (res != 0) {
                        Image(
                            painterResource(res), contentDescription = p.name,
                            modifier = Modifier.fillMaxWidth().height(220.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                    Text(p.name, style = MaterialTheme.typography.titleMedium)
                    Text("style:${p.style}  ·  templateid:${p.templateId}", style = MaterialTheme.typography.bodySmall)
                    Button(
                        enabled = granted,
                        onClick = { report(ClockSettings.apply(ctx, p.style, p.templateId), "Applied ${p.name}. Lock your phone to check.") },
                        modifier = Modifier.align(Alignment.End)
                    ) { Text("Apply") }
                }
            }
        }

        // ---------- Custom ----------
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Custom", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(cStyle, { cStyle = it.filter(Char::isDigit) }, label = { Text("style number") }, singleLine = true)
                    OutlinedTextField(cTemplate, { cTemplate = it.trim() }, label = { Text("templateid") }, singleLine = true)
                    Button(
                        enabled = granted && cStyle.isNotEmpty() && cTemplate.isNotEmpty(),
                        onClick = { report(ClockSettings.apply(ctx, cStyle.toInt(), cTemplate), "Applied custom style") },
                        modifier = Modifier.align(Alignment.End)
                    ) { Text("Apply") }
                }
            }
        }
    }
}
