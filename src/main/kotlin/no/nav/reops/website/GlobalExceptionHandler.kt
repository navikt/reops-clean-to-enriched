package no.nav.reops.website

import no.nav.reops.security.MembershipUnavailableException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.ErrorResponseException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.support.WebExchangeBindException
import org.springframework.web.server.ServerWebInputException

/**
 * Consistent ProblemDetail (RFC 7807) error bodies for the management API:
 * 400 validation/confirmation, 404 missing or hidden resource, 409 editing a deleted
 * registration or a lost-update optimistic-lock conflict, 503 membership lookup
 * unavailable. ErrorResponseException instances thrown by controllers/services already
 * carry their status and are rendered as ProblemDetail by Spring; the handlers below
 * cover the remaining exception shapes so no unhandled stacktrace leaks.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(WebsiteNotFoundException::class)
    fun handleNotFound(ex: WebsiteNotFoundException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.message)

    @ExceptionHandler(WebsiteConflictException::class)
    fun handleConflict(ex: WebsiteConflictException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.message)

    @ExceptionHandler(OptimisticLockingFailureException::class)
    fun handleOptimisticLockConflict(ex: OptimisticLockingFailureException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Registreringen er endret. Last inn siden på nytt.")

    @ExceptionHandler(MembershipUnavailableException::class)
    fun handleMembershipUnavailable(ex: MembershipUnavailableException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Team membership verification unavailable")

    @ExceptionHandler(WebExchangeBindException::class, ServerWebInputException::class)
    fun handleBadRequest(ex: Exception): ProblemDetail {
        val detail = when (ex) {
            is WebExchangeBindException ->
                ex.fieldErrors.joinToString("; ") { "${it.field}: ${it.defaultMessage ?: "invalid"}" }
                    .ifBlank { "Validation failed" }
            else -> "Invalid request"
        }
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail)
    }

    @ExceptionHandler(ErrorResponseException::class)
    fun handleErrorResponse(ex: ErrorResponseException): ProblemDetail = ex.body
}
