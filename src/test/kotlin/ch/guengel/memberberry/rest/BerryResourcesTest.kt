package ch.guengel.memberberry.rest

import ch.guengel.memberberry.domain.Berry
import ch.guengel.memberberry.domain.RememberPeriod
import ch.guengel.memberberry.dto.CreateUpdateBerry
import ch.guengel.memberberry.services.BerryService
import ch.guengel.memberberry.services.Permissions
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.quarkiverse.test.junit.mockk.InjectMock
import io.quarkus.test.common.http.TestHTTPEndpoint
import io.quarkus.test.junit.QuarkusMock
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.security.TestSecurity
import io.restassured.RestAssured.given
import io.restassured.response.ValidatableResponse
import io.smallrye.mutiny.Multi
import io.smallrye.mutiny.Uni
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.Matchers.matchesPattern
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*
import javax.ws.rs.core.MediaType

@QuarkusTest
@TestHTTPEndpoint(BerryResources::class)
internal class BerryResourcesTest {
    @InjectMock
    private lateinit var berryService: BerryService

    @Test
    @TestSecurity
    fun getBerriesUnauthenticated() {
        given()
            .`when`().get()
            .then()
            .statusCode(401)
    }

    @Test
    @TestSecurity(user = "user")
    fun getBerriesAuthenticatedAsUser() {
        val berry = createBerry()

        every { berryService.getAll(any()) } returns Multi.createFrom().item(berry)

        val result = given()
            .`when`().get()
            .then()
            .statusCode(200)
        assertFirstBerry(result, berry)

        verify { berryService.getAll(Permissions("user", false)) }
    }

    private fun assertFirstBerry(actual: ValidatableResponse, expected: Berry) = actual
        .body("id[0]", `is`(expected.id.toString()))
        .body("subject[0]", `is`(expected.subject))
        .body("period[0]", `is`(expected.period.toString()))
        .body("userId[0]", `is`(expected.userId))
        .body("nextExecution[0]", `is`(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(expected.nextExecution)))
        .body("lastExecution[0]", `is`(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(expected.lastExecution)))

    @Test
    @TestSecurity(user = "admin", roles = ["admin"])
    fun getBerriesAuthenticatedAsAdmin() {
        val berry = createBerry()

        every { berryService.getAll(any()) } returns Multi.createFrom().item(berry)

        val result = given()
            .`when`().get()
            .then()
            .statusCode(200)
        assertFirstBerry(result, berry)

        verify { berryService.getAll(Permissions("admin", true)) }
    }

    private fun createBerry() = Berry(
        UUID.randomUUID(), UUID.randomUUID().toString(), RememberPeriod.WEEKLY, UUID.randomUUID().toString(),
        OffsetDateTime.now(), OffsetDateTime.now()
    )

    @Test
    fun getBerryUnauthenticated() {
        given()
            .`when`().get("/{berry}", "id")
            .then()
            .statusCode(401)
    }

    @Test
    @TestSecurity(user = "user")
    fun getBerryAuthenticatedAsUser() {
        val berry = createBerry()

        every { berryService.find(any(), any()) } returns Uni.createFrom().item(berry)

        val result = given()
            .`when`().get("/{berry}", berry.id.toString())
            .then()
            .statusCode(200)

        assertBerry(result, berry)
        verify { berryService.find(berry.id.toString(), Permissions("user", false)) }
    }

    @Test
    @TestSecurity(user = "user")
    fun getNonExistingBerryAuthenticatedAsUser() {
        every { berryService.find(any(), any()) } returns Uni.createFrom().item { null }

        given()
            .`when`().get("/{berry}", "must-not-exist")
            .then()
            .statusCode(404)
    }

    @Test
    @TestSecurity(user = "user")
    fun exceptionWhileGettingBerryAuthenticatedAsUser() {
        every { berryService.find(any(), any()) } returns Uni.createFrom().item { throw RuntimeException() }

        given()
            .`when`().get("/{berry}", "must-not-exist")
            .then()
            .statusCode(400)
    }

