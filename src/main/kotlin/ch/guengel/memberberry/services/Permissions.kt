package ch.guengel.memberberry.services

import ch.guengel.memberberry.domain.Berry

data class Permissions(val userId: String, val admin: Boolean) {
    fun hasPermissionTo(berry: Berry) = userId == berry.userId || admin
}