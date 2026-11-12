package no.nav.reops.website

import no.nav.reops.event.WebsiteChangeEvent
import no.nav.reops.event.WebsiteEventStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.dao.DataIntegrityViolationException
import java.time.Instant
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CompletableFuture

class WebsiteServiceTest {

    private val websiteRepository: WebsiteRepository = mock()
    private val kafkaTemplate: KafkaTemplate<String, WebsiteChangeEvent> = mock()
    private val websiteTopic = "test-website-topic"
    private val actor = "Z123456"

    private lateinit var service: WebsiteService

    @BeforeEach
    fun setUp() {
        whenever(kafkaTemplate.send(any<String>(), any<String>(), any())).thenReturn(CompletableFuture.completedFuture(null))
        service = WebsiteService(websiteRepository, kafkaTemplate, websiteTopic)
    }

    private fun entity(
        websiteId: UUID = UUID.randomUUID(),
        name: String = "Test",
        domain: String = "test.no",
        ownerTeamId: UUID? = null,
        deletedAt: Instant? = null,
        version: Long = 0L,
    ) = WebsiteEntity(
        websiteId = websiteId,
        name = name,
        domain = domain,
        updatedAt = Instant.now(),
        createdAt = Instant.now(),
        ownerTeamId = ownerTeamId,
        deletedAt = deletedAt,
        version = version,
    )

    @Test
    fun `create generates server-side id, saves entity and publishes CREATE event`() {
        whenever(websiteRepository.save(any<WebsiteEntity>())).thenAnswer { it.arguments[0] }
        val ownerTeamId = UUID.randomUUID()

        val result = service.create("Test Site", "test.example.no", ownerTeamId, actor)

        assertThat(result.ownerTeamId).isEqualTo(ownerTeamId)
        assertThat(result.deletedAt).isNull()

        val eventCaptor = argumentCaptor<WebsiteChangeEvent>()
        verify(kafkaTemplate).send(eq(websiteTopic), any<String>(), eventCaptor.capture())
        assertThat(eventCaptor.firstValue.status).isEqualTo(WebsiteEventStatus.CREATE)
        assertThat(eventCaptor.firstValue.website.deletedAt).isNull()
    }

    @Test
    fun `create with a chosen ID preserves identity and publishes active metadata`() {
        val websiteId = UUID.randomUUID()
        whenever(websiteRepository.save(any<WebsiteEntity>())).thenAnswer { it.arguments[0] }

        val result = service.create("Restored", "restored.nav.no", null, actor, websiteId)

        assertThat(result.websiteId).isEqualTo(websiteId)
        assertThat(result.deletedAt).isNull()
        val entityCaptor = argumentCaptor<WebsiteEntity>()
        verify(websiteRepository).save(entityCaptor.capture())
        assertThat(entityCaptor.firstValue.version).isNull() // persist, never merge/upsert
        val eventCaptor = argumentCaptor<WebsiteChangeEvent>()
        verify(kafkaTemplate).send(eq(websiteTopic), eq(websiteId.toString()), eventCaptor.capture())
        assertThat(eventCaptor.firstValue.status).isEqualTo(WebsiteEventStatus.CREATE)
        assertThat(eventCaptor.firstValue.website.websiteId).isEqualTo(websiteId)
    }

    @Test
    fun `create rejects existing IDs including soft-deleted registrations without mutations`() {
        val websiteId = UUID.randomUUID()
        whenever(websiteRepository.existsById(websiteId)).thenReturn(true)

        assertThrows<WebsiteAlreadyExistsException> {
            service.create("Restored", "restored.nav.no", null, actor, websiteId)
        }

        verify(websiteRepository, never()).save(any<WebsiteEntity>())
        verify(kafkaTemplate, never()).send(any<String>(), any<String>(), any())
    }

    @Test
    fun `concurrent duplicate create maps primary key violation to conflict without publication`() {
        val websiteId = UUID.randomUUID()
        whenever(websiteRepository.existsById(websiteId)).thenReturn(false, true)
        whenever(websiteRepository.save(any<WebsiteEntity>())).thenThrow(DataIntegrityViolationException("duplicate"))

        val ex = assertThrows<WebsiteAlreadyExistsException> {
            service.create("Restored", "restored.nav.no", null, actor, websiteId)
        }

        assertThat(GlobalExceptionHandler().handleAlreadyExists(ex).status).isEqualTo(409)
        verify(kafkaTemplate, never()).send(any<String>(), any<String>(), any())
    }

