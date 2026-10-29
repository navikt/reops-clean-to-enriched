package no.nav.reops.security

import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt

/**
 * Requires the token to carry a delegated user scope (`scp` claim). The `scp` claim only
 * appears in Azure AD on-behalf-of tokens, never in app-only (client-credentials) tokens
 * — so requiring a non-empty `scp` rejects machine-to-machine callers even if one were
 * pre-authorized, and confirms a delegated user context.
 */
class DelegatedScopeValidator : OAuth2TokenValidator<Jwt> {
    override fun validate(token: Jwt): OAuth2TokenValidatorResult {
        val scp = token.getClaimAsString("scp")
        return if (!scp.isNullOrBlank()) {
            OAuth2TokenValidatorResult.success()
        } else {
            OAuth2TokenValidatorResult.failure(MISSING_DELEGATED_SCOPE)
        }
    }

    private companion object {
        private val MISSING_DELEGATED_SCOPE = OAuth2Error(
            "invalid_token",
            "The token has no delegated user scope (scp) — only on-behalf-of user tokens are accepted",
            null,
        )
    }
}
