package dev.terashima.yomitorirss.feature.game

import android.content.pm.ActivityInfo
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
    // The shared project defaults to portrait for Sudoku. Coerce engine startup
    // orientation requests so this Activity remains stable in fixed landscape.
    super.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
  }
}