    @Test
    fun `update persists changes and publishes UPDATE event when version matches`() {
        val websiteId = UUID.randomUUID()
        val existing = entity(websiteId, version = 4L)
        val newOwner = UUID.randomUUID()

        whenever(websiteRepository.findById(websiteId)).thenReturn(Optional.of(existing))
        whenever(websiteRepository.save(any<WebsiteEntity>())).thenAnswer { it.arguments[0] }

        val result = service.update(websiteId, "New Name", "new.no", newOwner, expectedVersion = 4L, actorNavIdent = actor)

        assertThat(result.name).isEqualTo("New Name")
        assertThat(result.ownerTeamId).isEqualTo(newOwner)

        val eventCaptor = argumentCaptor<WebsiteChangeEvent>()
        verify(kafkaTemplate).send(eq(websiteTopic), any<String>(), eventCaptor.capture())
        assertThat(eventCaptor.firstValue.status).isEqualTo(WebsiteEventStatus.UPDATE)
    }

    @Test
    fun `ownership-only changes stay in Postgres without republishing BigQuery metadata`() {
        val existing = entity(version = 3L)
        val newOwner = UUID.randomUUID()
        whenever(websiteRepository.findById(existing.websiteId)).thenReturn(Optional.of(existing))
        whenever(websiteRepository.save(any<WebsiteEntity>())).thenAnswer { it.arguments[0] }

        val result = service.update(
            existing.websiteId, existing.name, existing.domain, newOwner,
            expectedVersion = 3L, actorNavIdent = actor,
        )

        assertThat(result.ownerTeamId).isEqualTo(newOwner)
        verify(websiteRepository).save(any<WebsiteEntity>())
        verify(kafkaTemplate, never()).send(any<String>(), any<String>(), any())
    }

    @Test
    fun `update throws conflict when version does not match authorized snapshot`() {
        val websiteId = UUID.randomUUID()
        whenever(websiteRepository.findById(websiteId)).thenReturn(Optional.of(entity(websiteId, version = 9L)))

        assertThrows<WebsiteConflictException> {
            service.update(websiteId, "N", "d.no", null, expectedVersion = 4L, actorNavIdent = actor)
        }
        verify(websiteRepository, never()).save(any<WebsiteEntity>())
        verify(kafkaTemplate, never()).send(any<String>(), any<String>(), any())
    }

    @Test
    fun `update throws conflict on soft-deleted row so it cannot be revived`() {
        val websiteId = UUID.randomUUID()
        whenever(websiteRepository.findById(websiteId))
            .thenReturn(Optional.of(entity(websiteId, version = 2L, deletedAt = Instant.now())))

        assertThrows<WebsiteConflictException> {
            service.update(websiteId, "N", "d.no", null, expectedVersion = 2L, actorNavIdent = actor)
        }
        verify(websiteRepository, never()).save(any<WebsiteEntity>())
    }

    @Test
    fun `softDelete stamps deletedAt=updatedAt and publishes DELETE tombstone`() {
        val websiteId = UUID.randomUUID()
        val existing = entity(websiteId, version = 1L)

        whenever(websiteRepository.findById(websiteId)).thenReturn(Optional.of(existing))
        whenever(websiteRepository.save(any<WebsiteEntity>())).thenAnswer { it.arguments[0] }

        val result = service.softDelete(websiteId, expectedVersion = 1L, actorNavIdent = actor)

        assertThat(result.deletedAt).isNotNull()
        assertThat(result.updatedAt).isEqualTo(result.deletedAt)

        val eventCaptor = argumentCaptor<WebsiteChangeEvent>()
        verify(kafkaTemplate).send(eq(websiteTopic), any<String>(), eventCaptor.capture())
        assertThat(eventCaptor.firstValue.status).isEqualTo(WebsiteEventStatus.DELETE)
        assertThat(eventCaptor.firstValue.website.deletedAt).isEqualTo(result.deletedAt)
        verify(websiteRepository, never()).deleteById(any<UUID>())
    }

    @Test
    fun `softDelete is idempotent and does not republish for already deleted website`() {
        val websiteId = UUID.randomUUID()
        val deletionTime = Instant.now().minusSeconds(60)
        val existing = entity(websiteId, version = 3L, deletedAt = deletionTime)
        whenever(websiteRepository.findById(websiteId)).thenReturn(Optional.of(existing))

        val result = service.softDelete(websiteId, expectedVersion = 3L, actorNavIdent = actor)

        assertThat(result.deletedAt).isEqualTo(deletionTime)
        verify(websiteRepository, never()).save(any<WebsiteEntity>())
        verify(kafkaTemplate, never()).send(any<String>(), any<String>(), any())
    }

