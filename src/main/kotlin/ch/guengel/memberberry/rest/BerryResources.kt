package ch.guengel.memberberry.rest

import ch.guengel.memberberry.domain.Berry
import ch.guengel.memberberry.dto.CreateUpdateBerry

import ch.guengel.memberberry.services.BerryService
import ch.guengel.memberberry.services.Permissions
import io.quarkus.security.Authenticated
import io.smallrye.mutiny.Multi
import io.smallrye.mutiny.Uni
import java.net.URI
import javax.inject.Inject
import javax.validation.Valid
import javax.ws.rs.*
import javax.ws.rs.core.Context
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import javax.ws.rs.core.SecurityContext

@Path("/v1/berries")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
class BerryResources(@Inject private val berryService: BerryService) {

    @GET
    fun getBerries(@Context securityContext: SecurityContext): Multi<Berry> = berryService.getAll(securityContext.toPermissions())

    @GET
    @Path("{id}")
    fun getBerry(@PathParam("id") id: String, @Context securityContext: SecurityContext) = berryService
            .find(id, securityContext.toPermissions())
            .onItem().ifNotNull().transform { Response.ok(it) }
            .onItem().ifNull().continueWith { Response.status(Response.Status.NOT_FOUND) }
            .onFailure().recoverWithItem { -> Response.status(Response.Status.BAD_REQUEST) }
            .onItem().transform { it.build() }

    @POST
    fun createBerry(@Context securityContext: SecurityContext,
                    @Valid createUpdateBerry: CreateUpdateBerry): Uni<Response> = berryService
            .create(createUpdateBerry, securityContext.toPermissions())
            .onItem().transform { URI.create("/v1/berries/${it}") }
            .onItem().transform { uri -> Response.created(uri).build() }

    @PUT
    @Path("{id}")
    fun updateBerry(@Context securityContext: SecurityContext,
                    @PathParam("id") id: String,
                    @Valid createUpdateBerry: CreateUpdateBerry): Uni<Response> =
            berryService.update(id, createUpdateBerry, securityContext.toPermissions())
                    .onItem().ifNotNull().transform { result -> if (result == 0L) throw BerryUpdateException() else result }
                    .onItem().ifNotNull().transform { Response.noContent() }
                    .onItem().ifNull().continueWith { Response.status(Response.Status.NOT_FOUND) }
                    .onFailure().recoverWithItem { -> Response.status(Response.Status.BAD_REQUEST) }
                    .onItem().transform { it.build() }

    @DELETE
    @Path("{id}")
    fun deleteBerry(@PathParam("id") id: String, @Context securityContext: SecurityContext): Uni<Response> =
            berryService.deleteBerry(id, securityContext.toPermissions())
                    .onItem().ifNotNull().transform { Response.status(Response.Status.NO_CONTENT) }
                    .onItem().ifNull().continueWith { Response.status(Response.Status.NOT_FOUND) }
                    .onFailure().recoverWithItem { -> Response.status(Response.Status.BAD_REQUEST) }
                    .onItem().transform { it.build() }

    private fun SecurityContext.toPermissions() = Permissions(this.userPrincipal.name, this.isUserInRole("admin"))
}

class BerryUpdateException : RuntimeException()