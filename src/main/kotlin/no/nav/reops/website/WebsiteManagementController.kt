package no.nav.reops.website

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import no.nav.reops.security.AuthenticatedUser
import no.nav.reops.security.TeamCatalogService
import no.nav.reops.security.TeamCatalogTeam
import no.nav.reops.security.toAuthenticatedUserOrNull
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.validation.annotation.Validated
import org.springframework.web.ErrorResponseException
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.net.URI
import java.util.UUID

const val CONFIRM_WEBSITE_ID_HEADER = "X-Confirm-Website-Id"

data class ManagementContext(
    val teams: List<TeamInfo>,
    val isReopsTeamMember: Boolean,
)

data class TeamInfo(
    val id: UUID,
    val name: String,
)

data class UpsertWebsiteRequest(
    // Bounded to 100 to fit the BigQuery sink limit (tighter than the 255 PG column).
    @field:NotBlank @field:Size(max = 100) val name: String,
    // Hostname only (RFC 1034/1123 labels); max 253 per DNS. URLs, ports, paths and
    // trailing dots are rejected, so the stored value is always a plain hostname.
    @field:NotBlank @field:Size(max = 253)
    @field:Pattern(
        regexp = "^[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?(\\.[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$",
        message = "must be a valid hostname (e.g. www.nav.no), not a URL",
    )
    val domain: String,
    val ownerTeamId: UUID? = null,
    // Creation only, ReOps only. Omitted/null keeps server-generated IDs.
    val websiteId: UUID? = null,
)

/**
 * Website management API consumed by the Innblikk BFF with Azure AD OBO user tokens.
 * Authentication (issuer/audience/expiry/signature/azp/delegated-scope) is enforced by
 * the resource server; this class independently resolves the caller's Team Catalog
 * membership for every request — fail closed on lookup failure — and applies ownership
 * rules. Blocking JPA work is dispatched to the bounded-elastic scheduler, never the
 * event loop.
 *
 * Mutations are optimistic-lock guarded: the controller reads an authorized snapshot
 * (which carries the row version), then passes that expected version to the service so a
 * concurrent owner/state change between authorization and mutation fails with 409.
 */
