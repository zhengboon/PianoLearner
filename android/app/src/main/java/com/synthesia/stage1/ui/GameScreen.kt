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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.synthesia.stage1.game.GameSession
import com.synthesia.stage1.midi.midiListToNames
import kotlinx.coroutines.flow.collectLatest

private const val PRE_ROLL_SEC = 1.5f          // visual lead-in: notes start above the hit line and approach
private const val PX_PER_SEC_DEFAULT = 220f    // scroll speed; bigger = notes fall faster

@Composable
fun GameScreen(
    session: GameSession,
    modifier: Modifier = Modifier,
) {
    val state by session.state.collectAsState()
    val debug by session.debug.collectAsState()

    // Continuous-scroll playhead. Advances 1.0s/s in real time, but freezes at the
    // current slot's startTimeSec until the player advances. When slotIndex changes
    // (note hit), we don't snap; we just let the time keep flowing toward the new
    // slot's startTimeSec, giving the next falling note time to descend.
    var currentTimeSec by remember { mutableFloatStateOf(-PRE_ROLL_SEC) }

    // When the playhead changes slot (whether by hit or by reset), snap currentTime
    // to PRE_ROLL_SEC before the new target so the next note has lead-in.
    LaunchedEffect(session) {
        snapshotFlow { state.slotIndex }.collectLatest { idx ->
            val target = session.slots.getOrNull(idx)?.startTimeSec ?: return@collectLatest
            // First slot of the song: hold at -PRE_ROLL_SEC so the first note falls into place.
            if (idx == 0 && currentTimeSec > target) {
                currentTimeSec = target - PRE_ROLL_SEC
            }
        }
    }

    LaunchedEffect(session) {
        var lastFrameMs = -1L
        while (true) {
            withFrameMillis { frameTimeMs ->
                if (lastFrameMs < 0) {
                    lastFrameMs = frameTimeMs
                    return@withFrameMillis
                }
                val dt = (frameTimeMs - lastFrameMs).coerceAtMost(64L) / 1000f
                lastFrameMs = frameTimeMs
                val pauseAt = session.slots.getOrNull(state.slotIndex)?.startTimeSec
                if (pauseAt != null) {
                    val proposed = currentTimeSec + dt
                    currentTimeSec = if (proposed >= pauseAt) pauseAt else proposed
                } else {
                    currentTimeSec += dt   // song complete; let it scroll past
                }
            }
        }
    }

    Column(modifier = modifier.fillMaxSize().background(Color(0xFF0A0A0C))) {
        // Diagnostic strip (mic level + detected Hz)
        Text(
            text = "mic: rms=${"%.4f".format(debug.rms)}  hz=${debug.hz?.let { "%.1f".format(it) } ?: "—"}  frames=${debug.frameCount}",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            color = Color(0xFF7A7A7A),
        )

        if (state.isDone) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Song complete.", color = Color.White)
            }
        } else {
            val expectedNames = midiListToNames(state.expectedPitches)
            val heardNames = midiListToNames(state.heardPitches.sorted())
            Text(
                text = "${state.slotIndex + 1} / ${state.totalSlots}  ·  next [$expectedNames]  heard [$heardNames]",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                color = Color(0xFFE7E5E4),
            )
        }

        FallingNotesView(
            slots = session.slots,
            currentSlotIndex = state.slotIndex,
            currentTimeSec = currentTimeSec,
            heardCurrent = state.heardPitches,
            pxPerSec = PX_PER_SEC_DEFAULT,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )

        PianoKeyboardView(
            expected = state.expectedPitches.toSet(),
            heard = state.heardPitches,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
        )
    }
}
