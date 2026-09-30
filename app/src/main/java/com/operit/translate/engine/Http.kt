package com.operit.translate.engine

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** عميل HTTP مشترك لكل المحركات. */
object Http {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .build()

    val JSON = "application/json; charset=utf-8".toMediaType()
    val FORM = "application/x-www-form-urlencoded; charset=utf-8".toMediaType()

    fun get(url: String, headers: Map<String, String> = emptyMap()): String {
        val b = Request.Builder().url(url)
        headers.forEach { (k, v) -> b.addHeader(k, v) }
        client.newCall(b.build()).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw TranslateException("HTTP ${resp.code}: ${body.take(300)}")
            return body
        }
    }

    fun postJson(url: String, json: String, headers: Map<String, String> = emptyMap()): String {
        val b = Request.Builder().url(url)
        headers.forEach { (k, v) -> b.addHeader(k, v) }
        client.newCall(b.post(json.toRequestBody(JSON)).build()).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw TranslateException("HTTP ${resp.code}: ${body.take(300)}")
            return body
        }
    }

    fun postForm(url: String, form: String, headers: Map<String, String> = emptyMap()): String {
        val b = Request.Builder().url(url)
        headers.forEach { (k, v) -> b.addHeader(k, v) }
        client.newCall(b.post(form.toRequestBody(FORM)).build()).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw TranslateException("HTTP ${resp.code}: ${body.take(300)}")
            return body
        }
    }
}
