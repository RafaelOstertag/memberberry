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

        val getArguments = getArguments {
            userId = "user"
        }
        val berriesOfUser = berryPersistence.getAllByUserId(getArguments).await().indefinitely()
        assertThat(berriesOfUser.persistedBerry).hasSize(2)
        assertThat(berriesOfUser.totalCount).isEqualTo(2)

        getArguments.userId = "other-user"
        val berriesOfOtherUser =
            berryPersistence.getAllByUserId(getArguments).await().indefinitely()
        assertThat(berriesOfOtherUser.persistedBerry).hasSize(1)
        assertThat(berriesOfOtherUser.totalCount).isEqualTo(1)


        getArguments.userId = "user-with-no-berries"
        val noBerries = berryPersistence.getAllByUserId(getArguments).await().indefinitely()
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


        (0 until 10).forEach { currentPageIndex ->
            val getArguments = getArguments {
                userId = "user"
                pagination {
                    index = currentPageIndex
                    size = 10
                }
                ordering {
                    orderBy = OrderBy.TITLE
                    order = Order.ASCENDING
                }
            }
            val pagedBerries = berryPersistence.getAllByUserId(getArguments).await().indefinitely()
            val numberOfBerriesInPage = if (currentPageIndex < 9) 10 else 9
            assertThat(pagedBerries.persistedBerry).hasSize(numberOfBerriesInPage)
            val firstPage = currentPageIndex == 0
            assertThat(pagedBerries.first).isEqualTo(firstPage)
            val lastPage = currentPageIndex == 9
            assertThat(pagedBerries.last).isEqualTo(lastPage)
            assertThat(pagedBerries.hasPrevious).isEqualTo(!firstPage)
            assertThat(pagedBerries.hasNext).isEqualTo(!lastPage)
            assertThat(pagedBerries.totalCount).isEqualTo(99)
            (0 until numberOfBerriesInPage).forEach { berryIndex ->
                val berryNumber = (currentPageIndex * 10 + berryIndex).toString().padStart(3, '0')
                assertThat(pagedBerries.persistedBerry[berryIndex].berryPersistenceModel.title).isEqualTo(
                    "title $berryNumber"
                )
            }
        }

        // Page index greater than available pages
        var getArguments = getArguments {
            userId = "user"
            pagination {
                index = 100
                size = 10
            }
        }
        var pagedBerries = berryPersistence.getAllByUserId(getArguments).await().indefinitely()
        assertThat(pagedBerries.persistedBerry).isEmpty()
        assertThat(pagedBerries.totalCount).isEqualTo(99)
        assertThat(pagedBerries.first).isFalse()
        assertThat(pagedBerries.last).isTrue()
        assertThat(pagedBerries.hasNext).isFalse()
        assertThat(pagedBerries.hasPrevious).isTrue()

        // Handling of empty result set
        getArguments = getArguments {
            userId = "user-with-no-berries"
            pagination {
                index = 0
                size = 10
            }
        }
        pagedBerries = berryPersistence.getAllByUserId(getArguments).await().indefinitely()
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

        getArguments = getArguments {
            userId = "user-with-one-berry"
            pagination {
                index = 0
                size = 10
            }
        }
        pagedBerries = berryPersistence.getAllByUserId(getArguments).await().indefinitely()
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

        val getArguments = getArguments {
            userId = "user"
            pagination {
                index = 0
                size = 10
            }
            inState = "state-a"
        }
        val berriesStateA = berryPersistence.getAllByUserId(getArguments).await().indefinitely()
        assertThat(berriesStateA.persistedBerry).hasSize(1)
        assertThat(berriesStateA.totalCount).isEqualTo(1)

        getArguments.inState = "state-b"
        val berriesStateB = berryPersistence.getAllByUserId(getArguments).await().indefinitely()
        assertThat(berriesStateB.persistedBerry).hasSize(1)
        assertThat(berriesStateB.totalCount).isEqualTo(1)

        getArguments.inState = ""
        val anyState = berryPersistence.getAllByUserId(getArguments).await().indefinitely()
        assertThat(anyState.persistedBerry).hasSize(2)
        assertThat(anyState.totalCount).isEqualTo(2)
    }

    @Test
    fun `should find berries with various filters`() {
        setupForFilterTest()

        val getArguments = getArguments {
            userId = "user-1"
        }
        var result = berryPersistence.getAllByUserId(getArguments).await().indefinitely()
        assertThat(result.totalCount).isEqualTo(7)
        assertThat(result.persistedBerry).hasSize(7)

        getArguments.inState = "open"
        result = berryPersistence.getAllByUserId(getArguments).await().indefinitely()
        assertThat(result.totalCount).isEqualTo(4)
        assertThat(result.persistedBerry).hasSize(4)
        assertBerryTitles(
            result,
            "open-low-no-tag-berry",
            "open-medium-no-tag-berry",
            "open-medium-tags-berry",
            "open-low-tags-berry"
        )

        getArguments.inState = "closed"
        getArguments.withPriority = "medium"
        result = berryPersistence.getAllByUserId(getArguments).await().indefinitely()
        assertThat(result.totalCount).isEqualTo(2)
        assertThat(result.persistedBerry).hasSize(2)
        assertBerryTitles(result, "closed-medium-no-tag-berry", "closed-medium-tags-berry")

        getArguments.userId = "user-2"
        getArguments.withTag = "tag-1"
        result = berryPersistence.getAllByUserId(getArguments).await().indefinitely()
        assertThat(result.totalCount).isEqualTo(1)
        assertThat(result.persistedBerry).hasSize(1)
        assertBerryTitles(result, "closed-medium-tags-berry")

        getArguments.withPriority = ""
        getArguments.inState = ""
        result = berryPersistence.getAllByUserId(getArguments).await().indefinitely()
        assertThat(result.totalCount).isEqualTo(4)
        assertThat(result.persistedBerry).hasSize(4)
        assertBerryTitles(
            result,
            "closed-medium-tags-berry",
            "closed-low-tags-berry",
            "open-medium-tags-berry",
            "open-low-tags-berry"
        )

        getArguments.withTag = "tag-3"
        result = berryPersistence.getAllByUserId(getArguments).await().indefinitely()
        assertThat(result.totalCount).isEqualTo(1)
        assertThat(result.persistedBerry).hasSize(1)
        assertBerryTitles(
            result,
            "open-medium-tags-berry",
        )

        getArguments.inState = ""
        getArguments.withPriority = "low"
        getArguments.withTag = ""
        result = berryPersistence.getAllByUserId(getArguments).await().indefinitely()
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

    @Test
    fun `should order correctly`() {
        BerryPersistenceModel(null, "user", "title b", "state-b", "priority-b", "description", emptySet())
            .also { berryPersistence.save(it).await().indefinitely() }
        BerryPersistenceModel(null, "user", "title a", "state-a", "priority-a", "description", emptySet())
            .also { berryPersistence.save(it).await().indefinitely() }
        BerryPersistenceModel(null, "user", "title c", "state-c", "priority-c", "description", emptySet())
            .also { berryPersistence.save(it).await().indefinitely() }

        val getArguments = getArguments {
            userId = "user"
            ordering {
                orderBy = OrderBy.CREATED
                order = Order.ASCENDING
            }
        }
        var berries = berryPersistence.getAllByUserId(getArguments).await().indefinitely()
        var titles = berries.persistedBerry.map { it -> it.berryPersistenceModel.title }
        assertThat(titles).containsExactly("title b", "title a", "title c")

        getArguments.ordering.order = Order.DESCENDING
        berries = berryPersistence.getAllByUserId(getArguments).await().indefinitely()
        titles = berries.persistedBerry.map { it -> it.berryPersistenceModel.title }
        assertThat(titles).containsExactly("title c", "title a", "title b")

        getArguments.ordering.order = Order.ASCENDING
        getArguments.ordering.orderBy = OrderBy.TITLE
        berries = berryPersistence.getAllByUserId(getArguments).await().indefinitely()
        titles = berries.persistedBerry.map { it -> it.berryPersistenceModel.title }
        assertThat(titles).containsExactly("title a", "title b", "title c")

        getArguments.ordering.order = Order.DESCENDING
        getArguments.ordering.orderBy = OrderBy.TITLE
        berries = berryPersistence.getAllByUserId(getArguments).await().indefinitely()
        titles = berries.persistedBerry.map { it -> it.berryPersistenceModel.title }
        assertThat(titles).containsExactly("title c", "title b", "title a")

        getArguments.ordering.order = Order.ASCENDING
        getArguments.ordering.orderBy = OrderBy.PRIORITY
        berries = berryPersistence.getAllByUserId(getArguments).await().indefinitely()
        titles = berries.persistedBerry.map { it -> it.berryPersistenceModel.title }
        assertThat(titles).containsExactly("title a", "title b", "title c")

        getArguments.ordering.order = Order.DESCENDING
        getArguments.ordering.orderBy = OrderBy.PRIORITY
        berries = berryPersistence.getAllByUserId(getArguments).await().indefinitely()
        titles = berries.persistedBerry.map { it -> it.berryPersistenceModel.title }
        assertThat(titles).containsExactly("title c", "title b", "title a")

        getArguments.ordering.order = Order.ASCENDING
        getArguments.ordering.orderBy = OrderBy.STATE
        berries = berryPersistence.getAllByUserId(getArguments).await().indefinitely()
        titles = berries.persistedBerry.map { it -> it.berryPersistenceModel.title }
        assertThat(titles).containsExactly("title a", "title b", "title c")

        getArguments.ordering.order = Order.DESCENDING
        getArguments.ordering.orderBy = OrderBy.STATE
        berries = berryPersistence.getAllByUserId(getArguments).await().indefinitely()
        titles = berries.persistedBerry.map { it -> it.berryPersistenceModel.title }
        assertThat(titles).containsExactly("title c", "title b", "title a")
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
