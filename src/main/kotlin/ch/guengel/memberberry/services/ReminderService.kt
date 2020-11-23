package ch.guengel.memberberry.services

import ch.guengel.memberberry.domain.Berry
import ch.guengel.memberberry.repositories.BerryRepository
import io.quarkus.scheduler.Scheduled
import org.jboss.logging.Logger
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject

@ApplicationScoped
class ReminderService(@Inject private val berryRepository: BerryRepository,
                      @Inject private val reminderStrategy: ReminderStrategy,
                      @Inject private val executionCalculatorService: ExecutionCalculatorService) {

    @Scheduled(cron = "{memberberry.reminder.cron}")
    fun remind() {

        val now = OffsetDateTime.now()
        val today = now.truncatedTo(ChronoUnit.DAYS)
        logger.info("Start reminding")
        berryRepository
                .findBerriesDueAfter(today)
                .transform().byFilteringItemsWith { berry -> berry.nextExecution.truncatedTo(ChronoUnit.DAYS) == today }
                .onItem().transform { berry ->
                    try {
                        reminderStrategy.remind(berry)
                        val nextExecution = executionCalculatorService.calculateNextExecution(berry.period, now)
                        Berry(berry.id, berry.subject, berry.period, berry.userId, nextExecution, now)
                    } catch (e: Exception) {
                        logger.error("Error while reminding.", e)
                        berry
                    }
                }
                .subscribe().with(
                        { updatedBerry -> berryRepository.update(updatedBerry) },
                        { throwable -> logger.error("Error while reminding.", throwable) },
                        { logger.info("Done reminding") }
                )
    }

    private companion object {
        private val logger = Logger.getLogger(ReminderService::class.java)
    }
}