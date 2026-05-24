package com.synthesia.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.synthesia.desktop.audio.MicCapture
import com.synthesia.desktop.game.GameSession
import com.synthesia.desktop.midi.MidiFile
import com.synthesia.desktop.midi.MidiParser
import com.synthesia.desktop.ui.GameScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.prefs.Preferences
import javax.sound.sampled.Mixer
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

private val prefs: Preferences = Preferences.userRoot().node("com/synthesia/desktop")

fun main() {
    // Belt-and-suspenders: flush window-size prefs even on hard exit (Ctrl+C, kill).
    Runtime.getRuntime().addShutdownHook(Thread({
        try { prefs.flush() } catch (_: Throwable) {}
    }, "PrefsFlushHook"))

    application {
        val state = rememberWindowState(
            width = prefs.getInt("win.w", 1280).coerceAtLeast(640).dp,
            height = prefs.getInt("win.h", 720).coerceAtLeast(400).dp,
        )

        Window(
            onCloseRequest = {
                prefs.putInt("win.w", state.size.width.value.toInt().coerceAtLeast(640))
                prefs.putInt("win.h", state.size.height.value.toInt().coerceAtLeast(400))
                try { prefs.flush() } catch (_: Throwable) {}
                exitApplication()
            },
            title = "Synthesia Desktop",
            state = state,
        ) {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    AppRoot(window)
                }
            }
        }
    }
}

private sealed class MidiSource {
    abstract fun openStream(): InputStream

    class LocalFile(val file: File) : MidiSource() {
        override fun openStream() = file.inputStream()
    }

    class Resource(private val path: String) : MidiSource() {
        override fun openStream(): InputStream =
            MidiSource::class.java.classLoader.getResourceAsStream(path)
                ?: throw IllegalStateException("Bundled sample $path not found in resources")
    }
}

@Composable
private fun AppRoot(window: ComposeWindow) {
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var session by remember { mutableStateOf<GameSession?>(null) }
    var source by remember { mutableStateOf<MidiSource?>(null) }
    var selectedMic by remember { mutableStateOf<Mixer.Info?>(null) }
    val availableMics = remember { MicCapture.listInputDevices() }

    LaunchedEffect(source, selectedMic) {
        val src = source ?: return@LaunchedEffect
        loading = true
        error = null
        session?.stop()
        session = null
        try {
            val midi: MidiFile = withContext(Dispatchers.IO) {
                src.openStream().use { input -> MidiParser.parse(input) }
            }
            if (midi.notes.isEmpty()) {
                error = "The MIDI file has no playable notes."
            } else {
                session = GameSession(midi = midi, mic = MicCapture(mixerInfo = selectedMic))
            }
        } catch (t: Throwable) {
            error = t.message ?: t.javaClass.simpleName
        } finally {
            loading = false
        }
    }

    val active = session
    if (active != null) {
        GameContainer(
            session = active,
            onPickAnother = { source = pickMidiFile(window)?.let { MidiSource.LocalFile(it) } },
            onStop = {
                active.stop()
                session = null
                source = null
            },
        )
    } else {
        Picker(
            loading = loading,
            error = error,
            mics = availableMics,
            selectedMic = selectedMic,
            onSelectMic = { selectedMic = it },
            onPick = { source = pickMidiFile(window)?.let { MidiSource.LocalFile(it) } },
            onSample = { source = MidiSource.Resource("samples/scale.mid") },
        )
    }
}

@Composable
private fun Picker(
    loading: Boolean,
    error: String?,
    mics: List<Mixer.Info>,
    selectedMic: Mixer.Info?,
    onSelectMic: (Mixer.Info?) -> Unit,
    onPick: () -> Unit,
    onSample: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Pick a MIDI (.mid) file to practice.", color = Color.White)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Microphone: ${selectedMic?.name ?: "system default"}",
            color = Color.Gray,
        )
        if (mics.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Row {
                if (selectedMic != null) {
                    OutlinedButton(onClick = { onSelectMic(null) }) { Text("Default") }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                for (m in mics.take(4)) {
                    if (m.name != selectedMic?.name) {
                        OutlinedButton(onClick = { onSelectMic(m) }) {
                            Text(m.name.take(24))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onPick, enabled = !loading) {
            Text(if (loading) "Loading..." else "Pick MIDI file")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onSample, enabled = !loading) {
            Text("Try sample (C-major scale)")
        }
        if (error != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Error: $error", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun GameContainer(
    session: GameSession,
    onPickAnother: () -> Unit,
    onStop: () -> Unit,
) {
    DisposableEffect(session) {
        session.start()
        onDispose { session.stop() }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = onStop) { Text("Stop") }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onPickAnother) { Text("Pick another") }
        }
        GameScreen(session = session, modifier = Modifier.fillMaxSize())
    }
}

private fun pickMidiFile(parent: ComposeWindow): File? {
    val chooser = JFileChooser()
    chooser.dialogTitle = "Select a MIDI file"
    chooser.fileFilter = FileNameExtensionFilter("MIDI files (*.mid, *.midi)", "mid", "midi")
    val result = chooser.showOpenDialog(parent)
    return if (result == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}
