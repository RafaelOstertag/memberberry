package ch.guengel.memberberry.services

import ch.guengel.memberberry.domain.RememberPeriod
import java.time.OffsetDateTime
import javax.enterprise.context.ApplicationScoped

@ApplicationScoped
class ExecutionCalculatorService {
    fun calculateNextExecution(period: RememberPeriod, from: OffsetDateTime = OffsetDateTime.now()): OffsetDateTime =
        when (period) {
            RememberPeriod.DAILY -> from.plusDays(1)
            RememberPeriod.WEEKLY -> from.plusWeeks(1)
            RememberPeriod.MONTHLY -> from.plusMonths(1)
        }
}