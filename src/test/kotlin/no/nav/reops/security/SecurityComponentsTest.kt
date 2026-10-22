package no.nav.reops.security

import tools.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant
import java.util.UUID

private const val ISSUER = "https://login.microsoftonline.com/62319534-1bcb-4816-8775-3d4cb6a8a46f/v2.0"
private const val AUDIENCE = "clean-to-enriched-client-id"
private val INNBLIKK_CLIENT_ID = UUID.randomUUID().toString()
private val OTHER_CLIENT_ID = UUID.randomUUID().toString()

/**
 * Pure (non-Mockito) tests for the security validator chain and identity extraction.
 * These exercise the same validators wired into the NimbusReactiveJwtDecoder in
 * SecurityConfig, proving that an otherwise-valid token from the wrong azp or wrong
 * audience is rejected, and that expiry/issuer are enforced.
 */
class SecurityValidatorChainTest {

    private val azpValidator = AzpAllowedClientValidator(allowedClientIds(INNBLIKK_CLIENT_ID))

    private fun allowedClientIds(clientId: String): AllowedCallerClientIds =
        AllowedCallerClientIds(
            ObjectMapper(),
            """[{"name":"dev-gcp:team-researchops:reops-innblikk-frontend","clientId":"$clientId"}]""",
            "dev-gcp",
            "team-researchops",
        )

    private fun token(
        issuer: String = ISSUER,
        audience: List<String> = listOf(AUDIENCE),
        azp: String? = INNBLIKK_CLIENT_ID,
        scp: String? = "defaultaccess",
        navIdent: String? = "Z123456",
        idtyp: String? = "user",
        expiresAt: Instant = Instant.now().plusSeconds(3600),
    ): Jwt =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .issuer(issuer)
            .audience(audience)
            .apply { if (azp != null) claim("azp", azp) }
            .apply { if (scp != null) claim("scp", scp) }
            .apply { if (navIdent != null) claim("NAVident", navIdent) }
            .apply { if (idtyp != null) claim("idtyp", idtyp) }
            .issuedAt(expiresAt.minusSeconds(7200)) // always before expiry so the builder accepts it
            .expiresAt(expiresAt)
            .build()

    /** The exact validator chain wired into the decoder in SecurityConfig. */
    private fun chain(): OAuth2TokenValidator<Jwt> = managementTokenValidator(ISSUER, AUDIENCE, azpValidator)

    @Test
    fun `valid OBO token from Innblikk caller passes`() {
        assertThat(chain().validate(token()).hasErrors()).isFalse()
    }

    @Test
    fun `otherwise-valid token from wrong azp is rejected`() {
        val result = chain().validate(token(azp = OTHER_CLIENT_ID))
        assertThat(result.hasErrors()).isTrue()
    }

    @Test
    fun `valid issuer but wrong audience is rejected`() {
        val result = chain().validate(token(audience = listOf("some-other-audience")))
        assertThat(result.hasErrors()).isTrue()
    }

    @Test
    fun `wrong issuer is rejected`() {
        val result = chain().validate(token(issuer = "https://evil.example.com/v2.0"))
        assertThat(result.hasErrors()).isTrue()
    }

    @Test
    fun `expired token is rejected`() {
        val result = chain().validate(token(expiresAt = Instant.now().minusSeconds(3600)))
        assertThat(result.hasErrors()).isTrue()
    }

    @Test
    fun `token without an expiry is rejected`() {
        val withoutExpiry = Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .claims { it.putAll(token().claims.filterKeys { key -> key != "exp" }) }
            .build()
        assertThat(chain().validate(withoutExpiry).hasErrors()).isTrue()
    }

    @Test
    fun `missing azp is rejected`() {
        assertThat(chain().validate(token(azp = null)).hasErrors()).isTrue()
    }

    @Test
    fun `missing delegated scope (app-only) is rejected`() {
        assertThat(chain().validate(token(scp = null)).hasErrors()).isTrue()
    }

    @Test
    fun `azp validator fails closed when allow-list is empty`() {
        val empty = AzpAllowedClientValidator(
            AllowedCallerClientIds(ObjectMapper(), "", "dev-gcp", "team-researchops")
        )
        assertThat(empty.validate(token()).hasErrors()).isTrue()
    }

