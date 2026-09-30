package com.operit.translate.engine

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

// ==============================================================
//  1) Google Translate (نقطة النهاية العامة، بدون مفتاح)
// ==============================================================
object GoogleFreeEngine : TranslateEngine {
    override val id = "google_free"
    override val displayName = "Google Translate (بدون مفتاح)"
    override val needsKey = false
    override val needsEndpoint = false
    override val description = "محرك Google العام. مجاني تماماً، لا يحتاج مفتاحاً."

    override fun translate(text: String, from: String, to: String, key: String, endpoint: String, model: String): String {
        val sl = if (from == "auto") "auto" else from
        val url = "https://translate.googleapis.com/translate_a/single" +
            "?client=gtx&sl=$sl&tl=$to&dt=t&q=${URLEncoder.encode(text, "UTF-8")}"
        val resp = Http.get(url)
        val arr = JSONArray(resp)
        val segs = arr.optJSONArray(0) ?: return ""
        val sb = StringBuilder()
        for (i in 0 until segs.length()) {
            val seg = segs.optJSONArray(i) ?: continue
            sb.append(seg.optString(0))
        }
        return sb.toString()
    }
}

// ==============================================================
//  2) Bing / Microsoft Translator (نقطة النهاية العامة، بدون مفتاح)
// ==============================================================
object BingFreeEngine : TranslateEngine {
    override val id = "bing_free"
    override val displayName = "Bing Translate (بدون مفتاح)"
    override val needsKey = false
    override val needsEndpoint = false
    override val description = "محرك Bing العام. مجاني، لا يحتاج مفتاحاً. جودة ممتازة."

    @Volatile private var token: String? = null
    @Volatile private var tokenTime = 0L

    private fun getToken(): String {
        val now = System.currentTimeMillis()
        val t = token
        if (t != null && now - tokenTime < 8L * 60L * 1000L) return t
        val html = Http.get(
            "https://www.bing.com/translator",
            mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36")
        )
        val m = Regex("params_AbusePreventionHelper\\s*=\\s*\\[([0-9]+),\"([^\"]+)\"")
            .find(html)
            ?: throw TranslateException("تعذّر الحصول على رمز Bing (تغيّرت الصفحة؟)")
        val ig = m.groupValues[1]
        val key = m.groupValues[2]
        val reg = "$ig*$key"
        token = reg
        tokenTime = now
        return reg
    }

    override fun translate(text: String, from: String, to: String, key: String, endpoint: String, model: String): String {
        val igKey = getToken()
        val parts = igKey.split("*", limit = 2)
        val ig = parts[0]
        val tk = parts.getOrElse(1) { "" }
        val fl = if (from == "auto") "auto-detect" else from
        val form = "fromLang=${URLEncoder.encode(fl, "UTF-8")}" +
            "&text=${URLEncoder.encode(text, "UTF-8")}" +
            "&to=$to" +
            "&token=$tk" +
            "&key=$ig"
        val resp = Http.postForm(
            "https://www.bing.com/ttranslatev3?isVertical=1&&IG=$ig&IID=translator.5023",
            form,
            mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36",
                "Referer" to "https://www.bing.com/translator",
                "Content-Type" to "application/x-www-form-urlencoded"
            )
        )
        val obj = JSONObject(resp)
        val arr = obj.optJSONArray("translations") ?: return ""
        return arr.optJSONObject(0)?.optString("text").orEmpty()
    }
}

// ==============================================================
//  3) أي خدمة متوافقة مع OpenAI (OpenAI / Groq / OpenRouter / DeepSeek ...)
// ==============================================================
object OpenAICompatEngine : TranslateEngine {
    override val id = "openai_compat"
    override val displayName = "OpenAI-compatible (Groq / OpenRouter / DeepSeek ...)"
    override val needsKey = true
    override val needsEndpoint = true
    override val description = "أي خدمة تقبل /chat/completions. ضع الرابط والمفتاح واسم النموذج."

    override fun translate(text: String, from: String, to: String, key: String, endpoint: String, model: String): String {
        if (endpoint.isBlank()) throw TranslateException("حدّد رابط الـ endpoint")
        if (key.isBlank()) throw TranslateException("حدّد مفتاح API")
        val sys = "You are a professional translator. Translate from ${EngineUtil.langName(from)} " +
            "into ${EngineUtil.langName(to)}. Output ONLY the translation. " +
            "Preserve line breaks and paragraph structure exactly."
        val msgs = JSONArray()
        msgs.put(JSONObject().apply { put("role", "system"); put("content", sys) })
        msgs.put(JSONObject().apply { put("role", "user"); put("content", text) })
        val payload = JSONObject().apply {
            put("model", if (model.isBlank()) "gpt-4o-mini" else model)
            put("temperature", 0.2)
            put("stream", false)
            put("messages", msgs)
        }
        val resp = Http.postJson(
            endpoint,
            payload.toString(),
            mapOf("Authorization" to "Bearer $key", "Content-Type" to "application/json")
        )
        val obj = JSONObject(resp)
        obj.optJSONObject("error")?.let { throw TranslateException(it.optString("message")) }
        val out = obj.optJSONArray("choices")?.optJSONObject(0)
            ?.optJSONObject("message")?.optString("content")
            ?: obj.optString("text")
        return EngineUtil.clean(out.orEmpty())
    }
}

