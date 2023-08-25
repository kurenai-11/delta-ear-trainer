package com.example.deltaeartrainer

enum class PitchClass {
    C, CSharp, D, DSharp, E, F, FSharp, G, GSharp, A, ASharp, B;

    override fun toString(): String {
        return this.name.replace("Sharp", "#")
    }

    operator fun plus(semitones: Int): PitchClass {
        return PitchClass.values()[(this.ordinal + semitones) % 12]
    }

    operator fun minus(semitones: Int): PitchClass {
        val calc = this.ordinal - (semitones % 12)
        return if (calc < 0) {
            PitchClass.values()[calc + 12]
        } else {
            PitchClass.values()[calc]
        }
    }
}