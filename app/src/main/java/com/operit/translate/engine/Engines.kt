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
    override val description = "محرك Google العام. مجاني تماماً، لا يحتاج مفتاحاً. قد يُحجب أحياناً."
    override fun translate(text: String, from: String, to: String, key: String, endpoint: String, model: String, prompt: String): String {
        val sl = if (from == "auto") "auto" else from
        val q = URLEncoder.encode(text, "UTF-8")
        val hosts = listOf(
            "https://clients5.google.com/translate_a/t?client=dict-chrome-ex&sl=$sl&tl=$to&q=$q",
            "https://translate.google.com/translate_a/single?client=gtx&sl=$sl&tl=$to&dt=t&q=$q",
            "https://translate.googleapis.com/translate_a/single?client=gtx&sl=$sl&tl=$to&dt=t&q=$q"
        )
        var lastErr = ""
        for (url in hosts) {
            try {
                val resp = Http.get(url, mapOf(
                    "User-Agent" to "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
                ))
                if (resp.isBlank() || resp.startsWith("<")) { lastErr = "محجوب"; continue }
                val out = parse(resp)
                if (out.isNotBlank()) return out
            } catch (e: Exception) {
                lastErr = e.message ?: "خطأ"
            }
        }
        throw TranslateException("Google المجاني غير متاح حالياً ($lastErr). جرّب محركاً آخر أو حط مفتاحك.")
    }

    private fun parse(resp: String): String {
        val t = resp.trim()
        return try {
            if (t.startsWith("[")) {
                val arr = JSONArray(t)
                val first = arr.opt(0)
                if (first is JSONArray) {
                    val sb = StringBuilder()
                    for (i in 0 until first.length()) {
                        val seg = first.optJSONArray(i) ?: continue
                        sb.append(seg.optString(0))
                    }
                    if (sb.isNotBlank()) return sb.toString()
                }
                if (first is String) return first
                ""
            } else ""
        } catch (e: Exception) { "" }
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
    override fun translate(text: String, from: String, to: String, key: String, endpoint: String, model: String, prompt: String): String {
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
//  3) مزوّدون متوافقون مع OpenAI (DeepSeek / OpenAI / Groq / OpenRouter /
//     Mistral / Together / xAI / Ollama / أي خدمة مخصّصة)
// ==============================================================
/**
 * محرك موحّد لأي خدمة تقبل /chat/completions.
 * كل مزوّد يحدّد endpoint و model افتراضيين، لكن المستخدم يقدر يغيّرهم.
 */
class OpenAICompatEngine(
    override val id: String,
    override val displayName: String,
    override val description: String,
    private val defaultEndpoint: String,
    private val defaultModel: String,
) : TranslateEngine {
    override val needsKey = true
    override val needsEndpoint = true

    override fun translate(text: String, from: String, to: String, key: String, endpoint: String, model: String, prompt: String): String {
        val ep = endpoint.ifBlank { defaultEndpoint }
        if (ep.isBlank()) throw TranslateException("حدّد رابط الـ endpoint")
        // بعض المزوّدين المحليين (Ollama) ما يحتاجون مفتاح
        val needsKey = !ep.contains("localhost") && !ep.contains("127.0.0.1") && !ep.contains("10.0.2.2")
        if (needsKey && key.isBlank()) throw TranslateException("حدّد مفتاح API")

        val sys = EngineUtil.buildPrompt(prompt, from, to)
        val msgs = JSONArray()
        msgs.put(JSONObject().apply { put("role", "system"); put("content", sys) })
        msgs.put(JSONObject().apply { put("role", "user"); put("content", text) })
        val payload = JSONObject().apply {
            put("model", if (model.isBlank()) defaultModel else model)
            put("temperature", 0.2)
            put("stream", false)
            put("messages", msgs)
        }
        val headers = mutableMapOf("Content-Type" to "application/json")
        if (key.isNotBlank()) headers["Authorization"] = "Bearer $key"

        val resp = Http.postJson(ep, payload.toString(), headers)
        val obj = JSONObject(resp)
        obj.optJSONObject("error")?.let { throw TranslateException(it.optString("message")) }
        val out = obj.optJSONArray("choices")?.optJSONObject(0)
            ?.optJSONObject("message")?.optString("content")
            ?: obj.optString("text")
        return EngineUtil.clean(out.orEmpty())
    }
}

// ==============================================================
//  4) Google Gemini (مفتاح مجاني)
// ==============================================================
object GeminiEngine : TranslateEngine {
    override val id = "gemini"
    override val displayName = "Google Gemini (مفتاح مجاني)"
    override val needsKey = true
    override val needsEndpoint = false
    override val description = "Gemini عبر Google AI Studio. مفتاح مجاني. جودة ممتازة للنصوص الطويلة."
    override fun translate(text: String, from: String, to: String, key: String, endpoint: String, model: String, prompt: String): String {
        if (key.isBlank()) throw TranslateException("حدّد مفتاح Gemini")
        val m = if (model.isBlank()) "gemini-1.5-flash" else model
        val base = if (endpoint.isBlank()) "https://generativelanguage.googleapis.com/v1beta" else endpoint.trimEnd('/')
        val url = "$base/models/$m:generateContent?key=$key"
        val sys = EngineUtil.buildPrompt(prompt, from, to)
        val full = "$sys\n\n$text"
        val payload = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().apply { put("text", full) }))
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
//  5) LibreTranslate (مفتوح المصدر / سيرفرك، مفتاح اختياري)
// ==============================================================
object LibreEngine : TranslateEngine {
    override val id = "libre"
    override val displayName = "LibreTranslate (مفتوح المصدر)"
    override val needsKey = false
    override val needsEndpoint = true
    override val description = "مترجم مفتوح المصدر. ضع رابط سيرفرك أو السيرفر العام. المفتاح اختياري."
    override fun translate(text: String, from: String, to: String, key: String, endpoint: String, model: String, prompt: String): String {
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
    override fun translate(text: String, from: String, to: String, key: String, endpoint: String, model: String, prompt: String): String {
        val src = if (from == "auto") "en" else from
        val url = "https://api.mymemory.translated.net/get?q=${URLEncoder.encode(text, "UTF-8")}&langpair=$src|$to"
        val resp = Http.get(url)
        val obj = JSONObject(resp)
        val data = obj.optJSONObject("responseData")
        return data?.optString("translatedText").orEmpty()
    }
}

// ==============================================================
//  سجلّ المحركات
// ==============================================================
object EngineRegistry {
    val all: List<TranslateEngine> = listOf(
        // === نماذج ذكاء اصطناعي (تحتاج مفتاحك) ===
        OpenAICompatEngine(
            id = "deepseek",
            displayName = "DeepSeek",
            description = "نموذج DeepSeek. جودة عالية وسعر رخيص. يحتاج مفتاح DeepSeek.",
            defaultEndpoint = "https://api.deepseek.com/chat/completions",
            defaultModel = "deepseek-chat"
        ),
        OpenAICompatEngine(
            id = "openai",
            displayName = "OpenAI (GPT)",
            description = "نماذج OpenAI الرسمية. يحتاج مفتاح sk-...",
            defaultEndpoint = "https://api.openai.com/v1/chat/completions",
            defaultModel = "gpt-4o-mini"
        ),
        OpenAICompatEngine(
            id = "groq",
            displayName = "Groq (سريع جداً)",
            description = "نماذج Llama/Mixtral بسرعة عالية جداً. مفتاح مجاني من Groq.",
            defaultEndpoint = "https://api.groq.com/openai/v1/chat/completions",
            defaultModel = "llama-3.3-70b-versatile"
        ),
        OpenAICompatEngine(
            id = "openrouter",
            displayName = "OpenRouter (كل النماذج)",
            description = "وصول لمئات النماذج بمفتاح واحد. تقدر تغيّر اسم النموذج بحرّية.",
            defaultEndpoint = "https://openrouter.ai/api/v1/chat/completions",
            defaultModel = "meta-llama/llama-3.3-70b-instruct"
        ),
        OpenAICompatEngine(
            id = "mistral",
            displayName = "Mistral AI",
            description = "نماذج Mistral. يحتاج مفتاح Mistral.",
            defaultEndpoint = "https://api.mistral.ai/v1/chat/completions",
            defaultModel = "mistral-large-latest"
        ),
        OpenAICompatEngine(
            id = "together",
            displayName = "Together AI",
            description = "نماذج مفتوحة المصدر مستضافة (Llama/Qwen...). يحتاج مفتاح Together.",
            defaultEndpoint = "https://api.together.xyz/v1/chat/completions",
            defaultModel = "meta-llama/Llama-3.3-70B-Instruct-Turbo"
        ),
        OpenAICompatEngine(
            id = "xai",
            displayName = "xAI (Grok)",
            description = "نماذج Grok من xAI. يحتاج مفتاح xAI.",
            defaultEndpoint = "https://api.x.ai/v1/chat/completions",
            defaultModel = "grok-2-latest"
        ),
        GeminiEngine,
        OpenAICompatEngine(
            id = "ollama",
            displayName = "Ollama / محلي (مفتوح المصدر)",
            description = "نموذج محلي على جهازك بدون إنترنت. مثال: http://localhost:11434/v1/chat/completions",
            defaultEndpoint = "http://localhost:11434/v1/chat/completions",
            defaultModel = "llama3.1"
        ),
        OpenAICompatEngine(
            id = "custom_openai",
            displayName = "مخصّص (OpenAI-compatible)",
            description = "أي خدمة متوافقة مع OpenAI. اكتب الرابط والاسم والمفتاح بنفسك.",
            defaultEndpoint = "",
            defaultModel = ""
        ),
        // === محركات بدون مفتاح (احتياط) ===
        GoogleFreeEngine,
        BingFreeEngine,
        MyMemoryEngine,
        LibreEngine
    )

    fun byId(id: String): TranslateEngine = all.firstOrNull { it.id == id } ?: MyMemoryEngine

    /** هل هذا المحرك نموذج ذكاء اصطناعي (يقبل برومبت مخصّص)؟ */
    fun isAi(id: String): Boolean = id in setOf(
        "deepseek", "openai", "groq", "openrouter", "mistral", "together", "xai", "gemini", "ollama", "custom_openai"
    )
}
