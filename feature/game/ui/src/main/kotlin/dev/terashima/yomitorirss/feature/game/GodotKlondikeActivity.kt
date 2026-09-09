package dev.terashima.yomitorirss.feature.game

import android.content.pm.ActivityInfo
import org.godotengine.godot.GodotActivity

/** Hosts the embedded Godot runtime used by the Klondike game. */
class GodotKlondikeActivity : GodotActivity() {
  override fun getCommandLine(): MutableList<String> =
    super.getCommandLine().toMutableList().apply {
      add("--scene")
      add("res://klondike.tscn")
    }

  override fun setRequestedOrientation(requestedOrientation: Int) {
    super.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
  }
}
