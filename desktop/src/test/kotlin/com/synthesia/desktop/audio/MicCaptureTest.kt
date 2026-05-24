package com.synthesia.desktop.audio

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.LineUnavailableException
import javax.sound.sampled.TargetDataLine

// Skips gracefully when the build host has no mic (e.g. inside a Docker container).
class MicCaptureTest {
    @Test
    fun targetDataLineOpensAndReadsAFrame() {
        val format = AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            44100f, 16, 1, 2, 44100f, false,
        )
        val info = DataLine.Info(TargetDataLine::class.java, format)
        assumeTrue(
            "No supported TargetDataLine on this build host (expected inside Docker)",
            AudioSystem.isLineSupported(info),
        )

        val line: TargetDataLine = try {
            AudioSystem.getLine(info) as TargetDataLine
        } catch (e: LineUnavailableException) {
            assumeTrue("Line unavailable: ${e.message}", false)
            return
        }

        try {
            line.open(format, 4096 * 2)
            line.start()
            val bytes = ByteArray(64)
            val read = line.read(bytes, 0, bytes.size)
            assertTrue("Expected non-negative read, got $read", read >= 0)
        } catch (e: LineUnavailableException) {
            assumeTrue("Could not acquire mic: ${e.message}", false)
        } finally {
            try { line.stop() } catch (_: Throwable) {}
            try { line.close() } catch (_: Throwable) {}
        }
    }
}
