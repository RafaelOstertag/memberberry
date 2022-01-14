package ch.guengel.memberberry.repositories

import ch.guengel.memberberry.domain.FCMToken
import com.mongodb.BasicDBObject
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import com.mongodb.client.result.UpdateResult
import io.quarkus.mongodb.reactive.ReactiveMongoClient
import io.quarkus.mongodb.reactive.ReactiveMongoCollection
import io.smallrye.mutiny.Uni
import org.bson.Document
import javax.annotation.PostConstruct
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject

@ApplicationScoped
class FCMTokenRepository(@Inject private val reactiveMongoClient: ReactiveMongoClient) {
    private lateinit var collection: ReactiveMongoCollection<Document>

    @PostConstruct
    private fun postConstruct() {
        collection = reactiveMongoClient
            .getDatabase("memberberry")
            .getCollection("fcmtoken")
    }

    fun findTokensForUser(userId: String): Uni<FCMToken?> = collection
        .find(eq("_id", userId))
        .toUni()
        .onItem()
        .ifNotNull()
        .transform { document -> FCMToken(document.getString("_id"), document.getString("token")) }

    fun createOrUpdateToken(fcmToken: FCMToken): Uni<UpdateResult?> = collection
        .updateOne(eq("_id", fcmToken.userId), Updates.set("token", fcmToken.token), UpdateOptions().upsert(true))

    fun deleteAll(): Uni<Long> = collection
        .deleteMany(BasicDBObject())
        .onItem()
        .transform { it.deletedCount }
}
