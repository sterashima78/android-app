package dev.terashima.yomitorirss

import dev.terashima.yomitorirss.feature.web.LanWebServerController

class MainActivityPresentationDependencies internal constructor(
  val routeDependencies: AppRouteDependencies,
  val lanWebServerController: LanWebServerController,
)
