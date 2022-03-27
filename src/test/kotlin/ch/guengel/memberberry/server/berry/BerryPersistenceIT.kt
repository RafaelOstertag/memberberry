package ch.guengel.memberberry.server.berry

import assertk.assertThat
import assertk.assertions.*
import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*
import javax.inject.Inject

@QuarkusTest
internal class BerryPersistenceIT {

    @Inject
    lateinit var berryPersistence: BerryPersistence

    @BeforeEach
    fun beforeEach() {
        berryPersistence.deleteAllBerries()
    }

    @Test
    fun `should save new berry`() {
        val newBerry =
            BerryPersistenceModel(null, "userid", "title", "state", "priority", "description", setOf("tag-1", "tag-2"))
        val persistedBerry = berryPersistence.save(newBerry).await().indefinitely()
        assertNewlyPersistedBerry(persistedBerry, newBerry)

        val actual =
            berryPersistence.getById("userid", persistedBerry.berryPersistenceModel.id!!).await().indefinitely()
        assertNewlyPersistedBerry(actual, newBerry)
    }

    @Test
    fun `should throw exception on non-existing berry`() {
        assertThat {
            berryPersistence.getById("non-existing", UUID.randomUUID()).await().indefinitely()
        }.isFailure().hasClass(BerryNotFoundException::class)
    }

    @Test
    fun `should delete existing berry`() {
        val newBerry =
            BerryPersistenceModel(null, "userid", "title", "state", "priority", "description", setOf("tag-1", "tag-2"))
        var persistedBerry = berryPersistence.save(newBerry).await().indefinitely()
        assertThat(persistedBerry).isNotNull()

        persistedBerry =
            berryPersistence.getById("userid", persistedBerry.berryPersistenceModel.id!!).await().indefinitely()
        assertThat(persistedBerry).isNotNull()

        val result = berryPersistence.delete("userid", persistedBerry.berryPersistenceModel.id!!).await().indefinitely()
        assertThat(result).isTrue()

        assertThat {
            berryPersistence.getById("userid", persistedBerry.berryPersistenceModel.id!!).await().indefinitely()
        }.isFailure().hasClass(BerryNotFoundException::class)
    }

    @Test
    fun `should return false when deleting non-existing berry`() {
        assertThat(
            berryPersistence.delete("non-existing", UUID.randomUUID()).await().indefinitely()
        ).isFalse()
    }

    private fun assertNewlyPersistedBerry(
        persistedBerry: PersistedBerry,
        expected: BerryPersistenceModel
    ) {
        assertThat(persistedBerry).isNotNull()
        assertThat(persistedBerry.berryPersistenceModel.new).isFalse()
        assertThat(persistedBerry.berryPersistenceModel.id).isNotNull()
        assertThat(persistedBerry.berryPersistenceModel.title).isEqualTo(expected.title)
        assertThat(persistedBerry.berryPersistenceModel.tags).isEqualTo(expected.tags)
        assertThat(persistedBerry.berryPersistenceModel.state).isEqualTo(expected.state)
        assertThat(persistedBerry.berryPersistenceModel.priority).isEqualTo(expected.priority)
        assertThat(persistedBerry.berryPersistenceModel.description).isEqualTo(expected.description)
        assertThat(persistedBerry.berryPersistenceModel.userId).isEqualTo(expected.userId)
        assertThat(persistedBerry.berryAuditModel.created).isNotNull()
        assertThat(persistedBerry.berryAuditModel.updated).isNull()
    }

    @Test
    fun `should get all berries by user id`() {
        val newBerry1 =
            BerryPersistenceModel(null, "user", "title 1", "state", "priority", "description", setOf("tag-1", "tag-2"))
        val newBerry2 =
            BerryPersistenceModel(null, "user", "title 1", "state", "priority", "description", setOf("tag-1", "tag-2"))
        val newBerry3 =
            BerryPersistenceModel(
                null,
                "other-user",
                "title 1",
                "state",
                "priority",
                "description",
                setOf("tag-1", "tag-2")
            )

        listOf(newBerry1, newBerry2, newBerry3).forEach { berryPersistence.save(it).await().indefinitely() }

        val berriesOfUser = berryPersistence.getAllByUserId("user", 0, 100).await().indefinitely()
        assertThat(berriesOfUser.persistedBerry).hasSize(2)
        assertThat(berriesOfUser.totalCount).isEqualTo(2)

        val berriesOfOtherUser =
            berryPersistence.getAllByUserId("other-user", 0, 100).await().indefinitely()
        assertThat(berriesOfOtherUser.persistedBerry).hasSize(1)
        assertThat(berriesOfOtherUser.totalCount).isEqualTo(1)

        val noBerries = berryPersistence.getAllByUserId("user-with-no-berries", 0, 100).await().indefinitely()
        assertThat(noBerries.persistedBerry).isEmpty()
        assertThat(noBerries.totalCount).isZero()
    }

