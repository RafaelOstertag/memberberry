package ch.guengel.memberberry.services

import ch.guengel.memberberry.domain.Berry
import ch.guengel.memberberry.domain.RememberPeriod
import ch.guengel.memberberry.repositories.BerryRepository
import io.mockk.every
import io.mockk.slot
import io.mockk.verify
import io.quarkiverse.test.junit.mockk.InjectMock
import io.quarkus.test.junit.QuarkusTest
import io.smallrye.mutiny.Multi
import io.smallrye.mutiny.Uni
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.util.*
import javax.inject.Inject

@QuarkusTest
internal class ReminderServiceIT {
    @InjectMock
    lateinit var berryRepository: BerryRepository

    @InjectMock
    lateinit var reminderStrategy: ReminderStrategy

    @InjectMock
    lateinit var executionCalculatorService: ExecutionCalculatorService

    @Inject
    lateinit var reminderService: ReminderService

    @Test
    fun `happy path`() {
        val nextExecutionDate = OffsetDateTime.now()
        val lastExecutionDate = nextExecutionDate.minusDays(1)
        val berry = Berry(
            UUID.randomUUID(),
            "test",
            RememberPeriod.DAILY,
            "userid",
            nextExecutionDate,
            lastExecutionDate
        )

        every { berryRepository.findBerriesDueBy(any()) } returns Multi.createFrom().item(berry)
        every {
            executionCalculatorService.calculateNextExecution(
                RememberPeriod.DAILY,
                any()
            )
        } returns nextExecutionDate.plusDays(1)
        every { reminderStrategy.remind(berry) } returns Unit

        val captureSlot = slot<Berry>()
        every { berryRepository.update(capture(captureSlot)) }.answers { Uni.createFrom().item(1L) }

        reminderService.remind()

        Thread.sleep(500)

        with(captureSlot.captured) {
            assertThat(this.id, `is`(berry.id))
            assertThat(this.subject, `is`("test"))
            assertThat(this.userId, `is`("userid"))
            assertThat(this.period, `is`(RememberPeriod.DAILY))
            assertThat(this.nextExecution, `is`(nextExecutionDate.plusDays(1)))
        }

        verify(exactly = 1) { executionCalculatorService.calculateNextExecution(eq(RememberPeriod.DAILY), any()) }
        verify(exactly = 1) { berryRepository.update(any()) }
    }

    @Test
    fun `with errors`() {
        val nextExecutionDate = OffsetDateTime.now()
        val lastExecutionDate = nextExecutionDate.minusDays(1)
        val berry = Berry(
            UUID.randomUUID(),
            "test",
            RememberPeriod.DAILY,
            "userid",
            nextExecutionDate,
            lastExecutionDate
        )

        every { berryRepository.findBerriesDueBy(any()) } returns Multi.createFrom().items(berry, berry, berry)
        every {
            executionCalculatorService.calculateNextExecution(
                RememberPeriod.DAILY,
                any()
            )
        } returns nextExecutionDate.plusDays(1)
        every {
            reminderStrategy.remind(berry)
        }
            .answers {}
            .andThenThrows(RuntimeException("Test exception"))
            .andThenAnswer {}

        every { berryRepository.update(any()) } returns Uni.createFrom().item(1L)

        reminderService.remind()

        Thread.sleep(500)

        verify(exactly = 3) { reminderStrategy.remind(any()) }
        verify(exactly = 2) { executionCalculatorService.calculateNextExecution(eq(RememberPeriod.DAILY), any()) }
        verify(exactly = 3) { berryRepository.update(any()) }
    }
}
