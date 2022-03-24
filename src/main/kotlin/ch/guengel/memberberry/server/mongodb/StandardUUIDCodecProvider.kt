package ch.guengel.memberberry.server.mongodb

import org.bson.UuidRepresentation
import org.bson.codecs.Codec
import org.bson.codecs.UuidCodec
import org.bson.codecs.configuration.CodecProvider
import org.bson.codecs.configuration.CodecRegistry
import java.util.*

class StandardUUIDCodecProvider : CodecProvider {
    override fun <T : Any?> get(clazz: Class<T>, registry: CodecRegistry?): Codec<T>? =
        if (clazz.isAssignableFrom(UUID::class.java)) {
            UuidCodec(UuidRepresentation.STANDARD) as Codec<T>
        } else {
            null
        }
}
