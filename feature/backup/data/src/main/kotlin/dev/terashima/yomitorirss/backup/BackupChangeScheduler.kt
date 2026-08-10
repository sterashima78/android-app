package dev.terashima.yomitorirss.feature.backup.data

import android.content.Context
import dev.terashima.yomitorirss.feature.backup.BackupChangeScheduler

class AndroidBackupChangeScheduler(
  private val context: Context,
) : BackupChangeScheduler {
  override fun scheduleAfterChange() {
    GoogleDriveBackupScheduler.scheduleAfterChange(context)
  }
}
