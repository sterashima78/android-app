package dev.terashima.yomitorirss.feature.health

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HealthViewModelTest {
  @Test
  fun `権限がない場合は権限要求状態になる`() = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    try {
      val repository = FakeHealthRepository(hasPermissions = false)
      val viewModel = HealthViewModel(repository, FIXED_CLOCK, UTC)

      advanceUntilIdle()

      assertEquals(HealthUiState.PermissionRequired, viewModel.state.value)
    } finally {
      Dispatchers.resetMain()
    }
  }

  @Test
  fun `初期表示は今日の開始から現在時刻までを読み込む`() = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    try {
      val overview = HealthOverview(steps = 4321, exerciseMinutes = 45)
      val repository = FakeHealthRepository(overview = overview)
      val viewModel = HealthViewModel(repository, FIXED_CLOCK, UTC)

      advanceUntilIdle()

      val content = viewModel.state.value as HealthUiState.Content
      assertEquals(HealthPeriod.DAY, content.period)
      assertEquals(LocalDate.parse("2026-08-19"), content.selectedDate)
      assertEquals(HealthDateRange(LocalDate.parse("2026-08-19"), LocalDate.parse("2026-08-20")), content.range)
      assertEquals(overview, content.overview)
      assertEquals(Instant.parse("2026-08-19T00:00:00Z"), repository.lastStart)
      assertEquals(Instant.parse("2026-08-19T12:00:00Z"), repository.lastEnd)
      assertFalse(content.canMoveNext)
    } finally {
      Dispatchers.resetMain()
    }
  }

  @Test
  fun `週表示は月曜日から現在時刻までを読み込む`() = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    try {
      val repository = FakeHealthRepository()
      val viewModel = HealthViewModel(repository, FIXED_CLOCK, UTC)
      advanceUntilIdle()

      viewModel.selectPeriod(HealthPeriod.WEEK)
      advanceUntilIdle()

      val content = viewModel.state.value as HealthUiState.Content
      assertEquals(HealthPeriod.WEEK, content.period)
      assertEquals(HealthDateRange(LocalDate.parse("2026-08-17"), LocalDate.parse("2026-08-24")), content.range)
      assertEquals(Instant.parse("2026-08-17T00:00:00Z"), repository.lastStart)
      assertEquals(Instant.parse("2026-08-19T12:00:00Z"), repository.lastEnd)
    } finally {
      Dispatchers.resetMain()
    }
  }

  @Test
  fun `日付を指定するとその日の全期間を再取得する`() = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    try {
      val repository = FakeHealthRepository()
      val viewModel = HealthViewModel(repository, FIXED_CLOCK, UTC)
      advanceUntilIdle()

      viewModel.selectDate(LocalDate.parse("2026-08-10"))
      advanceUntilIdle()

      val content = viewModel.state.value as HealthUiState.Content
      assertEquals(LocalDate.parse("2026-08-10"), content.selectedDate)
      assertEquals(Instant.parse("2026-08-10T00:00:00Z"), repository.lastStart)
      assertEquals(Instant.parse("2026-08-11T00:00:00Z"), repository.lastEnd)
      assertTrue(content.canMoveNext)
    } finally {
      Dispatchers.resetMain()
    }
  }

  @Test
  fun `月表示は指定日の月初から翌月初までを読み込む`() = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    try {
      val repository = FakeHealthRepository(historyAccess = HealthHistoryAccess.AVAILABLE)
      val viewModel = HealthViewModel(repository, FIXED_CLOCK, UTC)
      advanceUntilIdle()

      viewModel.selectDate(LocalDate.parse("2026-07-15"))
      advanceUntilIdle()
      viewModel.selectPeriod(HealthPeriod.MONTH)
      advanceUntilIdle()

      val content = viewModel.state.value as HealthUiState.Content
      assertEquals(HealthPeriod.MONTH, content.period)
      assertEquals(HealthDateRange(LocalDate.parse("2026-07-01"), LocalDate.parse("2026-08-01")), content.range)
      assertEquals(Instant.parse("2026-07-01T00:00:00Z"), repository.lastStart)
      assertEquals(Instant.parse("2026-08-01T00:00:00Z"), repository.lastEnd)
      assertTrue(repository.historyAccessCalls > 0)
    } finally {
      Dispatchers.resetMain()
    }
  }

  @Test
  fun `30日より前を含む期間で履歴権限がなければ履歴権限要求状態になる`() = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    try {
      val repository = FakeHealthRepository(historyAccess = HealthHistoryAccess.PERMISSION_REQUIRED)
      val viewModel = HealthViewModel(repository, FIXED_CLOCK, UTC)
      advanceUntilIdle()

      viewModel.selectDate(LocalDate.parse("2026-07-01"))
      advanceUntilIdle()

      assertEquals(HealthUiState.HistoryPermissionRequired, viewModel.state.value)
      assertEquals(1, repository.readCalls)
    } finally {
      Dispatchers.resetMain()
    }
  }

  @Test
  fun `履歴機能が利用できない端末では古い期間を利用不可として扱う`() = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    try {
      val repository = FakeHealthRepository(historyAccess = HealthHistoryAccess.UNSUPPORTED)
      val viewModel = HealthViewModel(repository, FIXED_CLOCK, UTC)
      advanceUntilIdle()

      viewModel.selectDate(LocalDate.parse("2026-06-01"))
      advanceUntilIdle()

      assertEquals(HealthUiState.HistoryUnsupported, viewModel.state.value)
    } finally {
      Dispatchers.resetMain()
    }
  }

  @Test
  fun `週と月の期間境界をカレンダー単位で計算する`() {
    assertEquals(
      HealthDateRange(LocalDate.parse("2026-08-17"), LocalDate.parse("2026-08-24")),
      HealthPeriod.WEEK.dateRange(LocalDate.parse("2026-08-19")),
    )
    assertEquals(
      HealthDateRange(LocalDate.parse("2026-02-01"), LocalDate.parse("2026-03-01")),
      HealthPeriod.MONTH.dateRange(LocalDate.parse("2026-02-18")),
    )
  }

  @Test
  fun `未来の日付指定は今日へ丸める`() = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    try {
      val repository = FakeHealthRepository()
      val viewModel = HealthViewModel(repository, FIXED_CLOCK, UTC)
      advanceUntilIdle()

      viewModel.selectDate(LocalDate.parse("2027-01-01"))
      advanceUntilIdle()

      val content = viewModel.state.value as HealthUiState.Content
      assertEquals(LocalDate.parse("2026-08-19"), content.selectedDate)
      assertEquals(Instant.parse("2026-08-19T00:00:00Z"), repository.lastStart)
      assertEquals(Instant.parse("2026-08-19T12:00:00Z"), repository.lastEnd)
    } finally {
      Dispatchers.resetMain()
    }
  }

  private class FakeHealthRepository(
    private val availability: HealthAvailability = HealthAvailability.AVAILABLE,
    private val hasPermissions: Boolean = true,
    private val overview: HealthOverview = HealthOverview(),
    private val historyAccess: HealthHistoryAccess = HealthHistoryAccess.AVAILABLE,
  ) : HealthRepository {
    var lastStart: Instant? = null
    var lastEnd: Instant? = null
    var readCalls: Int = 0
    var historyAccessCalls: Int = 0

    override fun availability(): HealthAvailability = availability

    override suspend fun hasRequiredPermissions(): Boolean = hasPermissions

    override suspend fun historyAccess(): HealthHistoryAccess {
      historyAccessCalls += 1
      return historyAccess
    }

    override suspend fun readOverview(startTime: Instant, endTime: Instant): HealthOverview {
      readCalls += 1
      lastStart = startTime
      lastEnd = endTime
      return overview
    }
  }

  companion object {
    private val UTC = ZoneId.of("UTC")
    private val FIXED_CLOCK = Clock.fixed(Instant.parse("2026-08-19T12:00:00Z"), UTC)
  }
}
