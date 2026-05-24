package com.synthesia.stage1

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.synthesia.stage1.game.GameSession
import com.synthesia.stage1.midi.MidiFile
import com.synthesia.stage1.midi.MidiParser
import com.synthesia.stage1.ui.GameScreen
import com.synthesia.stage1.ui.theme.SynthesiaCloneTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

private val MIDI_MIME_TYPES = arrayOf(
    "audio/midi", "audio/x-midi", "audio/mid", "application/octet-stream", "*/*",
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SynthesiaCloneTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppRoot()
                }
            }
        }
    }

    @Composable
    private fun AppRoot() {
        var hasMic by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.RECORD_AUDIO,
                ) == PackageManager.PERMISSION_GRANTED
            )
        }
        var wasDenied by remember { mutableStateOf(false) }

        // Re-check the permission on every ON_RESUME — the user may have toggled it in
        // system Settings while we were backgrounded. We use the Activity directly as the
        // LifecycleOwner; LocalLifecycleOwner from lifecycle-runtime-compose 2.8 isn't
        // auto-provided on Compose UI 1.6.x.
        val activity = this@MainActivity
        DisposableEffect(activity) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    val granted = ContextCompat.checkSelfPermission(
                        activity,
                        Manifest.permission.RECORD_AUDIO,
                    ) == PackageManager.PERMISSION_GRANTED
                    hasMic = granted
                    if (granted) wasDenied = false
                }
            }
            activity.lifecycle.addObserver(observer)
            onDispose { activity.lifecycle.removeObserver(observer) }
        }

        val micLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            hasMic = granted
            if (!granted) wasDenied = true
        }

        if (!hasMic) {
            PermissionGate(
                wasDenied = wasDenied,
                onRequest = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
            )
        } else {
            MidiFlow()
        }
    }
}

@Composable
private fun PermissionGate(wasDenied: Boolean, onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (wasDenied) {
            Text("Microphone permission was denied. The app can't detect your piano without it. " +
                "Tap below to retry, or grant it from Settings → Apps → Synthesia Clone → Permissions.")
        } else {
            Text("Microphone access is required to listen for piano notes.")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRequest) { Text("Grant microphone permission") }
    }
}

private sealed class MidiSource {
    abstract fun open(context: android.content.Context): InputStream?

    // Intentionally NOT data classes: we want every pick to re-fire the LaunchedEffect,
    // even when the user re-picks the same URI / re-clicks "Try sample" after an error.
    class ContentUri(val uri: Uri) : MidiSource() {
        override fun open(context: android.content.Context) = context.contentResolver.openInputStream(uri)
    }

    class Asset(val path: String) : MidiSource() {
        override fun open(context: android.content.Context): InputStream = context.assets.open(path)
    }
}

@Composable
private fun MidiFlow() {
    val context = LocalContext.current
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var session by remember { mutableStateOf<GameSession?>(null) }
    var source by remember { mutableStateOf<MidiSource?>(null) }

    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> if (uri != null) source = MidiSource.ContentUri(uri) }

    LaunchedEffect(source) {
        val s = source ?: return@LaunchedEffect
        loading = true
        error = null
        session?.stop()
        session = null
        try {
            val midi: MidiFile = withContext(Dispatchers.IO) {
                s.open(context).use { input ->
                    if (input == null) error("Could not open the picked source.")
                    MidiParser.parse(input)
                }
            }
            if (midi.notes.isEmpty()) {
                error = "The MIDI file has no playable notes."
            } else {
                session = GameSession(midi)
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
            onPickAnother = { pickLauncher.launch(MIDI_MIME_TYPES) },
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
            onPick = { pickLauncher.launch(MIDI_MIME_TYPES) },
            onSample = { source = MidiSource.Asset("samples/scale.mid") },
        )
    }
}

@Composable
private fun Picker(
    loading: Boolean,
    error: String?,
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
        Text("Pick a MIDI (.mid) file to practice.")
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

@SuppressLint("MissingPermission")
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

    Column(modifier = Modifier.fillMaxSize()) {
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
