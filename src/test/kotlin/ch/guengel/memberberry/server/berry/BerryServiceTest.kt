package ch.guengel.memberberry.server.berry

import assertk.assertThat
import assertk.assertions.*
import ch.guengel.memberberry.model.Berry
import ch.guengel.memberberry.model.BerryPriority
import ch.guengel.memberberry.model.BerryState
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.smallrye.mutiny.Multi
import io.smallrye.mutiny.Uni
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.OffsetDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
internal class BerryServiceTest {

    @MockK
    private lateinit var berryPersistence: BerryPersistence

    @InjectMockKs
    private lateinit var berryService: BerryService

    @Test
    fun `should create berry`() {
        val berry: Berry = Berry().apply {
            title = "test"
            state = BerryState.OPEN
            priority = BerryPriority.MEDIUM
        }

        val expectedBerryPersistenceModel = BerryPersistenceModel(
            id = null,
            "testuser",
            "test",
            state = "open",
            priority = "medium",
            description = null,
            tags = emptySet()
        )

        val persistedBerry = PersistedBerry(
            expectedBerryPersistenceModel.copy(id = UUID.randomUUID()),
            BerryAuditModel(OffsetDateTime.now(), null)
        )

        every { berryPersistence.save(expectedBerryPersistenceModel) } returns Uni.createFrom().item(persistedBerry)

        val berryId = berryService.createBerry("testuser", berry).await().indefinitely()
        assertThat(berryId).isEqualTo(persistedBerry.berryPersistenceModel.id)
    }

    @Test
    fun `should get berry by id`() {
        val id = UUID.randomUUID()
        val expectedBerryPersistenceModel = BerryPersistenceModel(
            id = id,
            "testuser",
            "test",
            state = "open",
            priority = "low",
            description = "description",
            tags = setOf("tag")
        )

        val expectedPersistedBerry = PersistedBerry(
            expectedBerryPersistenceModel,
            BerryAuditModel(OffsetDateTime.now(), null)
        )

        every { berryPersistence.getById("user", id) } returns Uni.createFrom().item(expectedPersistedBerry)

        val berryWithId = berryService.getBerry("user", id).await().indefinitely()

        assertThat(berryWithId.id).isEqualTo(id)
        assertThat(berryWithId.title).isEqualTo(expectedBerryPersistenceModel.title)
        assertThat(berryWithId.tags).isEqualTo(expectedBerryPersistenceModel.tags)
        assertThat(berryWithId.state).isEqualTo(BerryState.fromValue(expectedBerryPersistenceModel.state))
        assertThat(berryWithId.created).isEqualTo(expectedPersistedBerry.berryAuditModel.created)
        assertThat(berryWithId.updated).isNull()
    }

    @Test
    fun `should delete berry by id`() {
        val id = UUID.randomUUID()
        every { berryPersistence.delete("user", id) } returns Uni.createFrom().item(true)

        assertThat(berryService.deleteBerry("user", id).await().indefinitely()).isTrue()
    }

    @Test
    fun `should update berry`() {
        val id = UUID.randomUUID()
        val persistedBerry = PersistedBerry(
            BerryPersistenceModel(
                id = id,
                title = "title",
                state = "open",
                priority = "high",
                description = "description",
                tags = emptySet(),
                userId = "userid"
            ),
            BerryAuditModel(OffsetDateTime.now().minusDays(1), OffsetDateTime.now())
        )

        every { berryPersistence.save(persistedBerry.berryPersistenceModel) } returns Uni.createFrom()
            .item(persistedBerry)

        val berry = Berry()
        berry.title = "title"
        berry.state = BerryState.OPEN
        berry.priority = BerryPriority.HIGH
        berry.tags = null
        berry.description = "description"

        val actual = berryService.updateBerry("userid", id, berry).await().indefinitely()
        assertThat(actual).isEqualTo(persistedBerry)
    }

    @Test
    fun `should throw exception on null persisted berry`() {
        val berry: Berry = Berry().apply {
            title = "test"
            state = BerryState.OPEN
            priority = BerryPriority.LOW
        }

        val expectedBerryPersistenceModel = BerryPersistenceModel(
            id = null,
            userId = "testuser",
            title = "test",
            priority = "low",
            state = "open",
            description = null,
            tags = emptySet()
        )

        every { berryPersistence.save(expectedBerryPersistenceModel) } returns Uni.createFrom().nullItem()

        assertThat { berryService.createBerry("testuser", berry).await().indefinitely() }.isFailure()
            .hasClass(BerryPersistenceException::class)
    }

