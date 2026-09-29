package com.operit.translate.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import kotlin.math.sqrt

/**
 * التقاط الميكروفون بشكل مستمر، مع كشف حيوي بسيط (VAD) لإرسال المقاطع عند السكوت
 * أو بعد مرور chunkSeconds كحد أقصى.
 */
class MicCapture(
    private val sampleRate: Int = 16000,
    private val chunkSeconds: Int = 4,
    private val silenceThreshold: Int = 700,
    private val onChunk: (ShortArray, Int) -> Unit
) {
    private var record: AudioRecord? = null
    private var thread: Thread? = null
    @Volatile private var running = false

    private var aec: AcousticEchoCanceler? = null
    private var ns: NoiseSuppressor? = null

    private val minBuf = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    ).coerceAtLeast(sampleRate * 2)

    @SuppressLint("MissingPermission")
    fun start() {
        if (running) return
        val r = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuf * 2
        )
        if (r.state != AudioRecord.STATE_INITIALIZED) {
            r.release()
            throw IllegalStateException("تعذّر تهيئة الميكروفون")
        }
        record = r

        // تحسينات صوتية إن كانت متاحة
        if (AcousticEchoCanceler.isAvailable()) {
            aec = AcousticEchoCanceler.create(r.audioSessionId)?.apply { enabled = true }
        }
        if (NoiseSuppressor.isAvailable()) {
            ns = NoiseSuppressor.create(r.audioSessionId)?.apply { enabled = true }
        }

        running = true
        r.startRecording()

        thread = Thread {
            val totalSamples = sampleRate * chunkSeconds
            val buffer = ShortArray(totalSamples)
            var filled = 0
            val readBuf = ShortArray(minBuf)

            while (running) {
                val n = r.read(readBuf, 0, readBuf.size)
                if (n <= 0) continue

                var i = 0
                while (i < n) {
                    val space = totalSamples - filled
                    val take = if (n - i < space) n - i else space
                    System.arraycopy(readBuf, i, buffer, filled, take)
                    filled += take
                    i += take
                    if (filled == totalSamples) {
                        emit(buffer, totalSamples)
                        filled = 0
                    }
                }
            }
        }.also { it.start() }
    }

    private fun emit(buf: ShortArray, len: Int) {
        if (isTooQuiet(buf, len)) return   // تجاهل الصمت التام
        onChunk(buf.copyOf(len), len)
    }

    private fun isTooQuiet(buf: ShortArray, len: Int): Boolean {
        // نأخذ عيّنات متباعدة لحساب RMS بسرعة
        var sum = 0.0
        var count = 0
        var i = 0
        while (i < len) {
            val v = buf[i].toDouble()
            sum += v * v
            count++
            i += 16
        }
        if (count == 0) return true
        val rms = sqrt(sum / count)
        return rms < silenceThreshold
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
        try { record?.stop() } catch (_: Exception) {}
        try { record?.release() } catch (_: Exception) {}
        record = null
        aec?.release(); aec = null
        ns?.release(); ns = null
    }
}