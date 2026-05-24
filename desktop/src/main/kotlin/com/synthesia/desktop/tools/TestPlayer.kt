package com.synthesia.desktop.tools

import com.synthesia.desktop.audio.PitchDetector
import com.synthesia.desktop.game.PitchMatcher
import com.synthesia.desktop.game.PlayheadController
import com.synthesia.desktop.midi.MidiParser
import com.synthesia.desktop.midi.midiListToNames
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.asKotlinRandom
import kotlin.system.exitProcess

// Offline test driver. Replaces MicCapture with a synthesizer that arpeggiates each
// chord slot, then feeds the PCM through PitchDetector + PitchMatcher exactly as
// GameSession.onFrame would.
//
// Single-file mode:
//   gradlew testPlayer -PmidiPath="C:\path\to\file.mid" [-PmaxSlots=20]
//
// Batch mode (directory recurse + random sample):
//   gradlew testPlayer -PmidiPath="d:\midi-files\output" [-PmaxSlots=20] [-PsampleSize=200] [-Pseed=42]

private const val SAMPLE_RATE = 44100
private const val FRAME_SIZE = 4096
private const val SECONDS_PER_NOTE = 0.25f

// Shared across all batch-mode files. PitchDetector reuses internal NSDF/float buffers across
// `detect()` calls — safe ONLY because every TestPlayer invocation uses identical SAMPLE_RATE
// + FRAME_SIZE. Change those constants together if either is overridden.
private val detector = PitchDetector(sampleRate = SAMPLE_RATE)
private val matcher = PitchMatcher()

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        System.err.println("Usage: TestPlayer <path-to-.mid-or-dir> [maxSlots] [sampleSize] [seed]")
        exitProcess(2)
    }
    val target = File(args[0])
    if (!target.exists()) {
        System.err.println("Path not found: ${target.absolutePath}")
        exitProcess(2)
    }
    fun argOr(i: Int): String? = args.getOrNull(i)?.takeIf { it.isNotEmpty() && it != "-" }
    val maxSlots = argOr(1)?.toIntOrNull() ?: Int.MAX_VALUE
    val sampleSize = argOr(2)?.toIntOrNull() ?: 200
    val seed = argOr(3)?.toLongOrNull() ?: 42L

    if (target.isFile) {
        runSingle(target, maxSlots, verbose = true)
    } else {
        runBatch(target, maxSlots, sampleSize, seed)
    }
}

private data class FileResult(
    val slotsTested: Int,
    val slotsAdvanced: Int,
    val notesExpected: Int,
    val notesHeard: Int,
    val firstMisses: List<String>,
)

private fun runOne(midi: File, maxSlots: Int, capture: Boolean): FileResult {
    val mf = midi.inputStream().use { MidiParser.parse(it) }
    val playhead = PlayheadController(mf)
    val slotsToTry = minOf(playhead.slots.size, maxSlots)
    var advanced = 0
    var totalExpected = 0
    var totalHeard = 0
    val misses = ArrayList<String>()

    while (!playhead.isDone && playhead.index < slotsToTry) {
        val slot = playhead.current!!
        val expected = slot.pitches.toList()
        totalExpected += expected.size

        val samples = synthesizeArpeggio(expected, SAMPLE_RATE, SECONDS_PER_NOTE)
        val heard = mutableSetOf<Int>()
        var frameIdx = 0
        while (frameIdx * FRAME_SIZE + FRAME_SIZE <= samples.size) {
            val frame = ShortArray(FRAME_SIZE) { i -> samples[frameIdx * FRAME_SIZE + i] }
            frameIdx++
            val hz = detector.detect(frame) ?: continue
            val match = matcher.matchInSlot(hz, slot.pitches) ?: continue
            heard.add(match)
            if (heard.size >= expected.size) break
        }
        totalHeard += heard.size
        val ok = heard.size >= expected.size
        if (ok) advanced++ else if (capture && misses.size < 3) {
            misses.add("slot ${playhead.index + 1}: expected=[${midiListToNames(expected)}] heard=[${midiListToNames(heard.sorted())}]")
        }
        playhead.advance()
    }
    return FileResult(slotsToTry, advanced, totalExpected, totalHeard, misses)
}

private fun runSingle(file: File, maxSlots: Int, verbose: Boolean) {
    println("[test-player] loading ${file.name} (${file.length()} bytes)")
    val mf = file.inputStream().use { MidiParser.parse(it) }
    println("[test-player] parsed: ${mf.notes.size} note(s), PPQN=${mf.ticksPerQuarter}, tempo=${mf.microsPerQuarter}us/qn")
    val playhead = PlayheadController(mf)
    val slotsToTry = minOf(playhead.slots.size, maxSlots)
    println("[test-player] ${playhead.slots.size} slot(s) total, testing first $slotsToTry")
    println()

    var advanced = 0
    var missed = 0
    var totalExpected = 0
    var totalHeard = 0

    while (!playhead.isDone && playhead.index < slotsToTry) {
        val slot = playhead.current!!
        val expected = slot.pitches.toList()
        totalExpected += expected.size

        val samples = synthesizeArpeggio(expected, SAMPLE_RATE, SECONDS_PER_NOTE)
        val heard = mutableSetOf<Int>()
        var frameIdx = 0
        while (frameIdx * FRAME_SIZE + FRAME_SIZE <= samples.size) {
            val frame = ShortArray(FRAME_SIZE) { i -> samples[frameIdx * FRAME_SIZE + i] }
            frameIdx++
            val hz = detector.detect(frame) ?: continue
            val match = matcher.matchInSlot(hz, slot.pitches) ?: continue
            heard.add(match)
            if (heard.size >= expected.size) break
        }
        totalHeard += heard.size
        val ok = heard.size >= expected.size
        val mark = if (ok) "PASS" else "MISS"
        if (verbose || !ok) {
            println(
                "  slot ${playhead.index + 1}/${playhead.slots.size} [$mark]" +
                    "  expected=[${midiListToNames(expected)}]" +
                    "  heard=[${midiListToNames(heard.sorted())}]" +
                    "  frames=$frameIdx",
            )
        }
        if (ok) advanced++ else missed++
        playhead.advance()
    }

    println()
    val pct = if (slotsToTry > 0) advanced * 100.0 / slotsToTry else 0.0
    println("[test-player] summary:")
    println("[test-player]   slots advanced : $advanced / $slotsToTry  (${"%.1f".format(pct)}%)")
    println("[test-player]   slots missed   : $missed")
    println("[test-player]   notes heard    : $totalHeard / $totalExpected")
}

