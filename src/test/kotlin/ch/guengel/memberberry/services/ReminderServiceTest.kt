package ch.guengel.memberberry.services

import ch.guengel.memberberry.domain.Berry
import ch.guengel.memberberry.domain.RememberPeriod
import ch.guengel.memberberry.repositories.BerryRepository
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import io.smallrye.mutiny.Multi
import io.smallrye.mutiny.Uni
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.extension.ExtendWith
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.*

@ExtendWith(MockKExtension::class)
internal class ReminderServiceTest {
    @MockK
    lateinit var berryRepository: BerryRepository

    @MockK
    lateinit var reminderStrategy: ReminderStrategy

    @MockK
    lateinit var executionCalculatorService: ExecutionCalculatorService

    @InjectMockKs
    lateinit var reminderService: ReminderService

    @Test
    fun `happy path`() {
        val nextExecutionDate = OffsetDateTime.now()
        val lastExecutionDate = nextExecutionDate.minusDays(1)
        val berry = Berry(UUID.randomUUID(),
                "test",
                RememberPeriod.DAILY,
                "userid",
                nextExecutionDate,
                lastExecutionDate)

        every { berryRepository.findBerriesDueBy(any()) }.answers {
            Multi
                .createFrom()
                .item(berry)
        }
        every {
            executionCalculatorService.calculateNextExecution(
                RememberPeriod.DAILY,
                any()
            )
        }.answers { nextExecutionDate.plusDays(1) }
        every {
            reminderStrategy.remind(berry)
        }.answers { }

        val captureSlot = slot<Berry>()
        every { berryRepository.update(capture(captureSlot)) }.answers { Uni.createFrom().item(1L) }

        reminderService.remind()

        with(captureSlot.captured) {
            assertThat(this.id, `is`(berry.id))
            assertThat(this.subject, `is`("test"))
            assertThat(this.userId, `is`("userid"))
            assertThat(this.period, `is`(RememberPeriod.DAILY))
            assertThat(this.nextExecution.truncatedTo(ChronoUnit.DAYS),
                    `is`(nextExecutionDate.plusDays(1).truncatedTo(ChronoUnit.DAYS)))
        }

        verify(exactly = 1) { executionCalculatorService.calculateNextExecution(eq(RememberPeriod.DAILY), any()) }
        verify(exactly = 1) { berryRepository.update(any()) }
    }

    @Test
    fun `with errors`() {
        val nextExecutionDate = OffsetDateTime.now()
        val lastExecutionDate = nextExecutionDate.minusDays(1)
        val berry = Berry(UUID.randomUUID(),
                "test",
                RememberPeriod.DAILY,
                "userid",
                nextExecutionDate,
                lastExecutionDate)

        every { berryRepository.findBerriesDueBy(any()) }.answers {
            Multi
                .createFrom()
                .items(berry, berry, berry)
        }
        every {
            executionCalculatorService.calculateNextExecution(
                RememberPeriod.DAILY,
                any()
            )
        }.answers { nextExecutionDate.plusDays(1) }
        every {
            reminderStrategy.remind(berry)
        }
                .answers { }
                .andThen { throw RuntimeException("Test exception") }
                .andThen { }

        every { berryRepository.update(any()) }.answers { Uni.createFrom().item(1L) }

        reminderService.remind()

        verify(exactly = 3) { reminderStrategy.remind(any()) }
        verify(exactly = 2) { executionCalculatorService.calculateNextExecution(eq(RememberPeriod.DAILY), any()) }
        verify(exactly = 3) { berryRepository.update(any()) }
    }
}