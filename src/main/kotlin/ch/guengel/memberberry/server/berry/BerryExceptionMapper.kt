package ch.guengel.memberberry.server.berry

import ch.guengel.memberberry.server.rest.ErrorResponse
import ch.guengel.memberberry.server.rest.ExceptionMapper.Companion.createErrorMessageUni
import org.jboss.resteasy.reactive.RestResponse
import org.jboss.resteasy.reactive.server.ServerExceptionMapper
import javax.enterprise.context.ApplicationScoped

@ApplicationScoped
class BerryExceptionMapper {
    @ServerExceptionMapper
    fun mapPersistenceException(ex: BerryPersistenceException): ErrorResponse =
        createErrorMessageUni(ex, RestResponse.Status.INTERNAL_SERVER_ERROR)

    @ServerExceptionMapper
    fun mapBerryNotFoundException(ex: BerryNotFoundException): ErrorResponse =
        createErrorMessageUni(ex, RestResponse.Status.NOT_FOUND)

    @ServerExceptionMapper
    fun mapBerryUpdateException(ex: BerryUpdateException): ErrorResponse =
        createErrorMessageUni(ex, RestResponse.Status.INTERNAL_SERVER_ERROR)

    @ServerExceptionMapper
    fun mapPageIndexOutOfRange(ex: PageIndexOutOfRange): ErrorResponse =
        createErrorMessageUni(ex, RestResponse.Status.NOT_FOUND)
}
