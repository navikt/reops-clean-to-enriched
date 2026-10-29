package no.nav.reops.security

import org.springframework.security.oauth2.jwt.Jwt

/**
 * The authenticated Azure AD OBO user calling the website-management API. Identity is
 * derived exclusively from validated JWT claims — never from request headers or bodies.
 */
data class AuthenticatedUser(
    val navIdent: String,
)

private val NAVIDENT_PATTERN = Regex("^[A-Z][0-9]{6}$")

/**
 * Builds an [AuthenticatedUser] from a validated JWT, or returns null if the token does
 * not represent a delegated user with a real NAVident:
 *  - machine-to-machine tokens (`idtyp == "app"`) are rejected, and
 *  - the `NAVident` claim must be present and match the NAV ident format. There is no
 *    `preferred_username` fallback — a missing/invalid NAVident is a hard failure.
 */
fun Jwt.toAuthenticatedUserOrNull(): AuthenticatedUser? {
    val idtyp = getClaimAsString("idtyp")
    if (idtyp != null && idtyp != "user") return null

    val navIdent = getClaimAsString("NAVident")
        ?.uppercase()
        ?.takeIf { NAVIDENT_PATTERN.matches(it) }
        ?: return null

    return AuthenticatedUser(navIdent = navIdent)
}
