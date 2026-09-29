package com.operit.translate.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.operit.translate.AppConfig
import com.operit.translate.EngineState
import com.operit.translate.overlay.OverlayService
import com.operit.translate.service.CaptureService

private val BG = Color(0xFF0B1220)
private val CARD = Color(0xFF16202F)
private val ACCENT = Color(0xFF4FC3F7)

@Composable
fun MainScreen() {
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }

    Scaffold(
        containerColor = BG,
        topBar = {
            TabRow(selectedTabIndex = tab, containerColor = CARD) {
                Tab(selected = tab == 0, onClick = { tab = 0 },
                    selectedContentColor = ACCENT, unselectedContentColor = Color(0xFF9FB3C8),
                    text = { Text("الترجمة") })
                Tab(selected = tab == 1, onClick = { tab = 1 },
                    selectedContentColor = ACCENT, unselectedContentColor = Color(0xFF9FB3C8),
                    text = { Text("الإعدادات") })
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (tab == 0) TranslateTab(ctx) else SettingsTab(ctx)
        }
    }
}

@Composable
private fun TranslateTab(ctx: Context) {
    val running by EngineState.running.collectAsStateWithLifecycle()
    val status by EngineState.status.collectAsStateWithLifecycle()
    val lines by EngineState.lines.collectAsStateWithLifecycle()
    var cfg by remember { mutableStateOf(AppConfig.load(ctx)) }

    // اختيار مصدر الصوت عند البدء
    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startMic(ctx)
        else EngineState.setStatus("مطلوب إذن الميكروفون")
    }

    val projLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK && res.data != null) {
            val i = Intent(ctx, CaptureService::class.java).apply {
                action = CaptureService.ACTION_START_SYSTEM
                putExtra(CaptureService.EXTRA_RESULT_CODE, res.resultCode)
                putExtra(CaptureService.EXTRA_RESULT_DATA, res.data)
            }
            ContextCompat.startForegroundService(ctx, i)
        } else {
            EngineState.setStatus("أُلغي إسقاط الشاشة")
        }
    }

    fun startSystem() {
        val mpm = ctx.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projLauncher.launch(mpm.createScreenCaptureIntent())
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(status, color = ACCENT, fontSize = 14.sp)
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    if (running) {
                        ctx.startService(Intent(ctx, CaptureService::class.java)
                            .setAction(CaptureService.ACTION_STOP))
                    } else {
                        val src = cfg.audioSource
                        when (src) {
                            "system" -> {
                                if (canOverlay(ctx)) startSystem()
                                else needOverlayDialog(ctx) { startSystem() }
                            }
                            "both" -> {
                                // نبدأ بالميكروفون + النظام معاً
                                micPerm.launch(Manifest.permission.RECORD_AUDIO)
                                if (canOverlay(ctx)) startSystem() else startSystem()
                            }
                            else -> {
                                // mic
                                if (hasMic(ctx)) startMic(ctx)
                                else micPerm.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (running) Color(0xFFE57373) else ACCENT
                )
            ) { Text(if (running) "إيقاف" else "بدء الترجمة الفورية") }

            OutlinedButton(onClick = {
                if (cfg.overlayEnabled) {
                    cfg = cfg.copy(overlayEnabled = false)
                    AppConfig.save(ctx, cfg)
                    ctx.stopService(Intent(ctx, OverlayService::class.java))
                } else {
                    if (canOverlay(ctx)) {
                        cfg = cfg.copy(overlayEnabled = true)
                        AppConfig.save(ctx, cfg)
                        ctx.startService(Intent(ctx, OverlayService::class.java))
                    } else needOverlayDialog(ctx) {}
                }
            }) { Text(if (cfg.overlayEnabled) "إخفاء الطبقة" else "الطبقة العائمة") }
        }

        Spacer(Modifier.height(16.dp))

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(lines.reversed()) { l ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(CARD).padding(12.dp)
                ) {
                    if (cfg.showOriginal) {
                        Text(l.original, color = Color(0xFF9FB3C8), fontSize = 13.sp)
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(l.translated, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun hasMic(ctx: Context): Boolean =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED

private fun startMic(ctx: Context) {
    val i = Intent(ctx, CaptureService::class.java).setAction(CaptureService.ACTION_START_MIC)
    ContextCompat.startForegroundService(ctx, i)
}

private fun canOverlay(ctx: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(ctx) else true

private fun needOverlayDialog(ctx: Context, onGranted: () -> Unit) {
    val i = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
    ctx.startActivity(i)
    onGranted()
}
