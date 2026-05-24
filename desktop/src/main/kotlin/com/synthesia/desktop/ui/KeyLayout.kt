package com.synthesia.desktop.ui

internal object KeyLayout {

    const val FIRST_MIDI = 21
    const val LAST_MIDI = 108
    const val WHITE_KEY_COUNT = 52
    const val BLACK_KEY_WIDTH_RATIO = 0.6f
    const val BLACK_KEY_HEIGHT_RATIO = 0.6f

    private val WHITE_PCS = setOf(0, 2, 4, 5, 7, 9, 11)

    private val WHITES_BEFORE: IntArray = IntArray(129).also { arr ->
        var count = 0
        for (m in 0..128) {
            arr[m] = count
            if (m in FIRST_MIDI..LAST_MIDI && isWhite(m)) count++
        }
    }

    // Per-pitch-class horizontal offset (in white-key-widths). Mirrors sightread's
    // `getBlackKeyXOffset` so black keys sit asymmetrically in their 2- and
    // 3-groupings, matching a real piano. Without this, blacks are mathematically
    // centered between their whites and look visibly off.
    private const val BLACK_OFFSET = 2f / 3f - 0.5f

    fun isWhite(midi: Int): Boolean = (midi % 12) in WHITE_PCS

    fun whitesBefore(midi: Int): Int = WHITES_BEFORE[midi.coerceIn(0, 128)]

    fun blackKeyOffset(midi: Int): Float = when ((midi % 12 + 12) % 12) {
        1 -> -BLACK_OFFSET
        3 -> +BLACK_OFFSET
        6 -> -BLACK_OFFSET
        8 -> 0f
        10 -> +BLACK_OFFSET
        else -> 0f
    }

    fun centerX(midi: Int, totalWidth: Float): Float {
        val whiteW = totalWidth / WHITE_KEY_COUNT
        val wIdx = whitesBefore(midi)
        return if (isWhite(midi)) {
            wIdx * whiteW + whiteW / 2f
        } else {
            wIdx * whiteW + blackKeyOffset(midi) * whiteW
        }
    }

    fun keyWidth(midi: Int, totalWidth: Float): Float {
        val whiteW = totalWidth / WHITE_KEY_COUNT
        return if (isWhite(midi)) whiteW else whiteW * BLACK_KEY_WIDTH_RATIO
    }
}
