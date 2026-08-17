package dev.terashima.yomitorirss.ui

internal enum class RootBackAction {
  OPEN_DRAWER,
  EXIT_APP,
}

internal fun rootBackAction(isDrawerOpen: Boolean): RootBackAction =
  if (isDrawerOpen) RootBackAction.EXIT_APP else RootBackAction.OPEN_DRAWER