// ==============================================================
//  4) Google Gemini (مجاني بطبقة free، يحتاج مفتاحك)
// ==============================================================
object GeminiEngine : TranslateEngine {
    override val id = "gemini"
    override val displayName = "Google Gemini (مفتاح مجاني)"
    override val needsKey = true
    override val needsEndpoint = false
    override val description = "Gemini عبر Google AI Studio. مفتاح مجاني. جودة ممتازة للنصوص الطويلة."

    override fun translate(text: String, from: String, to: String, key: String, endpoint: String, model: String): String {
        if (key.isBlank()) throw TranslateException("حدّد مفتاح Gemini")
        val m = if (model.isBlank()) "gemini-1.5-flash" else model
        val base = if (endpoint.isBlank()) "https://generativelanguage.googleapis.com/v1beta" else endpoint.trimEnd('/')
        val url = "$base/models/$m:generateContent?key=$key"
        val prompt = "You are a professional translator. Translate the following text from " +
            "${EngineUtil.langName(from)} into ${EngineUtil.langName(to)}. " +
            "Output ONLY the translation, preserving all line breaks and paragraph structure:\n\n$text"
        val payload = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().apply { put("text", prompt) }))
            }))
        }
        val resp = Http.postJson(url, payload.toString(), mapOf("Content-Type" to "application/json"))
        val obj = JSONObject(resp)
        obj.optJSONObject("error")?.let { throw TranslateException(it.optString("message")) }
        val out = obj.optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content")?.optJSONArray("parts")
            ?.optJSONObject(0)?.optString("text")
        return EngineUtil.clean(out.orEmpty())
    }
}

// ==============================================================
//  5) LibreTranslate (مجاني / self-hosted، مفتاح اختياري)
// ==============================================================
object LibreEngine : TranslateEngine {
    override val id = "libre"
    override val displayName = "LibreTranslate (مجاني / سيرفرك)"
    override val needsKey = false
    override val needsEndpoint = true
    override val description = "مفتوح المصدر. ضع رابط السيرفر (الافتراضي عام). مفتاح اختياري."

    override fun translate(text: String, from: String, to: String, key: String, endpoint: String, model: String): String {
        val base = if (endpoint.isBlank()) "https://libretranslate.com" else endpoint.trimEnd('/')
        val payload = JSONObject().apply {
            put("q", text)
            put("source", if (from == "auto") "auto" else from)
            put("target", to)
            put("format", "text")
            if (key.isNotBlank()) put("api_key", key)
        }
        val resp = Http.postJson("$base/translate", payload.toString(), mapOf("Content-Type" to "application/json"))
        val obj = JSONObject(resp)
        obj.optJSONObject("error")?.let { throw TranslateException(it.optString("message")) }
        return obj.optString("translatedText")
    }
}

// ==============================================================
//  6) MyMemory (بدون مفتاح، حد يومي، مفيد كاحتياط)
// ==============================================================
object MyMemoryEngine : TranslateEngine {
    override val id = "mymemory"
    override val displayName = "MyMemory (بدون مفتاح)"
    override val needsKey = false
    override val needsEndpoint = false
    override val description = "محرك مجاني بحد يومي. جيد كخطة احتياطية."

    override fun translate(text: String, from: String, to: String, key: String, endpoint: String, model: String): String {
        val src = if (from == "auto") "en" else from
        val url = "https://api.mymemory.translated.net/get?q=${URLEncoder.encode(text, "UTF-8")}&langpair=$src|$to"
        val resp = Http.get(url)
        val obj = JSONObject(resp)
        val data = obj.optJSONObject("responseData")
        return data?.optString("translatedText").orEmpty()
    }
}

/** سجلّ كل المحركات. */
object EngineRegistry {
    val all: List<TranslateEngine> = listOf(
        GoogleFreeEngine,
        BingFreeEngine,
        GeminiEngine,
        OpenAICompatEngine,
        MyMemoryEngine,
        LibreEngine
    )

    fun byId(id: String): TranslateEngine = all.firstOrNull { it.id == id } ?: GoogleFreeEngine
}
