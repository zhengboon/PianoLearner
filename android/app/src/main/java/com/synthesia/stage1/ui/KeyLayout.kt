package com.synthesia.stage1.ui

internal object KeyLayout {

    const val FIRST_MIDI = 21
    const val LAST_MIDI = 108
    const val WHITE_KEY_COUNT = 52
    const val BLACK_KEY_WIDTH_RATIO = 0.6f
    const val BLACK_KEY_HEIGHT_RATIO = 0.6f

    private val WHITE_PCS = setOf(0, 2, 4, 5, 7, 9, 11)

    // Cached: WHITES_BEFORE[m] = number of white keys in [FIRST_MIDI, m).
    // Saves an O(n) scan on every centerX/keyWidth call (hot in Canvas draws).
    private val WHITES_BEFORE: IntArray = IntArray(129).also { arr ->
        var count = 0
        for (m in 0..128) {
            arr[m] = count
            if (m in FIRST_MIDI..LAST_MIDI && isWhite(m)) count++
        }
    }

    // Per-pitch-class horizontal offset (in white-key-widths) so that black keys
    // sit asymmetrically within their 2- and 3-groupings, matching a real piano.
    // From sightread's `getBlackKeyXOffset` (`drawing/piano.ts:23`).
    // Without this, black keys are evenly distributed and visibly off-center for
    // the C#/D# pair and the F#/A# pair.
    private const val BLACK_OFFSET = 2f / 3f - 0.5f  // = 1/6

    fun isWhite(midi: Int): Boolean = (midi % 12) in WHITE_PCS

    fun whitesBefore(midi: Int): Int = WHITES_BEFORE[midi.coerceIn(0, 128)]

    fun blackKeyOffset(midi: Int): Float = when ((midi % 12 + 12) % 12) {
        1 -> -BLACK_OFFSET    // C#
        3 -> +BLACK_OFFSET    // D#
        6 -> -BLACK_OFFSET    // F#
        8 -> 0f               // G# centered in the 3-grouping
        10 -> +BLACK_OFFSET   // A#
        else -> 0f
    }

    fun centerX(midi: Int, totalWidth: Float): Float {
        val whiteW = totalWidth / WHITE_KEY_COUNT
        val wIdx = whitesBefore(midi)
        return if (isWhite(midi)) {
            wIdx * whiteW + whiteW / 2f
        } else {
            // Boundary between adjacent whites + per-pitch-class fudge factor.
            wIdx * whiteW + blackKeyOffset(midi) * whiteW
        }
    }

    fun keyWidth(midi: Int, totalWidth: Float): Float {
        val whiteW = totalWidth / WHITE_KEY_COUNT
        return if (isWhite(midi)) whiteW else whiteW * BLACK_KEY_WIDTH_RATIO
    }
}
