package no.nav.reops

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

@ControllerAdvice
class ControlAdvice {

    @ExceptionHandler(Exception::class)
    fun handleGeneralException(ex: Exception): ResponseEntity<ErrorResponse> {
        LOG.error("Unexpected error", ex)

        val body = ErrorResponse(
            error = "INTERNAL_ERROR", message = "Unexpected error", status = HttpStatus.INTERNAL_SERVER_ERROR.value()
        )

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body)
    }

    private companion object {
        private val LOG = LoggerFactory.getLogger(ControlAdvice::class.java)
    }
}

data class ErrorResponse(
    val error: String, val message: String, val status: Int
)