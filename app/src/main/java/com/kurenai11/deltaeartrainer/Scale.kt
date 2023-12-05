package com.kurenai11.deltaeartrainer

class Scale private constructor(pitches: List<Pitch>, val formula: Array<Int>) {
  val root = pitches[0]

  fun notes(startingOctave: Int): List<Note> {
    val rootNote = Note(root, startingOctave)
    return formula.runningReduce { acc, i -> acc + i }.map { t -> rootNote + t }
  }

  companion object {
    fun Major(root: Pitch): Scale {
      // root-W-W-H-W-W-W-(H)
      val formula = arrayOf(0, 2, 2, 1, 2, 2, 2, 1)
      val pitches = formula.runningReduce { acc, i -> acc + i }.map { i -> root + i }
      return Scale(pitches, formula)
    }

    fun NaturalMinor(root: Pitch): Scale {
      // root-W-H-W-W-H-W-(W)
      val formula = arrayOf(0, 2, 1, 2, 2, 1, 2, 2)
      val pitches = formula.runningReduce { acc, i -> acc + i }.map { i -> root + i }
      return Scale(pitches, formula)
    }
  }
}