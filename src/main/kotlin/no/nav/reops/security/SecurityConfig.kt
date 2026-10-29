package no.nav.reops.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtClaimValidator
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.ServerAuthenticationEntryPoint
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers.pathMatchers
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import tools.jackson.databind.ObjectMapper
import java.time.Instant

/**
 * Security layout:
 *  - `/internal` (actuator health/metrics for Nais probes and Prometheus): unauthenticated.
 *  - Everything else — which after retiring the static UI is only `/api` — requires a
 *    validated Azure AD JWT. Authorization per resource (ownership/ReOps membership) is
 *    resolved independently in the management controller from Team Catalog, not the token.
 *
 * CSRF is disabled: this API is consumed exclusively server-to-server with Bearer tokens
 * (no cookie-based session exists to forge requests against).
 */
@Configuration
@EnableWebFluxSecurity
class SecurityConfig {

    @Bean
    fun securityWebFilterChain(http: ServerHttpSecurity, objectMapper: ObjectMapper): SecurityWebFilterChain {
        val entryPoint = ServerAuthenticationEntryPoint { exchange, _ ->
            securityProblem(exchange, HttpStatus.UNAUTHORIZED, objectMapper)
        }
        val accessDenied = ServerAccessDeniedHandler { exchange, _ ->
            securityProblem(exchange, HttpStatus.FORBIDDEN, objectMapper)
        }
        return http
            .csrf { it.disable() }
            .exceptionHandling { it.authenticationEntryPoint(entryPoint).accessDeniedHandler(accessDenied) }
            .authorizeExchange { exchanges ->
                exchanges
                    .matchers(pathMatchers("/internal/**")).permitAll()
                    .anyExchange().authenticated()
            }
            .oauth2ResourceServer { rs ->
                rs.authenticationEntryPoint(entryPoint).accessDeniedHandler(accessDenied).jwt { }
            }
            .build()
    }

    private fun securityProblem(
        exchange: ServerWebExchange,
        status: HttpStatus,
        objectMapper: ObjectMapper,
    ): Mono<Void> {
        val response = exchange.response
        response.statusCode = status
        response.headers.contentType = MediaType.APPLICATION_PROBLEM_JSON
        response.headers.cacheControl = "no-store"
        if (status == HttpStatus.UNAUTHORIZED) response.headers.set("WWW-Authenticate", "Bearer")
        val body = objectMapper.writeValueAsBytes(
            mapOf("type" to "about:blank", "status" to status.value(), "title" to status.reasonPhrase)
        )
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)))
    }

    /**
     * The reactive JWT decoder for the kubernetes deployment. Builds a
     * [NimbusReactiveJwtDecoder] from the issuer location (which validates the JWT
     * signature against the issuer's JWKS) and installs an explicit validator chain:
     *   - default validators (issuer `iss` + `exp`/`nbf` timestamps), then
     *   - audience (`aud` must contain this app's client id),
     *   - `azp` must be an allowed caller (the Innblikk BFF), and
     *   - a delegated user scope (`scp`) must be present.
     * The default issuer/timestamp validators are preserved, not replaced.
     */
    @Bean
    @Profile("kubernetes")
    fun reactiveJwtDecoder(
        @Value("\${spring.security.oauth2.resourceserver.jwt.issuer-uri}") issuerUri: String,
        @Value("\${spring.security.oauth2.resourceserver.jwt.audiences[0]}") audience: String,
        azpValidator: AzpAllowedClientValidator,
    ): ReactiveJwtDecoder {
        val decoder = NimbusReactiveJwtDecoder.withIssuerLocation(issuerUri).build()
        decoder.setJwtValidator(managementTokenValidator(issuerUri, audience, azpValidator))
        return decoder
    }
}

internal fun managementTokenValidator(
    issuerUri: String,
    audience: String,
    azpValidator: AzpAllowedClientValidator,
): OAuth2TokenValidator<Jwt> = DelegatingOAuth2TokenValidator(
    JwtValidators.createDefaultWithIssuer(issuerUri),
    JwtClaimValidator<List<String>>("aud") { aud -> aud != null && aud.contains(audience) },
    JwtClaimValidator<Instant>("exp") { expiresAt -> expiresAt != null },
    azpValidator,
    DelegatedScopeValidator(),
)
