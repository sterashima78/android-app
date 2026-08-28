package dev.terashima.yomitorirss

import dev.terashima.yomitorirss.entry.IncomingIntentDependencies

interface MainActivityDependenciesProvider {
  val mainActivityPresentationDependencies: MainActivityPresentationDependencies
  val incomingIntentDependencies: IncomingIntentDependencies
}
