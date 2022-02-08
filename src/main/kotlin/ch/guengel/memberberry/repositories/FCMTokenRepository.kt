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
import javax.enterprise.context.ApplicationScoped

@ApplicationScoped
class FCMTokenRepository(private val reactiveMongoClient: ReactiveMongoClient) {
    private fun ReactiveMongoClient.getTokenCollection(): ReactiveMongoCollection<Document> =
        getDatabase("memberberry").getCollection("fcmtoken")

    fun findTokensForUser(userId: String): Uni<FCMToken?> = reactiveMongoClient.getTokenCollection()
        .find(eq("_id", userId))
        .toUni()
        .onItem()
        .ifNotNull()
        .transform { document -> FCMToken(document.getString("_id"), document.getString("token")) }

    fun createOrUpdateToken(fcmToken: FCMToken): Uni<UpdateResult?> = reactiveMongoClient.getTokenCollection()
        .updateOne(eq("_id", fcmToken.userId), Updates.set("token", fcmToken.token), UpdateOptions().upsert(true))

    fun deleteAll(): Uni<Long> = reactiveMongoClient.getTokenCollection()
        .deleteMany(BasicDBObject())
        .onItem()
        .transform { it.deletedCount }
}
