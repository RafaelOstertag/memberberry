package ch.guengel.memberberry.health

import org.eclipse.microprofile.health.HealthCheck
import org.eclipse.microprofile.health.HealthCheckResponse
import org.eclipse.microprofile.health.Liveness
import javax.enterprise.context.ApplicationScoped

@Liveness
@ApplicationScoped
class ApplicationLiveness : HealthCheck {
    override fun call(): HealthCheckResponse {
        return HealthCheckResponse.up("Application alive")
    }
}