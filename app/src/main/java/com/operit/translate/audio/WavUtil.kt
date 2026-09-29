package com.operit.translate.audio

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * كاتب WAV بسيط + تشغيل PCM كأرقام قصيرة.
 * نستخدم WAV لأن Whisper-compatible APIs تقبل wav مباشرة.
 */
object WavUtil {

    /** ترميز PCM 16-bit إلى بايتات little-endian. */
    fun shortsToBytes(samples: ShortArray, len: Int): ByteArray {
        val out = ByteArray(len * 2)
        var i = 0
        var j = 0
        while (i < len) {
            val s = samples[i].toInt()
            out[j++] = (s and 0xFF).toByte()
            out[j++] = ((s shr 8) and 0xFF).toByte()
            i++
        }
        return out
    }

    /** يبني ملف WAV كامل من عينات PCM. */
    fun writeWav(file: File, samples: ByteArray, sampleRate: Int, channels: Int) {
        val dataLen = samples.size
        val byteRate = sampleRate * channels * 2
        val blockAlign = channels * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt(36 + dataLen)
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16)            // PCM chunk size
            putShort(1)           // PCM format
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort(blockAlign.toShort())
            putShort(16)          // bits per sample
            put("data".toByteArray())
            putInt(dataLen)
        }.array()

        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(0)
            raf.write(header)
            raf.write(samples)
        }
    }
}