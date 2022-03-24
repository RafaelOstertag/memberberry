package ch.guengel.memberberry.server.mongodb

import org.bson.codecs.Codec
import org.bson.codecs.configuration.CodecProvider
import org.bson.codecs.configuration.CodecRegistry
import java.time.OffsetDateTime

class OffsetDateTimeCodecProvider : CodecProvider {
    override fun <T : Any?> get(clazz: Class<T>, registry: CodecRegistry?): Codec<T>? =
        if (clazz.isAssignableFrom(OffsetDateTime::class.java)) {
            OffsetDateTimeCodec() as Codec<T>
        } else {
            null
        }
}
