package com.synthesia.desktop.ui

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
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.synthesia.desktop.game.GameSession
import com.synthesia.desktop.midi.midiListToNames

private const val PRE_ROLL_SEC = 1.5f
private const val PX_PER_SEC_DEFAULT = 220f

@Composable
fun GameScreen(
    session: GameSession,
    modifier: Modifier = Modifier,
) {
    val state by session.state.collectAsState()
    val debug by session.debug.collectAsState()
    val seekSnap by session.seekSnap.collectAsState()

    var currentTimeSec by remember { mutableFloatStateOf(-PRE_ROLL_SEC) }

    // Snap target travels atomically with seek version — no race on end-of-song scrub.
    LaunchedEffect(seekSnap.version) {
        currentTimeSec = (seekSnap.targetTimeSec - PRE_ROLL_SEC).coerceAtLeast(-PRE_ROLL_SEC)
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
                    currentTimeSec += dt
                }
            }
        }
    }

    Column(modifier = modifier.fillMaxSize().background(Color(0xFF0A0A0C))) {
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
            val cur = session.slots.getOrNull(state.slotIndex)?.startTimeSec ?: 0f
            val total = session.slots.lastOrNull()?.startTimeSec ?: 0f
            Text(
                text = "${state.slotIndex + 1} / ${state.totalSlots}  ·  ${"%.1f".format(cur)}s / ${"%.1f".format(total)}s  ·  next [$expectedNames]  heard [$heardNames]",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                color = Color(0xFFE7E5E4),
            )
        }

        SongScrubBar(
            totalSlots = state.totalSlots,
            currentSlot = state.slotIndex,
            onSeek = { session.seekTo(it) },
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )

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
