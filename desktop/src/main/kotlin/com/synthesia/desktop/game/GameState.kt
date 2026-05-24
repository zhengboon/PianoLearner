package com.synthesia.desktop.game

data class GameState(
    val slotIndex: Int,
    val totalSlots: Int,
    val expectedPitches: List<Int>,
    val heardPitches: Set<Int>,
    val isDone: Boolean,
) {
    companion object {
        fun fromPlayhead(p: PlayheadController): GameState = GameState(
            slotIndex = p.index,
            totalSlots = p.slots.size,
            expectedPitches = p.current?.pitches?.toList() ?: emptyList(),
            heardPitches = emptySet(),
            isDone = p.isDone,
        )
    }
}
