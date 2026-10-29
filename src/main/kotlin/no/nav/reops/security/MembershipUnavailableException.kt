package no.nav.reops.security

/**
 * Thrown when Team Catalog cannot be reached or answered unexpectedly. The caller must
 * fail closed (no team membership is assumed); the management API surfaces this as 503
 * (see GlobalExceptionHandler) so the client can retry instead of getting an incorrect
 * authorization decision.
 */
class MembershipUnavailableException(cause: Throwable? = null) :
    RuntimeException("Team membership verification unavailable", cause)
