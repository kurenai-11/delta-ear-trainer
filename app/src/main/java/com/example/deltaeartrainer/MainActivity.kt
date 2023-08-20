package com.example.deltaeartrainer

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.example.deltaeartrainer.ui.theme.DeltaEarTrainerTheme
import com.un4seen.bass.BASS
import com.un4seen.bass.BASSMIDI
import com.un4seen.bass.BASSMIDI.BASS_MIDI_FONT
import java.io.InputStream
import java.nio.ByteBuffer
import kotlin.math.roundToInt

fun getHiword(num: Int): Int {
    return num shr 8
}

fun getLoword(num: Int): Int {
    return num shl 8
}

fun createDword(loWord: Int, hiWord: Int): Int {
    return hiWord shl 8 or loWord
}

fun playNote(midiChan: Int, noteNum: Int, velocity: Int = 127) {
    val param = createDword(noteNum, velocity)
    BASSMIDI.BASS_MIDI_StreamEvent(midiChan, 0, BASSMIDI.MIDI_EVENT_NOTE, param)
}

fun stopNote(midiChan: Int, noteNum: Int) {
    val param = createDword(noteNum, 0)
    BASSMIDI.BASS_MIDI_StreamEvent(midiChan, 0, BASSMIDI.MIDI_EVENT_NOTE, param)
}

fun getBuffer(resource: InputStream): ByteBuffer {
    val bytes = resource.readBytes()
    resource.close()
    return ByteBuffer.wrap(bytes)
}

fun playChord(midiChan: Int, notes: List<Int>, velocity: Int = 127) {
    val events: MutableList<BASSMIDI.BASS_MIDI_EVENT> = mutableListOf()
    for (note in notes) {
        val event = BASSMIDI.BASS_MIDI_EVENT()
        event.event = BASSMIDI.MIDI_EVENT_NOTE
        event.param = createDword(note, velocity)
        events.add(event)
    }
    BASSMIDI.BASS_MIDI_StreamEvents(
        midiChan,
        BASSMIDI.BASS_MIDI_EVENTS_STRUCT,
        Array(events.size) { i -> events[i] }, events.size
    )
}

class MainActivity : ComponentActivity() {
    private var midiChan = 0 // midi channel handle
    private var fontChan = 0 // soundfont channel handle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // initialize default output device
        if (!BASS.BASS_Init(-1, 44100, 0)) {
            throw Error("can't init bass")
        }
        BASSMIDI.BASS_MIDI_GetVersion() // force BASSMIDI to load
        BASS.BASS_SetConfig(BASSMIDI.BASS_CONFIG_MIDI_VOICES, 128) // set default voice limit
        BASS.BASS_SetConfig(BASS.BASS_CONFIG_UPDATEPERIOD, 15)
        BASS.BASS_SetConfig(BASS.BASS_CONFIG_BUFFER, 50)

        val result = BASSMIDI.BASS_MIDI_StreamCreate(128, 0, 0)
        if (result == 0) {
            throw Error("failed to initialize stream")
        } else {
            Log.d("Info", "opened stream: $result")
            midiChan = result
        }
        // load default soundfont

        val resource = resources.openRawResource(R.raw.steinway)
        val font = BASSMIDI.BASS_MIDI_FontInit(getBuffer(resource), 0)
        if (font == 0) {
            throw Error("font bad")
        } else {
            val sf = arrayOf(BASS_MIDI_FONT())
            sf[0].font = font
            sf[0].bank = 0
            sf[0].preset = -1
            BASSMIDI.BASS_MIDI_StreamSetFonts(0, sf, 1) // set default soundfont
            BASSMIDI.BASS_MIDI_StreamSetFonts(midiChan, sf, 1) // set for current stream too
        }
        BASS.BASS_ChannelPlay(midiChan, false) // start playing
        BASSMIDI.BASS_MIDI_StreamEvent(midiChan, 0, BASSMIDI.MIDI_EVENT_PROGRAM, 0)
        val fontInfo = BASSMIDI.BASS_MIDI_FONTINFO()
        BASSMIDI.BASS_MIDI_FontGetInfo(font, fontInfo)
        val presets = IntArray(fontInfo.presets)
        BASSMIDI.BASS_MIDI_FontGetPresets(font, presets)
        for (i in presets.indices) {
            val presetName = BASSMIDI.BASS_MIDI_FontGetPreset(font, presets[i], 0)
            Log.d("Info", "Preset name: $presetName ind: $i")
        }