    @Test
    fun `softDelete throws conflict when version does not match`() {
        val websiteId = UUID.randomUUID()
        whenever(websiteRepository.findById(websiteId)).thenReturn(Optional.of(entity(websiteId, version = 5L)))

        assertThrows<WebsiteConflictException> {
            service.softDelete(websiteId, expectedVersion = 1L, actorNavIdent = actor)
        }
        verify(websiteRepository, never()).save(any<WebsiteEntity>())
    }

    @Test
    fun `hardDelete removes PG row and publishes NO event`() {
        val websiteId = UUID.randomUUID()
        val existing = entity(websiteId, version = 0L)
        whenever(websiteRepository.findById(websiteId)).thenReturn(Optional.of(existing))

        service.hardDelete(websiteId, expectedVersion = 0L, actorNavIdent = actor)

        verify(websiteRepository).delete(existing)
        verify(kafkaTemplate, never()).send(any<String>(), any<String>(), any())
    }

    @Test
    fun `hardDelete throws conflict when version does not match`() {
        val websiteId = UUID.randomUUID()
        whenever(websiteRepository.findById(websiteId)).thenReturn(Optional.of(entity(websiteId, version = 2L)))

        assertThrows<WebsiteConflictException> {
            service.hardDelete(websiteId, expectedVersion = 0L, actorNavIdent = actor)
        }
        verify(websiteRepository, never()).delete(any<WebsiteEntity>())
    }

    @Test
    fun `hardDelete throws not found when website missing`() {
        val websiteId = UUID.randomUUID()
        whenever(websiteRepository.findById(websiteId)).thenReturn(Optional.empty())

        assertThrows<WebsiteNotFoundException> {
            service.hardDelete(websiteId, expectedVersion = 0L, actorNavIdent = actor)
        }
    }

    @Test
    fun `findById returns registration when found and null when missing`() {
        val websiteId = UUID.randomUUID()
        whenever(websiteRepository.findById(websiteId)).thenReturn(Optional.of(entity(websiteId)))
        assertThat(service.findById(websiteId)!!.websiteId).isEqualTo(websiteId)

        whenever(websiteRepository.findById(websiteId)).thenReturn(Optional.empty())
        assertThat(service.findById(websiteId)).isNull()
    }

    @Test
    fun `findAll excludes soft-deleted rows unless includeDeleted`() {
        val active = entity(name = "Site 1")
        val deleted = entity(name = "Site 2", deletedAt = Instant.now())
        whenever(websiteRepository.findByDeletedAtIsNull()).thenReturn(listOf(active))
        whenever(websiteRepository.findAll()).thenReturn(listOf(active, deleted))

        assertThat(service.findAll(includeDeleted = false)).hasSize(1)
        assertThat(service.findAll(includeDeleted = true)).hasSize(2)
    }

    @Test
    fun `findByOwnerTeams returns empty for no teams without hitting repository`() {
        assertThat(service.findByOwnerTeams(emptyList(), includeDeleted = false)).isEmpty()
        verify(websiteRepository, never()).findByOwnerTeamIdIn(any())
    }

    @Test
    fun `findByOwnerTeams filters by ownership`() {
        val teamId = UUID.randomUUID()
        val owned = entity(ownerTeamId = teamId)
        whenever(websiteRepository.findByOwnerTeamIdInAndDeletedAtIsNull(listOf(teamId))).thenReturn(listOf(owned))

        val result = service.findByOwnerTeams(listOf(teamId), includeDeleted = false)

        assertThat(result).hasSize(1)
        assertThat(result.first().ownerTeamId).isEqualTo(teamId)
    }

    @Test
    fun `isAllowed is true only for active registrations`() {
        val websiteId = UUID.randomUUID()
        whenever(websiteRepository.existsByWebsiteIdAndDeletedAtIsNull(websiteId)).thenReturn(true)
        assertThat(service.isAllowed(websiteId)).isTrue()

        whenever(websiteRepository.existsByWebsiteIdAndDeletedAtIsNull(websiteId)).thenReturn(false)
        val freshService = WebsiteService(websiteRepository, kafkaTemplate, websiteTopic)
        assertThat(freshService.isAllowed(websiteId)).isFalse()
    }
}
