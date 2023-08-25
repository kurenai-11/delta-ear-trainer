package com.example.deltaeartrainer

import kotlin.math.ceil

class Note private constructor() {
    var midiIndex: Int = -1

    constructor(index: Int) : this() {
        midiIndex = index
    }

    constructor(pitchClass: PitchClass, octave: Int = 4) : this() {
        midiIndex = if (octave == 0) {
            if (!arrayOf(PitchClass.A, PitchClass.ASharp, PitchClass.B).contains(pitchClass)) {
                throw Exception("wrong input bruh")
            }
            midiOffset + pitchClass.ordinal
        } else {
            3 + midiOffset + ((octave - 1) * 12) + pitchClass.ordinal
        }
    }

    val pitch: PitchClass
        get() {
            return when (midiIndex) {
                in 0..2 -> {
                    PitchClass.values()[midiIndex + 9]
                }

                in 3..108 -> {
                    PitchClass.values()[(midiIndex % 12)]
                }

                else -> {
                    throw Exception("error getting pitch class")
                }
            }
        }

    val octave: Int
        get() {
            return when (pianoKeyNumber) {
                in 0..2 -> {
                    0
                }

                in 3..88 -> {
                    ceil((pianoKeyNumber.toDouble() - 3) / 12).toInt()
                }

                else -> {
                    throw Exception("octave out of bounds")
                }
            }
        }

    val name: String
        get() {
            return pitch.toString() + octave
        }
    val fullName: String
        get() {
            if (!name.contains("#")) return name
            val sharped = name
            val flatted = (pitch - 1).toString() + "f"
            return "$sharped/$flatted"
        }

    val pianoKeyNumber: Int
        get() {
            return midiIndex - midiOffset + 1
        }

    operator fun plus(semitones: Int): Note {
        if (midiIndex + semitones > 88 + midiOffset || midiIndex + semitones < midiOffset) {
            throw Exception("wrong math bruh")
        }
        return Note(midiIndex + semitones)
    }

    operator fun minus(semitones: Int): Note {
        if (midiIndex - semitones > 88 + midiOffset || midiIndex - semitones < midiOffset) {
            throw Exception("wrong math bruh")
        }
        return Note(midiIndex - semitones)
    }

    override fun toString(): String {
        return name
    }

    companion object {
        const val midiOffset = 21
    }
}