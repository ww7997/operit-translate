package com.operit.translate.doc

import com.operit.translate.Config
import com.operit.translate.Translator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * حالة ترجمة مستند: قائمة الفقرات + التقدّم + حالة التشغيل.
 */
data class DocState(
    val title: String = "",
    val chunks: List<DocChunk> = emptyList(),
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val status: String = "جاهز",
    val mode: String = "bilingual"
) {
    val progress: Float
        get() = if (total <= 0) 0f else done.toFloat() / total.toFloat()
}

/**
 * يترجم كتاب/مستند كامل فقرة فقرة، ويحدّث الحالة تباعاً.
 */
object DocTranslator {

    private val _state = MutableStateFlow(DocState())
    val state: StateFlow<DocState> = _state

    fun reset() {
        _state.value = DocState()
    }

    fun loadDoc(title: String, chunks: List<DocChunk>) {
        _state.value = DocState(
            title = title,
            chunks = chunks,
            running = false,
            done = 0,
            total = chunks.size,
            status = "تم التحميل: " + chunks.size + " فقرة"
        )
    }

    /**
     * يبدأ الترجمة. scope لازم يكون مطابق لدورة حياة الواجهة.
     */
    fun start(cfg: Config, scope: CoroutineScope) {
        val cur = _state.value
        if (cur.running) return
        scope.launch {
            _state.value = cur.copy(running = true, done = 0, status = "عم نترجم...")
            val list = cur.chunks.toMutableList()
            var done = 0
            for (i in list.indices) {
                val c = list[i]
                if (c.text.isBlank() || c.text.startsWith("[[")) {
                    done++
                    _state.value = _state.value.copy(done = done)
                    continue
                }
                try {
                    val t = withContext(Dispatchers.IO) {
                        Translator.translate(cfg, c.text)
                    }
                    c.translated = t
                    c.error = null
                } catch (e: Exception) {
                    c.error = e.message ?: "خطأ"
                }
                done++
                _state.value = _state.value.copy(
                    chunks = list.toList(),
                    done = done,
                    status = "عم نترجم... $done / ${list.size}"
                )
            }
            val errs = list.count { it.error != null }
            _state.value = _state.value.copy(
                running = false,
                status = if (errs == 0) "خلصت الترجمة ✅ ($done فقرة)" else "خلصت ($errs فقرة فشلت)"
            )
        }
    }

    fun currentMode(): String = _state.value.mode

    fun setMode(m: String) {
        _state.value = _state.value.copy(mode = m)
    }

    /** يبني المستند المترجم نصاً أو HTML للتصدير. */
    fun export(asHtml: Boolean): Pair<String, String> {
        val s = _state.value
        val ext = if (asHtml) "html" else "txt"
        val name = (if (s.title.isBlank()) "translated" else s.title) + "_translated." + ext
        val content = DocWriter.buildText(s.title, s.chunks, s.mode, asHtml)
        return name to content
    }
}