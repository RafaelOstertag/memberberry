package ch.guengel.memberberry.services

import ch.guengel.memberberry.domain.Berry
import ch.guengel.memberberry.google.CloudMessaging
import ch.guengel.memberberry.repositories.FCMTokenRepository
import javax.enterprise.context.ApplicationScoped

@ApplicationScoped
class FCMReminder(
    private val fcmTokenRepository: FCMTokenRepository,
    private val cloudMessaging: CloudMessaging
) : ReminderStrategy {
    override fun remind(berry: Berry) {
        fcmTokenRepository.findTokensForUser(berry.userId)
            .onItem().ifNotNull().transform { token ->
                cloudMessaging.sendMessage(token!!.token, berry.subject)
            }
            .await().indefinitely()
    }
}
