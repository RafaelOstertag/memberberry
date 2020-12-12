package ch.guengel.memberberry.domain

import org.bson.codecs.pojo.annotations.BsonCreator
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.codecs.pojo.annotations.BsonProperty
import java.time.OffsetDateTime
import java.util.*

data class Berry @BsonCreator constructor(
    @BsonId val id: UUID,
    @BsonProperty("subject") val subject: String,
    @BsonProperty("period") val period: RememberPeriod,
    @BsonProperty("userId") val userId: String,
    @BsonProperty("nextExecution") val nextExecution: OffsetDateTime,
    @BsonProperty("lastExecution") val lastExecution: OffsetDateTime
)