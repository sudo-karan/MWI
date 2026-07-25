package com.ismartcoding.plain.features.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Live sound-level meter (spec §7A). Reads PCM from the mic and publishes a 0–90 "dB-ish" level.
 * Requires RECORD_AUDIO; [start] is a no-op (returns false) if the permission/mic is unavailable.
 */
object SoundMeter {
    val level = MutableStateFlow(0.0)

    private const val SAMPLE_RATE = 44_100

    @Volatile
    private var running = false
    private var record: AudioRecord? = null
    private var thread: Thread? = null

    fun start(): Boolean {
        if (running) return true
        return runCatching {
            val minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            ).coerceAtLeast(2048)
            val rec = AudioRecord(
                MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf * 2,
            )
            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                rec.release()
                return false
            }
            record = rec
            running = true
            rec.startRecording()
            thread = Thread({ loop(rec, minBuf) }, "mwi-sound-meter").apply { start() }
            true
        }.getOrDefault(false)
    }

    private fun loop(rec: AudioRecord, bufSize: Int) {
        val buffer = ShortArray(bufSize)
        while (running) {
            val n = rec.read(buffer, 0, buffer.size)
            if (n > 0) {
                var sum = 0.0
                for (i in 0 until n) {
                    val s = buffer[i].toDouble()
                    sum += s * s
                }
                val rms = sqrt(sum / n)
                val dbfs = if (rms > 0) 20 * log10(rms / 32768.0) else -160.0
                // Shift dBFS (~-90..0) into a friendly 0..90 scale.
                level.value = (dbfs + 90).coerceIn(0.0, 90.0)
            }
        }
    }

    fun stop() {
        running = false
        runCatching { thread?.join(300) }
        runCatching { record?.stop() }
        runCatching { record?.release() }
        record = null
        thread = null
        level.value = 0.0
    }
}