    @Test
    fun `should have working pagination`() {
        (0 until 99)
            .map {
                val berryNumber = it.toString().padStart(3, '0')
                BerryPersistenceModel(
                    null,
                    "user",
                    "title $berryNumber",
                    "state",
                    "priority",
                    "description",
                    emptySet()
                )
            }
            .forEach { berryPersistence.save(it).await().indefinitely() }

        (0 until 10).forEach { pageIndex ->
            val pagedBerries = berryPersistence.getAllByUserId("user", pageIndex, 10).await().indefinitely()
            val numberOfBerriesInPage = if (pageIndex < 9) 10 else 9
            assertThat(pagedBerries.persistedBerry).hasSize(numberOfBerriesInPage)
            val firstPage = pageIndex == 0
            assertThat(pagedBerries.first).isEqualTo(firstPage)
            val lastPage = pageIndex == 9
            assertThat(pagedBerries.last).isEqualTo(lastPage)
            assertThat(pagedBerries.hasPrevious).isEqualTo(!firstPage)
            assertThat(pagedBerries.hasNext).isEqualTo(!lastPage)
            assertThat(pagedBerries.totalCount).isEqualTo(99)
            (0 until numberOfBerriesInPage).forEach { berryIndex ->
                val berryNumber = (pageIndex * 10 + berryIndex).toString().padStart(3, '0')
                assertThat(pagedBerries.persistedBerry[berryIndex].berryPersistenceModel.title).isEqualTo(
                    "title $berryNumber"
                )
            }
        }

        // Page index greater than available pages
        var pagedBerries = berryPersistence.getAllByUserId("user", 100, 10).await().indefinitely()
        assertThat(pagedBerries.persistedBerry).isEmpty()
        assertThat(pagedBerries.totalCount).isEqualTo(99)
        assertThat(pagedBerries.first).isFalse()
        assertThat(pagedBerries.last).isTrue()
        assertThat(pagedBerries.hasNext).isFalse()
        assertThat(pagedBerries.hasPrevious).isTrue()

        // Handling of empty result set
        pagedBerries = berryPersistence.getAllByUserId("user-with-no-berries", 0, 10).await().indefinitely()
        assertThat(pagedBerries.persistedBerry).isEmpty()
        assertThat(pagedBerries.totalCount).isEqualTo(0)
        assertThat(pagedBerries.first).isTrue()
        assertThat(pagedBerries.last).isTrue()
        assertThat(pagedBerries.hasNext).isFalse()
        assertThat(pagedBerries.hasPrevious).isFalse()

        // Handling of only one page
        val oneBerry = BerryPersistenceModel(
            null,
            "user-with-one-berry",
            "title 1",
            "state",
            "priority",
            "description",
            emptySet()
        )
        berryPersistence.save(oneBerry).await().indefinitely()
        pagedBerries = berryPersistence.getAllByUserId("user-with-one-berry", 0, 10).await().indefinitely()
        assertThat(pagedBerries.persistedBerry).hasSize(1)
        assertThat(pagedBerries.totalCount).isEqualTo(1)
        assertThat(pagedBerries.first).isTrue()
        assertThat(pagedBerries.last).isTrue()
        assertThat(pagedBerries.hasNext).isFalse()
        assertThat(pagedBerries.hasPrevious).isFalse()
    }

