package ch.guengel.memberberry.server.berry

import ch.guengel.memberberry.model.Berry
import ch.guengel.memberberry.model.BerryPriority
import ch.guengel.memberberry.model.BerryState
import ch.guengel.memberberry.model.BerryWithId
import io.smallrye.mutiny.Multi
import io.smallrye.mutiny.Uni
import org.jboss.logging.Logger
import java.util.*
import javax.enterprise.context.ApplicationScoped
import kotlin.math.ceil

@ApplicationScoped
class BerryService(private val berryPersistence: BerryPersistence) {

    fun createBerry(userId: String, berry: Berry): Uni<UUID> {
        logger.info("Create new Berry for user '$userId'")
        return Uni.combine()
            .all().unis(Uni.createFrom().item(userId), Uni.createFrom().item(berry))
            .asTuple()
            .onItem().transform { tuple -> tuple.item2.toBerryPersistenceModelForUser(tuple.item1, null) }
            .onItem().transformToUni { berryPersistenceModel -> berryPersistence.save(berryPersistenceModel) }
            .onItem().ifNull().failWith(BerryPersistenceException("Could not save Berry"))
            .onItem().ifNotNull().transform { persistedBerry -> persistedBerry.berryPersistenceModel.id }
    }

    fun updateBerry(userId: String, berryId: UUID, berry: Berry): Uni<PersistedBerry> {
        logger.info("Update Berry '$berryId' for user '$userId'")
        return Uni.combine().all()
            .unis(Uni.createFrom().item(userId), Uni.createFrom().item(berryId), Uni.createFrom().item(berry))
            .asTuple()
            .onItem().transformToUni { tuple ->
                val berryPersistenceModel = tuple.item3.toBerryPersistenceModelForUser(tuple.item1, tuple.item2)
                berryPersistence.save(berryPersistenceModel)
            }
    }

    fun deleteBerry(userId: String, berryId: UUID): Uni<Boolean> {
        logger.info("Delete Berry '$berryId' for user '$userId'")
        return Uni.combine()
            .all().unis(Uni.createFrom().item(userId), Uni.createFrom().item(berryId))
            .asTuple()
            .onItem().transformToUni { tuple ->
                berryPersistence.delete(tuple.item1, tuple.item2)
            }
    }

    fun getAllTags(userId: String): Multi<String> = Uni.createFrom().item(userId)
        .onItem().transformToMulti { uid -> berryPersistence.getTags(uid) }

    fun getBerries(
        getArguments: GetArguments
    ): Uni<PagedBerriesResult> {
        if (getArguments.pagination.index < 0) {
            throw IllegalArgumentException("Page index must not be less than 0")
        }
        if (getArguments.pagination.size < 1) {
            throw IllegalArgumentException("Page size must not be less than 1")
        }
        if (getArguments.pagination.size > 250) {
            throw IllegalArgumentException("Page size must not be greater than 250")
        }
        return berryPersistence.getAllByUserId(getArguments)
            .onItem().transform { pagedPersistedBerries ->
                val berries = pagedPersistedBerries.persistedBerry.map { it.toBerryWithId() }
                PagedBerriesResult(
                    berriesWithId = berries,
                    pageSize = getArguments.pagination.size,
                    pageIndex = getArguments.pagination.index,
                    previousPageIndex = if (pagedPersistedBerries.first) null else getArguments.pagination.index - 1,
                    nextPageIndex = if (pagedPersistedBerries.last) null else getArguments.pagination.index + 1,
                    firstPage = pagedPersistedBerries.first,
                    lastPage = pagedPersistedBerries.last,
                    totalPages = ceil(pagedPersistedBerries.totalCount / getArguments.pagination.size.toDouble()).toInt(),
                    totalEntries = pagedPersistedBerries.totalCount
                )
            }
            .onItem().transform { page ->
                if (page.pageIndex >= page.totalPages && page.totalEntries > 0)
                    throw IllegalArgumentException("Page index '${page.pageIndex}' references non existing page. Maximum page index allowed '${page.totalPages - 1}")
                else
                    page
            }
    }

    fun getBerry(userId: String, berryId: UUID): Uni<BerryWithId> = berryPersistence.getById(userId, berryId)
        .onItem().transform { persistedBerry -> persistedBerry.toBerryWithId() }

    private fun Berry.toBerryPersistenceModelForUser(userId: String, id: UUID?) = BerryPersistenceModel(
        id = id,
        userId = userId,
        title = title,
        state = state.toString(),
        priority = priority.toString(),
        description = description,
        tags = tags ?: emptySet()
    )

    private fun PersistedBerry.toBerryWithId(): BerryWithId {
        val berryWithId = BerryWithId()
        berryWithId.id = berryPersistenceModel.id
        berryWithId.title = berryPersistenceModel.title
        berryWithId.state = BerryState.fromValue(berryPersistenceModel.state)
        berryWithId.priority = BerryPriority.fromValue(berryPersistenceModel.priority)
        berryWithId.description = berryPersistenceModel.description
        berryWithId.tags = berryPersistenceModel.tags
        berryWithId.created = berryAuditModel.created
        berryWithId.updated = berryAuditModel.updated
        return berryWithId
    }

    private companion object {
        val logger = Logger.getLogger(BerryService::class.java)
    }
}

data class PagedBerriesResult(
    val berriesWithId: List<BerryWithId>,
    val pageSize: Int,
    val pageIndex: Int,
    val previousPageIndex: Int?,
    val nextPageIndex: Int?,
    val firstPage: Boolean,
    val lastPage: Boolean,
    val totalPages: Int,
    val totalEntries: Int
)
