package dev.terashima.yomitorirss.feature.game

import org.godotengine.godot.GodotActivity

internal fun klondikeGodotCommandLine(base: List<String>): MutableList<String> =
  base.toMutableList().apply {
    add("--scene")
    add("res://klondike.tscn")
  }

/** Hosts the embedded Godot runtime used by the Klondike game. */
class GodotKlondikeActivity : GodotActivity() {
  override fun getCommandLine(): MutableList<String> =
    klondikeGodotCommandLine(super.getCommandLine())

  override fun setRequestedOrientation(requestedOrientation: Int) {
    // The manifest owns this Activity's fixed landscape orientation. Godot's
    // shared project is portrait for Sudoku, so ignore runtime orientation requests.
  }
}
