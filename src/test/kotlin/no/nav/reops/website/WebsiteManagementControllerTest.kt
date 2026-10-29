package no.nav.reops.website

import no.nav.reops.security.TeamCatalogService
import no.nav.reops.security.TeamCatalogTeam
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.ErrorResponseException
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import java.util.UUID

/**
 * Focused authorization/ownership/confirmation tests for the management controller,
 * driving the controller methods directly with pre-authenticated JWT principals.
 * HTTP-level authentication (signature/issuer/audience/azp) is the resource server's job
 * and is covered separately in the security validator-chain tests.
 */
class WebsiteManagementControllerTest {

    private val websiteService: WebsiteService = mock()
    private val teamCatalogService: TeamCatalogService = mock()
    private val reopsTeamId: UUID = UUID.fromString("26dba481-fd96-40a8-b47d-b1ad0002bc74")

    private lateinit var controller: WebsiteManagementController

    private val teamA = TeamCatalogTeam(UUID.randomUUID(), "Team A")
    private val teamB = TeamCatalogTeam(UUID.randomUUID(), "Team B")
    private val reopsTeam = TeamCatalogTeam(reopsTeamId, "ResearchOps")

    @BeforeEach
    fun setUp() {
        controller = WebsiteManagementController(websiteService, teamCatalogService, reopsTeamId)
    }

    private fun user(navIdent: String = "Z123456"): JwtAuthenticationToken =
        JwtAuthenticationToken(
            Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("NAVident", navIdent)
                .claim("azp", "innblikk-frontend-client-id")
                .claim("scp", "defaultaccess")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build()
        )

    private fun machineToken(): JwtAuthenticationToken =
        JwtAuthenticationToken(
            Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("idtyp", "app")
                .claim("azp", "innblikk-frontend-client-id")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build()
        )

    private fun entity(
        websiteId: UUID = UUID.randomUUID(),
        ownerTeamId: UUID? = teamA.id,
        name: String = "Site",
        deletedAt: Instant? = null,
        version: Long = 0L,
    ) = WebsiteEntity(
        websiteId = websiteId,
        name = name,
        domain = "site.nav.no",
        updatedAt = Instant.now().minusSeconds(3600),
        createdAt = Instant.now().minusSeconds(7200),
        ownerTeamId = ownerTeamId,
        deletedAt = deletedAt,
        version = version,
    )

    private fun registration(
        websiteId: UUID = UUID.randomUUID(),
        ownerTeamId: UUID? = teamA.id,
        name: String = "Site",
        deletedAt: Instant? = null,
    ) = entity(websiteId, ownerTeamId, name, deletedAt).toRegistration()

    private fun stubTeams(navIdent: String, teams: List<TeamCatalogTeam>) {
        whenever(teamCatalogService.getTeamsForUser(navIdent)).thenReturn(Mono.just(teams))
    }

    // --- identity ---

