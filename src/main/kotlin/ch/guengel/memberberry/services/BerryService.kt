package ch.guengel.memberberry.services

import ch.guengel.memberberry.domain.Berry
import ch.guengel.memberberry.dto.CreateUpdateBerry
import ch.guengel.memberberry.repositories.BerryRepository
import io.smallrye.mutiny.Multi
import io.smallrye.mutiny.Uni
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*
import javax.enterprise.context.ApplicationScoped

@ApplicationScoped
class BerryService(private val berryRepository: BerryRepository) {

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
            Berry(
                UUID.randomUUID(),
                createUpdateBerry.subject,
                createUpdateBerry.period,
                permissions.userId,
                createUpdateBerry.firstExecution,
                startOfEpoch
            )
        }
        .onItem().transformToUni { berry -> berryRepository.create(berry) }

    fun update(berryId: String, createUpdateBerry: CreateUpdateBerry, permissions: Permissions): Uni<Long> = Uni
        .createFrom().item(berryId)
        .onItem().transformToUni { id -> find(id, permissions) }
        .onItem().ifNotNull().transform { existingBerry ->
            Berry(
                existingBerry!!.id,
                createUpdateBerry.subject,
                createUpdateBerry.period,
                permissions.userId,
                createUpdateBerry.firstExecution,
                existingBerry.lastExecution
            )
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

    private companion object {
        private val startOfEpoch = OffsetDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
    }
}
