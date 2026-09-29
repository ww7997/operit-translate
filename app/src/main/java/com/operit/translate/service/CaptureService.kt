package com.operit.translate.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.operit.translate.AppConfig
import com.operit.translate.EngineState
import com.operit.translate.SubtitleLine
import com.operit.translate.audio.MicCapture
import com.operit.translate.audio.SystemAudioCapture
import com.operit.translate.audio.WavUtil
import com.operit.translate.net.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * الخدمة المركزية: تلتقط الصوت -> STT -> ترجمة -> تحديث الحالة/الطبقة العائمة.
 */
class CaptureService : Service() {

    companion object {
        const val ACTION_START_MIC = "com.operit.translate.START_MIC"
        const val ACTION_START_SYSTEM = "com.operit.translate.START_SYSTEM"
        const val ACTION_STOP = "com.operit.translate.STOP"

        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"

        private const val CH_ID = "operit_translate_ch"
        private const val NOTIF_ID = 4711
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var mic: MicCapture? = null
    private var sys: SystemAudioCapture? = null
    private var projection: MediaProjection? = null
    private var cfg: AppConfig = AppConfig()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        cfg = AppConfig.load(this)

        when (intent?.action) {
            ACTION_START_MIC -> {
                startForegroundCompat("يترجم عبر الميكروفون…")
                EngineState.setStatus("التقاط الميكروفون…")
                startMic()
            }
            ACTION_START_SYSTEM -> {
                startForegroundCompat("يترجم صوت النظام…")
                EngineState.setStatus("التقاط صوت النظام…")
                val code = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
                val data = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                if (code != -1 && data != null) startSystem(code, data)
                else { EngineState.setStatus("فشل إسقاط الشاشة"); stopSelf() }
            }
            ACTION_STOP -> {
                stopAll()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startMic() {
        if (mic != null) return
        mic = MicCapture(
            chunkSeconds = cfg.chunkSeconds,
            silenceThreshold = cfg.silenceThreshold,
            onChunk = ::handleChunk
        ).also { it.start() }
        EngineState.setRunning(true)
    }

    private fun startSystem(resultCode: Int, data: Intent) {
        if (sys != null) return
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val proj = mpm.getMediaProjection(resultCode, data) ?: run {
            EngineState.setStatus("تعذّر بدء إسقاط الشاشة"); return
        }
        projection = proj
        val playCfg = SystemAudioCapture.buildPlaybackConfig(proj)
        sys = SystemAudioCapture(
            projection = proj,
            playCfg = playCfg,
            chunkSeconds = cfg.chunkSeconds,
            silenceThreshold = (cfg.silenceThreshold / 2).coerceAtLeast(150),
            onChunk = ::handleChunk
        ).also { it.start() }
        EngineState.setRunning(true)
    }

    /** يُستدعى من خيط الالتقاط. ننقله لخيط IO عبر scope. */
    private fun handleChunk(samples: ShortArray, len: Int) {
        val snapshot = samples
        scope.launch {
            try {
                val bytes = WavUtil.shortsToBytes(snapshot, len)
                val wav = File(cacheDir, "chunk_${System.currentTimeMillis()}.wav")
                WavUtil.writeWav(wav, bytes, 16000, 1)

                EngineState.setStatus("تحويل الكلام لنص…")
                val text = ApiClient.transcribe(cfg, wav)
                wav.delete()

                if (text.isBlank()) { EngineState.setStatus("…"); return@launch }

                EngineState.setStatus("ترجمة…")
                val translated = ApiClient.translate(cfg, text)

                EngineState.addLine(SubtitleLine(text.trim(), translated.trim()))
                EngineState.setStatus("يعمل 🎧")
            } catch (e: Exception) {
                EngineState.setStatus("خطأ: ${e.message?.take(120)}")
            }
        }
    }

    private fun stopAll() {
        mic?.stop(); mic = null
        sys?.stop(); sys = null
        projection?.stop(); projection = null
        EngineState.setRunning(false)
        EngineState.setStatus("متوقف")
    }

    override fun onDestroy() {
        stopAll()
        super.onDestroy()
    }

    // ===== الإشعار =====
    private fun startForegroundCompat(content: String) {
        createChannel()
        val notif: Notification = NotificationCompat.Builder(this, CH_ID)
            .setContentTitle("Operit Translate")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID, notif,
                if (cfg.audioSource == "system")
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                else
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CH_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CH_ID, "Translation", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
    }
}