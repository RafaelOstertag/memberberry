package ch.guengel.memberberry.repositories

import ch.guengel.memberberry.domain.Berry
import ch.guengel.memberberry.domain.RememberPeriod
import ch.guengel.memberberry.testutils.MongoDbResource
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.util.*
import java.util.stream.Collectors
import javax.enterprise.inject.Default
import javax.inject.Inject

@QuarkusTest
@QuarkusTestResource(MongoDbResource::class)
internal class BerryRepositoryTest {
    @Inject
    @field: Default
    private lateinit var berryRepository: BerryRepository

    private val lastExecution = OffsetDateTime.now().minusDays(1)
    private val nextExecution = lastExecution.plusDays(2)

    @BeforeEach
    fun setUp() {
        berryRepository.deleteAll().await().indefinitely()
    }

    @Test
    fun `create berry`() {
        val berry = Berry(UUID.randomUUID(), "test", RememberPeriod.WEEKLY, "user-id", nextExecution, lastExecution)
        val berryId = berryRepository.create(berry).await().indefinitely()

        val actual = berryRepository.findBerry(berryId).await().indefinitely()

        assertThat(actual, `is`(notNullValue()))
        assertThat(actual?.id, `is`(berryId))
        assertThat(actual?.subject, `is`(berry.subject))
        assertThat(actual?.period, `is`(berry.period))
        assertThat(actual?.userId, `is`(berry.userId))
        assertThat(actual?.nextExecution?.toEpochSecond(), `is`(nextExecution.toEpochSecond()))
        assertThat(actual?.lastExecution?.toEpochSecond(), `is`(lastExecution.toEpochSecond()))
    }

    @Test
    fun `find non-existing berry`() {
        val berry = berryRepository
                .findBerry(UUID.randomUUID())
                .await()
                .indefinitely()
        assertThat(berry, `is`(nullValue()))
    }

    @Test
    fun `find berry by id and user id`() {
        val berry = Berry(UUID.randomUUID(),
                "test",
                RememberPeriod.DAILY,
                UUID.randomUUID().toString(),
                nextExecution,
                lastExecution)
        val actual = berryRepository
                .create(berry)
                .chain { uuid ->
                    berryRepository.findBerryByIdAndUserId(uuid, berry.userId)
                }
                .await()
                .indefinitely()
        assertThat(actual?.id, `is`(berry.id))
        assertThat(actual?.subject, `is`(berry.subject))
        assertThat(actual?.nextExecution?.toEpochSecond(), `is`(berry.nextExecution.toEpochSecond()))
        assertThat(actual?.lastExecution?.toEpochSecond(), `is`(berry.lastExecution.toEpochSecond()))
    }

    @Test
    fun `find non-existing berry by id and user id`() {
        val berry = berryRepository
                .findBerryByIdAndUserId(UUID.randomUUID(), "non-existing")
                .await()
                .indefinitely()
        assertThat(berry, `is`(nullValue()))
    }

    @Test
    fun update() {
        val berry = Berry(UUID.randomUUID(), "test", RememberPeriod.WEEKLY, "user-id", nextExecution, lastExecution)
        val updatedBerry: Berry? = berryRepository.create(berry)
                .onItem()
                .transform {
                    Berry(it, "test updated", RememberPeriod.DAILY, "other-id", lastExecution, nextExecution)
                }
                .chain { it: Berry ->
                    berryRepository.update(it)
                }
                .chain { count ->
                    assertThat(count, `is`(1L))
                    berryRepository.findBerry(berry.id)
                }
                .await()
                .indefinitely()
        assertThat(updatedBerry, `is`(notNullValue()))
        assertThat(updatedBerry?.subject, `is`("test updated"))
        assertThat(updatedBerry?.period, `is`(RememberPeriod.DAILY))
        assertThat(updatedBerry?.userId, `is`("other-id"))
        assertThat(updatedBerry?.nextExecution?.toEpochSecond(), `is`(lastExecution.toEpochSecond()))
        assertThat(updatedBerry?.lastExecution?.toEpochSecond(), `is`(nextExecution.toEpochSecond()))
    }

    @Test
    fun `get all on empty database`() {
        val emptyList = berryRepository.getAll().subscribe().asStream().collect(Collectors.toList())
        assertThat(emptyList, hasSize(0))
    }

    @Test
    fun `get all berries`() {
        berryRepository.create(Berry(UUID.randomUUID(),
                "",
                RememberPeriod.WEEKLY,
                "",
                nextExecution,
                lastExecution)).await().indefinitely()
        berryRepository.create(Berry(UUID.randomUUID(),
                "",
                RememberPeriod.DAILY,
                "",
                nextExecution,
                lastExecution)).await().indefinitely()

        val berries = berryRepository
                .getAll()
                .subscribe()
                .asStream()
                .collect(Collectors.toList())

        assertThat(berries, hasSize(2))
    }

    @Test
    fun `get all berries for user`() {
        berryRepository.create(Berry(UUID.randomUUID(),
                "berry 1",
                RememberPeriod.WEEKLY,
                "user1",
                nextExecution,
                lastExecution)).await().indefinitely()
        berryRepository.create(Berry(UUID.randomUUID(),
                "berry 2",
                RememberPeriod.DAILY,
                "user1",
                nextExecution,
                lastExecution)).await().indefinitely()
        berryRepository.create(Berry(UUID.randomUUID(),
                "",
                RememberPeriod.DAILY,
                "user2",
                nextExecution,
                lastExecution)).await().indefinitely()

        val berries = berryRepository
                .getAll("user1")
                .subscribe()
                .asStream()
                .collect(Collectors.toList())

        assertThat(berries, hasSize(2))
        berries.forEach {
            assertThat(it.userId, `is`("user1"))
        }
    }

    @Test
    fun `update non-existing berry`() {
        val berry = Berry(UUID.randomUUID(), "test", RememberPeriod.WEEKLY, "user-id", nextExecution, lastExecution)
        val count = berryRepository
                .update(berry)
                .await()
                .indefinitely()
        assertThat(count, `is`(0L))
    }

    @Test
    fun `delete berry by id`() {
        val berry = Berry(UUID.randomUUID(), "test", RememberPeriod.WEEKLY, "user-id", nextExecution, lastExecution)
        val count = berryRepository
                .create(berry)
                .chain { it ->
                    berryRepository.deleteBerryById(it)
                }
                .await()
                .indefinitely()
        assertThat(count, `is`(1L))
    }

    @Test
    fun `find berries due after`() {
        repeat(3) {
            val berry = Berry(UUID.randomUUID(), "test", RememberPeriod.WEEKLY, "user-id", nextExecution, lastExecution)
            berryRepository.create(berry).await().indefinitely()
        }

        repeat(3) {
            val berry = Berry(UUID.randomUUID(),
                    "test",
                    RememberPeriod.WEEKLY,
                    "user-id",
                    nextExecution.minusDays(2),
                    lastExecution)
            berryRepository.create(berry).await().indefinitely()
        }

        val now = OffsetDateTime.now()
        val berries = berryRepository
                .findBerriesDueAfter(now)
                .subscribe()
                .asStream()
                .collect(Collectors.toList())

        assertThat(berries, hasSize(3))
        berries.forEach {
            assertThat(now, `is`(lessThan(it.nextExecution)))
        }
    }
}