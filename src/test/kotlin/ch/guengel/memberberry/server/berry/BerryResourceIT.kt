package ch.guengel.memberberry.server.berry

import assertk.assertThat
import assertk.assertions.isNotNull
import ch.guengel.memberberry.model.Berry
import ch.guengel.memberberry.model.BerryPriority
import ch.guengel.memberberry.model.BerryState
import io.quarkus.test.common.http.TestHTTPEndpoint
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.security.TestSecurity
import io.restassured.http.ContentType
import io.restassured.module.kotlin.extensions.Extract
import io.restassured.module.kotlin.extensions.Given
import io.restassured.module.kotlin.extensions.Then
import io.restassured.module.kotlin.extensions.When
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.nullValue
import org.hamcrest.core.Is.`is`
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.*

@QuarkusTest
internal class BerryResourceIT {
    @Nested
    @DisplayName("Unauthenticated")
    @TestHTTPEndpoint(BerryResource::class)
    inner class Unauthenticated {
        @Test
        fun `should not allow getting berries`() {
            When {
                get()
            } Then {
                statusCode(401)
            }
        }

        @Test
        fun `should not allow deleting berry`() {
            When {
                delete("/{id}", UUID.randomUUID().toString())
            } Then {
                statusCode(401)
            }
        }

        @Test
        fun `should not allow getting tags`() {
            When {
                get("/tags")
            } Then {
                statusCode(401)
            }
        }

        @Test
        fun `should not allow getting a berry`() {
            When {
                get("/{id}", UUID.randomUUID().toString())
            } Then {
                statusCode(401)
            }
        }

        @Test
        fun `should not allow creating berry`() {
            Given {
                contentType(ContentType.JSON)
            } When {
                post()
            } Then {
                statusCode(401)
            }
        }

        @Test
        fun `should not allow updating berry`() {
            When {
                put("/{id}", UUID.randomUUID().toString())
            } Then {
                statusCode(401)
            }
        }
    }

