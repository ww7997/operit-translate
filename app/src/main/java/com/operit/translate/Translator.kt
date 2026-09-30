package com.operit.translate

import com.operit.translate.engine.EngineUtil
import com.operit.translate.engine.TranslateEngine
import com.operit.translate.engine.TranslateException
import java.util.concurrent.ConcurrentHashMap

/**
 * محرك الترجمة الرئيسي
 * - يقسّم النص الطويل لقطع
 * - تخزين مؤقت للنتائج المتكررة
 * - يمرّ عبر المحرك المختار في الإعدادات
 */
object Translator {

    private const val MAX_CHARS = 3500
    private const val SEP = "\n\n"

    private val cache = ConcurrentHashMap<String, String>()

    fun clearCache() = cache.clear()

    private fun cacheKey(cfg: Config, text: String) =
        "${cfg.engineId}|${cfg.sourceLang}|${cfg.targetLang}|${cfg.model}|${cfg.prompt.hashCode()}|${text.hashCode()}"

    fun translate(cfg: Config, text: String): String {
        if (text.isBlank()) return ""
        val engine: TranslateEngine = cfg.engine
        val key = cacheKey(cfg, text)
        if (cfg.cacheEnabled) cache[key]?.let { return it }
        val out = if (text.length <= MAX_CHARS) runEngine(engine, text, cfg)
        else translateLong(engine, text, cfg)
        if (cfg.cacheEnabled) cache[key] = out
        return out
    }

    private fun runEngine(engine: TranslateEngine, text: String, cfg: Config): String {
        val r = engine.translate(
            text,
            cfg.sourceLang,
            cfg.targetLang,
            cfg.apiKey,
            cfg.endpoint,
            cfg.model,
            cfg.prompt
        )
        if (r.isBlank()) throw TranslateException("المحرك أرجع نتيجة فارغة")
        return EngineUtil.clean(r)
    }

    private fun translateLong(engine: TranslateEngine, text: String, cfg: Config): String {
        val chunks = splitChunks(text, MAX_CHARS)
        return chunks.joinToString(SEP) { runEngine(engine, it, cfg) }
    }

    private fun splitChunks(text: String, max: Int): List<String> {
        val out = mutableListOf<String>()
        val paras = text.split(Regex("\n\\s*\n"))
        val cur = StringBuilder()
        for (pt in paras) {
            val para = pt.trim()
            if (para.isEmpty()) continue
            if (para.length > max) {
                if (cur.isNotEmpty()) {
                    out.add(cur.toString().trim())
                    cur.clear()
                }
                out.addAll(splitSentences(para, max))
            } else if (cur.length + para.length + 2 > max) {
                out.add(cur.toString().trim())
                cur.clear()
                cur.append(para)
            } else {
                if (cur.isNotEmpty()) cur.append("\n")
                cur.append(para)
            }
        }
        if (cur.isNotEmpty()) out.add(cur.toString().trim())
        return if (out.isEmpty()) listOf(text) else out
    }

    private fun splitSentences(s: String, max: Int): List<String> {
        val out = mutableListOf<String>()
        val parts = s.split(Regex("(?<=[.\\u066B!?;؟\\u061B])\\s+"))
        val cur = StringBuilder()
        for (p in parts) {
            if (cur.length + p.length > max && cur.isNotEmpty()) {
                out.add(cur.toString().trim())
                cur.clear()
            }
            cur.append(p).append(" ")
        }
        if (cur.isNotEmpty()) out.add(cur.toString().trim())
        return if (out.isEmpty()) listOf(s) else out
    }

    fun testEngine(cfg: Config): Pair<Boolean, String> {
        return try {
            val out = cfg.engine.translate(
                "Hello, this is a test.",
                "en",
                cfg.targetLang,
                cfg.apiKey,
                cfg.endpoint,
                cfg.model,
                cfg.prompt
            )
            Pair(true, out)
        } catch (e: Exception) {
            Pair(false, e.message ?: e.javaClass.simpleName)
        }
    }
}