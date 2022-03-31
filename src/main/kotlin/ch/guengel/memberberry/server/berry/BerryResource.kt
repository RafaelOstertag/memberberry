package ch.guengel.memberberry.server.berry

import ch.guengel.memberberry.api.BerryV1Api
import ch.guengel.memberberry.model.Berry
import ch.guengel.memberberry.model.BerryWithId
import io.smallrye.mutiny.Uni
import org.eclipse.microprofile.jwt.JsonWebToken
import org.jboss.resteasy.reactive.RestResponse
import java.net.URI
import java.util.*
import javax.annotation.security.RolesAllowed
import javax.inject.Inject

@RolesAllowed("user")
class BerryResource(private val berryService: BerryService) : BerryV1Api {
    @Inject
    lateinit var jwt: JsonWebToken

    override fun createBerry(berry: Berry): Uni<RestResponse<Void>> = Uni.combine().all()
        .unis(createUniFrom(jwt.name), createUniFrom(berry)).asTuple()
        .onItem().transformToUni { tuple ->
            berryService.createBerry(tuple.item1, tuple.item2)
        }
        .onItem().transform { newBerryId ->
            RestResponse.ResponseBuilder.created<Void>(URI.create(newBerryId.toString()))
                .build()
        }

    private fun <T> createUniFrom(item: T): Uni<T> = Uni.createFrom().item(item)

    override fun deleteBerry(berryId: UUID): Uni<RestResponse<Void>> = Uni.combine().all()
        .unis(createUniFrom(jwt.name), createUniFrom(berryId))
        .asTuple()
        .onItem().transformToUni { tuple ->
            berryService.deleteBerry(tuple.item1, tuple.item2)
        }
        .onItem().transform {
            RestResponse.ResponseBuilder.noContent<Void>().build()
        }

    override fun getAllTags(): Uni<RestResponse<List<String>>> = createUniFrom(jwt.name)
        .onItem().transformToMulti { userId ->
            berryService.getAllTags(userId)
        }
        .collect().asList()
        .onItem().transform { tags ->
            RestResponse.ResponseBuilder.ok(tags).build()
        }

    override fun getBerries(
        pageSize: Int?,
        pageIndex: Int?,
        berryState: String?,
        berryPriority: String?,
        berryTag: String?
    ): Uni<RestResponse<List<BerryWithId>>> =
        berryService.getBerries(
            getArguments {
                userId = jwt.name
                pagination {
                    index = pageIndex ?: 0
                    size = pageSize ?: 25
                }
                inState = berryState ?: ""
                withPriority = berryPriority ?: ""
                withTag = berryTag ?: ""
            })
            .onItem().transform { pagedBerriesResult ->
                val responseBuilder = RestResponse.ResponseBuilder.ok(pagedBerriesResult.berriesWithId)
                    .header("x-page-size", pagedBerriesResult.pageSize)
                    .header("x-page-index", pagedBerriesResult.pageIndex)
                    .header("x-first-page", pagedBerriesResult.firstPage)
                    .header("x-last-page", pagedBerriesResult.lastPage)
                    .header("x-total-pages", pagedBerriesResult.totalPages)
                    .header("x-total-entries", pagedBerriesResult.totalEntries)

                if (pagedBerriesResult.previousPageIndex != null) {
                    responseBuilder.header("x-previous-page-index", pagedBerriesResult.previousPageIndex)
                }
                if (pagedBerriesResult.nextPageIndex != null) {
                    responseBuilder.header("x-next-page-index", pagedBerriesResult.nextPageIndex)
                }

                responseBuilder.build()
            }

    override fun getBerry(berryId: UUID): Uni<RestResponse<BerryWithId>> = Uni.combine().all()
        .unis(createUniFrom(jwt.name), createUniFrom(berryId))
        .asTuple()
        .onItem().transformToUni { tuple ->
            berryService.getBerry(tuple.item1, tuple.item2)
        }
        .onItem().transform { berryWithId ->
            RestResponse.ResponseBuilder.ok(berryWithId).build()
        }

    override fun updateBerry(berryId: UUID, berry: Berry): Uni<RestResponse<Void>> = Uni.combine().all()
        .unis(createUniFrom(jwt.name), createUniFrom(berryId), createUniFrom(berry))
        .asTuple()
        .onItem().transformToUni { tuple ->
            berryService.updateBerry(tuple.item1, tuple.item2, tuple.item3)
        }
        .onItem().transform {
            RestResponse.ResponseBuilder.noContent<Void>().build()
        }
}
