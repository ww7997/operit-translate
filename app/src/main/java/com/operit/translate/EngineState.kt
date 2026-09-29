package com.operit.translate

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SubtitleLine(
    val original: String,
    val translated: String,
    val ts: Long = System.currentTimeMillis()
)

/**
 * حالة التشغيل المشتركة بين الواجهة والخدمة والطبقة العائمة.
 */
object EngineState {
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _status = MutableStateFlow("جاهز")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _lines = MutableStateFlow<List<SubtitleLine>>(emptyList())
    val lines: StateFlow<List<SubtitleLine>> = _lines.asStateFlow()

    fun setRunning(v: Boolean) { _running.value = v }
    fun setStatus(s: String) { _status.value = s }
    fun addLine(line: SubtitleLine) {
        _lines.value = (_lines.value + line).takeLast(30)
    }
    fun clear() { _lines.value = emptyList() }
}