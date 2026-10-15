package com.example.lockclock

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val ADB_CMD =
    "adb shell pm grant com.pumyk.lockclockswitcher android.permission.WRITE_SECURE_SETTINGS"

// ---------------------------------------------------------------------------
// AMOLED-black expressive theme, palette pulled from the "19 AG" key art:
// mustard tee, orange lettering, cream type, blue-grey concrete.
// ---------------------------------------------------------------------------
private val Mustard = Color(0xFFC6B335)
private val Tangerine = Color(0xFFFF8C42)
private val Cream = Color(0xFFF5E6C8)

private val AmoledScheme = darkColorScheme(
    primary = Mustard,
    onPrimary = Color(0xFF1C1900),
    primaryContainer = Color(0xFF36300C),
    onPrimaryContainer = Color(0xFFE9DA7A),
    secondary = Color(0xFF7C8592),          // blue-grey concrete
    onSecondary = Color(0xFF0D1116),
    secondaryContainer = Color(0xFF232830),
    onSecondaryContainer = Color(0xFFB3BDCF),
    tertiary = Tangerine,
    onTertiary = Color(0xFF2A1200),
    tertiaryContainer = Color(0xFF3A1D05),
    onTertiaryContainer = Color(0xFFFFB77E),
    background = Color(0xFF000000),
    onBackground = Cream,
    surface = Color(0xFF000000),
    onSurface = Cream,
    surfaceVariant = Color(0xFF17150F),
    onSurfaceVariant = Color(0xFFC0B8A0),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0A0A08),
    surfaceContainer = Color(0xFF10100C),
    surfaceContainerHigh = Color(0xFF161611),
    surfaceContainerHighest = Color(0xFF1C1B15),
    outline = Color(0xFF4A4636),
    outlineVariant = Color(0xFF28251B),
    error = Color(0xFFFF6B5B),
    onError = Color(0xFF220302),
    errorContainer = Color(0xFF3B0B06),
    onErrorContainer = Color(0xFFFFCDBD),
    inverseSurface = Cream,
    inverseOnSurface = Color(0xFF111111),
)

private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

private val PillShape = RoundedCornerShape(50)

@Composable
private fun AmoledTheme(content: @Composable () -> Unit) {
    val base = Typography()
    val typography = base.copy(
        displayMedium = base.displayMedium.copy(
            fontSize = 42.sp, fontWeight = FontWeight.Black, letterSpacing = (-1.5).sp
        ),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
    )
    MaterialTheme(
        colorScheme = AmoledScheme,
        shapes = ExpressiveShapes,
        typography = typography,
        content = content,
    )
}

/** Card styling shared by every section: soft container + hairline outline. */
@Composable
private fun Modifier.amoledCard(): Modifier = this
    .clip(MaterialTheme.shapes.extraLarge)
    .background(MaterialTheme.colorScheme.surfaceContainerLow)
    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.extraLarge)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            AmoledTheme { Surface(Modifier.fillMaxSize()) { App() } }
        }
    }
}

/** Looks for res/drawable/preview_<templateid>_<style> first, then preview_<templateid>. */
private fun previewRes(ctx: Context, p: ClockPreset): Int {
    // Look up in the app's resource package (the namespace), so it keeps
    // working when applicationId differs from the namespace.
    val pkg = R::class.java.getPackage()?.name ?: ctx.packageName
    val id = p.templateId.lowercase()
    val a = ctx.resources.getIdentifier("preview_${id}_${p.style}", "drawable", pkg)
    return if (a != 0) a else ctx.resources.getIdentifier("preview_$id", "drawable", pkg)
}

@Composable
private fun Dot(active: Boolean) {
    Box(
        Modifier
            .size(8.dp)
            .clip(RoundedCornerShape(50))
            .background(if (active) Color(0xFF5BD97A) else Tangerine)
    )
}

