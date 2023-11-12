package com.kurenai11.deltaeartrainer

enum class Pitch {
    C, CSharp, D, DSharp, E, F, FSharp, G, GSharp, A, ASharp, B;

    override fun toString(): String {
        return this.name.replace("Sharp", "#")
    }

    operator fun plus(semitones: Int): Pitch {
        return Pitch.values()[(this.ordinal + semitones) % 12]
    }

    operator fun minus(semitones: Int): Pitch {
        val calc = this.ordinal - (semitones % 12)
        return if (calc < 0) {
            Pitch.values()[calc + 12]
        } else {
            Pitch.values()[calc]
        }
    }
}