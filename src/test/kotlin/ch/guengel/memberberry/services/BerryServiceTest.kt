package ch.guengel.memberberry.services

import ch.guengel.memberberry.domain.Berry
import ch.guengel.memberberry.domain.RememberPeriod
import ch.guengel.memberberry.dto.CreateUpdateBerry
import ch.guengel.memberberry.repositories.BerryRepository
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import io.smallrye.mutiny.Multi
import io.smallrye.mutiny.Uni
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.OffsetDateTime
import java.util.*
import java.util.stream.Collectors

@ExtendWith(MockKExtension::class)
internal class BerryServiceTest {
    private val lastExecution = OffsetDateTime.now().minusDays(1)
    private val nextExecution = lastExecution.plusDays(2)

    @MockK
    lateinit var berryRepository: BerryRepository

    @InjectMockKs
    lateinit var berryService: BerryService

    @Test
    fun `find berry as user`() {
        val berryUUID = UUID.randomUUID()
        val userUUID = UUID.randomUUID()
        val berry = Berry(berryUUID,
                "subject",
                RememberPeriod.MONTHLY,
                userUUID.toString(),
                nextExecution,
                lastExecution)

        every { berryRepository.findBerry(berryUUID) }.answers {
            Uni.createFrom().item(berry)
        }

        val permissions = Permissions(userUUID.toString(), false)
        val result = berryService.find(berryUUID.toString(), permissions).await().indefinitely()

        assertThat(result, `is`(notNullValue()))
        assertThat(result, `is`(berry))
    }

    @Test
    fun `find berry as admin`() {
        val berryUUID = UUID.randomUUID()
        val userUUID = UUID.randomUUID()
        val berry = Berry(berryUUID,
                "subject",
                RememberPeriod.MONTHLY,
                userUUID.toString(),
                nextExecution,
                lastExecution)

        every { berryRepository.findBerry(berryUUID) }.answers {
            Uni.createFrom().item(berry)
        }

        val permissions = Permissions(UUID.randomUUID().toString(), true)
        val result = berryService.find(berryUUID.toString(), permissions).await().indefinitely()

        assertThat(result, `is`(notNullValue()))
        assertThat(result, `is`(berry))
    }

    @Test
    fun `should not find other users berries`() {
        val berryUUID = UUID.randomUUID()
        val userUUID = UUID.randomUUID()
        val berry = Berry(berryUUID,
                "subject",
                RememberPeriod.MONTHLY,
                userUUID.toString(),
                nextExecution,
                lastExecution)

        every { berryRepository.findBerry(berryUUID) }.answers {
            Uni.createFrom().item(berry)
        }

        val permissions = Permissions(UUID.randomUUID().toString(), false)
        val result = berryService.find(berryUUID.toString(), permissions).await().indefinitely()

        assertThat(result, `is`(nullValue()))
    }

    @Test
    fun `create berry`() {
        val captureSlot = slot<Berry>()
        every { berryRepository.create(capture(captureSlot)) }.answers {
            val berry: Berry = arg(0)
            Uni.createFrom().item(berry.id)
        }

        val createUpdateBerry = CreateUpdateBerry("test", RememberPeriod.WEEKLY)
        val permissions = Permissions("user1", false)
        val newBerryId = berryService.create(createUpdateBerry, permissions).await().indefinitely()

        with(captureSlot.captured) {
            assertThat(newBerryId, `is`(id))
            assertThat(userId, `is`("user1"))
            assertThat(subject, `is`("test"))
            assertThat(period, `is`(RememberPeriod.WEEKLY))
        }
    }

    @Test
    fun update() {
    }

    @Test
    fun deleteBerry() {
    }

    @Test
    fun `should retrieve all items by user`() {
        val answer = Multi.createFrom().item(Berry(UUID.randomUUID(),
                "",
                RememberPeriod.DAILY,
                "",
                nextExecution,
                lastExecution))
        every { berryRepository.getAll(any()) }.answers { answer }
        every { berryRepository.getAll() }.answers { answer }

        val permissions = Permissions("user1", false)
        val allBerries = berryService.getAll(permissions).subscribe().asStream().collect(Collectors.toList())
        assertThat(allBerries, hasSize(1))

        verify { berryRepository.getAll("user1") }
        verify(exactly = 0) { berryRepository.getAll() }
    }

    @Test
    fun `should retrieve all items as admin`() {
        val answer = Multi.createFrom().item(Berry(UUID.randomUUID(),
                "",
                RememberPeriod.DAILY,
                "",
                nextExecution,
                lastExecution))
        every { berryRepository.getAll(any()) }.answers { answer }
        every { berryRepository.getAll() }.answers { answer }

        val permissions = Permissions("admin", true)
        val allBerries = berryService.getAll(permissions).subscribe().asStream().collect(Collectors.toList())
        assertThat(allBerries, hasSize(1))

        verify(exactly = 0) { berryRepository.getAll(any()) }
        verify { berryRepository.getAll() }
    }
}