package ch.guengel.memberberry.services

import ch.guengel.memberberry.domain.Berry
import javax.enterprise.context.ApplicationScoped

@ApplicationScoped
class DummyReminder : ReminderStrategy {
    override fun remind(berry: Berry) {
        println(berry)
    }
}