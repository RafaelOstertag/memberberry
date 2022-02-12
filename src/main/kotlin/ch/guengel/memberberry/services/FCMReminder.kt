package ch.guengel.memberberry.services

import ch.guengel.memberberry.domain.Berry
import ch.guengel.memberberry.google.CloudMessaging
import ch.guengel.memberberry.repositories.FCMTokenRepository
import com.google.common.util.concurrent.ThreadFactoryBuilder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.annotation.PreDestroy
import javax.enterprise.context.ApplicationScoped

@ApplicationScoped
class FCMReminder(
    private val fcmTokenRepository: FCMTokenRepository,
    private val cloudMessaging: CloudMessaging
) : ReminderStrategy {
    private val executor: ExecutorService =
        Executors.newSingleThreadExecutor(ThreadFactoryBuilder().setNameFormat("fcm-reminder-%d").build())

    @PreDestroy
    internal fun preDestroy() {
        executor.shutdown()
    }

    override fun remind(berry: Berry) {
        fcmTokenRepository.findTokensForUser(berry.userId).emitOn(executor)
            .onItem().ifNotNull().transform { token ->
                cloudMessaging.sendMessage(token!!.token, berry.subject)
            }
            .await().indefinitely()
    }
}
