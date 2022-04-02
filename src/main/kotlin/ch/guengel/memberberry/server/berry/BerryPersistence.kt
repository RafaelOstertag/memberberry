package ch.guengel.memberberry.server.berry

import com.mongodb.client.model.*
import com.mongodb.client.model.Filters.*
import com.mongodb.client.model.Sorts.ascending
import com.mongodb.client.model.Sorts.descending
import io.quarkus.mongodb.FindOptions
import io.quarkus.mongodb.reactive.ReactiveMongoClient
import io.quarkus.mongodb.reactive.ReactiveMongoCollection
import io.quarkus.runtime.annotations.RegisterForReflection
import io.smallrye.mutiny.Multi
import io.smallrye.mutiny.Uni
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.bson.BsonDocument
import org.bson.Document
import org.bson.codecs.pojo.annotations.BsonCreator
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.codecs.pojo.annotations.BsonProperty
import org.bson.conversions.Bson
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.time.Duration
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import javax.annotation.PostConstruct
import javax.enterprise.context.ApplicationScoped

open class BerryPersistenceException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
class BerryNotFoundException(message: String, cause: Throwable? = null) : BerryPersistenceException(message, cause)
class BerryUpdateException(message: String, cause: Throwable? = null) : BerryPersistenceException(message, cause)

/**
 * Mongo persistence layer
 */
