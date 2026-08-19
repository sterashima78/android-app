package dev.terashima.yomitorirss.feature.health

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
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
  fun `許可済みなら今日の概要を読み込む`() = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    try {
      val overview = HealthOverview(steps = 4321, exerciseMinutes = 45)
      val repository = FakeHealthRepository(overview = overview)
      val viewModel = HealthViewModel(repository, FIXED_CLOCK, UTC)

      advanceUntilIdle()

      assertEquals(HealthUiState.Content(HealthPeriod.TODAY, overview), viewModel.state.value)
      assertEquals(Instant.parse("2026-08-19T00:00:00Z"), repository.lastStart)
      assertEquals(Instant.parse("2026-08-19T12:00:00Z"), repository.lastEnd)
    } finally {
      Dispatchers.resetMain()
    }
  }

  @Test
  fun `期間を30日に変更すると30日前から再取得する`() = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    try {
      val repository = FakeHealthRepository()
      val viewModel = HealthViewModel(repository, FIXED_CLOCK, UTC)
      advanceUntilIdle()

      viewModel.selectPeriod(HealthPeriod.THIRTY_DAYS)
      advanceUntilIdle()

      assertEquals(Instant.parse("2026-07-20T12:00:00Z"), repository.lastStart)
      assertEquals(HealthPeriod.THIRTY_DAYS, (viewModel.state.value as HealthUiState.Content).period)
    } finally {
      Dispatchers.resetMain()
    }
  }

  private class FakeHealthRepository(
    private val availability: HealthAvailability = HealthAvailability.AVAILABLE,
    private val hasPermissions: Boolean = true,
    private val overview: HealthOverview = HealthOverview(),
  ) : HealthRepository {
    var lastStart: Instant? = null
    var lastEnd: Instant? = null

    override fun availability(): HealthAvailability = availability

    override suspend fun hasRequiredPermissions(): Boolean = hasPermissions

    override suspend fun readOverview(startTime: Instant, endTime: Instant): HealthOverview {
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
