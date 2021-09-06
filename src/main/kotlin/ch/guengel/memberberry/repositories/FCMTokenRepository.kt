package ch.guengel.memberberry.repositories

import ch.guengel.memberberry.domain.FCMToken
import com.mongodb.BasicDBObject
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import com.mongodb.client.result.UpdateResult
import io.quarkus.mongodb.reactive.ReactiveMongoClient
import io.smallrye.mutiny.Uni
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject

@ApplicationScoped
class FCMTokenRepository(@Inject private val reactiveMongoClient: ReactiveMongoClient) {
    private val collection
        get() = reactiveMongoClient
            .getDatabase("memberberry")
            .getCollection("fcmtoken")

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
