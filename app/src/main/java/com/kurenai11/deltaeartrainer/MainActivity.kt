package com.kurenai11.deltaeartrainer

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.unit.sp
import com.kurenai11.deltaeartrainer.ui.theme.DeltaEarTrainerTheme
import com.un4seen.bass.BASS
import com.un4seen.bass.BASSMIDI
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
data class Preset(val name: String?, val index: Int)

fun getPlayableNotes(
  chosenNotes: List<Pitch>, lowestNote: Note, highestNote: Note
): List<Note> {
  return getPossibleNotes(lowestNote, highestNote).filter { chosenNotes.contains(it.pitch) }
}

fun getPossibleNotes(lowestNote: Note, highestNote: Note): List<Note> {
  return (0..(highestNote.midiIndex - lowestNote.midiIndex)).map { lowestNote + it }
}

@Composable
fun NoteRow(
  // lowest, highest
  bounds: Pair<Note, Note> = Note(Pitch.A, 0) to Note(Pitch.C, 8),
  onChoose: (note: Note) -> Unit = {},
  defaultNote: Note = Note(Pitch.C, 4)
) {
  var activeNote by remember { mutableStateOf(defaultNote) }
  val lazyListState = rememberLazyListState(defaultNote.midiIndex - Note.midiOffset - 3)
  LazyRow(state = lazyListState) {
    for (note in bounds.first..bounds.second) {
      item {
        NoteListItem(note = note,
          active = (activeNote.midiIndex) == note.midiIndex,
          onClicked = { clickedNote ->
            activeNote = clickedNote
            onChoose(note)
          })
        Spacer(modifier = Modifier.width(4.dp))
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteListItem(note: Note, active: Boolean, onClicked: (note: Note) -> Unit = {}) {
  val surfaceColor = if (active) {
    MaterialTheme.colorScheme.surfaceTint
  } else {
    MaterialTheme.colorScheme.surfaceVariant
  }
  Surface(color = surfaceColor,
    modifier = Modifier
      .width(50.dp)
      .height(50.dp),
    shape = RoundedCornerShape(4.dp),
    shadowElevation = 4.dp,
    onClick = {
      onClicked(note)
    }) {
    Box(contentAlignment = Alignment.Center) {
      val textColor = if (active) {
        MaterialTheme.colorScheme.inverseOnSurface
      } else {
        MaterialTheme.colorScheme.onSurface
      }
      Text(
        text = note.name, color = textColor, fontSize = 16.sp
      )
    }
  }
}


@SuppressLint("MutableCollectionMutableState")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Preview(showBackground = true)
@Composable
fun MainScreen(
  @PreviewParameter(MainScreenPreviewParameterProvider1::class) bassData: BassData,
  context: Context = LocalContext.current
) {
  val (midiChan, fontChan) = bassData
  var chosenPitches = remember { mutableStateListOf<Pitch>() }
  var selectedLowestNote by remember { mutableStateOf(Note(Pitch.C)) }
  var selectedHighestNote by remember { mutableStateOf(Note(Pitch.C, 5)) }
  var possibleNotes by remember {
    mutableStateOf(
      getPossibleNotes(
        selectedLowestNote, selectedHighestNote
      )
    )
  }

  var presets by remember { mutableStateOf(arrayOf<Preset>()) }
  var selectedPreset by remember { mutableStateOf<Preset?>(null) }

  var readyToPlay by remember { mutableStateOf(false) }

  var playing by remember { mutableStateOf(false) }
  var prevNote by remember { mutableStateOf<Note?>(null) }
  var tempo by remember { mutableStateOf(20) }

  Scaffold(topBar = {
    TopAppBar(
      title = { Text(text = "Delta Ear Trainer") },
      colors = TopAppBarDefaults.mediumTopAppBarColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer
      )
    )
  }, floatingActionButton = {
    FloatingActionButton(onClick = {
      playing = !playing
      Thread {
        while (playing) {
          val notes = getPlayableNotes(chosenPitches, selectedLowestNote, selectedHighestNote)
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
            val postDuration = (if (duration > 1000) duration - 1000 else 0).toLong()
            playNote(midiChan, randNote.midiIndex)
            Thread.sleep(realDuration)
            stopNote(midiChan, randNote.midiIndex)
            Thread.sleep(postDuration)
          }
        }
      }.start()
    }) {
      Icon(
        imageVector = if (!playing) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
        contentDescription = "Play"
      )
    }
  }) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(it)
        .padding(8.dp)
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
      ) {
        val selectFileActivity = rememberLauncherForActivityResult(
          contract = ActivityResultContracts.OpenDocument()
        ) {
          // load soundfont
          Log.d("Info", "selected uri $it")
          Log.d("Info", "selected path ${it?.encodedPath}")
          Log.d("Info", "is absolute ${it?.isAbsolute}")
          Log.d("Info", "is relative ${it?.isRelative}")
          if (it == null) return@rememberLauncherForActivityResult
          BASSMIDI.BASS_MIDI_FontFree(fontChan)
          val presetsTemp = mutableListOf<Preset>()
          val descriptor = context.contentResolver.openFileDescriptor(it, "r")
          val font = BASSMIDI.BASS_MIDI_FontInit(descriptor, 0)
          if (font == 0) {
            val errCode = BASS.BASS_ErrorGetCode()
            Log.d("Info", "Font bad, $errCode")
            return@rememberLauncherForActivityResult
          } else {
            val sf = arrayOf(BASSMIDI.BASS_MIDI_FONT())
            sf[0].font = font
            sf[0].bank = 0
            sf[0].preset = -1
            BASSMIDI.BASS_MIDI_StreamSetFonts(
              0, sf, 1
            ) // set default soundfont
            BASSMIDI.BASS_MIDI_StreamSetFonts(
              midiChan, sf, 1
            ) // set for current stream too
          }
          BASSMIDI.BASS_MIDI_StreamEvent(
            midiChan, 0, BASSMIDI.MIDI_EVENT_PROGRAM, 0
          )

          readyToPlay = true

          // list presets
          val fontInfo = BASSMIDI.BASS_MIDI_FONTINFO()
          BASSMIDI.BASS_MIDI_FontGetInfo(font, fontInfo)
          val presetIds = IntArray(fontInfo.presets)
          BASSMIDI.BASS_MIDI_FontGetPresets(font, presetIds)
          for (i in presetIds.indices) {
            val presetId = presetIds[i]
            val presetName = BASSMIDI.BASS_MIDI_FontGetPreset(font, presetId, 0)
            Log.d("Info", "Preset name: $presetName ind: $presetId")
            presetsTemp.add(Preset(presetName, presetId))
          }
          if (presetsTemp.size > 0) {
            presets = presetsTemp.toTypedArray()
            selectedPreset = presets[0]
          }
        }
        Button(onClick = { selectFileActivity.launch(arrayOf("application/octet-stream")) }) {
          Text(text = "Open .sf2 soundfont")
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (presets.isNotEmpty()) {
          Row {
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { e -> expanded = e }) {
              TextField(
                readOnly = true,
                value = selectedPreset?.name ?: "",
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
                      "Info", "Chosen preset: ${preset.name} index: ${preset.index}"
                    )
                    BASSMIDI.BASS_MIDI_StreamEvent(
                      midiChan, 0, BASSMIDI.MIDI_EVENT_PROGRAM, preset.index
                    )
                  }, text = { Text(text = preset.name ?: "(no name)") })
                }
              }
            }
          }
          Spacer(modifier = Modifier.height(12.dp))
        }
        NoteRow(onChoose = { note ->
          Log.d("Info", "note chosen: ${note.name}")
          selectedLowestNote = note
          possibleNotes = getPossibleNotes(selectedLowestNote, selectedHighestNote)
        })
        Spacer(modifier = Modifier.height(8.dp))
        NoteRow(defaultNote = Note(Pitch.C, 5), onChoose = { note ->
          Log.d("Info", "note chosen: ${note.name}")
          selectedHighestNote = note
          possibleNotes = getPossibleNotes(selectedLowestNote, selectedHighestNote)
        })
        Spacer(modifier = Modifier.height(16.dp))
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
          Row {
            Button(onClick = {
              val pitches = mutableListOf<Pitch>()
              for (n in selectedLowestNote.midiIndex..selectedHighestNote.midiIndex) {
                val note = Note(n)
                if (!pitches.contains(note.pitch)) {
                  pitches.add(note.pitch)
                }
              }
              chosenPitches.clear()
              chosenPitches.addAll(pitches)
            }) {
              Text(text = "All")
            }
            Spacer(modifier = Modifier.width(6.dp))
            Button(onClick = {
              chosenPitches.clear()
            }) {
              Text(text = "None")
            }
          }
          FlowRow(horizontalArrangement = Arrangement.Center, maxItemsInEachRow = 4) {
            possibleNotes.map { it.pitch }.distinct().forEach {
              Row(
                modifier = Modifier
                  .weight(1f)
                  .clickable {
                    if (!chosenPitches.contains(it)) {
                      chosenPitches.add(it)
                    } else {
                      chosenPitches.remove(it)
                    }
                    Log.d("Info", "chosenNote: $it")
                  }
                  .requiredHeight(ButtonDefaults.MinHeight),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Checkbox(checked = chosenPitches.contains(it), onCheckedChange = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = it.toString())
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = {
          val scale = Scale.NaturalMinor(Pitch.random())
          val notes = scale.notes(4)
          val duration = 500L
          if (!readyToPlay) return@Button
          Thread {
            for (note in notes) {
              playNote(midiChan, note.midiIndex)
              Thread.sleep(duration)
              stopNote(midiChan, note.midiIndex)
            }
          }.start()
        }) {
          Text(text = "Play random natural Minor scale")
        }
      }
    }
  }

}
