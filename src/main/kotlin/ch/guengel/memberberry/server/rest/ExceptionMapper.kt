package ch.guengel.memberberry.server.rest

import ch.guengel.memberberry.model.ErrorMessage
import io.smallrye.mutiny.Uni
import org.jboss.logging.Logger
import org.jboss.resteasy.reactive.RestResponse
import org.jboss.resteasy.reactive.RestResponse.Status
import org.jboss.resteasy.reactive.server.ServerExceptionMapper
import javax.enterprise.context.ApplicationScoped
import javax.ws.rs.WebApplicationException

typealias ErrorResponse = Uni<RestResponse<ErrorMessage>>

@ApplicationScoped
class ExceptionMapper {
    @ServerExceptionMapper
    fun mapException(ex: Exception): ErrorResponse =
        createErrorMessageUni(ex, Status.INTERNAL_SERVER_ERROR)

    @ServerExceptionMapper
    fun mapIllegalArgumentException(ex: IllegalArgumentException): ErrorResponse =
        createErrorMessageUni(ex, Status.BAD_REQUEST)

    @ServerExceptionMapper
    fun mapWebApplicationException(ex: WebApplicationException): ErrorResponse =
        createErrorMessageUni(ex, ex.response.status)

    companion object {
        private val logger = Logger.getLogger(ExceptionMapper::class.java)

        fun <T : Exception> createErrorMessageUni(
            exception: T,
            httpStatusCode: Status
        ): ErrorResponse = createErrorMessageUni(exception, httpStatusCode.statusCode)

        fun <T : Exception> createErrorMessageUni(exception: T, statusCode: Int) = Uni.createFrom().item(exception)
            .onItem().transform {
                logger.error(it.message, it)
                it.toErrorMessage()
            }
            .onItem().transform { errorMessage ->
                RestResponse
                    .ResponseBuilder.create<ErrorMessage>(statusCode)
                    .entity(errorMessage)
                    .build()
            }
    }
}

fun Exception.toErrorMessage(): ErrorMessage {
    val errorMessage = ErrorMessage()
    errorMessage.reason = message
    errorMessage.type = this::class.simpleName
    return errorMessage
}
