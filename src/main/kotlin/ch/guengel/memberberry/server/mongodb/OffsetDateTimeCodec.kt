package ch.guengel.memberberry.server.mongodb

import org.bson.BsonReader
import org.bson.BsonType
import org.bson.BsonWriter
import org.bson.codecs.Codec
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext
import org.bson.codecs.configuration.CodecConfigurationException
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

class OffsetDateTimeCodec : Codec<OffsetDateTime> {
    override fun encode(writer: BsonWriter, value: OffsetDateTime, encoderContext: EncoderContext?) {
        writer.writeDateTime(value.toInstant().toEpochMilli())
    }

    override fun getEncoderClass(): Class<OffsetDateTime> {
        return OffsetDateTime::class.java
    }

    override fun decode(reader: BsonReader, decoderContext: DecoderContext): OffsetDateTime {
        val currentType = reader.currentBsonType
        if (currentType != BsonType.DATE_TIME) {
            throw CodecConfigurationException("Could not decode into ${encoderClass.simpleName}, expected '${BsonType.DATE_TIME}' BsonType but got '$currentType'.")
        }
        return OffsetDateTime.ofInstant(Instant.ofEpochMilli(reader.readDateTime()), ZoneOffset.UTC)
    }
}