    @Test
    fun `azp validator fails closed on malformed pre-authorized-apps JSON`() {
        val malformed = AzpAllowedClientValidator(
            AllowedCallerClientIds(ObjectMapper(), "not-json", "dev-gcp", "team-researchops")
        )
        assertThat(malformed.validate(token()).hasErrors()).isTrue()
    }

    @Test
    fun `azp validator fails closed when expected caller name absent`() {
        val noMatch = AzpAllowedClientValidator(
            AllowedCallerClientIds(
                ObjectMapper(),
                """[{"name":"dev-gcp:other-namespace:reops-innblikk-frontend","clientId":"$INNBLIKK_CLIENT_ID"}]""",
                "dev-gcp",
                "team-researchops",
            )
        )
        assertThat(noMatch.validate(token()).hasErrors()).isTrue()
    }
}

class AllowedCallerClientIdsTest {

    private val mapper = ObjectMapper()

    @Test
    fun `extracts client id for exact same-cluster same-namespace caller`() {
        val id = UUID.randomUUID().toString()
        val parsed = AllowedCallerClientIds(
            mapper,
            """[{"name":"prod-gcp:team-researchops:reops-innblikk-frontend","clientId":"$id"}]""",
            "prod-gcp",
            "team-researchops",
        )
        assertThat(parsed.clientIds).containsExactly(id)
    }

    @Test
    fun `ignores other entries and picks only the expected caller`() {
        val id = UUID.randomUUID().toString()
        val parsed = AllowedCallerClientIds(
            mapper,
            """[
                {"name":"prod-gcp:team-researchops:some-other-app","clientId":"${UUID.randomUUID()}"},
                {"name":"prod-gcp:team-researchops:reops-innblikk-frontend","clientId":"$id"}
            ]""",
            "prod-gcp",
            "team-researchops",
        )
        assertThat(parsed.clientIds).containsExactly(id)
    }

    @Test
    fun `fails closed on blank json`() {
        assertThat(AllowedCallerClientIds(mapper, "", "dev-gcp", "team-researchops").clientIds).isEmpty()
        assertThat(AllowedCallerClientIds(mapper, "   ", "dev-gcp", "team-researchops").clientIds).isEmpty()
    }

    @Test
    fun `fails closed on malformed json`() {
        assertThat(AllowedCallerClientIds(mapper, "{broken", "dev-gcp", "team-researchops").clientIds).isEmpty()
    }

    @Test
    fun `fails closed when cluster or namespace missing`() {
        val json = """[{"name":"prod-gcp:team-researchops:reops-innblikk-frontend","clientId":"x"}]"""
        assertThat(AllowedCallerClientIds(mapper, json, "", "team-researchops").clientIds).isEmpty()
        assertThat(AllowedCallerClientIds(mapper, json, "prod-gcp", "").clientIds).isEmpty()
    }
}

class AuthenticatedUserTest {

    private fun jwt(claims: Map<String, Any?>): Jwt =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .apply { claims.forEach { (k, v) -> if (v != null) claim(k, v) } }
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build()

    @Test
    fun `maps valid NAVident claim to user identity`() {
        val user = jwt(mapOf("NAVident" to "z123456")).toAuthenticatedUserOrNull()
        assertThat(user).isNotNull
        assertThat(user!!.navIdent).isEqualTo("Z123456")
    }

    @Test
    fun `rejects token without NAVident — no preferred_username fallback`() {
        assertThat(jwt(mapOf("preferred_username" to "ola.nordmann@nav.no")).toAuthenticatedUserOrNull()).isNull()
    }

    @Test
    fun `rejects malformed NAVident`() {
        assertThat(jwt(mapOf("NAVident" to "not-an-ident")).toAuthenticatedUserOrNull()).isNull()
        assertThat(jwt(mapOf("NAVident" to "Z12345")).toAuthenticatedUserOrNull()).isNull()   // too short
        assertThat(jwt(mapOf("NAVident" to "1234567")).toAuthenticatedUserOrNull()).isNull()   // no leading letter
    }

    @Test
    fun `rejects machine-to-machine tokens with idtyp app`() {
        assertThat(jwt(mapOf("idtyp" to "app", "NAVident" to "Z123456")).toAuthenticatedUserOrNull()).isNull()
    }

    @Test
    fun `accepts explicit idtyp user`() {
        assertThat(jwt(mapOf("idtyp" to "user", "NAVident" to "A123456")).toAuthenticatedUserOrNull()).isNotNull
    }
}