    @Test
    @TestSecurity(user = "admin", roles = ["admin"])
    fun getBerryAuthenticatedAsAdmin() {
        val berry = createBerry()

        every { berryService.find(any(), any()) } returns Uni.createFrom().item(berry)

        val result = given()
            .`when`().get("/{berry}", berry.id.toString())
            .then()
            .statusCode(200)

        assertBerry(result, berry)
        verify { berryService.find(berry.id.toString(), Permissions("admin", true)) }
    }

    private fun assertBerry(actual: ValidatableResponse, expected: Berry) {
        actual
            .body("id", `is`(expected.id.toString()))
            .body("subject", `is`(expected.subject))
            .body("period", `is`(expected.period.toString()))
            .body("userId", `is`(expected.userId))
            .body("nextExecution", `is`(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(expected.nextExecution)))
            .body("lastExecution", `is`(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(expected.lastExecution)))
    }

    @Test
    fun createBerryUnauthenticated() {
        given()
            .`when`()
            .contentType(MediaType.APPLICATION_JSON)
            .body(createCreateUpdateBerry())
            .post("")
            .then()
            .statusCode(401)
    }

    private fun createCreateUpdateBerry() = CreateUpdateBerry(
        UUID.randomUUID().toString(), OffsetDateTime.of(2021, 2, 20, 0, 0, 0, 0, ZoneOffset.UTC), RememberPeriod.DAILY
    )

    @Test
    @TestSecurity(user = "user")
    fun createBerryAuthenticatedAsUser() {
        val createUpdateBerry = createCreateUpdateBerry()
        val berryUUID = UUID.randomUUID()

        every { berryService.create(any(), any()) } returns Uni.createFrom().item(berryUUID)

        given()
            .`when`()
            .contentType(MediaType.APPLICATION_JSON)
            .body(createUpdateBerry)
            .post("")
            .then()
            .statusCode(201)
            .header("Location", matchesPattern("^http://localhost:[\\d]+/v1/berries/${berryUUID}\$"))

        verify { berryService.create(createUpdateBerry, Permissions("user", false)) }
    }

    @Test
    @TestSecurity(user = "admin", roles = ["admin"])
    fun createBerryAuthenticatedAsAdmin() {
        val createUpdateBerry = createCreateUpdateBerry()
        val berryUUID = UUID.randomUUID()

        every { berryService.create(any(), any()) } returns Uni.createFrom().item(berryUUID)

        given()
            .`when`()
            .contentType(MediaType.APPLICATION_JSON)
            .body(createUpdateBerry)
            .post("")
            .then()
            .statusCode(201)
            .header("Location", matchesPattern("^http://localhost:[\\d]+/v1/berries/${berryUUID}\$"))

        verify { berryService.create(createUpdateBerry, Permissions("admin", true)) }
    }

    @Test
    fun updateBerryUnauthenticated() {
        given()
            .`when`()
            .contentType(MediaType.APPLICATION_JSON)
            .body(createCreateUpdateBerry())
            .put("/{id}", "wdc")
            .then()
            .statusCode(401)
    }

    @Test
    @TestSecurity(user = "user")
    fun updateBerryAuthenticatedAsUser() {
        val berry = createCreateUpdateBerry()
        val berryId = UUID.randomUUID().toString()

        every { berryService.update(any(), any(), any()) } returns Uni.createFrom().item(1L)

        given()
            .`when`()
            .contentType(MediaType.APPLICATION_JSON)
            .body(berry)
            .put("/{id}", berryId)
            .then()
            .statusCode(204)

        verify { berryService.update(berryId, berry, Permissions("user", false)) }
    }

    @Test
    @TestSecurity(user = "user")
    fun updateNonExistingBerryAuthenticatedAsUser() {
        val berry = createCreateUpdateBerry()
        val berryId = UUID.randomUUID().toString()

        every { berryService.update(any(), any(), any()) } returns Uni.createFrom().item(0L)

        given()
            .`when`()
            .contentType(MediaType.APPLICATION_JSON)
            .body(berry)
            .put("/{id}", berryId)
            .then()
            .statusCode(404)

        verify { berryService.update(berryId, berry, Permissions("user", false)) }
    }