    @Test
    fun `should get all tags`() {
        every { berryPersistence.getTags("user-id") } returns Multi.createFrom().items("tag-1", "tag-2")

        val tags = berryService.getAllTags("user-id").collect().asList().await().indefinitely()
        assertThat(tags).containsExactly("tag-1", "tag-2")
    }

    @Test
    fun `should get all berries paged`() {
        // On page in total
        var pagedPersistedBerry = PagedPersistedBerries(emptyList(), 10, true, true, false, false)
        every { berryPersistence.getAllByUserId("user", 0, 100, "open") } returns Uni.createFrom()
            .item(pagedPersistedBerry)

        var actual = berryService.getBerries("user", 0, 100, "open", null, null).await().indefinitely()
        var expected = PagedBerriesResult(emptyList(), 100, 0, null, null, true, true, 1, 10)
        assertThat(actual).isEqualTo(expected)

        // Three pages in total, second page
        pagedPersistedBerry = PagedPersistedBerries(emptyList(), 280, false, false, true, true)
        every { berryPersistence.getAllByUserId("user", 1, 100, "open") } returns Uni.createFrom()
            .item(pagedPersistedBerry)

        actual = berryService.getBerries("user", 1, 100, "open", null, null).await().indefinitely()
        expected = PagedBerriesResult(emptyList(), 100, 1, 0, 2, false, false, 3, 280)
        assertThat(actual).isEqualTo(expected)

        // Three pages in total, last paged
        pagedPersistedBerry = PagedPersistedBerries(emptyList(), 280, false, true, false, true)
        every { berryPersistence.getAllByUserId("user", 2, 100, "open") } returns Uni.createFrom()
            .item(pagedPersistedBerry)

        actual = berryService.getBerries("user", 2, 100, "open", null, null).await().indefinitely()
        expected = PagedBerriesResult(emptyList(), 100, 2, 1, null, false, true, 3, 280)
        assertThat(actual).isEqualTo(expected)

        // No results
        pagedPersistedBerry = PagedPersistedBerries(emptyList(), 0, true, true, false, false)
        every { berryPersistence.getAllByUserId("user", 0, 100, "open") } returns Uni.createFrom()
            .item(pagedPersistedBerry)

        actual = berryService.getBerries("user", 0, 100, "open", null, null).await().indefinitely()
        expected = PagedBerriesResult(emptyList(), 100, 0, null, null, true, true, 0, 0)
        assertThat(actual).isEqualTo(expected)

        // exceed page index
        pagedPersistedBerry = PagedPersistedBerries(emptyList(), 280, false, true, false, true)
        every { berryPersistence.getAllByUserId("user", 3, 100, "open") } returns Uni.createFrom()
            .item(pagedPersistedBerry)

        assertThat { berryService.getBerries("user", 3, 100, "open", null, null).await().indefinitely() }.isFailure()
            .hasClass(IllegalArgumentException::class)
    }

    @Test
    fun `should validate pagination parameters`() {
        assertThat { berryService.getBerries("userid", -1, 10, null, null, null) }.isFailure()
            .hasClass(IllegalArgumentException::class)
        assertThat { berryService.getBerries("userid", 2, 0, null, null, null) }.isFailure()
            .hasClass(IllegalArgumentException::class)
        assertThat { berryService.getBerries("userid", 2, 251, null, null, null) }.isFailure()
            .hasClass(IllegalArgumentException::class)
    }

    @Test
    fun `should pass along all filters`() {
        // On page in total
        val pagedPersistedBerry = PagedPersistedBerries(emptyList(), 10, true, true, false, false)
        every { berryPersistence.getAllByUserId("user", 0, 100, "open", "medium", "a-tag") } returns Uni.createFrom()
            .item(pagedPersistedBerry)

        val actual = berryService.getBerries("user", 0, 100, "open", "medium", "a-tag").await().indefinitely()
        val expected = PagedBerriesResult(emptyList(), 100, 0, null, null, true, true, 1, 10)
        assertThat(actual).isEqualTo(expected)
    }
}
