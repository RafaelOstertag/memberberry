package ch.guengel.memberberry.dto

import ch.guengel.memberberry.domain.RememberPeriod
import javax.validation.constraints.NotEmpty

data class CreateUpdateBerry(@field:NotEmpty(message = "No Subject") val subject: String, val period: RememberPeriod)