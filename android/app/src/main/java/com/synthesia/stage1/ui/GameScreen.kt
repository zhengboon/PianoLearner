package com.synthesia.stage1.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.synthesia.stage1.game.GameSession
import com.synthesia.stage1.midi.midiListToNames

private const val LOOKAHEAD_SLOTS = 6

@Composable
fun GameScreen(
    session: GameSession,
    modifier: Modifier = Modifier,
) {
    val state by session.state.collectAsState()

    val upcoming: List<List<Int>> = remember(state.slotIndex, state.totalSlots) {
        val start = state.slotIndex
        val end = (start + LOOKAHEAD_SLOTS).coerceAtMost(session.slots.size)
        (start until end).map { idx -> session.slots[idx].notes.map { it.pitch } }
    }

    Column(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (state.isDone) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Song complete.", color = Color.White)
            }
        } else {
            val expectedNames = midiListToNames(state.expectedPitches)
            val heardNames = midiListToNames(state.heardPitches.sorted())
            Text(
                text = "Slot ${state.slotIndex + 1} / ${state.totalSlots}  expected=[$expectedNames]  heard=[$heardNames]",
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                color = Color.White,
            )
        }

        FallingNotesView(
            upcomingSlots = upcoming,
            heardCurrent = state.heardPitches,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )

        PianoKeyboardView(
            expected = state.expectedPitches.toSet(),
            heard = state.heardPitches,
            modifier = Modifier.fillMaxWidth().height(120.dp),
        )
    }
}