        setContent {
            DeltaEarTrainerTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(BassData(midiChan, fontChan))
                }
            }
        }

    }

    override fun onDestroy() {
        // free the output device and all plugins and the soundfont
        BASS.BASS_Free()
        BASS.BASS_PluginFree(0)
        BASSMIDI.BASS_MIDI_FontFree(fontChan)

        super.onDestroy()
    }
}

class MainScreenPreviewParameterProvider1 : PreviewParameterProvider<BassData> {
    override val values = sequenceOf(BassData(0, 0))
}

data class BassData(val midiChan: Int, val fontChan: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun MainScreen(
    @PreviewParameter(MainScreenPreviewParameterProvider1::class) bassData: BassData
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Row {
                var expanded by remember { mutableStateOf(false) }
                val options =
                    listOf("Steinway", "Guitars", "Flute", "Nylon and steel guitars", "Strings")
                var selectedOptionText by remember { mutableStateOf(options[0]) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { e -> expanded = e }) {
                    OutlinedTextField(
                        readOnly = true,
                        value = selectedOptionText,
                        onValueChange = { },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(
                                expanded = expanded
                            )
                        },
                        colors = ExposedDropdownMenuDefaults.textFieldColors(),
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = {
                            expanded = false
                        }
                    ) {
                        options.forEach { selectionOption ->
                            DropdownMenuItem(
                                onClick = {
                                    selectedOptionText = selectionOption
                                    expanded = false
                                },
                                text = { Text(text = selectionOption) }
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                var expanded by remember { mutableStateOf(false) }
                val options = (1..88).map { v -> v.toString() }
                var selectedOptionText by remember { mutableStateOf(options[0]) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { e -> expanded = e }) {
                    TextField(
                        readOnly = true,
                        value = selectedOptionText,
                        onValueChange = { },
                        label = { Text("Lowest note") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(
                                expanded = expanded
                            )
                        },
                        colors = ExposedDropdownMenuDefaults.textFieldColors(),
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = {
                            expanded = false
                        }
                    ) {
                        Box(modifier = Modifier.size(width = 100.dp, height = 300.dp)) {
                            LazyColumn {
                                items(options) { item ->
                                    DropdownMenuItem(
                                        onClick = {
                                            selectedOptionText = item
                                            expanded = false
                                        },
                                        text = { Text(text = item) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                var expanded by remember { mutableStateOf(false) }
                val options = (1..88).map { v -> v.toString() }
                var selectedOptionText by remember { mutableStateOf(options[0]) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { e -> expanded = e }) {
                    TextField(
                        readOnly = true,
                        value = selectedOptionText,
                        onValueChange = { },
                        label = { Text("Highest note") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(
                                expanded = expanded
                            )
                        },
                        colors = ExposedDropdownMenuDefaults.textFieldColors(),
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = {
                            expanded = false
                        }
                    ) {
                        options.forEach { selectionOption ->
                            DropdownMenuItem(
                                onClick = {
                                    selectedOptionText = selectionOption
                                    expanded = false
                                },
                                text = { Text(text = selectionOption) }
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            var tempo by remember { mutableStateOf(20) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Tempo:")
                Text(text = tempo.toString())
            }
            Slider(
                value = tempo.toFloat(),
                onValueChange = { v -> tempo = v.roundToInt() },
                valueRange = 20F..160F
            )
            Button(onClick = {
                playNote(bassData.midiChan, 60)
                Thread.sleep(1000)
                stopNote(bassData.midiChan, 60)
            }) {
                Text(text = "Play C")
            }
        }
    }
}
