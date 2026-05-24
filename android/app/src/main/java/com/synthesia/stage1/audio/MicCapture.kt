package com.synthesia.stage1.audio

import android.Manifest
import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission

// Background-thread microphone reader. Tries UNPROCESSED first (no AGC / echo cancellation,
// better pitch fidelity), falls back to VOICE_RECOGNITION if the device doesn't support it.
// Accumulates partial reads into full frames before invoking the callback.
// Caller must verify RECORD_AUDIO is granted before invoking start().
class MicCapture(
    val sampleRate: Int = 44100,
    val frameSize: Int = 4096,
) {
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val encoding = AudioFormat.ENCODING_PCM_16BIT

    val bufferSize: Int = run {
        val min = AudioRecord.getMinBufferSize(sampleRate, channelConfig, encoding)
        if (min <= 0) frameSize * 4 else maxOf(min, frameSize * 2)
    }

    @Volatile private var recorder: AudioRecord? = null
    @Volatile private var running = false
    private var thread: Thread? = null

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start(onFrame: (ShortArray) -> Unit) {
        if (running) return
        val r = openRecorder()
        if (r.state != AudioRecord.STATE_INITIALIZED) {
            r.release()
            throw IllegalStateException("AudioRecord initialization failed for both VOICE_RECOGNITION and MIC sources")
        }
        recorder = r
        running = true
        r.startRecording()
        thread = Thread({
            val buf = ShortArray(frameSize)
            var offset = 0
            while (running) {
                val read = r.read(buf, offset, buf.size - offset)
                if (read < 0) break
                if (read == 0) {
                    if (!running) break   // stop() in flight — don't spin
                    Thread.yield()        // avoid busy-loop if the line transiently returns 0
                    continue
                }
                offset += read
                if (offset >= buf.size) {
                    onFrame(buf.copyOf())
                    offset = 0
                }
            }
        }, "MicCapture").apply {
            isDaemon = true
            start()
        }
    }

    @SuppressLint("MissingPermission")
    private fun openRecorder(): AudioRecord {
        // VOICE_RECOGNITION is universally supported and gives clean audio without AGC.
        // We previously tried UNPROCESSED first, but many devices report STATE_INITIALIZED
        // for it and then deliver pure silence (the HAL doesn't actually support it,
        // and only the AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED probe — which
        // needs a Context — tells the truth). Stay safe.
        try {
            val ar = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate, channelConfig, encoding, bufferSize,
            )
            if (ar.state == AudioRecord.STATE_INITIALIZED) return ar
            ar.release()
        } catch (_: Throwable) { /* fall through to MIC */ }
        // Last resort: generic MIC source.
        return AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate, channelConfig, encoding, bufferSize,
        )
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
        recorder?.let {
            try { it.stop() } catch (_: Throwable) {}
            it.release()
        }
        recorder = null
    }
}
