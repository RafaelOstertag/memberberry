package ch.guengel.memberberry.services

import ch.guengel.memberberry.domain.Berry

interface ReminderStrategy {
    fun remind(berry: Berry)
}