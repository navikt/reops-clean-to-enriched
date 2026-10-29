package no.nav.reops.security

import org.junit.jupiter.api.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtValidationException
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.config.EnableWebFlux
import org.springframework.web.reactive.function.server.RequestPredicates.GET
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.RouterFunctions.route
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.publisher.Mono
import tools.jackson.databind.ObjectMapper
import java.time.Instant

class SecurityFilterChainTest {

    private fun withClient(assertions: (WebTestClient) -> Unit) {
        AnnotationConfigApplicationContext(TestConfiguration::class.java).use { context ->
            assertions(WebTestClient.bindToApplicationContext(context).build())
        }
    }

    @Test
    fun `anonymous management request returns 401 ProblemDetail`() = withClient { client ->
        client.get().uri("/api/test").exchange()
            .expectStatus().isUnauthorized
            .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody().jsonPath("$.status").isEqualTo(401)
    }

    @Test
    fun `wrong caller token is refused by the HTTP security chain`() = withClient { client ->
        client.get().uri("/api/test").headers { it.setBearerAuth("wrong-caller") }.exchange()
            .expectStatus().isUnauthorized
            .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
    }

    @Test
    fun `wrong audience token is refused by the HTTP security chain`() = withClient { client ->
        client.get().uri("/api/test").headers { it.setBearerAuth("wrong-audience") }.exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `valid delegated caller reaches the protected handler`() = withClient { client ->
        client.get().uri("/api/test").headers { it.setBearerAuth("valid") }.exchange()
            .expectStatus().isOk
    }

    @Test
    fun `health remains available without authentication`() = withClient { client ->
        client.get().uri("/internal/health").exchange().expectStatus().isEqualTo(HttpStatus.OK)
    }

    @Configuration
    @EnableWebFlux
    @Import(SecurityConfig::class)
    open class TestConfiguration {
        @Bean
        open fun objectMapper(): ObjectMapper = ObjectMapper()

        @Bean
        open fun routes(): RouterFunction<ServerResponse> =
            route(GET("/api/test")) { ServerResponse.ok().bodyValue(mapOf("ok" to true)) }
                .andRoute(GET("/internal/health")) { ServerResponse.ok().bodyValue(mapOf("status" to "UP")) }

        // No network IdP or Mockito: production validators run through the real HTTP
        // security chain; signature/JWKS fetching remains Nimbus's production concern.
        @Bean
        open fun decoder(): ReactiveJwtDecoder {
            val issuer = "https://issuer.example.test"
            val audience = "registration-api"
            val caller = "11111111-2222-3333-4444-555555555555"
            val allowed = AllowedCallerClientIds(
                objectMapper(),
                """[{"name":"dev-gcp:team-researchops:reops-innblikk-frontend","clientId":"$caller"}]""",
                "dev-gcp",
                "team-researchops",
            )
            val validator = managementTokenValidator(issuer, audience, AzpAllowedClientValidator(allowed))
            return ReactiveJwtDecoder { raw ->
                val token = Jwt.withTokenValue(raw)
                    .header("alg", "RS256")
                    .issuer(issuer)
                    .audience(listOf(if (raw == "wrong-audience") "another-api" else audience))
                    .issuedAt(Instant.now().minusSeconds(30))
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .claim("azp", if (raw == "wrong-caller") "untrusted-caller" else caller)
                    .claim("scp", "defaultaccess")
                    .claim("NAVident", "Z123456")
                    .build()
                val result = validator.validate(token)
                if (result.hasErrors()) Mono.error(JwtValidationException("Token rejected", result.errors))
                else Mono.just(token)
            }
        }
    }
}
