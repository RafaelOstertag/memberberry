package ch.guengel.memberberry.services

import ch.guengel.memberberry.domain.FCMToken
import ch.guengel.memberberry.dto.CreateUpdateFCMToken
import ch.guengel.memberberry.repositories.FCMTokenRepository
import io.smallrye.mutiny.Uni
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject

@ApplicationScoped
class FCMTokenService(@Inject private val fcmTokenRepository: FCMTokenRepository) {

    fun createOrUpdateToken(createUpdateFCMToken: CreateUpdateFCMToken, userId: String): Uni<Unit> = Uni
            .createFrom()
            .item(createUpdateFCMToken)
            .onItem().transform { FCMToken(userId, it.token) }
            .onItem().transformToUni { fcmToken -> fcmTokenRepository.createOrUpdateToken(fcmToken) }
            .onItem().ifNotNull().transform { it -> }
            .onItem().ifNull().failWith(FCMTokenCreateOrUpdateExecption("Unable to create or update FCM token"))

    fun findTokenForUser(userId: String): Uni<FCMToken?> = Uni
            .createFrom()
            .item(userId)
            .onItem().transformToUni { userId -> fcmTokenRepository.findTokensForUser(userId) }
}

class FCMTokenCreateOrUpdateExecption(msg: String) : RuntimeException(msg)