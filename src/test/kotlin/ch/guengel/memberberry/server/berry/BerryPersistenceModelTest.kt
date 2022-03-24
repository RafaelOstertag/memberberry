package ch.guengel.memberberry.server.berry

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test
import java.util.*

internal class BerryPersistenceModelTest {

    @Test
    fun shouldIndicateNewBerry() {
        val newBerryPersistenceModel = BerryPersistenceModel(null, "", "", "", "", "", emptySet())
        assertThat(newBerryPersistenceModel.new).isTrue()

        val existingBerryPersistenceModel = BerryPersistenceModel(UUID.randomUUID(), "", "", "", "", "", emptySet())
        assertThat(existingBerryPersistenceModel.new).isFalse()
    }
}
