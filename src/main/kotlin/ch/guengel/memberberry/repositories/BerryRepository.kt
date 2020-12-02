package ch.guengel.memberberry.repositories

import ch.guengel.memberberry.domain.Berry
import com.mongodb.BasicDBObject
import com.mongodb.client.model.Filters.*
import com.mongodb.client.model.Indexes
import io.quarkus.mongodb.reactive.ReactiveMongoClient
import io.quarkus.mongodb.reactive.ReactiveMongoCollection
import io.smallrye.mutiny.Multi
import io.smallrye.mutiny.Uni
import org.bson.*
import org.bson.codecs.Codec
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext
import org.bson.codecs.configuration.CodecProvider
import org.bson.codecs.configuration.CodecRegistry
import org.bson.internal.UuidHelper
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.*
import javax.annotation.PostConstruct
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject

private const val ID_FIELD = "_id"

@ApplicationScoped
class BerryRepository(@Inject private val reactiveMongoClient: ReactiveMongoClient) {
    private val collection: ReactiveMongoCollection<Berry> = reactiveMongoClient
        .getDatabase("memberberry")
        .getCollection("berry", Berry::class.java)

    @PostConstruct
    private fun createIndex() {
        collection.createIndex(Indexes.ascending("userId"))
        collection.createIndex(Indexes.ascending("nextExecution"))
    }

    fun findBerry(id: UUID): Uni<Berry?> = collection
        .find(eq(ID_FIELD, id))
        .toUni()

    fun findBerryByIdAndUserId(id: UUID, userId: String): Uni<Berry?> = collection
        .find(and(eq(ID_FIELD, id), eq("userId", userId)))
        .toUni()

    fun findBerriesDueBy(pointInTime: OffsetDateTime): Multi<Berry> = Uni
        .createFrom().item(pointInTime)
        .onItem().transform { it -> it.atZoneSameInstant(ZoneId.of("UTC")) }
        .onItem().transform { it -> it.toOffsetDateTime() }
        .onItem().transformToMulti { it ->
            collection.find(lte("nextExecution", it))
        }

    fun deleteBerryById(id: UUID): Uni<Long> = collection
        .deleteOne(eq(ID_FIELD, id))
        .onItem()
        .transform {
            it.deletedCount
        }

    fun update(berry: Berry): Uni<Long> = collection.replaceOne(eq(ID_FIELD, berry.id), berry)
        .onItem()
        .transform { it.modifiedCount }

    fun create(berry: Berry): Uni<UUID> {
        return collection
            .insertOne(berry)
            .onItem()
            .transform {
                it.insertedId?.asBinary()?.asUUID() ?: throw IllegalStateException("Cannot get ID of Berry")
            }
    }

    fun getAll(): Multi<Berry> = collection.find()

    fun getAll(userId: String): Multi<Berry> = collection.find(eq("userId", userId))

    fun deleteAll(): Uni<Long> = collection
        .deleteMany(BasicDBObject())
        .onItem()
        .transform { it.deletedCount }

    private fun BsonBinary.asUUID(): UUID = UuidHelper.decodeBinaryToUuid(
        this.data,
        BsonBinarySubType.UUID_STANDARD.value, UuidRepresentation.STANDARD
    )
}


class OffsetDateTimeCodec : Codec<OffsetDateTime> {
    override fun encode(writer: BsonWriter, value: OffsetDateTime, encoderContext: EncoderContext) {
        writer.writeDateTime(value.toInstant().toEpochMilli())
    }

    override fun getEncoderClass(): Class<OffsetDateTime> = OffsetDateTime::class.java

    override fun decode(reader: BsonReader, decoderContext: DecoderContext): OffsetDateTime {
        val millisecondsSinceEpoch = reader.readDateTime()
        return OffsetDateTime.ofInstant(Instant.ofEpochMilli(millisecondsSinceEpoch), ZoneId.of("UTC"))
    }
}

class OffsetDateTimeCodecProvider : CodecProvider {
    override fun <T : Any?> get(clazz: Class<T>?, registry: CodecRegistry?): Codec<T>? =
        if (clazz == OffsetDateTime::class.java) {
            OffsetDateTimeCodec() as Codec<T>
        } else {
            null
        }
}