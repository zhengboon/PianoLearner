package com.synthesia.desktop.midi

import java.io.IOException
import java.io.InputStream

// SMF Format 0/1 reader. Extracts NoteOn/NoteOff into a time-ordered List<NoteEvent>.
// First Set Tempo meta is captured; subsequent tempo changes are ignored (Stage 2).
// Channel 9 (GM drums) is dropped. NoteOn velocity=0 is treated as NoteOff.
// SMPTE division is rejected.
object MidiParser {

    private const val HEADER_TAG = 0x4D546864 // "MThd"
    private const val TRACK_TAG = 0x4D54726B  // "MTrk"
    private const val MAX_HEADER_LEN = 1024
    private const val MAX_TRACK_LEN = 256 * 1024 * 1024

    fun parse(input: InputStream): MidiFile = input.use { parseBytes(it.readBytes()) }

    fun parseBytes(bytes: ByteArray): MidiFile {
        val r = ByteReader(bytes)

        if (r.readInt32() != HEADER_TAG) throw IOException("Not an SMF: missing MThd")
        val headerLen = r.readInt32()
        if (headerLen < 6 || headerLen > MAX_HEADER_LEN) {
            throw IOException("Implausible SMF header length: $headerLen")
        }
        val format = r.readUInt16()
        val ntracks = r.readUInt16()
        val division = r.readUInt16()
        repeat(headerLen - 6) { r.readUInt8() }

        if (format != 0 && format != 1) throw IOException("Unsupported MIDI format $format")
        if (division and 0x8000 != 0) throw IOException("SMPTE timecode division not supported in Stage 1")
        val ppqn = division and 0x7FFF

        var microsPerQuarter = 500_000
        var sawTempo = false
        val allNotes = ArrayList<NoteEvent>(1024)

        for (t in 0 until ntracks) {
            val tag = r.readInt32()
            if (tag != TRACK_TAG) throw IOException("Track $t: expected MTrk at offset ${r.position - 4}")
            val trackLen = r.readInt32()
            if (trackLen < 0 || trackLen > MAX_TRACK_LEN) {
                throw IOException("Track $t: implausible length $trackLen")
            }
            // Long math: r.position + trackLen could overflow Int for very large files.
            val trackEndLong: Long = r.position.toLong() + trackLen
            if (trackEndLong > bytes.size.toLong()) {
                throw IOException("Track $t: trackLen extends past end of file")
            }
            val trackEnd = trackEndLong.toInt()

            var runningStatus = 0
            var absTick = 0L
            val pending = Array(16) { arrayOfNulls<Pending>(128) }

            while (r.position < trackEnd) {
                absTick += r.readVarLen()
                var status = r.readUInt8()
                if (status < 0x80) {
                    r.unread(status)
                    status = runningStatus
                    if (status == 0) throw IOException("Running status with no prior channel event")
                } else if (status < 0xF0) {
                    runningStatus = status
                }

                val type = status and 0xF0
                val channel = status and 0x0F

                when (status) {
                    0xFF -> {
                        val metaType = r.readUInt8()
                        val metaLen = r.readVarLen()
                        when (metaType) {
                            0x51 -> {
                                if (metaLen != 3) throw IOException("Set Tempo length must be 3, got $metaLen")
                                val us = (r.readUInt8() shl 16) or (r.readUInt8() shl 8) or r.readUInt8()
                                if (us <= 0) throw IOException("Set Tempo: microsPerQuarter must be positive, got $us")
                                if (!sawTempo) {
                                    microsPerQuarter = us
                                    sawTempo = true
                                }
                            }
                            0x2F -> {
                                if (metaLen != 0) r.skip(metaLen)
                            }
                            else -> r.skip(metaLen)
                        }
                    }
                    0xF0, 0xF7 -> {
                        val sysLen = r.readVarLen()
                        r.skip(sysLen)
                    }
                    else -> when (type) {
                        0x80 -> {
                            val pitch = r.readUInt8()
                            r.readUInt8()
                            emitNoteOff(pending, channel, pitch, absTick, allNotes)
                        }
                        0x90 -> {
                            val pitch = r.readUInt8()
                            val vel = r.readUInt8()
                            if (vel == 0) {
                                emitNoteOff(pending, channel, pitch, absTick, allNotes)
                            } else if (channel != 9) {
                                // Flush any prior pending note on this channel/pitch — re-trigger
                                // without an intervening NoteOff would otherwise lose the first note.
                                emitNoteOff(pending, channel, pitch, absTick, allNotes)
                                pending[channel][pitch] = Pending(absTick, vel)
                            }
                        }
                        0xA0, 0xB0, 0xE0 -> r.skip(2)
                        0xC0, 0xD0 -> r.skip(1)
                        else -> throw IOException(
                            "Unknown MIDI status 0x${status.toString(16)} at track $t offset ${r.position}"
                        )
                    }
                }
            }

            // Flush any notes still on at end-of-track (malformed MIDIs sometimes omit final NoteOff).
            for (ch in 0 until 16) {
                if (ch == 9) continue
                for (pitch in 0 until 128) {
                    val p = pending[ch][pitch] ?: continue
                    pending[ch][pitch] = null
                    val dur = absTick - p.startTick
                    if (dur <= 0L) continue  // skip zero-duration ghost
                    allNotes.add(NoteEvent(pitch, p.startTick, dur, p.velocity, ch))
                }
            }

            r.position = trackEnd
        }

        allNotes.sortBy { it.startTick }
        return MidiFile(
            ticksPerQuarter = ppqn,
            microsPerQuarter = microsPerQuarter,
            notes = allNotes,
        )
    }

    private fun emitNoteOff(
        pending: Array<Array<Pending?>>,
        channel: Int,
        pitch: Int,
        absTick: Long,
        out: MutableList<NoteEvent>,
    ) {
        if (channel == 9) return
        val p = pending[channel][pitch] ?: return
        pending[channel][pitch] = null
        val dur = absTick - p.startTick
        if (dur <= 0L) return  // skip zero-duration ghosts (e.g., NoteOn re-triggered at same tick)
        out.add(
            NoteEvent(
                pitch = pitch,
                startTick = p.startTick,
                durationTicks = dur,
                velocity = p.velocity,
                channel = channel,
            ),
        )
    }

    private data class Pending(val startTick: Long, val velocity: Int)

    private class ByteReader(private val data: ByteArray) {
        var position: Int = 0
        private var pushed: Int = -1

        fun readUInt8(): Int {
            if (pushed >= 0) {
                val v = pushed
                pushed = -1
                return v
            }
            if (position >= data.size) throw IOException("Unexpected EOF at $position")
            return data[position++].toInt() and 0xFF
        }

        fun unread(value: Int) {
            if (pushed >= 0) throw IOException("Unread buffer full")
            pushed = value
        }

        fun readUInt16(): Int = (readUInt8() shl 8) or readUInt8()

        fun readInt32(): Int =
            (readUInt8() shl 24) or (readUInt8() shl 16) or (readUInt8() shl 8) or readUInt8()

        fun readVarLen(): Int {
            var v = 0
            var b: Int
            var count = 0
            do {
                b = readUInt8()
                v = (v shl 7) or (b and 0x7F)
                count++
                if (count > 4) throw IOException("Variable-length quantity exceeds 4 bytes")
            } while ((b and 0x80) != 0)
            return v
        }

        fun skip(n: Int) {
            var remaining = n
            if (pushed >= 0 && remaining > 0) {
                pushed = -1
                remaining--
            }
            position += remaining
        }
    }
}
