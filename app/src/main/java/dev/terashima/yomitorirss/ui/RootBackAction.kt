package dev.terashima.yomitorirss.ui

internal enum class RootBackAction {
  POP_NAVIGATION,
  OPEN_DRAWER,
  EXIT_APP,
}

internal fun rootBackAction(
  isDrawerOpen: Boolean,
  canNavigateBack: Boolean,
): RootBackAction = when {
  isDrawerOpen -> RootBackAction.EXIT_APP
  canNavigateBack -> RootBackAction.POP_NAVIGATION
  else -> RootBackAction.OPEN_DRAWER
}
