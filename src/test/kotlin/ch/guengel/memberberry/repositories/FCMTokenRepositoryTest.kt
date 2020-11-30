package ch.guengel.memberberry.repositories

import ch.guengel.memberberry.domain.FCMToken
import ch.guengel.memberberry.testutils.MongoDbResource
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.enterprise.inject.Default
import javax.inject.Inject

@QuarkusTest
@QuarkusTestResource(MongoDbResource::class)
internal class FCMTokenRepositoryTest {
    @Inject
    @field: Default
    private lateinit var fcmTokenRepository: FCMTokenRepository

    @BeforeEach
    fun setUp() {
        fcmTokenRepository.deleteAll().await().indefinitely()
    }

    @Test
    fun findNonExistingToken() {
        val actualFcmToken = fcmTokenRepository.findTokensForUser("does-not-exist").await().indefinitely()
        assertThat(actualFcmToken, `is`(nullValue()))
    }

    @Test
    fun createToken() {
        val fcmToken = FCMToken("test-user", "fcm-token")
        val result = fcmTokenRepository.createOrUpdateToken(fcmToken).await().indefinitely()
        assertThat(result, `is`(notNullValue()))
        assertThat(result?.upsertedId?.asString()?.value, `is`("test-user"))

        val actualFcmToken = fcmTokenRepository.findTokensForUser("test-user").await().indefinitely()
        assertThat(actualFcmToken, `is`(notNullValue()))
        assertThat(actualFcmToken?.userId, `is`("test-user"))
        assertThat(actualFcmToken?.token, `is`("fcm-token"))
    }

    @Test
    fun updateToken() {
        val fcmToken = FCMToken("test-user", "fcm-token")
        fcmTokenRepository.createOrUpdateToken(fcmToken).await().indefinitely()
        val updateFcmToke = FCMToken("test-user", "updated-fcm-token")
        fcmTokenRepository.createOrUpdateToken(updateFcmToke).await().indefinitely()

        val actualFcmToken = fcmTokenRepository.findTokensForUser("test-user").await().indefinitely()
        assertThat(actualFcmToken, `is`(notNullValue()))
        assertThat(actualFcmToken?.userId, `is`("test-user"))
        assertThat(actualFcmToken?.token, `is`("updated-fcm-token"))
    }
}