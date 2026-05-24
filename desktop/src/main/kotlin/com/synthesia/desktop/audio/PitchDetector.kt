package com.synthesia.desktop.audio

import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

// McLeod Pitch Method, Tartini-style peak picker. Faithful port of
// TarsosDSP's McLeodPitchMethod.java with two adaptations:
//   - we limit NSDF computation to maxTau (saves O(n*tau) below piano A0)
//   - we expose a Hz-only `detect()` to match our existing call sites
// Returns detected Hz, or null when RMS gate fires or no peak survives cutoff.
class PitchDetector(
    val sampleRate: Int = 44100,
    private val minFreqHz: Float = 27.5f,
    private val maxFreqHz: Float = 4500f,
    private val cutoff: Float = 0.97f,
    private val smallCutoff: Float = 0.5f,
    private val rmsGate: Float = 0.005f,
) {

    private var floatBuf: FloatArray = FloatArray(0)
    private var nsdf: FloatArray = FloatArray(0)

    // Diagnostic: last frame's RMS, exposed so the UI can show a mic-level meter.
    @Volatile var lastRms: Float = 0f
        private set

    fun detect(frame: ShortArray): Float? {
        val n = frame.size
        if (n < 64) return null

        if (floatBuf.size != n) floatBuf = FloatArray(n)

        var sumSq = 0f
        for (i in 0 until n) {
            val v = frame[i] / 32768f
            floatBuf[i] = v
            sumSq += v * v
        }
        val rms = sqrt(sumSq / n)
        lastRms = rms
        if (rms < rmsGate) return null

        val maxTau = (sampleRate / minFreqHz).toInt().coerceAtMost(n / 2)
        val minTau = (sampleRate / maxFreqHz).toInt().coerceAtLeast(2)
        if (maxTau <= minTau + 1) return null
        val nsdfLen = maxTau + 1
        if (nsdf.size != nsdfLen) nsdf = FloatArray(nsdfLen)

        computeNsdf(floatBuf, nsdf, maxTau)

        // Filter peaks by tau >= minTau post-process (cleaner than clamping pos inside picker).
        val maxPositions = peakPicking(nsdf).filter { it >= minTau }
        if (maxPositions.isEmpty()) return null

        var highestAmp = -Float.MAX_VALUE
        val periodEstimates = ArrayList<Float>(maxPositions.size)
        val ampEstimates = ArrayList<Float>(maxPositions.size)
        for (tau in maxPositions) {
            highestAmp = maxOf(highestAmp, nsdf[tau])
            if (nsdf[tau] > smallCutoff) {
                val a = nsdf[tau - 1]
                val b = nsdf[tau]
                val c = nsdf[tau + 1]
                val bottom = c + a - 2f * b
                val px: Float
                val py: Float
                if (bottom == 0f) {
                    px = tau.toFloat()
                    py = b
                } else {
                    val delta = a - c
                    px = tau.toFloat() + delta / (2f * bottom)
                    py = b - delta * delta / (8f * bottom)
                }
                periodEstimates.add(px)
                ampEstimates.add(py)
                highestAmp = maxOf(highestAmp, py)
            }
        }
        if (periodEstimates.isEmpty()) return null

        val actualCutoff = cutoff * highestAmp
        var chosenIdx = 0
        for (i in ampEstimates.indices) {
            if (ampEstimates[i] >= actualCutoff) {
                chosenIdx = i
                break
            }
        }
        val period = periodEstimates[chosenIdx]
        if (period <= 0f) return null

        val hz = sampleRate / period
        if (hz < minFreqHz || hz > maxFreqHz) return null
        return hz
    }

    private fun computeNsdf(x: FloatArray, out: FloatArray, maxTau: Int) {
        val n = x.size
        for (tau in 0..maxTau) {
            var acf = 0f
            var div = 0f
            val end = n - tau
            for (i in 0 until end) {
                val xi = x[i]
                val xj = x[i + tau]
                acf += xi * xj
                div += xi * xi + xj * xj
            }
            out[tau] = if (div > 0f) 2f * acf / div else 0f
        }
    }

    // Port of TarsosDSP McLeodPitchMethod.peakPicking — no min-tau filter here; caller filters.
    private fun peakPicking(nsdf: FloatArray): List<Int> {
        val n = nsdf.size
        val out = ArrayList<Int>()
        var pos = 0
        var curMax = 0

        while (pos < (n - 1) / 3 && nsdf[pos] > 0f) pos++
        while (pos < n - 1 && nsdf[pos] <= 0f) pos++
        if (pos == 0) pos = 1

        while (pos < n - 1) {
            if (nsdf[pos] > nsdf[pos - 1] && nsdf[pos] >= nsdf[pos + 1]) {
                if (curMax == 0 || nsdf[pos] > nsdf[curMax]) curMax = pos
            }
            pos++
            if (pos < n - 1 && nsdf[pos] <= 0f) {
                if (curMax > 0) { out.add(curMax); curMax = 0 }
                while (pos < n - 1 && nsdf[pos] <= 0f) pos++
            }
        }
        if (curMax > 0) out.add(curMax)
        return out
    }

    companion object {
        fun midiToHz(midi: Int): Float =
            (440.0 * 2.0.pow((midi - 69) / 12.0)).toFloat()

        fun hzToMidi(hz: Float): Int =
            (ln(hz / 440.0) / ln(2.0) * 12.0 + 69.0).roundToInt()
    }
}