    @Test
    fun `should update existing berry`() {
        val newBerry =
            BerryPersistenceModel(null, "user", "title 1", "state", "priority", "description", setOf("tag-1", "tag-2"))
        val persistedBerry = berryPersistence.save(newBerry).await().indefinitely()

        val updatedBerry = persistedBerry.berryPersistenceModel.copy(title = "new title")
        val updatedPersistedBerry = berryPersistence.save(updatedBerry).await().indefinitely()
        assertUpdatedBerry(updatedPersistedBerry, newBerry, persistedBerry.berryPersistenceModel.id)

        val actual = berryPersistence.getById("user", persistedBerry.berryPersistenceModel.id!!).await().indefinitely()
        assertUpdatedBerry(actual, newBerry, persistedBerry.berryPersistenceModel.id)
    }

    @Test
    fun `should find berries with state`() {
        BerryPersistenceModel(null, "user", "title 1", "state-a", "priority", "description", setOf("tag-1", "tag-2"))
            .also { berryPersistence.save(it).await().indefinitely() }
        BerryPersistenceModel(null, "user", "title 1", "state-b", "priority", "description", setOf("tag-1", "tag-2"))
            .also { berryPersistence.save(it).await().indefinitely() }

        val berriesStateA = berryPersistence.getAllByUserId("user", 0, 10, "state-a").await().indefinitely()
        assertThat(berriesStateA.persistedBerry).hasSize(1)
        assertThat(berriesStateA.totalCount).isEqualTo(1)

        val berriesStateB = berryPersistence.getAllByUserId("user", 0, 10, "state-b").await().indefinitely()
        assertThat(berriesStateB.persistedBerry).hasSize(1)
        assertThat(berriesStateB.totalCount).isEqualTo(1)

        val anyState = berryPersistence.getAllByUserId("user", 0, 10, null).await().indefinitely()
        assertThat(anyState.persistedBerry).hasSize(2)
        assertThat(anyState.totalCount).isEqualTo(2)
    }

    @Test
    fun `should find berries with various filters`() {
        setupForFilterTest()

        var result = berryPersistence.getAllByUserId("user-1", 0, 10).await().indefinitely()
        assertThat(result.totalCount).isEqualTo(7)
        assertThat(result.persistedBerry).hasSize(7)

        result = berryPersistence.getAllByUserId("user-1", 0, 10, "open").await().indefinitely()
        assertThat(result.totalCount).isEqualTo(4)
        assertThat(result.persistedBerry).hasSize(4)
        assertBerryTitles(
            result,
            "open-low-no-tag-berry",
            "open-medium-no-tag-berry",
            "open-medium-tags-berry",
            "open-low-tags-berry"
        )

        result = berryPersistence.getAllByUserId("user-1", 0, 10, "closed", "medium").await().indefinitely()
        assertThat(result.totalCount).isEqualTo(2)
        assertThat(result.persistedBerry).hasSize(2)
        assertBerryTitles(result, "closed-medium-no-tag-berry", "closed-medium-tags-berry")

        result = berryPersistence.getAllByUserId("user-2", 0, 10, "closed", "medium", "tag-1").await().indefinitely()
        assertThat(result.totalCount).isEqualTo(1)
        assertThat(result.persistedBerry).hasSize(1)
        assertBerryTitles(result, "closed-medium-tags-berry")

        result = berryPersistence.getAllByUserId("user-2", 0, 10, null, null, "tag-1").await().indefinitely()
        assertThat(result.totalCount).isEqualTo(4)
        assertThat(result.persistedBerry).hasSize(4)
        assertBerryTitles(
            result,
            "closed-medium-tags-berry",
            "closed-low-tags-berry",
            "open-medium-tags-berry",
            "open-low-tags-berry"
        )

        result = berryPersistence.getAllByUserId("user-2", 0, 10, null, null, "tag-3").await().indefinitely()
        assertThat(result.totalCount).isEqualTo(1)
        assertThat(result.persistedBerry).hasSize(1)
        assertBerryTitles(
            result,
            "open-medium-tags-berry",
        )

        result = berryPersistence.getAllByUserId("user-2", 0, 10, null, "low", null).await().indefinitely()
        assertThat(result.totalCount).isEqualTo(3)
        assertThat(result.persistedBerry).hasSize(3)
        assertBerryTitles(
            result,
            "open-low-no-tag-berry",
            "closed-low-tags-berry",
            "open-low-tags-berry"
        )
    }

