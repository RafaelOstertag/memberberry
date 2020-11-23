package ch.guengel.memberberry.services

import ch.guengel.memberberry.domain.Berry
import ch.guengel.memberberry.dto.CreateUpdateBerry
import ch.guengel.memberberry.repositories.BerryRepository
import io.smallrye.mutiny.Multi
import io.smallrye.mutiny.Uni
import java.time.OffsetDateTime
import java.util.*
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject

@ApplicationScoped
class BerryService(@Inject private val berryRepository: BerryRepository,
                   @Inject private val executionCalculatorService: ExecutionCalculatorService) {

    fun find(berryId: String, permissions: Permissions): Uni<Berry?> = berryRepository
            .findBerry(UUID.fromString(berryId))
            .onItem().ifNotNull().transform { berry ->
                if (permissions.hasPermissionTo(berry!!)) {
                    berry
                } else {
                    null
                }
            }

    fun create(createUpdateBerry: CreateUpdateBerry, permissions: Permissions): Uni<UUID> = Uni
            .createFrom()
            .item(createUpdateBerry)
            .onItem().transform {
                val currentDateTime = OffsetDateTime.now()
                val period = createUpdateBerry.period
                val nextExecution = executionCalculatorService.calculateNextExecution(period, currentDateTime)

                Berry(UUID.randomUUID(),
                        createUpdateBerry.subject,
                        period,
                        permissions.userId,
                        nextExecution,
                        currentDateTime)
            }
            .onItem().transformToUni { berry -> berryRepository.create(berry) }

    fun update(berryId: String, createUpdateBerry: CreateUpdateBerry, permissions: Permissions): Uni<Long> = Uni
            .createFrom().item(berryId)
            .onItem().transformToUni { id -> find(id, permissions) }
            .onItem().ifNotNull().transform { existingBerry ->
                val nextExecution = executionCalculatorService.calculateNextExecution(createUpdateBerry.period,
                        existingBerry!!.lastExecution)
                Berry(existingBerry.id,
                        createUpdateBerry.subject,
                        createUpdateBerry.period,
                        permissions.userId,
                        nextExecution,
                        existingBerry.lastExecution)
            }
            .onItem().ifNotNull().transformToUni { updatedBerry -> berryRepository.update(updatedBerry) }

    fun deleteBerry(berryId: String, permissions: Permissions): Uni<Long> = Uni
            .createFrom().item(berryId)
            .onItem().transformToUni { id -> find(id, permissions) }
            .onItem().ifNotNull().transformToUni { existingBerry -> berryRepository.deleteBerryById(existingBerry!!.id) }

    fun getAll(permissions: Permissions): Multi<Berry> =
            if (permissions.admin) {
                berryRepository.getAll()
            } else {
                berryRepository.getAll(permissions.userId)
            }
}