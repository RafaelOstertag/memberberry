package ch.guengel.memberberry.services

import ch.guengel.memberberry.domain.Berry
import ch.guengel.memberberry.domain.FCMToken
import ch.guengel.memberberry.domain.RememberPeriod
import ch.guengel.memberberry.google.CloudMessaging
import ch.guengel.memberberry.repositories.FCMTokenRepository
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.justRun
import io.mockk.verify
import io.smallrye.mutiny.Uni
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.OffsetDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
internal class FCMReminderTest {
    @MockK
    private lateinit var fcmTokenRepository: FCMTokenRepository

    @MockK
    private lateinit var cloudMessaging: CloudMessaging

    @InjectMockKs
    private lateinit var fcmReminder: FCMReminder

    @AfterEach
    fun afterEach() {
        fcmReminder.preDestroy()
    }

    @Test
    fun `happy path`() {
        every { fcmTokenRepository.findTokensForUser("userId") } returns Uni.createFrom()
            .item(FCMToken("userId", "fcmToken"))
        justRun { cloudMessaging.sendMessage("fcmToken", "berry subject") }

        fcmReminder.remind(
            Berry(
                UUID.randomUUID(), "berry subject", RememberPeriod.DAILY, "userId", OffsetDateTime.now(),
                OffsetDateTime.now()
            )
        )

        verify(exactly = 1) { fcmTokenRepository.findTokensForUser("userId") }
        verify(exactly = 1) { cloudMessaging.sendMessage("fcmToken", "berry subject") }
    }
}
