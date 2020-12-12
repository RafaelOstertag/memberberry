package ch.guengel.memberberry.testutils

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.utility.DockerImageName
import java.util.*

class MongoDbResource : QuarkusTestResourceLifecycleManager {
    private var mongoDBContainer = MongoDBContainer(DockerImageName.parse("mongo:4"))

    override fun start(): MutableMap<String, String> {
        mongoDBContainer.start()
        return Collections
            .singletonMap(
                "quarkus.mongodb.connection-string",
                "${mongoDBContainer.replicaSetUrl}?uuidRepresentation=standard"
            )
    }

    override fun stop() {
        mongoDBContainer.close()
    }
}