@Composable
private fun StatusPill(granted: Boolean) {
    Row(
        Modifier
            .clip(PillShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, PillShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Dot(granted)
        Text(
            if (granted) "Permission granted" else "Permission needed",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
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

    /** Opens the system Wireless debugging page so the pairing dialog is one tap away. */
    fun openWirelessDebugging() {
        // "android.settings.WIRELESS_DEBUGGING_SETTINGS" (Settings constant is API 35+)
        val action = if (Build.VERSION.SDK_INT >= 30)
            "android.settings.WIRELESS_DEBUGGING_SETTINGS"
        else
            Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS
        runCatching { ctx.startActivity(Intent(action)) }
            .onFailure { runCatching { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) } }
    }

    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) PairingService.start(ctx)
    }
    fun startPairingNotification() {
        // Both at once: bring up the notification AND jump to Wireless debugging.
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        else PairingService.start(ctx)
        openWirelessDebugging()
    }

    // Auto-fill the pairing port while the "Pair with pairing code" dialog is open.
    DisposableEffect(Unit) {
        val finder = PairingPortFinder(ctx) { port -> if (port != null) pairPort = port.toString() }
        finder.start()
        onDispose { finder.stop() }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 32.dp)
    ) {
        // ---------- Hero ----------
        item(span = { GridItemSpan(2) }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("LOCK CLOCK", style = MaterialTheme.typography.displayMedium)
                Text(
                    "switcher",
                    style = TextStyle(
                        brush = Brush.linearGradient(listOf(Tangerine, Mustard)),
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-1.5).sp,
                        fontStyle = FontStyle.Italic
                    )
                )
                Row(Modifier.padding(top = 2.dp)) { StatusPill(granted) }
            }
        }

        // ---------- Permission / wireless debugging ----------
        if (!granted) item(span = { GridItemSpan(2) }) {
            Column(Modifier.amoledCard().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Grant WRITE_SECURE_SETTINGS", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Wireless debugging, from this phone — no computer needed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "1. Tap \"Start pairing notification\" — it also opens Wireless debugging.\n" +
                        "2. On that page, open \"Pair device with pairing code\".\n" +
                        "3. Reply to this app's notification with the 6-digit code — no split screen needed.\n" +
                        "(Manual fallback: the port fills in below; split screen works too.)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { startPairingNotification() },
                    shape = PillShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Mustard, contentColor = Color(0xFF1C1900)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Start pairing notification") }
                OutlinedTextField(
                    pairPort, { pairPort = it.filter(Char::isDigit) },
                    label = { Text("pairing port (auto-detected)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    pairCode, { pairCode = it.filter(Char::isDigit).take(6) },
                    label = { Text("6-digit pairing code") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        enabled = !busy && pairCode.length == 6 && pairPort.isNotEmpty(),
                        onClick = { runAdb { AdbSetup.pairAndGrant(ctx, pairPort.toIntOrNull(), pairCode) } },
                        shape = PillShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Mustard, contentColor = Color(0xFF1C1900)
                        )
                    ) { Text("Pair & grant") }
                    OutlinedButton(
                        enabled = !busy,
                        onClick = { runAdb { AdbSetup.connectAndGrant(ctx) } },
                        shape = PillShape
                    ) { Text("Already paired: connect") }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text("Or from a computer:", style = MaterialTheme.typography.bodySmall)
                SelectionContainer {
                    Text(
                        ADB_CMD, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(shape = PillShape, onClick = {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("adb", ADB_CMD))
                        status = "Command copied"
                    }) { Text("Copy command") }
                    OutlinedButton(shape = PillShape, onClick = { refresh() }) { Text("Re-check") }
                }
            }
        }

        // ---------- Current value ----------
        item(span = { GridItemSpan(2) }) {
            Column(Modifier.amoledCard().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Current value", style = MaterialTheme.typography.titleSmall)
                SelectionContainer {
                    Text(
                        current ?: "(key not found)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(shape = PillShape, onClick = { refresh() }) { Text("Refresh") }
                    OutlinedButton(
                        shape = PillShape,
                        enabled = granted && ClockSettings.hasOriginalBackup(ctx),
                        onClick = { report(ClockSettings.restore(ctx), "Original restored") }
                    ) { Text("Restore original") }
                }
            }
        }

        // ---------- Status banner ----------
        if (status.isNotEmpty()) item(span = { GridItemSpan(2) }) {
            Row(
                Modifier
                    .clip(PillShape)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, PillShape)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
            }
        }

        // ---------- Presets (from assets/presets.json) ----------
        item(span = { GridItemSpan(2) }) { SectionLabel("Presets") }
        items(presets, key = { "${it.templateId}:${it.style}" }) { p ->
            Column(Modifier.amoledCard().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val res = remember(p) { previewRes(ctx, p) }
                if (res != 0) {
                    Image(
                        painterResource(res), contentDescription = p.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.large)
                            .height(190.dp),
                        contentScale = ContentScale.Fit
                    )
                }
                Text(p.name, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                Text(
                    "style: ${p.style} · ${p.templateId}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    enabled = granted,
                    onClick = { report(ClockSettings.apply(ctx, p.style, p.templateId), "Applied ${p.name}. Lock your phone to check.") },
                    shape = PillShape,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Mustard, contentColor = Color(0xFF1C1900)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Apply", style = MaterialTheme.typography.labelLarge) }
            }
        }

        // ---------- Custom ----------
        item(span = { GridItemSpan(2) }) {
            Column(Modifier.amoledCard().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Custom", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(cStyle, { cStyle = it.filter(Char::isDigit) }, label = { Text("style number") }, singleLine = true)
                OutlinedTextField(cTemplate, { cTemplate = it.trim() }, label = { Text("templateid") }, singleLine = true)
                Button(
                    enabled = granted && cStyle.isNotEmpty() && cTemplate.isNotEmpty(),
                    onClick = { report(ClockSettings.apply(ctx, cStyle.toInt(), cTemplate), "Applied custom style") },
                    shape = PillShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Mustard, contentColor = Color(0xFF1C1900)
                    ),
                    modifier = Modifier.align(Alignment.End)
                ) { Text("Apply") }
            }
        }
    }
}
