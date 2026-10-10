package com.ditz.boosterpro

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Bg = Color(0xFF0A0A0C)
private val Card = Color(0xFF16161A)
private val Red = Color(0xFFFF2D3A)
private val RedDark = Color(0xFF8A0F18)
private val Muted = Color(0xFF8C8C96)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Red, background = Bg, surface = Card)) {
                Home()
            }
        }
    }
}

data class AppItem(val label: String, val pkg: String)

private fun hasUsage(c: Context): Boolean {
    val ops = c.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName) ==
        AppOpsManager.MODE_ALLOWED
}

@Composable
fun Home() {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(o); onDispose { owner.lifecycle.removeObserver(o) }
    }

    val gaming by Engine.gamingMode.collectAsState()
    val detector by Engine.detectorOn.collectAsState()
    val activeGame by Engine.activeGame.collectAsState()

    var hud by remember { mutableStateOf(Prefs.hud(ctx)) }
    var silence by remember { mutableStateOf(Prefs.silence(ctx)) }
    var bright by remember { mutableStateOf(Prefs.lockBrightness(ctx)) }
    var selected by remember { mutableStateOf(Prefs.games(ctx)) }
    var apps by remember { mutableStateOf(listOf<AppItem>()) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(i, 0)
                .map { AppItem(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
                .filter { it.pkg != ctx.packageName }
                .distinctBy { it.pkg }
                .sortedBy { it.label.lowercase() }
        }
    }

    // Izin (dihitung ulang tiap ON_RESUME)
    val pUsage = remember(tick) { hasUsage(ctx) }
    val pOverlay = remember(tick) { Settings.canDrawOverlays(ctx) }
    val pWrite = remember(tick) { Settings.System.canWrite(ctx) }
    val pListener = remember(tick) { NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName) }
    val pDnd = remember(tick) { ctx.getSystemService(android.app.NotificationManager::class.java).isNotificationPolicyAccessGranted }

    fun open(a: String, pkg: Boolean = false) {
        val i = Intent(a)
        if (pkg) i.data = Uri.parse("package:${ctx.packageName}")
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    LazyColumn(
        Modifier.fillMaxSize().background(Bg).statusBarsPadding().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(14.dp))
            Text("DITZ BOOSTER PRO", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        }

        // Dial status — satu-satunya elemen "berani" di layar
        item {
            Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(210.dp).clip(CircleShape)
                        .background(Brush.radialGradient(
                            if (gaming) listOf(Red, RedDark, Bg) else listOf(Color(0xFF26262C), Card, Bg)))
                        .border(3.dp, if (gaming) Red else Color(0xFF2E2E36), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Gaming Mode", color = Muted, fontSize = 13.sp)
                        Text(if (gaming) "ON" else "OFF", color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Black)
                        Text(activeGame ?: "Menunggu game", color = Muted, fontSize = 12.sp)
                    }
                }
            }
        }

        item {
            Panel {
                RowSwitch("Deteksi game otomatis", "Aktif di latar belakang, Gaming Mode jalan saat game dibuka", detector) { on ->
                    if (on) {
                        if (!pUsage) open(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                        else ctx.startForegroundService(Intent(ctx, DetectorService::class.java))
                    } else {
                        ctx.stopService(Intent(ctx, DetectorService::class.java))
                    }
                }
                RowSwitch("HUD FPS & suhu", "Overlay di atas game", hud) { hud = it; Prefs.setHud(ctx, it) }
                RowSwitch("Total Silence", "Telepon & chat WhatsApp dibisukan saat main", silence) { silence = it; Prefs.setSilence(ctx, it) }
                RowSwitch("Kunci kecerahan", "Auto-brightness dimatikan saat main", bright) { bright = it; Prefs.setLockBrightness(ctx, it) }
            }
        }

        item {
            Panel {
                Text("Izin yang dibutuhkan", color = Color.White, fontWeight = FontWeight.Bold)
                PermRow("Akses penggunaan aplikasi", pUsage) { open(Settings.ACTION_USAGE_ACCESS_SETTINGS) }
                PermRow("Tampil di atas aplikasi lain", pOverlay) { open(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, true) }
                PermRow("Ubah setelan sistem", pWrite) { open(Settings.ACTION_MANAGE_WRITE_SETTINGS, true) }
                PermRow("Akses notifikasi", pListener) { open(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }
                PermRow("Akses Jangan Ganggu", pDnd) { open(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS) }
            }
        }

        item { Text("Pilih game", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) }

        items(apps, key = { it.pkg }) { app ->
            val on = app.pkg in selected
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Card)
                    .clickable {
                        selected = if (on) selected - app.pkg else selected + app.pkg
                        Prefs.setGames(ctx, selected)
                    }.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(app.label, color = Color.White)
                    Text(app.pkg, color = Muted, fontSize = 11.sp)
                }
                Checkbox(checked = on, onCheckedChange = null,
                    colors = CheckboxDefaults.colors(checkedColor = Red))
            }
        }
        item { Spacer(Modifier.height(24.dp).navigationBarsPadding()) }
    }
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Card).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp), content = content
    )
}

@Composable
private fun RowSwitch(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White)
            Text(sub, color = Muted, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Red, checkedThumbColor = Color.White))
    }
}

@Composable
private fun PermRow(name: String, granted: Boolean, onGrant: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, color = Color.White, modifier = Modifier.weight(1f), fontSize = 14.sp)
        if (granted) Text("Aktif", color = Color(0xFF3DDC84), fontSize = 13.sp)
        else TextButton(onClick = onGrant) { Text("Aktifkan", color = Red) }
    }
}
