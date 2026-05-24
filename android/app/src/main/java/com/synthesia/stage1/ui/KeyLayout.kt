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

    fun isWhite(midi: Int): Boolean = (midi % 12) in WHITE_PCS

    fun whitesBefore(midi: Int): Int = WHITES_BEFORE[midi.coerceIn(0, 128)]

    fun centerX(midi: Int, totalWidth: Float): Float {
        val whiteW = totalWidth / WHITE_KEY_COUNT
        val wIdx = whitesBefore(midi)
        return if (isWhite(midi)) wIdx * whiteW + whiteW / 2f else wIdx * whiteW
    }

    fun keyWidth(midi: Int, totalWidth: Float): Float {
        val whiteW = totalWidth / WHITE_KEY_COUNT
        return if (isWhite(midi)) whiteW else whiteW * BLACK_KEY_WIDTH_RATIO
    }
}
