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
    super.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
  }
}