    @Nested
    @DisplayName("Authenticated")
    @TestHTTPEndpoint(BerryResource::class)
    inner class Authenticated {
        @Test
        @TestSecurity(user = "a-user", roles = ["user"])
        fun `should allow getting berries`() {
            When {
                get()
            } Then {
                statusCode(200)
            }
        }

        @Test
        @TestSecurity(user = "a-user", roles = ["user"])
        fun `should create berry`() {
            val berry = createBerry()

            val location = Given {
                contentType(ContentType.JSON)
                body(berry)
            } When {
                post()
            } Then {
                statusCode(201)
            } Extract {
                header("Location")
            }

            assertThat(location).isNotNull()

            When {
                get("/{id}", location)
            } Then {
                statusCode(200)
            }
        }

        private fun createBerry(): Berry {
            val berry = Berry()
            berry.tags(setOf("tag1", "tag2"))
            berry.priority(BerryPriority.HIGH)
            berry.title("first berry")
            berry.state(BerryState.OPEN)
            berry.description("A description")
            return berry
        }

        @Test
        @TestSecurity(user = "a-user", roles = ["user"])
        fun `should update berry`() {
            val berry = createBerry()

            val location = Given {
                contentType(ContentType.JSON)
                body(berry)
            } When {
                post()
            } Then {
                statusCode(201)
            } Extract {
                header("Location")
            }

            assertThat(location).isNotNull()

            When {
                get("/{id}", location)
            } Then {
                statusCode(200)
                body("title", equalTo("first berry"))
                body("state", equalTo("open"))
            }


            berry.state(BerryState.CLOSED)
            berry.title("new title")

            Given {
                contentType(ContentType.JSON)
                body(berry)
            } When {
                put("/{id}", location)
            } Then {
                statusCode(204)
            }

            When {
                get("/{id}", location)
            } Then {
                statusCode(200)
                body("title", equalTo("new title"))
                body("state", equalTo("closed"))
            }
        }

        @Test
        @TestSecurity(user = "a-user", roles = ["user"])
        fun `should fail updating non-existing berry`() {
            val berry = createBerry()

            Given {
                contentType(ContentType.JSON)
                body(berry)
            } When {
                put("/{id}", UUID.randomUUID().toString())
            } Then {
                statusCode(404)
            }
        }

        @Test
        @TestSecurity(user = "b-user", roles = ["user"])
        fun `should delete berry`() {
            val berry = createBerry()

            val location = Given {
                contentType(ContentType.JSON)
                body(berry)
            } When {
                post()
            } Then {
                statusCode(201)
            } Extract {
                header("Location")
            }

            assertThat(location).isNotNull()

            When {
                get("/{id}", location)
            } Then {
                statusCode(200)
            }

            When {
                delete("/{id}", location)
            } Then {
                statusCode(204)
            }

            When {
                get("/{id}", location)
            } Then {
                statusCode(404)
            }
        }

        @Test
        @TestSecurity(user = "b-user", roles = ["user"])
        fun `should get tags`() {
            val berry = createBerry()

            Given {
                contentType(ContentType.JSON)
                body(berry)
            } When {
                post()
            } Then {
                statusCode(201)
            }

            When {
                get("/tags")
            } Then {
                statusCode(200)
                assertThat().body("size()", `is`(2))
            }
        }

        @Test
        @TestSecurity(user = "c-user", roles = ["user"])
        fun `should apply filters`() {
            val berry = createBerry()

            Given {
                contentType(ContentType.JSON)
                body(berry)
            } When {
                post()
            } Then {
                statusCode(201)
            }

            Given {
                queryParam("berry-state", "open")
                queryParam("berry-priority", "high")
                queryParam("berry-tag", "tag2")
            } When {
                get()
            } Then {
                statusCode(200)
                assertThat().body("size()", `is`(1))
            }

            Given {
                queryParam("berry-priority", "high")
                queryParam("berry-tag", "tag2")
            } When {
                get()
            } Then {
                statusCode(200)
                assertThat().body("size()", `is`(1))
            }

            Given {
                queryParam("berry-state", "open")
                queryParam("berry-tag", "tag2")
            } When {
                get()
            } Then {
                statusCode(200)
                assertThat().body("size()", `is`(1))
            }

            Given {
                queryParam("berry-state", "open")
                queryParam("berry-priority", "high")
            } When {
                get()
            } Then {
                statusCode(200)
                assertThat().body("size()", `is`(1))
            }

            Given {
                queryParam("berry-state", "closed")
                queryParam("berry-priority", "high")
                queryParam("berry-tag", "tag2")
            } When {
                get()
            } Then {
                statusCode(200)
                assertThat().body("size()", `is`(0))
            }

            Given {
                queryParam("berry-state", "open")
                queryParam("berry-priority", "medium")
                queryParam("berry-tag", "tag2")
            } When {
                get()
            } Then {
                statusCode(200)
                assertThat().body("size()", `is`(0))
            }

            Given {
                queryParam("berry-state", "open")
                queryParam("berry-priority", "high")
                queryParam("berry-tag", "another-tag")
            } When {
                get()
            } Then {
                statusCode(200)
                assertThat().body("size()", `is`(0))
            }
        }

        @Test
        @TestSecurity(user = "d-user", roles = ["user"])
        fun `should set headers when getting berries`() {
            val berry = createBerry()

            Given {
                contentType(ContentType.JSON)
                body(berry)
            } When {
                post()
            } Then {
                statusCode(201)
            }

            When {
                get()
            } Then {
                statusCode(200)
                header("x-page-size", "25")
                header("x-page-index", "0")
                header("x-previous-page-index", nullValue())
                header("x-next-page-index", nullValue())
                header("x-first-page", "true")
                header("x-last-page", "true")
                header("x-total-pages", "1")
                header("x-total-entries", "1")
            }
        }
    }
}
