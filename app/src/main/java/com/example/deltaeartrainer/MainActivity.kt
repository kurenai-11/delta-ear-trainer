package com.example.deltaeartrainer

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.example.deltaeartrainer.ui.theme.DeltaEarTrainerTheme
import com.un4seen.bass.BASS
import com.un4seen.bass.BASSMIDI
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.Timer
import kotlin.concurrent.schedule
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.random.nextInt

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

fun playChord(midiChan: Int, notes: IntArray, velocity: Int = 127) {
    val events = notes.map { note ->
        val event = BASSMIDI.BASS_MIDI_EVENT()
        event.event = BASSMIDI.MIDI_EVENT_NOTE
        event.param = createDword(note, velocity)
        event
    }.toTypedArray()
    BASSMIDI.BASS_MIDI_StreamEvents(
        midiChan, BASSMIDI.BASS_MIDI_EVENTS_STRUCT, events, events.size
    )
}


class MainActivity : ComponentActivity() {
    private var midiChan = 0 // midi channel handle
    private var fontChan = 0 // soundfont channel handle

    fun abc() {
        Log.d("Info", "hey")
    }

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

        BASS.BASS_ChannelPlay(midiChan, false) // start playing

        val soundfonts = listOf(
            SoundFont("Steinway", R.raw.steinway),
            SoundFont("Guitars 1 (general)", R.raw.guitars1),
            SoundFont("Guitars 2 (more acoustic)", R.raw.guitars2),
            SoundFont("Flute", R.raw.flute),
            SoundFont("Strings", R.raw.strings),
        )

