package com.operit.translate

import android.content.Context
import androidx.core.content.edit

/**
 * إعدادات التطبيق: كل شيء قابل للضبط من داخل التطبيق.
 * لا يوجد أي endpoint أو مفتاح مثبّت في الكود — أنت من تختار النموذج.
 */
data class AppConfig(
    // ===== STT (تحويل الصوت لنص) =====
    val sttEndpoint: String = "https://api.openai.com/v1/audio/transcriptions",
    val sttApiKey: String = "",
    val sttModel: String = "whisper-1",

    // ===== MT (الترجمة) =====
    val mtEndpoint: String = "https://api.openai.com/v1/chat/completions",
    val mtApiKey: String = "",
    val mtModel: String = "gpt-4o-mini",

    // ===== اللغات =====
    val sourceLang: String = "auto",
    val targetLang: String = "ar",

    // ===== مصدر الصوت =====
    // "mic" = ميكروفون، "system" = صوت النظام (MediaProjection)
    val audioSource: String = "mic",

    // ===== العرض =====
    val showOriginal: Boolean = true,
    val overlayEnabled: Boolean = false,

    // ===== تقسيم الجمل =====
    val chunkSeconds: Int = 4,           // كل كم ثانية نرسل مقطعاً للترجمة
    val silenceThreshold: Int = 700      // عتبة اكتشاف السكوت (متوسط الشدة)
) {
    companion object {
        private const val PREF = "operit_translate_prefs"

        private const val K_STT_ENDPOINT = "stt_endpoint"
        private const val K_STT_KEY = "stt_key"
        private const val K_STT_MODEL = "stt_model"
        private const val K_MT_ENDPOINT = "mt_endpoint"
        private const val K_MT_KEY = "mt_key"
        private const val K_MT_MODEL = "mt_model"
        private const val K_SRC = "src_lang"
        private const val K_TGT = "tgt_lang"
        private const val K_AUDIO_SOURCE = "audio_source"
        private const val K_SHOW_ORIGINAL = "show_original"
        private const val K_OVERLAY = "overlay_enabled"
        private const val K_CHUNK = "chunk_seconds"
        private const val K_SILENCE = "silence_threshold"

        fun load(ctx: Context): AppConfig {
            val p = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            val d = AppConfig()
            return AppConfig(
                sttEndpoint = p.getString(K_STT_ENDPOINT, d.sttEndpoint) ?: d.sttEndpoint,
                sttApiKey = p.getString(K_STT_KEY, d.sttApiKey) ?: d.sttApiKey,
                sttModel = p.getString(K_STT_MODEL, d.sttModel) ?: d.sttModel,
                mtEndpoint = p.getString(K_MT_ENDPOINT, d.mtEndpoint) ?: d.mtEndpoint,
                mtApiKey = p.getString(K_MT_KEY, d.mtApiKey) ?: d.mtApiKey,
                mtModel = p.getString(K_MT_MODEL, d.mtModel) ?: d.mtModel,
                sourceLang = p.getString(K_SRC, d.sourceLang) ?: d.sourceLang,
                targetLang = p.getString(K_TGT, d.targetLang) ?: d.targetLang,
                audioSource = p.getString(K_AUDIO_SOURCE, d.audioSource) ?: d.audioSource,
                showOriginal = p.getBoolean(K_SHOW_ORIGINAL, d.showOriginal),
                overlayEnabled = p.getBoolean(K_OVERLAY, d.overlayEnabled),
                chunkSeconds = p.getInt(K_CHUNK, d.chunkSeconds),
                silenceThreshold = p.getInt(K_SILENCE, d.silenceThreshold)
            )
        }

        fun save(ctx: Context, c: AppConfig) {
            val p = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            p.edit {
                putString(K_STT_ENDPOINT, c.sttEndpoint)
                putString(K_STT_KEY, c.sttApiKey)
                putString(K_STT_MODEL, c.sttModel)
                putString(K_MT_ENDPOINT, c.mtEndpoint)
                putString(K_MT_KEY, c.mtApiKey)
                putString(K_MT_MODEL, c.mtModel)
                putString(K_SRC, c.sourceLang)
                putString(K_TGT, c.targetLang)
                putString(K_AUDIO_SOURCE, c.audioSource)
                putBoolean(K_SHOW_ORIGINAL, c.showOriginal)
                putBoolean(K_OVERLAY, c.overlayEnabled)
                putInt(K_CHUNK, c.chunkSeconds)
                putInt(K_SILENCE, c.silenceThreshold)
            }
        }
    }
}