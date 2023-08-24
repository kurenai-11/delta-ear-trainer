package com.example.deltaeartrainer

class Note private constructor() {
    var midiIndex: Int = -1

    constructor(index: Int) : this() {
        midiIndex = index
    }

    constructor(noteName: String) : this() {
        var input = noteName
        input = input.uppercase()
        if (input.contains("#")) {
            input = input.replace("#", "s")
            midiIndex = notes.indexOf(notes.find { it == input }
                ?: throw Exception("wrong input when creating a note")) + midiOffset
        } else if (input.length > 2 && input.contains("F")) {
            input = input.replace("F", "")
            val naturalNote =
                notes.indexOf(notes.find { it == input }
                    ?: throw Exception("wrong input when creating a note")) + midiOffset
            midiIndex = naturalNote - 1
        } else {
            midiIndex = notes.indexOf(notes.find { it == input }
                ?: throw Exception("wrong input when creating a note")) + midiOffset
        }
    }

    private val _name: String
        get() {
            return notes[midiIndex - midiOffset]
        }
    val name: String
        get() {
            if (!_name.contains("s")) return _name
            val sharped = _name.replace("s", "#")
            val flatted = Note(midiIndex + 1)._name.toCharArray().joinToString("f")
            return "$sharped/$flatted"
        }
    val nameSharped: String
        get() {
            if (!_name.contains("s")) return _name[0].toString()
            return _name.replace("s", "#").substring(0 until 2)
        }

    val pianoKeyNumber: Int
        get() {
            return notes.indexOf(_name) + 1
        }

    operator fun plus(semitones: Int): Note {
        if (midiIndex + semitones > notes.size + midiOffset || midiIndex + semitones < midiOffset) {
            throw Exception("wrong math bruh")
        }
        return Note(midiIndex + semitones)
    }

    operator fun minus(semitones: Int): Note {
        if (midiIndex - semitones > notes.size + midiOffset || midiIndex - semitones < midiOffset) {
            throw Exception("wrong math bruh")
        }
        return Note(midiIndex - semitones)
    }

    override fun toString(): String {
        return "Note: $name"
    }

    companion object {
        private val notes = arrayOf(
            "A0",
            "As0",
            "B0",
            "C1",
            "Cs1",
            "D1",
            "Ds1",
            "E1",
            "F1",
            "Fs1",
            "G1",
            "Gs1",
            "A1",
            "As1",
            "B1",
            "C2",
            "Cs2",
            "D2",
            "Ds2",
            "E2",
            "F2",
            "Fs2",
            "G2",
            "Gs2",
            "A2",
            "As2",
            "B2",
            "C3",
            "Cs3",
            "D3",
            "Ds3",
            "E3",
            "F3",
            "Fs3",
            "G3",
            "Gs3",
            "A3",
            "As3",
            "B3",
            "C4",
            "Cs4",
            "D4",
            "Ds4",
            "E4",
            "F4",
            "Fs4",
            "G4",
            "Gs4",
            "A4",
            "As4",
            "B4",
            "C5",
            "Cs5",
            "D5",
            "Ds5",
            "E5",
            "F5",
            "Fs5",
            "G5",
            "Gs5",
            "A5",
            "As5",
            "B5",
            "C6",
            "Cs6",
            "D6",
            "Ds6",
            "E6",
            "F6",
            "Fs6",
            "G6",
            "Gs6",
            "A6",
            "As6",
            "B6",
            "C7",
            "Cs7",
            "D7",
            "Ds7",
            "E7",
            "F7",
            "Fs7",
            "G7",
            "Gs7",
            "A7",
            "As7",
            "B7",
            "C8"
        )
        const val midiOffset = 21
    }
}