        setContent {
            DeltaEarTrainerTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(BassData(midiChan, fontChan, soundfonts))
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
    override val values = sequenceOf(BassData(0, 0, listOf(SoundFont("Soundfont", 123))))
}

data class BassData(val midiChan: Int, val fontChan: Int, val soundfonts: List<SoundFont>)
data class SoundFont(val name: String, val resourceId: Int)
data class Preset(val name: String, val index: Int)

fun getPlayableNotes(
    chosenNotes: Array<PitchClass>,
    lowestNote: Note,
    highestNote: Note
): List<Note> {
    return getPossibleNotes(lowestNote, highestNote)
        .filter { chosenNotes.contains(it.pitch) }
}

fun getPossibleNotes(lowestNote: Note, highestNote: Note): List<Note> {
    return (0..(highestNote.midiIndex - lowestNote.midiIndex)).map { lowestNote + it }
}

@SuppressLint("MutableCollectionMutableState")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Preview(showBackground = true)
@Composable
fun MainScreen(
    @PreviewParameter(MainScreenPreviewParameterProvider1::class) bassData: BassData,
    context: Context = LocalContext.current
) {
    val (midiChan, fontChan, soundfonts) = bassData
    var chosenNotes by remember { mutableStateOf(arrayOf<PitchClass>()) }
    var selectedLowestNote by remember { mutableStateOf(Note(PitchClass.C)) }
    var selectedHighestNote by remember { mutableStateOf(Note(PitchClass.C, 5)) }
    var possibleNotes by remember {
        mutableStateOf(
            getPossibleNotes(
                selectedLowestNote,
                selectedHighestNote
            )
        )
    }

    var presets: MutableList<Preset> by remember { mutableStateOf(mutableListOf()) }

    var readyToPlay by remember { mutableStateOf(false) }

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
                var selectedOptionText by remember { mutableStateOf("") }
                ExposedDropdownMenuBox(expanded = expanded,
                    onExpandedChange = { e -> expanded = e }) {
                    TextField(
                        readOnly = true,
                        value = selectedOptionText,
                        label = { Text("Soundfont") },
                        onValueChange = {},
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(
                                expanded = expanded
                            )
                        },
                        colors = ExposedDropdownMenuDefaults.textFieldColors(),
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = {
                        expanded = false
                    }) {
                        soundfonts.forEach { (name, resourceId) ->
                            val resource =
                                context.resources.openRawResource(resourceId)
                            DropdownMenuItem(onClick = {
                                selectedOptionText = name
                                expanded = false
                                BASSMIDI.BASS_MIDI_FontFree(fontChan)
                                presets = mutableListOf()
                                val font = BASSMIDI.BASS_MIDI_FontInit(
                                    ByteBuffer.wrap(resource.readBytes()),
                                    0
                                )
                                if (font == 0) {
                                    Log.d("Info", "Font bad")
                                } else {
                                    val sf = arrayOf(BASSMIDI.BASS_MIDI_FONT())
                                    sf[0].font = font
                                    sf[0].bank = 0
                                    sf[0].preset = -1
                                    BASSMIDI.BASS_MIDI_StreamSetFonts(
                                        0,
                                        sf,
                                        1
                                    ) // set default soundfont
                                    BASSMIDI.BASS_MIDI_StreamSetFonts(
                                        midiChan,
                                        sf,
                                        1
                                    ) // set for current stream too
                                }
                                BASSMIDI.BASS_MIDI_StreamEvent(
                                    midiChan,
                                    0,
                                    BASSMIDI.MIDI_EVENT_PROGRAM,
                                    0
                                )

                                readyToPlay = true

                                // list presets
                                val fontInfo = BASSMIDI.BASS_MIDI_FONTINFO()
                                BASSMIDI.BASS_MIDI_FontGetInfo(font, fontInfo)
                                val presetIds = IntArray(fontInfo.presets)
                                BASSMIDI.BASS_MIDI_FontGetPresets(font, presetIds)
                                for (i in presetIds.indices) {
                                    val presetId = presetIds[i]
                                    val presetName =
                                        BASSMIDI.BASS_MIDI_FontGetPreset(font, presetId, 0)
                                    Log.d("Info", "Preset name: $presetName ind: $presetId")
                                    presets.add(Preset(presetName, presetId))
                                }
                            }, text = { Text(text = name) })
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (presets.size > 0) {
                Row {
                    var expanded by remember { mutableStateOf(false) }
                    var selectedPreset by remember { mutableStateOf(presets[0]) }
                    ExposedDropdownMenuBox(expanded = expanded,
                        onExpandedChange = { e -> expanded = e }) {
                        TextField(
                            readOnly = true,
                            value = selectedPreset.name,
                            onValueChange = { },
                            label = { Text("Preset") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(
                                    expanded = expanded
                                )
                            },
                            colors = ExposedDropdownMenuDefaults.textFieldColors(),
                            modifier = Modifier.menuAnchor()
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = {
                            expanded = false
                        }) {
                            presets.forEach { preset ->
                                DropdownMenuItem(onClick = {
                                    selectedPreset = preset
                                    expanded = false
                                    Log.d(
                                        "Info",
                                        "Chosen preset: ${preset.name} index: ${preset.index}"
                                    )
                                    BASSMIDI.BASS_MIDI_StreamEvent(
                                        midiChan,
                                        0,
                                        BASSMIDI.MIDI_EVENT_PROGRAM,
                                        preset.index
                                    )
                                }, text = { Text(text = preset.name) })
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            Row {
                var expanded by remember { mutableStateOf(false) }
                val notes = (0 until 88).map { v -> Note(v + Note.midiOffset) }
                var selectedNote by remember { mutableStateOf(notes[39]) }
                ExposedDropdownMenuBox(expanded = expanded,
                    onExpandedChange = { e -> expanded = e }) {
                    TextField(
                        readOnly = true,
                        value = selectedNote.name,
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
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = {
                        expanded = false
                    }) {
                        notes.forEach { note ->
                            DropdownMenuItem(onClick = {
                                selectedNote = note
                                expanded = false
                                selectedLowestNote = note
                                possibleNotes =
                                    getPossibleNotes(selectedLowestNote, selectedHighestNote)
                            }, text = { Text(text = note.name) })
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                var expanded by remember { mutableStateOf(false) }
                val notes = (0 until 88).map { v -> Note(v + Note.midiOffset) }
                var selectedNote by remember { mutableStateOf(notes[51]) }
                ExposedDropdownMenuBox(expanded = expanded,
                    onExpandedChange = { e -> expanded = e }) {
                    TextField(
                        readOnly = true,
                        value = selectedNote.name,
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
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = {
                        expanded = false
                    }) {
                        notes.forEach { note ->
                            DropdownMenuItem(onClick = {
                                selectedNote = note
                                expanded = false
                                selectedHighestNote = note
                                possibleNotes =
                                    getPossibleNotes(selectedLowestNote, selectedHighestNote)
                            }, text = { Text(text = note.name) })
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

            if (possibleNotes.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.Center, maxItemsInEachRow = 4) {
                    possibleNotes.map { it.pitch }.distinct().forEach {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    chosenNotes = if (!chosenNotes.contains(it)) {
                                        arrayOf(*chosenNotes, it)
                                    } else {
                                        arrayOf(
                                            *chosenNotes
                                                .filter { p ->
                                                    p != it
                                                }
                                                .toTypedArray()
                                        )
                                    }
                                    Log.d("Info", "chosenNote: $it")
                                }
                                .requiredHeight(ButtonDefaults.MinHeight),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = chosenNotes.contains(it), onCheckedChange = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = it.toString())
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            var playing by remember { mutableStateOf(false) }
            var prevNote by remember { mutableStateOf<Note?>(null) }
            Button(onClick = {
                playing = !playing

                Thread {
                    while (playing) {
                        val notes =
                            getPlayableNotes(chosenNotes, selectedLowestNote, selectedHighestNote)
                        Log.d("Info", "playableNotes: $notes")
                        if (notes.isNotEmpty() && readyToPlay) {
                            var randNote: Note
                            while (true) {
                                randNote = notes[Random.nextInt(notes.indices)]
                                if (prevNote == null || randNote.midiIndex != prevNote!!.midiIndex) break
                            }
                            prevNote = randNote
                            Log.d("Info", "Playing note: $randNote")
                            val duration = 60000 / tempo
                            val realDuration = (if (duration > 1000) 1000 else duration).toLong()
                            val postDuration =
                                (if (duration > 1000) duration - 1000 else 0).toLong()
                            playNote(midiChan, randNote.midiIndex)
                            Thread.sleep(realDuration)
                            stopNote(midiChan, randNote.midiIndex)
                            Thread.sleep(postDuration)
                        }
                    }
                }.start()
            }) {
                Text(text = if (playing) "Stop" else "Play")
            }
        }
    }
}
