package ch.guengel.memberberry.server.rest

import ch.guengel.memberberry.model.ErrorMessage
import io.smallrye.mutiny.Uni
import org.jboss.logging.Logger
import org.jboss.resteasy.reactive.RestResponse
import org.jboss.resteasy.reactive.RestResponse.Status
import org.jboss.resteasy.reactive.server.ServerExceptionMapper
import javax.enterprise.context.ApplicationScoped

typealias ErrorResponse = Uni<RestResponse<ErrorMessage>>

@ApplicationScoped
class ExceptionMapper {
    @ServerExceptionMapper
    fun mapException(ex: Exception): ErrorResponse =
        createErrorMessageUni(ex, Status.INTERNAL_SERVER_ERROR)

    @ServerExceptionMapper
    fun mapBerryUpdateException(ex: IllegalArgumentException): ErrorResponse =
        createErrorMessageUni(ex, Status.BAD_REQUEST)

    companion object {
        private val logger = Logger.getLogger(ExceptionMapper::class.java)

        fun <T : Exception> createErrorMessageUni(
            exception: T,
            httpStatusCode: Status
        ): ErrorResponse = Uni.createFrom().item(exception)
            .onItem().transform {
                logger.error(it.message, it)
                it.toErrorMessage()
            }
            .onItem().transform { errorMessage ->
                RestResponse
                    .ResponseBuilder.create<ErrorMessage>(httpStatusCode)
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
