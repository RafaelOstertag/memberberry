package ch.guengel.memberberry.services

import ch.guengel.memberberry.domain.RememberPeriod
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

internal class ExecutionCalculatorServiceTest {
    private val now = OffsetDateTime.now()
    private val executionCalculatorService = ExecutionCalculatorService()

    @Test
    fun `should calculate next daily execution`() {
        val nextExecution = executionCalculatorService.calculateNextExecution(RememberPeriod.DAILY, now)

        assertThat(nextExecution, `is`(now.plusDays(1)))
    }

    @Test
    fun `should calculate next weekly execution`() {
        val nextExecution = executionCalculatorService.calculateNextExecution(RememberPeriod.WEEKLY, now)

        assertThat(nextExecution, `is`(now.plusWeeks(1)))
    }

    @Test
    fun `should calculate next month execution`() {
        val nextExecution = executionCalculatorService.calculateNextExecution(RememberPeriod.MONTHLY, now)

        assertThat(nextExecution, `is`(now.plusMonths(1)))
    }
}