@RestController
@RequestMapping("/api/manage/websites")
@Validated
class WebsiteManagementController(
    private val websiteService: WebsiteService,
    private val teamCatalogService: TeamCatalogService,
    @param:Value("\${app.reops-team-id}") private val reopsTeamId: UUID,
) {

    @GetMapping("/context")
    fun context(token: JwtAuthenticationToken): Mono<ManagementContext> {
        val user = userFrom(token)
        return callerTeams(user).map { teams ->
            ManagementContext(
                teams = teams.map { TeamInfo(it.id, it.name) },
                isReopsTeamMember = user.isReopsMember(teams),
            )
        }
    }

    @GetMapping
    fun list(
        token: JwtAuthenticationToken,
        @RequestParam(required = false, defaultValue = "mine") scope: String,
        @RequestParam(required = false, defaultValue = "false") includeDeleted: Boolean,
    ): Mono<List<WebsiteRegistration>> {
        val user = userFrom(token)
        if (scope != "mine" && scope != "all") {
            throw ErrorResponseException(HttpStatus.BAD_REQUEST)
        }
        return callerTeams(user).flatMap { teams ->
            val isAdmin = user.isReopsMember(teams)
            if (scope == "all" && !isAdmin) {
                throw ErrorResponseException(HttpStatus.FORBIDDEN)
            }
            Mono.fromCallable {
                if (scope == "all") {
                    websiteService.findAll(includeDeleted)
                } else {
                    websiteService.findByOwnerTeams(teams.map { it.id }, includeDeleted)
                }
            }.subscribeOn(Schedulers.boundedElastic())
        }
    }

    @GetMapping("/{websiteId}")
    fun getById(
        token: JwtAuthenticationToken,
        @PathVariable websiteId: UUID,
    ): Mono<WebsiteRegistration> {
        val user = userFrom(token)
        return callerTeams(user).flatMap { teams ->
            val isAdmin = user.isReopsMember(teams)
            Mono.fromCallable { websiteService.findById(websiteId) }
                .subscribeOn(Schedulers.boundedElastic())
                .switchIfEmpty(Mono.error(ErrorResponseException(HttpStatus.NOT_FOUND)))
                .flatMap { registration ->
                    when {
                        !isAdmin && !canSee(teams, registration) ->
                            Mono.error(ErrorResponseException(HttpStatus.NOT_FOUND))
                        else -> Mono.just(registration)
                    }
                }
        }
    }

    @PostMapping
    fun create(
        token: JwtAuthenticationToken,
        @Validated @RequestBody request: UpsertWebsiteRequest,
    ): Mono<ResponseEntity<WebsiteRegistration>> {
        val user = userFrom(token)
        val name = request.name.trim()
        val domain = request.domain.trim().lowercase()
        return callerTeams(user).flatMap { teams ->
            val isAdmin = user.isReopsMember(teams)
            val ownerTeamId = request.ownerTeamId
            if (request.websiteId != null && !isAdmin) {
                throw ErrorResponseException(HttpStatus.FORBIDDEN)
            }
            if (!isAdmin) {
                // Non-admin must assign ownership to one of their own verified teams;
                // unassigned (null) is not permitted for self-service creation.
                if (ownerTeamId == null || ownerTeamId !in teams.map { it.id }) {
                    throw ErrorResponseException(HttpStatus.FORBIDDEN)
                }
            }
            // Admin may choose any team UUID, or null for unassigned.
            Mono.fromCallable { websiteService.create(name, domain, ownerTeamId, user.navIdent, request.websiteId) }
                .subscribeOn(Schedulers.boundedElastic())
                .map { created ->
                    ResponseEntity
                        .created(URI.create("/api/manage/websites/${created.websiteId}"))
                        .body(created)
                }
        }
    }

    @PutMapping("/{websiteId}")
    fun update(
        token: JwtAuthenticationToken,
        @PathVariable websiteId: UUID,
        @Validated @RequestBody request: UpsertWebsiteRequest,
    ): Mono<WebsiteRegistration> {
        val user = userFrom(token)
        if (request.websiteId != null) {
            throw ErrorResponseException(HttpStatus.BAD_REQUEST)
        }
        val name = request.name.trim()
        val domain = request.domain.trim().lowercase()
        return callerTeams(user).flatMap { teams ->
            val isAdmin = user.isReopsMember(teams)
            Mono.fromCallable {
                val existing = websiteService.findEntityById(websiteId)
                    ?: throw ErrorResponseException(HttpStatus.NOT_FOUND)
                if (!isAdmin && !canSee(teams, existing.toRegistration())) {
                    throw ErrorResponseException(HttpStatus.NOT_FOUND)
                }
                if (existing.deletedAt != null) {
                    // Restore is out of scope; deleted registrations are read-only.
                    throw ErrorResponseException(HttpStatus.CONFLICT)
                }
                // non-admin owners cannot change ownership; admin may reassign to any team UUID or null
                val newOwner = if (isAdmin) request.ownerTeamId else existing.ownerTeamId
                websiteService.update(websiteId, name, domain, newOwner, requireNotNull(existing.version), user.navIdent)
            }.subscribeOn(Schedulers.boundedElastic())
        }
    }

    @DeleteMapping("/{websiteId}")
    fun softDelete(
        token: JwtAuthenticationToken,
        @PathVariable websiteId: UUID,
    ): Mono<ResponseEntity<Void>> {
        val user = userFrom(token)
        return callerTeams(user).flatMap { teams ->
            val isAdmin = user.isReopsMember(teams)
            Mono.fromCallable {
                val existing = websiteService.findEntityById(websiteId)
                    ?: throw ErrorResponseException(HttpStatus.NOT_FOUND)
                if (!isAdmin && !canSee(teams, existing.toRegistration())) {
                    throw ErrorResponseException(HttpStatus.NOT_FOUND)
                }
                // Idempotent — no duplicate publication; guarded by version.
                websiteService.softDelete(websiteId, requireNotNull(existing.version), user.navIdent)
                ResponseEntity.noContent().build<Void>()
            }.subscribeOn(Schedulers.boundedElastic())
        }
    }

    @DeleteMapping("/{websiteId}/registration")
    fun hardDelete(
        token: JwtAuthenticationToken,
        @PathVariable websiteId: UUID,
        @RequestHeader(name = CONFIRM_WEBSITE_ID_HEADER, required = false) confirmWebsiteId: String?,
    ): Mono<ResponseEntity<Void>> {
        val user = userFrom(token)
        return callerTeams(user).flatMap { teams ->
            if (!user.isReopsMember(teams)) {
                throw ErrorResponseException(HttpStatus.FORBIDDEN)
            }
            if (confirmWebsiteId != websiteId.toString()) {
                throw ErrorResponseException(HttpStatus.BAD_REQUEST)
            }
            Mono.fromCallable<ResponseEntity<Void>> {
                val existing = websiteService.findEntityById(websiteId)
                    ?: throw ErrorResponseException(HttpStatus.NOT_FOUND)
                // PG row only — no Kafka publication, no session/analytics deletion.
                // Active registrations are accepted to support already-purged BQ rows.
                websiteService.hardDelete(existing.websiteId, requireNotNull(existing.version), user.navIdent)
                ResponseEntity.noContent().build()
            }.subscribeOn(Schedulers.boundedElastic())
        }
    }

    private fun userFrom(token: JwtAuthenticationToken): AuthenticatedUser =
        token.token.toAuthenticatedUserOrNull()
            ?: throw ErrorResponseException(HttpStatus.UNAUTHORIZED).also {
                LOG.warn("Rejected management request: no valid delegated user identity in token")
            }

    private fun callerTeams(user: AuthenticatedUser) =
        teamCatalogService.getTeamsForUser(user.navIdent)

    private fun AuthenticatedUser.isReopsMember(teams: List<TeamCatalogTeam>): Boolean =
        teams.any { it.id == reopsTeamId }

    private fun canSee(teams: List<TeamCatalogTeam>, registration: WebsiteRegistration): Boolean {
        val owner = registration.ownerTeamId ?: return false // unassigned registrations are admin-only
        return teams.any { it.id == owner }
    }

    private companion object {
        private val LOG = LoggerFactory.getLogger(WebsiteManagementController::class.java)
    }
}
