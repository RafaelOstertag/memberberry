package ch.guengel.memberberry.services

import ch.guengel.memberberry.domain.Berry
import ch.guengel.memberberry.repositories.BerryRepository
import io.quarkus.scheduler.Scheduled
import org.jboss.logging.Logger
import java.time.OffsetDateTime
import javax.enterprise.context.ApplicationScoped

@ApplicationScoped
class ReminderService(
    private val berryRepository: BerryRepository,
    private val reminderStrategy: ReminderStrategy,
    private val executionCalculatorService: ExecutionCalculatorService
) {

    @Scheduled(cron = "{memberberry.reminder.cron}")
    fun remind() {
        val now = OffsetDateTime.now()
        logger.info("Start reminding")
        berryRepository
            .findBerriesDueBy(now)
            .onItem().transform { berry ->
                try {
                    reminderStrategy.remind(berry)
                    logger.info("Notification for berry ${berry.id} sent")
                    val currentExecution = now.withHour(berry.nextExecution.hour)
                        .withMinute(berry.nextExecution.minute)
                        .withSecond(0)
                        .withNano(0)
                    val nextExecution =
                        executionCalculatorService.calculateNextExecution(berry.period, currentExecution)
                    Berry(berry.id, berry.subject, berry.period, berry.userId, nextExecution, now)
                } catch (e: Exception) {
                    logger.error("Error while reminding.", e)
                    berry
                }
            }
            .onItem().transformToUniAndConcatenate { berry -> berryRepository.update(berry) }
            .subscribe().with(
                { _ -> },
                { throwable -> logger.error("Error while reminding.", throwable) },
                { logger.info("Done reminding") }
            )
    }

    private companion object {
        private val logger = Logger.getLogger(ReminderService::class.java)
    }
}