    private fun assertBerryTitles(pagedPersistedBerries: PagedPersistedBerries, vararg expectedTitle: String) {
        assertThat(pagedPersistedBerries.persistedBerry.map { it.berryPersistenceModel.title }).containsExactlyInAnyOrder(
            *expectedTitle
        )
    }

    private fun assertUpdatedBerry(
        updatedPersistedBerry: PersistedBerry,
        expected: BerryPersistenceModel,
        expectedId: UUID?
    ) {
        assertThat(updatedPersistedBerry).isNotNull()
        assertThat(updatedPersistedBerry.berryPersistenceModel.new).isFalse()
        assertThat(updatedPersistedBerry.berryPersistenceModel.id)
            .isNotNull()
            .isEqualTo(expectedId)
        assertThat(updatedPersistedBerry.berryPersistenceModel.title).isEqualTo("new title")
        assertThat(updatedPersistedBerry.berryPersistenceModel.tags).isEqualTo(expected.tags)
        assertThat(updatedPersistedBerry.berryPersistenceModel.state).isEqualTo(expected.state)
        assertThat(updatedPersistedBerry.berryPersistenceModel.description).isEqualTo(expected.description)
        assertThat(updatedPersistedBerry.berryPersistenceModel.userId).isEqualTo(expected.userId)
        assertThat(updatedPersistedBerry.berryAuditModel.created).isNotNull()
        assertThat(updatedPersistedBerry.berryAuditModel.updated).isNotNull()
    }

    @Test
    fun `should fail on non-existing berry`() {
        val nonExistingBerry =
            BerryPersistenceModel(
                UUID.randomUUID(),
                "user",
                "title 1",
                "state",
                "priority",
                "description",
                setOf("tag-1", "tag-2")
            )
        assertThat { berryPersistence.save(nonExistingBerry).await().indefinitely() }.isFailure()
            .hasClass(BerryNotFoundException::class)
    }

    @Test
    fun `should get all tags`() {
        BerryPersistenceModel(null, "user", "title 1", "state", "priority", "description", setOf("tag-1", "tag-2"))
            .also { berryPersistence.save(it).await().indefinitely() }
        BerryPersistenceModel(null, "user", "title 2", "state", "priority", "description", setOf("tag-1", "tag-4"))
            .also { berryPersistence.save(it).await().indefinitely() }
        BerryPersistenceModel(null, "user", "title 2", "state", "priority", "description", setOf("tag-3"))
            .also { berryPersistence.save(it).await().indefinitely() }

        val tagList = berryPersistence.getTags("user").collect().asList().await().indefinitely()
        assertThat(tagList).containsExactly("tag-1", "tag-2", "tag-3", "tag-4")

        val emptyTagList = berryPersistence.getTags("user-with-no-berries").collect().asList().await().indefinitely()
        assertThat(emptyTagList).isEmpty()
    }

    private fun setupForFilterTest() {
        val berries = mutableListOf<BerryPersistenceModel>()
        for (user in arrayOf("user-1", "user-2")) {
            berries += BerryPersistenceModel(null, user, "open-low-no-tag-berry", "open", "low", "", emptySet())
            berries += BerryPersistenceModel(null, user, "open-medium-no-tag-berry", "open", "medium", "", emptySet())
            berries += BerryPersistenceModel(
                null,
                user,
                "closed-medium-no-tag-berry",
                "closed",
                "medium",
                "",
                emptySet()
            )

            berries += BerryPersistenceModel(
                null,
                user,
                "closed-medium-tags-berry",
                "closed",
                "medium",
                "",
                setOf("tag-1", "tag-2")
            )
            berries += BerryPersistenceModel(
                null,
                user,
                "closed-low-tags-berry",
                "closed",
                "low",
                "",
                setOf("tag-1", "tag-2")
            )
            berries += BerryPersistenceModel(
                null,
                user,
                "open-medium-tags-berry",
                "open",
                "medium",
                "",
                setOf("tag-1", "tag-2", "tag-3")
            )
            berries += BerryPersistenceModel(
                null,
                user,
                "open-low-tags-berry",
                "open",
                "low",
                "",
                setOf("tag-1", "tag-2")
            )
        }

        berries.forEach {
            berryPersistence.save(it).await().indefinitely()
        }
    }
}
