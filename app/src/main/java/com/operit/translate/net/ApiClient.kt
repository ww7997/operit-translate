package com.operit.translate.net

import com.operit.translate.AppConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * عميل موحّد لأي خدمة متوافقة مع OpenAI:
 * - STT:  POST {sttEndpoint}  (multipart: file, model, language)
 * - MT :  POST {mtEndpoint}   (JSON: model, messages)
 *
 * كل النماذج والروابط تأتي من AppConfig => أنت من يختار.
 */
object ApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()
    private val WAV = "audio/wav".toMediaType()

    /** تحويل مقطع صوت WAV إلى نص. */
    fun transcribe(cfg: AppConfig, wav: File): String {
        val langPart = if (cfg.sourceLang == "auto" || cfg.sourceLang.isBlank())
            null else cfg.sourceLang

        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("model", cfg.sttModel)
            .addFormDataPart(
                "file", wav.name,
                wav.asRequestBody(WAV)
            )
        langPart?.let { builder.addFormDataPart("language", it) }
        builder.addFormDataPart("response_format", "json")

        val req = Request.Builder()
            .url(cfg.sttEndpoint)
            .addHeader("Authorization", "Bearer ${cfg.sttApiKey}")
            .post(builder.build())
            .build()

        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                throw RuntimeException("STT ${resp.code}: ${body.take(400)}")
            }
            // دعم أكثر من شكل استجابة: {text:...} أو {results:[...]} (بعض المزودين)
            return try {
                val obj = JSONObject(body)
                obj.optString("text").ifBlank {
                    obj.optJSONArray("results")?.optJSONObject(0)?.optString("text").orEmpty()
                }
            } catch (e: Exception) {
                body
            }
        }
    }

    /** ترجمة نص عبر chat/completions متوافق مع OpenAI. */
    fun translate(cfg: AppConfig, text: String): String {
        val sys = buildSystemPrompt(cfg)
        val payload = JSONObject().apply {
            put("model", cfg.mtModel)
            put("temperature", 0.2)
            put("stream", false)
            val msgs = JSONArray()
            msgs.put(JSONObject().apply {
                put("role", "system"); put("content", sys)
            })
            msgs.put(JSONObject().apply {
                put("role", "user"); put("content", text)
            })
            put("messages", msgs)
        }

        val req = Request.Builder()
            .url(cfg.mtEndpoint)
            .addHeader("Authorization", "Bearer ${cfg.mtApiKey}")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON))
            .build()

        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                throw RuntimeException("MT ${resp.code}: ${body.take(400)}")
            }
            val obj = JSONObject(body)
            return obj.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                ?.trim()
                ?: obj.optString("text").trim()
        }
    }

    private fun buildSystemPrompt(cfg: AppConfig): String {
        val src = if (cfg.sourceLang == "auto") "the detected language" else cfg.sourceLang
        return "You are a real-time subtitle translator. " +
            "Translate from $src into ${cfg.targetLang}. " +
            "Output ONLY the translation, no explanations, no quotes, no notes. " +
            "Keep it natural and concise as a subtitle line."
    }
}