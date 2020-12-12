package ch.guengel.memberberry.rest

import ch.guengel.memberberry.dto.CreateUpdateFCMToken
import ch.guengel.memberberry.services.FCMTokenService
import io.quarkus.security.Authenticated
import io.smallrye.mutiny.Uni
import javax.inject.Inject
import javax.validation.Valid
import javax.ws.rs.GET
import javax.ws.rs.PUT
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.Context
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import javax.ws.rs.core.SecurityContext

@Path("/v1/me/fcm-token")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
class FCMTokenResource(@Inject private val fcmTokenService: FCMTokenService) {
    @PUT
    fun createOrUpdateFCMToken(
        @Context securityContext: SecurityContext,
        @Valid createUpdateFCMToken: CreateUpdateFCMToken
    ): Uni<Response> =
        fcmTokenService.createOrUpdateToken(createUpdateFCMToken, securityContext.userPrincipal.name)
            .onItem().ifNotNull().transform { Response.noContent() }
            .onFailure().recoverWithItem { -> Response.status(Response.Status.INTERNAL_SERVER_ERROR) }
            .onItem().transform { it.build() }

    @GET
    fun getFCMTOken(@Context securityContext: SecurityContext): Uni<Response> = fcmTokenService
        .findTokenForUser(securityContext.userPrincipal.name)
        .onItem().ifNotNull().transform { Response.ok(it) }
        .onItem().ifNull().continueWith { Response.status(Response.Status.NOT_FOUND) }
        .onFailure().recoverWithItem { -> Response.status(Response.Status.BAD_REQUEST) }
        .onItem().transform { it.build() }
}