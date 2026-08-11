package dev.terashima.yomitorirss

import android.app.Application
import dev.terashima.yomitorirss.feature.task.TaskRepository
import dev.terashima.yomitorirss.feature.widget.TaskRepositoryProvider
import dev.terashima.yomitorirss.feature.widget.WidgetRepository
import dev.terashima.yomitorirss.feature.widget.WidgetRepositoryProvider

class YomitoriApplication : Application(), WidgetRepositoryProvider, TaskRepositoryProvider {
  val container: AppContainer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    AppContainer(this)
  }

  override val widgetRepository: WidgetRepository
    get() = container.widgetRepository

  override val taskRepository: TaskRepository
    get() = container.taskRepository

  override fun onCreate() {
    super.onCreate()
    StartupCrashStore.install(this)
  }
}