    @Test
    @TestSecurity(user = "user")
    fun updateBerryWithExceptionAuthenticatedAsUser() {
        val berry = createCreateUpdateBerry()
        val berryId = UUID.randomUUID().toString()

        every { berryService.update(any(), any(), any()) } returns Uni.createFrom().item { throw RuntimeException() }

        given()
            .`when`()
            .contentType(MediaType.APPLICATION_JSON)
            .body(berry)
            .put("/{id}", berryId)
            .then()
            .statusCode(400)

        verify { berryService.update(berryId, berry, Permissions("user", false)) }
    }

    @Test
    @TestSecurity(user = "admin", roles = ["admin"])
    fun updateBerryAuthenticatedAsAdmin() {
        val berry = createCreateUpdateBerry()
        val berryId = UUID.randomUUID().toString()

        every { berryService.update(any(), any(), any()) } returns Uni.createFrom().item(1L)

        given()
            .`when`()
            .contentType(MediaType.APPLICATION_JSON)
            .body(berry)
            .put("/{id}", berryId)
            .then()
            .statusCode(204)

        verify { berryService.update(berryId, berry, Permissions("admin", true)) }
    }

    @Test
    fun deleteBerryUnauthenticated() {
        given()
            .`when`()
            .contentType(MediaType.APPLICATION_JSON)
            .delete("/{id}", "wdc")
            .then()
            .statusCode(401)
    }

    @Test
    @TestSecurity(user = "user")
    fun deleteBerryAuthenticatedAsUser() {
        val berryId = UUID.randomUUID().toString()
        every { berryService.deleteBerry(any(), any()) } returns Uni.createFrom().item(1L)

        given()
            .`when`()
            .contentType(MediaType.APPLICATION_JSON)
            .delete("/{id}", berryId)
            .then()
            .statusCode(204)

        verify { berryService.deleteBerry(berryId, Permissions("user", false)) }
    }

    @Test
    @TestSecurity(user = "user")
    fun deleteNonExistingBerryAuthenticatedAsUser() {
        val berryId = UUID.randomUUID().toString()
        every { berryService.deleteBerry(any(), any()) } returns Uni.createFrom().item { null }

        given()
            .`when`()
            .contentType(MediaType.APPLICATION_JSON)
            .delete("/{id}", berryId)
            .then()
            .statusCode(404)

        verify { berryService.deleteBerry(berryId, Permissions("user", false)) }
    }

    @Test
    @TestSecurity(user = "user")
    fun deleteBerryWithExceptionAuthenticatedAsUser() {
        val berryId = UUID.randomUUID().toString()
        every { berryService.deleteBerry(any(), any()) } returns Uni.createFrom().item { throw RuntimeException() }

        given()
            .`when`()
            .contentType(MediaType.APPLICATION_JSON)
            .delete("/{id}", berryId)
            .then()
            .statusCode(400)

        verify { berryService.deleteBerry(berryId, Permissions("user", false)) }
    }

    @Test
    @TestSecurity(user = "admin", roles = ["admin"])
    fun deleteBerryAuthenticatedAsAdmin() {
        val berryId = UUID.randomUUID().toString()
        every { berryService.deleteBerry(any(), any()) } returns Uni.createFrom().item(1L)

        given()
            .`when`()
            .contentType(MediaType.APPLICATION_JSON)
            .delete("/{id}", berryId)
            .then()
            .statusCode(204)

        verify { berryService.deleteBerry(berryId, Permissions("admin", true)) }
    }

    companion object {
        @BeforeAll
        @JvmStatic
        fun setup() {
            val berryServiceMock = mockk<BerryService>()
            QuarkusMock.installMockForType(berryServiceMock, BerryService::class.java)
        }
    }
}
