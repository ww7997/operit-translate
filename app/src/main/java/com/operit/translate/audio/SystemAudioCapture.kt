package com.operit.translate.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import kotlin.math.sqrt

/**
 * التقاط صوت النظام (تطبيقات أخرى) عبر MediaProjection + AudioPlaybackCapture.
 *
 * ملاحظة قانونية/تقنية مهمة:
 *  - يشتغل فقط مع التطبيقات التي تسمح بـ allowAudioPlaybackCapture.
 *  - التطبيقات المحمية بـ DRM (يوتيوب/نتفليكس/سبوتيفاي غالباً) لا تُلتقط.
 *  - الأفضل للاجتماعات والتطبيقات التي تسمح بذلك.
 */
class SystemAudioCapture(
    private val projection: MediaProjection,
    private val playCfg: AudioPlaybackCaptureConfiguration,
    private val sampleRate: Int = 16000,
    private val chunkSeconds: Int = 4,
    private val silenceThreshold: Int = 500,
    private val onChunk: (ShortArray, Int) -> Unit
) {
    private var record: AudioRecord? = null
    private var thread: Thread? = null
    @Volatile private var running = false

    companion object {
        /** يبني إعداد الالتقاط من إسقاط الشاشة. */
        fun buildPlaybackConfig(projection: MediaProjection): AudioPlaybackCaptureConfiguration {
            return AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()
        }
    }

    private val minBuf = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    ).coerceAtLeast(sampleRate * 2)

    @SuppressLint("MissingPermission")
    fun start() {
        if (running) return
        val fmt = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()

        val r = AudioRecord.Builder()
            .setAudioFormat(fmt)
            .setBufferSizeInBytes(minBuf * 2)
            .setAudioPlaybackCaptureConfig(playCfg)
            .build()

        if (r.state != AudioRecord.STATE_INITIALIZED) {
            r.release()
            throw IllegalStateException("تعذّر التقاط صوت النظام لهذا التطبيق (ربما محجوب)")
        }
        record = r
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
                        if (!isTooQuiet(buffer, totalSamples)) {
                            onChunk(buffer.copyOf(totalSamples), totalSamples)
                        }
                        filled = 0
                    }
                }
            }
        }.also { it.start() }
    }

    private fun isTooQuiet(buf: ShortArray, len: Int): Boolean {
        var sum = 0.0; var count = 0; var i = 0
        while (i < len) {
            val v = buf[i].toDouble(); sum += v * v; count++; i += 16
        }
        if (count == 0) return true
        return sqrt(sum / count) < silenceThreshold
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
        try { record?.stop() } catch (_: Exception) {}
        try { record?.release() } catch (_: Exception) {}
        record = null
    }
}