package ch.guengel.memberberry.services

import ch.guengel.memberberry.domain.Berry
import ch.guengel.memberberry.google.CloudMessaging
import ch.guengel.memberberry.repositories.FCMTokenRepository
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject

@ApplicationScoped
class FCMReminder(
    @Inject private val fcmTokenRepository: FCMTokenRepository,
    @Inject private val cloudMessaging: CloudMessaging
) : ReminderStrategy {
    override fun remind(berry: Berry) {
        fcmTokenRepository.findTokensForUser(berry.userId)
            .onItem().ifNotNull().transform { token ->
                cloudMessaging.sendMessage(token!!.token, berry.subject)
            }
            .await().indefinitely()
    }
}