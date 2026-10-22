package no.nav.reops.security

import org.slf4j.LoggerFactory
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component

/**
 * Fail-closed azp check: the only client application allowed to call this service is the
 * Innblikk BFF, which exchanges the user's token via Azure AD OBO and thus appears as
 * `azp`. The allowed client ids are resolved at runtime from the Nais-injected
 * pre-authorized apps (see [AllowedCallerClientIds]), matching the `azure.application.
 * preAuthorizedApps` in the Nais manifest — so the app allowed at the network/Azure level
 * is also verified at the token level, with no manual client-id configuration.
 */
@Component
class AzpAllowedClientValidator(
    private val allowedCallerClientIds: AllowedCallerClientIds,
) : OAuth2TokenValidator<Jwt> {

    override fun validate(token: Jwt): OAuth2TokenValidatorResult {
        val allowed = allowedCallerClientIds.clientIds
        if (allowed.isEmpty()) {
            LOG.error("Allowed-caller client id set is empty — failing closed (rejecting all tokens)")
            return OAuth2TokenValidatorResult.failure(FORBIDDEN_CALLER)
        }
        val azp = token.getClaimAsString("azp")
        if (azp != null && azp in allowed) {
            return OAuth2TokenValidatorResult.success()
        }
        LOG.warn("Rejected token: azp not in allowed caller set")
        return OAuth2TokenValidatorResult.failure(FORBIDDEN_CALLER)
    }

    private companion object {
        private val LOG = LoggerFactory.getLogger(AzpAllowedClientValidator::class.java)
        private val FORBIDDEN_CALLER = OAuth2Error(
            "invalid_token",
            "The token was not issued to an allowed caller application",
            null,
        )
    }
}
