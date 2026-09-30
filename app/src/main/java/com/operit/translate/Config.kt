package com.operit.translate

import android.content.Context
import androidx.core.content.edit
import com.operit.translate.engine.EngineRegistry
import com.operit.translate.engine.TranslateEngine

/**
 * إعدادات الترجمة — كل شي قابل للضبط.
 * ما في محرك مدمج إجباري — أنت اللي تختار المحرك والمفتاح.
 */
data class Config(
    // ==== المحرك ====
    val engineId: String = "mymemory",
    val apiKey: String = "",
    val endpoint: String = "",
    val model: String = "",

    // ==== اللغات ====
    val sourceLang: String = "auto",
    val targetLang: String = "ar",

    // ==== نمط العرض ====
    // bilingual        = الأصل، وتحته الترجمة
    // replace          = الترجمة تحل مكان الأصل
    // translation_only = الترجمة فقط
    val displayMode: String = "bilingual",

    // ==== التخزين المؤقت ====
    val cacheEnabled: Boolean = true,

    // ==== الترجمة الفورية ====
    val pageSize: Int = 15,

    // ==== الصوت ====
    val audioSource: String = "mic",
    val overlayEnabled: Boolean = false
) {
    /** المحرك المختار فعلياً */
    val engine: TranslateEngine
        get() = EngineRegistry.byId(engineId)

    companion object {
        private const val PREF = "operit_translate_prefs_v2"

        fun load(ctx: Context): Config {
            val p = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            val d = Config()
            return Config(
                engineId = p.getString("key_engine", d.engineId) ?: d.engineId,
                apiKey = p.getString("key_api", d.apiKey) ?: d.apiKey,
                endpoint = p.getString("key_endpoint", d.endpoint) ?: d.endpoint,
                model = p.getString("key_model", d.model) ?: d.model,
                sourceLang = p.getString("key_src", d.sourceLang) ?: d.sourceLang,
                targetLang = p.getString("key_tgt", d.targetLang) ?: d.targetLang,
                displayMode = p.getString("key_display", d.displayMode) ?: d.displayMode,
                cacheEnabled = p.getBoolean("key_cache", d.cacheEnabled),
                pageSize = p.getInt("key_pagesize", d.pageSize),
                audioSource = p.getString("key_audio", d.audioSource) ?: d.audioSource,
                overlayEnabled = p.getBoolean("key_overlay", d.overlayEnabled)
            )
        }

        fun save(ctx: Context, c: Config) {
            ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit {
                putString("key_engine", c.engineId)
                putString("key_api", c.apiKey)
                putString("key_endpoint", c.endpoint)
                putString("key_model", c.model)
                putString("key_src", c.sourceLang)
                putString("key_tgt", c.targetLang)
                putString("key_display", c.displayMode)
                putBoolean("key_cache", c.cacheEnabled)
                putInt("key_pagesize", c.pageSize)
                putString("key_audio", c.audioSource)
                putBoolean("key_overlay", c.overlayEnabled)
            }
        }
    }
}