    @Test
    fun `requests without delegated user identity are rejected as unauthorized`() {
        val ex = assertThrows<ErrorResponseException> {
            controller.context(machineToken()).block()
        }
        assertThat(ex.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
    }

    // --- context ---

    @Test
    fun `context returns caller teams and ReOps flag`() {
        stubTeams("Z123456", listOf(teamA, reopsTeam))
        val ctx = controller.context(user()).block()!!
        assertThat(ctx.isReopsTeamMember).isTrue()
        assertThat(ctx.teams.map { it.id }).containsExactlyInAnyOrder(teamA.id, reopsTeamId)
    }

    @Test
    fun `context reports non-member as not ReOps`() {
        stubTeams("Z123456", listOf(teamA))
        assertThat(controller.context(user()).block()!!.isReopsTeamMember).isFalse()
    }

    // --- list ---

    @Test
    fun `list mine returns only own team registrations`() {
        stubTeams("Z123456", listOf(teamA))
        whenever(websiteService.findByOwnerTeams(listOf(teamA.id), false))
            .thenReturn(listOf(registration(ownerTeamId = teamA.id)))

        val result = controller.list(user(), "mine", false).block()!!

        assertThat(result).hasSize(1)
        verify(websiteService, never()).findAll(any())
    }

    @Test
    fun `list mine allows includeDeleted for own teams`() {
        stubTeams("Z123456", listOf(teamA))
        whenever(websiteService.findByOwnerTeams(listOf(teamA.id), true))
            .thenReturn(listOf(registration(ownerTeamId = teamA.id, deletedAt = Instant.now())))

        val result = controller.list(user(), "mine", true).block()!!

        assertThat(result).hasSize(1)
        verify(websiteService).findByOwnerTeams(listOf(teamA.id), true)
    }

    @Test
    fun `list scope all is forbidden for non-admin`() {
        stubTeams("Z123456", listOf(teamA))
        val ex = assertThrows<ErrorResponseException> { controller.list(user(), "all", false).block() }
        assertThat(ex.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        verify(websiteService, never()).findAll(any())
    }

    @Test
    fun `list scope all with includeDeleted is allowed for ReOps admin`() {
        stubTeams("Z123456", listOf(reopsTeam))
        whenever(websiteService.findAll(true)).thenReturn(
            listOf(registration(ownerTeamId = teamA.id), registration(deletedAt = Instant.now()))
        )
        val result = controller.list(user(), "all", true).block()!!
        assertThat(result).hasSize(2)
    }

    @Test
    fun `unknown scope is rejected`() {
        stubTeams("Z123456", listOf(reopsTeam))
        val ex = assertThrows<ErrorResponseException> { controller.list(user(), "everything", false).block() }
        assertThat(ex.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    // --- get by id ---

    @Test
    fun `get returns 404 when registration does not exist`() {
        stubTeams("Z123456", listOf(reopsTeam))
        whenever(websiteService.findById(any())).thenReturn(null)

        StepVerifier.create(controller.getById(user(), UUID.randomUUID()))
            .expectErrorMatches { it is ErrorResponseException && it.statusCode == HttpStatus.NOT_FOUND }
            .verify()
    }

    @Test
    fun `get returns 404 for registration owned by another team`() {
        stubTeams("Z123456", listOf(teamA))
        whenever(websiteService.findById(any())).thenReturn(registration(ownerTeamId = teamB.id))

        StepVerifier.create(controller.getById(user(), UUID.randomUUID()))
            .expectErrorMatches { it is ErrorResponseException && it.statusCode == HttpStatus.NOT_FOUND }
            .verify()
    }

    @Test
    fun `get returns 404 for unassigned registration for non-admin`() {
        stubTeams("Z123456", listOf(teamA))
        whenever(websiteService.findById(any())).thenReturn(registration(ownerTeamId = null))

        StepVerifier.create(controller.getById(user(), UUID.randomUUID()))
            .expectErrorMatches { it is ErrorResponseException && it.statusCode == HttpStatus.NOT_FOUND }
            .verify()
    }

    @Test
    fun `get returns registration for owning team member`() {
        val websiteId = UUID.randomUUID()
        stubTeams("Z123456", listOf(teamA))
        whenever(websiteService.findById(websiteId)).thenReturn(registration(websiteId, teamA.id))
        assertThat(controller.getById(user(), websiteId).block()!!.websiteId).isEqualTo(websiteId)
    }

    // --- create ---

    @Test
    fun `non-admin create with null ownerTeamId is forbidden`() {
        stubTeams("Z123456", listOf(teamA))
        val ex = assertThrows<ErrorResponseException> {
            controller.create(user(), UpsertWebsiteRequest("X", "x.nav.no", null)).block()
        }
        assertThat(ex.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        verify(websiteService, never()).create(any(), any(), any(), any())
    }

    @Test
    fun `non-admin create with ownerTeamId outside caller teams is forbidden`() {
        stubTeams("Z123456", listOf(teamA))
        val ex = assertThrows<ErrorResponseException> {
            controller.create(user(), UpsertWebsiteRequest("X", "x.nav.no", teamB.id)).block()
        }
        assertThat(ex.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        verify(websiteService, never()).create(any(), any(), any(), any())
    }

    @Test
    fun `non-admin create with no teams is forbidden`() {
        stubTeams("Z123456", emptyList())
        val ex = assertThrows<ErrorResponseException> {
            controller.create(user(), UpsertWebsiteRequest("X", "x.nav.no", teamA.id)).block()
        }
        assertThat(ex.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
    }

    @Test
    fun `non-admin create with own team ownerTeamId returns 201 with Location and normalizes domain`() {
        val created = registration(ownerTeamId = teamA.id)
        stubTeams("Z123456", listOf(teamA))
        whenever(websiteService.create(eq("X"), eq("x.nav.no"), eq(teamA.id), eq("Z123456"))).thenReturn(created)

        val response = controller.create(user(), UpsertWebsiteRequest("X", "  X.NAV.no ", teamA.id)).block()!!

        assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED)
        assertThat(response.headers.location.toString()).endsWith("/api/manage/websites/${created.websiteId}")
        // domain trimmed + lowercased server-side
        verify(websiteService).create("X", "x.nav.no", teamA.id, "Z123456")
    }

    @Test
    fun `ReOps admin may create unassigned registration`() {
        val created = registration(ownerTeamId = null)
        stubTeams("Z123456", listOf(reopsTeam))
        whenever(websiteService.create(eq("X"), eq("x.nav.no"), eq(null), any())).thenReturn(created)

        val response = controller.create(user(), UpsertWebsiteRequest("X", "x.nav.no", null)).block()!!

        assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED)
    }

    // --- update ---

    @Test
    fun `update keeps existing ownerTeamId for non-admin owner and passes expected version`() {
        val websiteId = UUID.randomUUID()
        val existing = entity(websiteId, teamA.id, version = 7L)
        stubTeams("Z123456", listOf(teamA))
        whenever(websiteService.findEntityById(websiteId)).thenReturn(existing)
        whenever(websiteService.update(eq(websiteId), any(), any(), eq(teamA.id), eq(7L), any()))
            .thenReturn(existing.copy(name = "New").toRegistration())

        // Non-admin tries (and fails) to smuggle an ownership change to teamB
        controller.update(user(), websiteId, UpsertWebsiteRequest("New", "site.nav.no", teamB.id)).block()

        verify(websiteService).update(websiteId, "New", "site.nav.no", teamA.id, 7L, "Z123456")
    }

    @Test
    fun `update of foreign team registration is hidden as 404 for non-admin`() {
        val websiteId = UUID.randomUUID()
        stubTeams("Z123456", listOf(teamA))
        whenever(websiteService.findEntityById(websiteId)).thenReturn(entity(websiteId, teamB.id))

        val ex = assertThrows<ErrorResponseException> {
            controller.update(user(), websiteId, UpsertWebsiteRequest("New", "site.nav.no", null)).block()
        }
        assertThat(ex.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        verify(websiteService, never()).update(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `update of soft-deleted registration is 409 conflict`() {
        val websiteId = UUID.randomUUID()
        stubTeams("Z123456", listOf(teamA))
        whenever(websiteService.findEntityById(websiteId))
            .thenReturn(entity(websiteId, teamA.id, deletedAt = Instant.now()))

        val ex = assertThrows<ErrorResponseException> {
            controller.update(user(), websiteId, UpsertWebsiteRequest("New", "site.nav.no", null)).block()
        }
        assertThat(ex.statusCode).isEqualTo(HttpStatus.CONFLICT)
        verify(websiteService, never()).update(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `update of inaccessible soft-deleted registration is 404 not 409`() {
        val websiteId = UUID.randomUUID()
        stubTeams("Z123456", listOf(teamA))
        whenever(websiteService.findEntityById(websiteId))
            .thenReturn(entity(websiteId, teamB.id, deletedAt = Instant.now()))

        val ex = assertThrows<ErrorResponseException> {
            controller.update(user(), websiteId, UpsertWebsiteRequest("New", "site.nav.no", null)).block()
        }
        assertThat(ex.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `admin may reassign ownership`() {
        val websiteId = UUID.randomUUID()
        val existing = entity(websiteId, teamA.id, version = 3L)
        stubTeams("Z123456", listOf(reopsTeam))
        whenever(websiteService.findEntityById(websiteId)).thenReturn(existing)
        whenever(websiteService.update(eq(websiteId), any(), any(), eq(teamB.id), eq(3L), any()))
            .thenReturn(existing.copy(ownerTeamId = teamB.id).toRegistration())

        controller.update(user(), websiteId, UpsertWebsiteRequest("Site", "site.nav.no", teamB.id)).block()

        verify(websiteService).update(websiteId, "Site", "site.nav.no", teamB.id, 3L, "Z123456")
    }

    // --- soft delete ---

    @Test
    fun `soft delete allowed for owning team member and passes version`() {
        val websiteId = UUID.randomUUID()
        stubTeams("Z123456", listOf(teamA))
        whenever(websiteService.findEntityById(websiteId)).thenReturn(entity(websiteId, teamA.id, version = 5L))

        val response = controller.softDelete(user(), websiteId).block()!!

        assertThat(response.statusCode).isEqualTo(HttpStatus.NO_CONTENT)
        verify(websiteService).softDelete(websiteId, 5L, "Z123456")
    }

    @Test
    fun `soft delete of foreign team registration is hidden as 404`() {
        val websiteId = UUID.randomUUID()
        stubTeams("Z123456", listOf(teamA))
        whenever(websiteService.findEntityById(websiteId)).thenReturn(entity(websiteId, teamB.id))

        val ex = assertThrows<ErrorResponseException> { controller.softDelete(user(), websiteId).block() }
        assertThat(ex.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        verify(websiteService, never()).softDelete(any(), any(), any())
    }

    // --- hard delete ---

    @Test
    fun `hard delete is forbidden for non-ReOps member`() {
        val websiteId = UUID.randomUUID()
        stubTeams("Z123456", listOf(teamA))

        val ex = assertThrows<ErrorResponseException> {
            controller.hardDelete(user(), websiteId, websiteId.toString()).block()
        }
        assertThat(ex.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        verify(websiteService, never()).hardDelete(any(), any(), any())
    }

    @Test
    fun `hard delete without confirmation header is rejected`() {
        val websiteId = UUID.randomUUID()
        stubTeams("Z123456", listOf(reopsTeam))

        val ex = assertThrows<ErrorResponseException> { controller.hardDelete(user(), websiteId, null).block() }
        assertThat(ex.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        verify(websiteService, never()).hardDelete(any(), any(), any())
    }

    @Test
    fun `hard delete with wrong confirmation id is rejected`() {
        val websiteId = UUID.randomUUID()
        stubTeams("Z123456", listOf(reopsTeam))

        val ex = assertThrows<ErrorResponseException> {
            controller.hardDelete(user(), websiteId, UUID.randomUUID().toString()).block()
        }
        assertThat(ex.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        verify(websiteService, never()).hardDelete(any(), any(), any())
    }

    @Test
    fun `hard delete with matching confirmation succeeds for ReOps member and passes version`() {
        val websiteId = UUID.randomUUID()
        stubTeams("Z123456", listOf(reopsTeam))
        whenever(websiteService.findEntityById(websiteId)).thenReturn(entity(websiteId, teamA.id, version = 9L))

        val response = controller.hardDelete(user(), websiteId, websiteId.toString()).block()!!

        assertThat(response.statusCode).isEqualTo(HttpStatus.NO_CONTENT)
        verify(websiteService).hardDelete(websiteId, 9L, "Z123456")
    }

    // --- membership failure ---

    @Test
    fun `membership lookup failure fails closed with 503`() {
        whenever(teamCatalogService.getTeamsForUser("Z123456"))
            .thenReturn(Mono.error(no.nav.reops.security.MembershipUnavailableException()))

        StepVerifier.create(controller.list(user(), "mine", false))
            .expectError(no.nav.reops.security.MembershipUnavailableException::class.java)
            .verify()

        verify(websiteService, never()).findAll(any())
        verify(websiteService, never()).findByOwnerTeams(any(), any())
    }
}