private fun runBatch(dir: File, maxSlots: Int, sampleSize: Int, seed: Long) {
    val all = dir.walk().filter { it.isFile && it.name.endsWith(".mid", ignoreCase = true) }.toList()
    // kotlin.random.asKotlinRandom is the stdlib bridge — no need to roll our own.
    val sample = if (all.size > sampleSize) all.shuffled(java.util.Random(seed).asKotlinRandom()).take(sampleSize) else all
    println("[test-player] batch mode: testing ${sample.size} of ${all.size} .mid file(s) in $dir")
    println("[test-player] seed=$seed maxSlotsPerFile=$maxSlots")
    println()

    var perfect = 0
    var totalSlots = 0
    var totalAdvanced = 0
    var totalNotesExp = 0
    var totalNotesHrd = 0
    val imperfect = ArrayList<Triple<String, Float, List<String>>>()
    var processed = 0

    for (f in sample) {
        val r = try {
            runOne(f, maxSlots, capture = true)
        } catch (t: Throwable) {
            imperfect.add(Triple(f.relativeTo(dir).path, 0f, listOf("PARSE-FAIL: ${t.message}")))
            processed++
            continue
        }
        totalSlots += r.slotsTested
        totalAdvanced += r.slotsAdvanced
        totalNotesExp += r.notesExpected
        totalNotesHrd += r.notesHeard
        if (r.slotsAdvanced == r.slotsTested) {
            perfect++
        } else {
            val acc = if (r.slotsTested > 0) r.slotsAdvanced * 100f / r.slotsTested else 0f
            imperfect.add(Triple(f.relativeTo(dir).path, acc, r.firstMisses))
        }
        processed++
        if (processed % 25 == 0) println("[test-player]   ...$processed/${sample.size}")
    }

    println()
    println("[test-player] AGGREGATE:")
    println("[test-player]   files perfect : $perfect / ${sample.size}  (${"%.1f".format(perfect * 100f / sample.size)}%)")
    println("[test-player]   slots        : $totalAdvanced / $totalSlots  (${"%.1f".format(totalAdvanced * 100f / totalSlots.coerceAtLeast(1))}%)")
    println("[test-player]   notes        : $totalNotesHrd / $totalNotesExp")
    if (imperfect.isNotEmpty()) {
        println()
        println("[test-player] WORST 25 FILES:")
        for ((path, acc, misses) in imperfect.sortedBy { it.second }.take(25)) {
            println("  ${"%.1f".format(acc)}%  $path")
            for (m in misses) println("        $m")
        }
    }
}

private fun synthesizeArpeggio(pitches: List<Int>, sampleRate: Int, perNoteSec: Float): ShortArray {
    if (pitches.isEmpty()) return ShortArray(0)
    val perNoteSamples = (sampleRate * perNoteSec).toInt()
    val attack = (0.010f * sampleRate).toInt()
    val release = (0.030f * sampleRate).toInt()
    val total = perNoteSamples * pitches.size
    val pcm = ShortArray(total)
    for ((idx, pitch) in pitches.withIndex()) {
        val f = PitchDetector.midiToHz(pitch).toDouble()
        val w = 2.0 * PI * f / sampleRate
        val w2 = w * 2.0
        val base = idx * perNoteSamples
        for (i in 0 until perNoteSamples) {
            var v = (sin(w * i) + 0.3 * sin(w2 * i)).toFloat() / 1.3f
            if (i < attack) v *= i.toFloat() / attack
            val tail = perNoteSamples - i
            if (tail < release) v *= tail.toFloat() / release
            pcm[base + i] = (v * 28000f).toInt().coerceIn(-32768, 32767).toShort()
        }
    }
    return pcm
}

@Suppress("unused")
private fun synthesizeChordSimultaneous(pitches: List<Int>, sampleRate: Int, seconds: Float): ShortArray {
    val n = (sampleRate * seconds).toInt()
    val out = FloatArray(n)
    for (pitch in pitches) {
        val f = PitchDetector.midiToHz(pitch).toDouble()
        val w = 2.0 * PI * f / sampleRate
        for (i in 0 until n) out[i] += sin(w * i).toFloat()
    }
    val peak = out.maxOf { abs(it) }.coerceAtLeast(1e-9f)
    val scale = 0.7f / peak
    return ShortArray(n) { i -> (out[i] * scale * 32760f).toInt().coerceIn(-32768, 32767).toShort() }
}