@ApplicationScoped
class BerryPersistence(
    private val reactiveMongoClient: ReactiveMongoClient,
    @ConfigProperty(name = "memberberry.mongo.db-name") private val databaseName: String,
    @ConfigProperty(name = "memberberry.mongo.berry-collection") private val collectionName: String
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    private val collection: ReactiveMongoCollection<BerryDocument>
        get() = reactiveMongoClient.getDatabase(databaseName).getCollection(collectionName, BerryDocument::class.java)
    private val untypedCollection: ReactiveMongoCollection<Document>
        get() = reactiveMongoClient.getDatabase(
            databaseName
        ).getCollection(collectionName)

    @PostConstruct
    internal fun initializeIndices() {
        scope.launch {
            collection.createIndex(Indexes.ascending("userId", "state", "priority", "created")).await()
                .atMost(Duration.of(1, ChronoUnit.MINUTES))
        }
    }

    /**
     * Save new berry or update existing.
     */
    fun save(berryPersistenceModel: BerryPersistenceModel): Uni<PersistedBerry> = if (berryPersistenceModel.new) {
        createNewBerry(berryPersistenceModel)
    } else {
        updateExistingBerry(berryPersistenceModel)
    }

    fun delete(userId: String, berryId: UUID): Uni<Boolean> = collection
        .deleteOne(and(userIdEqualTo(userId), idEqualTo(berryId)))
        .onItem().transform { deleteResult ->
            deleteResult.deletedCount == 1L
        }

    /**
     * Get berry by user id and berry id
     */
    fun getById(userId: String, id: UUID): Uni<PersistedBerry> = collection
        .find(and(idEqualTo(id), userIdEqualTo(userId))).collect()
        .first()
        .onItem().ifNull().failWith(BerryNotFoundException("Berry with Id '$id' not found"))
        .onItem().ifNotNull().transform { berryDocument -> berryDocument.asPersistedBerry() }

    /**
     * Get all distinct tags of user
     */
    fun getTags(userId: String): Multi<String> = untypedCollection
        .find(
            userIdEqualTo(userId),
            FindOptions().projection(Projections.fields(Projections.include("tags"), Projections.exclude("_id")))
        )
        .onItem().transform { document -> document.getList("tags", String::class.java, emptyList()) }
        .onItem().transformToMulti { tags -> Multi.createFrom().iterable(tags) }
        .merge().select().distinct()
        .collect().asList().onItem().transformToMulti { tagList ->
            tagList.sort()
            Multi.createFrom().iterable(tagList)
        }

    /**
     * Get all berries by user id paginated.
     *
     * If `inState` is null, berries in any state are returned
     */
    fun getAllByUserId(getArguments: GetArguments): Uni<PagedPersistedBerries> = Uni.combine()
        .all()
        .unis(
            getAllByUserIdMulti(getArguments).collect().asList(),
            countBerriesOfUser(getArguments)
        )
        .asTuple()
        .onItem().ifNull().failWith(BerryPersistenceException("Unable to get Berries for user $getArguments.userId"))
        .onItem().transform { tuple ->
            val totalCount = tuple.item2
            val lastPage = (getArguments.pagination.index + 1) * getArguments.pagination.size >= totalCount
            val firstPage = getArguments.pagination.index == 0
            PagedPersistedBerries(
                tuple.item1,
                totalCount,
                first = firstPage || totalCount == 0,
                last = lastPage || totalCount == 0,
                hasNext = !lastPage && totalCount > 0,
                hasPrevious = !firstPage && totalCount > 0
            )
        }

    private fun getAllByUserIdMulti(getArguments: GetArguments): Multi<PersistedBerry> {
        val matcher = berryMatcher(getArguments)
        return collection
            .find(matcher, paginationOptions(getArguments))
            .onItem().transform { berryDocument -> berryDocument.asPersistedBerry() }
    }

    private fun countBerriesOfUser(getArguments: GetArguments): Uni<Int> {
        val matcher = berryMatcher(getArguments)
        return untypedCollection.aggregate(listOf(Aggregates.match(matcher), Aggregates.count("count")))
            .collect().first()
            .onItem().ifNull().continueWith { Document() }
            .onItem().transform { aggregationDocument -> aggregationDocument.getInteger("count", 0) }
    }

    /**
     * Create berry matcher.
     */
    private fun berryMatcher(getArguments: GetArguments): Bson {
        val matchers = mutableListOf<Bson>()
        matchers += userIdEqualTo(getArguments.userId)

        if (getArguments.inState.isNotBlank()) {
            matchers += stateEqualTo(getArguments.inState)
        }

        if (getArguments.withPriority.isNotBlank()) {
            matchers += priorityEqualTo(getArguments.withPriority)
        }

        if (getArguments.withTag.isNotBlank()) {
            matchers += containsTag(getArguments.withTag)
        }

        if (matchers.size == 1) {
            return matchers[0]
        }

        return and(matchers)
    }

    private fun paginationOptions(getArguments: GetArguments): FindOptions {
        val selectedOrderBy = getArguments.ordering.orderBy
        val selectedOrder = getArguments.ordering.order

        val sortBson = when (selectedOrder) {
            Order.ASCENDING -> ascending(selectedOrderBy.fieldName)
            Order.DESCENDING -> descending(selectedOrderBy.fieldName)
        }

        return FindOptions().sort(sortBson).skip(getArguments.pagination.index * getArguments.pagination.size)
            .limit(getArguments.pagination.size)
    }

    private fun updateExistingBerry(berryPersistenceModel: BerryPersistenceModel): Uni<PersistedBerry> =
        collection.find(idEqualTo(berryPersistenceModel.id!!))
            .collect().first()
            .onItem().ifNull()
            .failWith(BerryNotFoundException("Cannot update Berry with id ${berryPersistenceModel.id}, Berry does not exist"))
            .onItem().ifNotNull().transform { existingBerry ->
                BerryDocument(
                    id = existingBerry.id,
                    userId = berryPersistenceModel.userId,
                    title = berryPersistenceModel.title,
                    state = berryPersistenceModel.state,
                    priority = berryPersistenceModel.priority,
                    description = berryPersistenceModel.description,
                    tags = berryPersistenceModel.tags,
                    created = existingBerry.created,
                    updated = OffsetDateTime.now()
                )
            }
            .onItem().transformToUni { updatedBerryDocument ->
                collection.findOneAndReplace(
                    idEqualTo(updatedBerryDocument.id), updatedBerryDocument,
                    FindOneAndReplaceOptions().returnDocument(ReturnDocument.AFTER)
                )
            }
            .onItem().ifNull()
            .failWith(BerryUpdateException("Cannot update Berry with id ${berryPersistenceModel.id}, Berry disappeared"))
            .onItem().ifNotNull().transform { updatedBerryDocument -> updatedBerryDocument.asPersistedBerry() }

    internal fun deleteAllBerries() {
        collection.deleteMany(BsonDocument()).await().indefinitely()
    }

    private fun createNewBerry(berryPersistenceModel: BerryPersistenceModel): Uni<PersistedBerry> {
        return Uni.createFrom().item(berryPersistenceModel)
            .onItem().transform { berry -> berry.asNewDocument() }
            .onItem().transformToUni { newBerryDocument ->
                collection.insertOne(newBerryDocument)
                    .onItem()
                    .transform { _ -> newBerryDocument.asPersistedBerry() }
            }
    }

    private fun idEqualTo(id: UUID) = eq("_id", id)

    private fun userIdEqualTo(userId: String) = eq("userId", userId)

    private fun stateEqualTo(state: String) = eq("state", state)

    private fun priorityEqualTo(priority: String) = eq("priority", priority)

    private fun containsTag(tag: String) = `in`("tags", tag)
}

private fun BerryPersistenceModel.asNewDocument() = BerryDocument(
    id = UUID.randomUUID(),
    userId = userId,
    title = title,
    state = state,
    priority = priority,
    description = description,
    tags = tags,
    created = OffsetDateTime.now(),
    updated = null
)

private fun BerryDocument.asPersistedBerry() = PersistedBerry(
    BerryPersistenceModel(
        id = id,
        userId = userId,
        title = title,
        state = state,
        priority = priority,
        description = description,
        tags = tags
    ),
    BerryAuditModel(
        created = created,
        updated = updated
    )
)

/**
 * The representation of the BerryPersistenceModel as Mongo document
 */
@RegisterForReflection
data class BerryDocument @BsonCreator constructor(
    @BsonId
    val id: UUID,
    @BsonProperty("userId")
    val userId: String,
    @BsonProperty("title")
    val title: String,
    @BsonProperty("state")
    val state: String,
    @BsonProperty("priority")
    val priority: String,
    @BsonProperty("description")
    val description: String?,
    @BsonProperty("tags")
    val tags: Set<String>,
    @BsonProperty("created")
    val created: OffsetDateTime,
    @BsonProperty("updated")
    val updated: OffsetDateTime?
)
