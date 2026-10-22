package no.nav.reops.security

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.UUID

data class TeamCatalogTeam(
    val id: UUID,
    val name: String,
)

private data class MembershipResponse(
    val teams: List<TeamCatalogTeam>? = null,
)

/**
 * Membership lookup against Team Catalog (https://github.com/navikt/team-catalog).
 * Team/member data is openly readable within Nav — no token is attached. Results are
 * cached briefly (see CacheConfiguration, "teamMemberships") since membership changes
 * rarely. Any failure throws [MembershipUnavailableException]; callers must fail closed
 * and never assume membership when the lookup fails.
 */
@Service
class TeamCatalogService(
    webClientBuilder: WebClient.Builder,
    @Value("\${app.team-catalog.base-url}") baseUrl: String,
) {
    private val webClient = webClientBuilder
        .baseUrl(baseUrl)
        .codecs { it.defaultCodecs().maxInMemorySize(256 * 1024) }
        .build()

    @Cacheable("teamMemberships")
    fun getTeamsForUser(navIdent: String): Mono<List<TeamCatalogTeam>> =
        webClient.get()
            .uri("/member/membership/{navIdent}", navIdent)
            .retrieve()
            .bodyToMono(MembershipResponse::class.java)
            .map { it.teams.orEmpty() }
            .timeout(REQUEST_TIMEOUT)
            .onErrorMap(
                { it !is MembershipUnavailableException },
                {
                    LOG.warn("Team Catalog membership lookup failed for navIdent={}: {}", navIdent, it.message)
                    MembershipUnavailableException(it)
                },
            )

    private companion object {
        private val LOG = LoggerFactory.getLogger(TeamCatalogService::class.java)
        private val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(5)
    }
}
