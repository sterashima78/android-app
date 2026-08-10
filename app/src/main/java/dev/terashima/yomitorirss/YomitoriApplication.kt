package dev.terashima.yomitorirss

import android.app.Application
import dev.terashima.yomitorirss.feature.widget.WidgetRepository
import dev.terashima.yomitorirss.feature.widget.WidgetRepositoryProvider

class YomitoriApplication : Application(), WidgetRepositoryProvider {
  val container: AppContainer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    AppContainer(this)
  }

  override val widgetRepository: WidgetRepository
    get() = container.widgetRepository

  override fun onCreate() {
    super.onCreate()
    StartupCrashStore.install(this)
  }
}
