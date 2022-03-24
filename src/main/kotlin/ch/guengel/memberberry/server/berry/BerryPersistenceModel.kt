package ch.guengel.memberberry.server.berry

import java.time.OffsetDateTime
import java.util.*

/**
 * Input BerryPersistenceModel to persistence layer.
 */
data class BerryPersistenceModel(
    // When null, it's assumed this is a new berryPersistenceModel
    val id: UUID?,
    val userId: String,
    val title: String,
    val state: String,
    val priority: String,
    val description: String?,
    val tags: Set<String>
) {
    val new: Boolean get() = id == null
}

data class BerryAuditModel(
    val created: OffsetDateTime,
    val updated: OffsetDateTime?
)

/**
 * Output from persistence layer
 */
data class PersistedBerry(val berryPersistenceModel: BerryPersistenceModel, val berryAuditModel: BerryAuditModel)
data class PagedPersistedBerries(
    val persistedBerry: List<PersistedBerry>,
    val totalCount: Int,
    val first: Boolean,
    val last: Boolean,
    val hasNext: Boolean,
    val hasPrevious: Boolean
)
