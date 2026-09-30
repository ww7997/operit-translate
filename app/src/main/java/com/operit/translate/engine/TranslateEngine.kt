package com.operit.translate.engine

/**
 * واجهة موحّدة لكل محركات الترجمة.
 * كل محرك يعرّف: هل يحتاج مفتاحاً؟ وهل يحتاج endpoint؟ ثم ينفّذ translate().
 */
interface TranslateEngine {
    val id: String
    val displayName: String
    val needsKey: Boolean
    val needsEndpoint: Boolean
    val description: String
    val supportedLangs: List<String>? get() = null

    fun translate(
        text: String,
        from: String,
        to: String,
        key: String,
        endpoint: String,
        model: String,
    ): String
}

class TranslateException(message: String) : RuntimeException(message)

object EngineUtil {
    fun clean(out: String): String = out
        .trim()
        .removePrefix("```").removeSuffix("```")
        .trim()
        .trim('"')
        .trim()

    val LANG_NAMES: Map<String, String> = mapOf(
        "auto" to "auto-detect",
        "ar" to "Arabic",
        "en" to "English",
        "fr" to "French",
        "de" to "German",
        "es" to "Spanish",
        "it" to "Italian",
        "pt" to "Portuguese",
        "ru" to "Russian",
        "zh" to "Chinese (Simplified)",
        "zh-TW" to "Chinese (Traditional)",
        "ja" to "Japanese",
        "ko" to "Korean",
        "tr" to "Turkish",
        "fa" to "Persian",
        "hi" to "Hindi",
        "ur" to "Urdu",
        "he" to "Hebrew",
        "nl" to "Dutch",
        "pl" to "Polish",
        "sv" to "Swedish",
        "id" to "Indonesian",
        "th" to "Thai",
        "vi" to "Vietnamese",
        "uk" to "Ukrainian",
        "el" to "Greek",
        "ro" to "Romanian",
        "cs" to "Czech",
    )

    fun langName(code: String): String = LANG_NAMES[code] ?: code
}
