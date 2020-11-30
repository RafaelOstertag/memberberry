package ch.guengel.memberberry.dto

import javax.validation.constraints.NotEmpty

data class CreateUpdateFCMToken(@field:NotEmpty(message = "No Token") val token: String)