package com.synthesia.desktop.audio

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.Mixer
import javax.sound.sampled.TargetDataLine

// Reads frames from a microphone via javax.sound.sampled. If mixerInfo is null,
// uses the system default; otherwise pins to that specific input device.
// 16-bit signed PCM, mono, little-endian. Accumulates partial reads into a full
// frame before invoking the callback so audio alignment is preserved.
class MicCapture(
    val sampleRate: Int = 44100,
    val frameSize: Int = 4096,
    private val mixerInfo: Mixer.Info? = null,
) {
    private val format = AudioFormat(
        AudioFormat.Encoding.PCM_SIGNED,
        sampleRate.toFloat(),
        16, 1, 2, sampleRate.toFloat(), false,
    )

    @Volatile private var line: TargetDataLine? = null
    @Volatile private var running = false
    private var thread: Thread? = null

    fun start(onFrame: (ShortArray) -> Unit) {
        if (running) return
        val info = DataLine.Info(TargetDataLine::class.java, format)
        val l = openLine(info).apply {
            open(format, frameSize * 4)
            start()
        }
        line = l
        running = true
        thread = Thread({
            val bytes = ByteArray(frameSize * 2)
            val shorts = ShortArray(frameSize)
            var byteOffset = 0
            while (running) {
                val read = l.read(bytes, byteOffset, bytes.size - byteOffset)
                if (read < 0) break
                if (read == 0) {
                    if (!running) break   // stop() in flight — don't spin
                    Thread.yield()        // avoid busy-loop if the line transiently returns 0
                    continue
                }
                byteOffset += read
                if (byteOffset >= bytes.size) {
                    for (i in 0 until frameSize) {
                        val lo = bytes[i * 2].toInt() and 0xFF
                        val hi = bytes[i * 2 + 1].toInt()
                        shorts[i] = ((hi shl 8) or lo).toShort()
                    }
                    onFrame(shorts.copyOf())
                    byteOffset = 0
                }
            }
        }, "MicCapture").apply {
            isDaemon = true
            start()
        }
    }

    private fun openLine(info: DataLine.Info): TargetDataLine {
        if (mixerInfo != null) {
            val m = AudioSystem.getMixer(mixerInfo)
            if (!m.isLineSupported(info)) {
                throw IllegalStateException(
                    "Selected mic '${mixerInfo.name}' does not support 16-bit PCM mono @ $sampleRate Hz."
                )
            }
            return m.getLine(info) as TargetDataLine
        }
        if (!AudioSystem.isLineSupported(info)) {
            val supported = listInputDevices(sampleRate).joinToString(", ") { it.name }
            throw IllegalStateException(
                "Default input does not support 16-bit PCM mono @ $sampleRate Hz. " +
                    "Compatible devices on this system: [$supported]"
            )
        }
        return AudioSystem.getLine(info) as TargetDataLine
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
        line?.run {
            try { stop() } catch (_: Throwable) {}
            try { close() } catch (_: Throwable) {}
        }
        line = null
    }

    companion object {
        // Mixer.Info entries that can supply a TargetDataLine in our default format.
        fun listInputDevices(sampleRate: Int = 44100): List<Mixer.Info> {
            val format = AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                sampleRate.toFloat(), 16, 1, 2, sampleRate.toFloat(), false,
            )
            val info = DataLine.Info(TargetDataLine::class.java, format)
            return AudioSystem.getMixerInfo().filter {
                try { AudioSystem.getMixer(it).isLineSupported(info) } catch (_: Throwable) { false }
            }
        }
    }
}
