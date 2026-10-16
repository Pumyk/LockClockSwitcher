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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlinx.coroutines.delay
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AmoledTheme(materialYou: Boolean, content: @Composable () -> Unit) {
    val base = Typography()
    val typography = base.copy(
        displayMedium = base.displayMedium.copy(
            fontSize = 42.sp, fontWeight = FontWeight.Black, letterSpacing = (-1.5).sp
        ),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
    )
    // Material You: dynamic color from wallpaper on Android 12+ when enabled.
    val ctx = LocalContext.current
    val scheme = if (materialYou && Build.VERSION.SDK_INT >= 31) dynamicDarkColorScheme(ctx)
                 else AmoledScheme
    MaterialExpressiveTheme(
        colorScheme = scheme,
        shapes = ExpressiveShapes,
        typography = typography,
        // motionScheme omitted -> defaults to the expressive motion scheme
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
            val prefs = remember { getSharedPreferences("ui", MODE_PRIVATE) }
            var materialYou by remember { mutableStateOf(prefs.getBoolean("material_you", false)) }
            fun toggleYou() {
                materialYou = !materialYou
                prefs.edit().putBoolean("material_you", materialYou).apply()
            }
            AmoledTheme(materialYou) {
                Surface(Modifier.fillMaxSize()) {
                    App(materialYou = materialYou, onToggleMaterialYou = ::toggleYou)
                }
            }
        }
    }
}

/** Launch splash: icon springs in, gradient title rises, accent line sweeps, curtain fades out. */
@Composable
private fun SplashScreen(onFinish: () -> Unit) {
    val iconScale = remember { Animatable(0.55f) }
    val iconAlpha = remember { Animatable(0f) }
    val titleAlpha = remember { Animatable(0f) }
    val titleRise = remember { Animatable(30f) }
    val lineFill = remember { Animatable(0f) }
    val curtain = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        launch { iconAlpha.animateTo(1f, tween(320, easing = FastOutSlowInEasing)) }
        launch { iconScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 380f)) }
        launch {
            delay(280)
            launch { titleAlpha.animateTo(1f, tween(420, easing = FastOutSlowInEasing)) }
            titleRise.animateTo(0f, tween(480, easing = FastOutSlowInEasing))
        }
        launch {
            delay(140)
            lineFill.animateTo(1f, tween(1100, easing = LinearEasing))
        }
        delay(1500)
        curtain.animateTo(0f, tween(400, easing = FastOutSlowInEasing))
        onFinish()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0908))
            .alpha(curtain.value)
    ) {
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                // Raster icon: R.mipmap.ic_launcher resolves to the adaptive-icon
                // XML on API 26+, which painterResource cannot load.
                painterResource(R.drawable.splash_icon),
                contentDescription = null,
                modifier = Modifier
                    .size(88.dp)
                    .scale(iconScale.value)
                    .alpha(iconAlpha.value)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Lock Clock Switcher",
                style = TextStyle(
                    brush = Brush.linearGradient(listOf(Tangerine, Mustard)),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic
                ),
                modifier = Modifier
                    .graphicsLayer { translationY = titleRise.value }
                    .alpha(titleAlpha.value)
            )
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp)
                .fillMaxWidth(lineFill.value * 0.5f + 0.02f)
                .height(3.dp)
                .background(
                    Brush.linearGradient(
                        listOf(Color.Transparent, Mustard, Color.Transparent)
                    )
                )
        )
    }
}

/** Looks for res/drawable-nodpi/<templateid>_<style>.png first, then <templateid>.png. */
private fun previewRes(ctx: Context, p: ClockPreset): Int {
    // The APK's resource package (in resources.arsc) is named after the
    // applicationId, so ctx.packageName is the correct defPackage here.
    val id = p.templateId.lowercase()
    val a = ctx.resources.getIdentifier("${id}_${p.style}", "drawable", ctx.packageName)
    return if (a != 0) a else ctx.resources.getIdentifier(id, "drawable", ctx.packageName)
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

/** Material You dynamic-colour toggle chip. */
@Composable
private fun MaterialYouChip(active: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .clip(PillShape)
            .background(
                if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainer
            )
            .border(
                1.dp,
                if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
                PillShape
            )
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(PillShape)
                .background(MaterialTheme.colorScheme.tertiary)
        )
        Text(
            "Material You",
            style = MaterialTheme.typography.labelMedium,
            color = if (active) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Confirmation pill that springs in when a lock style is applied successfully. */
@Composable
private fun AppliedConfirmation(tick: Int) {
    if (tick == 0) return
    val scale = remember { Animatable(0.4f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(tick) {
        scale.snapTo(0.4f); alpha.snapTo(0f)
        launch { alpha.animateTo(1f, tween(180)) }
        scale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, 320f))
        delay(2400)
        alpha.animateTo(0f, tween(260))
        scale.animateTo(0.6f, tween(260))
    }
    Row(
        Modifier
            .graphicsLayer {
                scaleX = scale.value; scaleY = scale.value; this.alpha = alpha.value
            }
            .clip(PillShape)
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "✓",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimary
        )
        Text(
            "Applied",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
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
fun App(materialYou: Boolean, onToggleMaterialYou: () -> Unit) {
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
    var showSplash by remember { mutableStateOf(true) }
    var appliedTick by remember { mutableStateOf(0) }

    fun refresh() { current = ClockSettings.read(ctx); granted = ClockSettings.canWrite(ctx) }
    fun report(r: Result<String>, ok: String) {
        status = r.fold({ ok }, { it.message ?: "Failed" })
        if (r.isSuccess && ok.startsWith("Applied")) appliedTick++
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
                Row(
                    Modifier.padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusPill(granted)
                    MaterialYouChip(materialYou, onToggleMaterialYou)
                    AppliedConfirmation(appliedTick)
                }
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
                } else {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .clip(MaterialTheme.shapes.large)
                            .background(Color(0xFF141311)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No preview",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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

    // Splash draws last so it layers ON TOP of the grid until its curtain fades.
    if (showSplash) SplashScreen { showSplash = false }
}
