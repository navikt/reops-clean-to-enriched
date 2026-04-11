package no.nav.reops.website

import no.nav.reops.event.Website
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
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.kafka.core.KafkaTemplate
import java.time.Instant
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CompletableFuture

class WebsiteServiceTest {

    private val websiteRepository: WebsiteRepository = mock()
    private val kafkaTemplate: KafkaTemplate<String, WebsiteChangeEvent> = mock()
    private val websiteTopic = "test-website-topic"

    private lateinit var service: WebsiteService

    @BeforeEach
    fun setUp() {
        whenever(kafkaTemplate.send(any<String>(), any<String>(), any())).thenReturn(CompletableFuture.completedFuture(null))
        service = WebsiteService(websiteRepository, kafkaTemplate, websiteTopic)
    }

    @Test
    fun `create saves entity and publishes CREATE event`() {
        whenever(websiteRepository.save(any<WebsiteEntity>())).thenAnswer { it.arguments[0] }

        val result = service.create(name = "Test Site", domain = "test.example.no")

        assertThat(result.name).isEqualTo("Test Site")
        assertThat(result.domain).isEqualTo("test.example.no")
        assertThat(result.websiteId).isNotNull()
        assertThat(result.updatedAt).isNotNull()

        val eventCaptor = argumentCaptor<WebsiteChangeEvent>()
        verify(kafkaTemplate).send(eq(websiteTopic), any<String>(), eventCaptor.capture())
        assertThat(eventCaptor.firstValue.status).isEqualTo(WebsiteEventStatus.CREATE)
        assertThat(eventCaptor.firstValue.website.name).isEqualTo("Test Site")
    }

    @Test
    fun `update persists changes and publishes UPDATE event`() {
        val websiteId = UUID.randomUUID()
        val existing = WebsiteEntity(websiteId, "Old Name", "old.no", Instant.now())

        whenever(websiteRepository.findById(websiteId)).thenReturn(Optional.of(existing))
        whenever(websiteRepository.save(any<WebsiteEntity>())).thenAnswer { it.arguments[0] }

        val result = service.update(websiteId, "New Name", "new.no")

        assertThat(result.websiteId).isEqualTo(websiteId)
        assertThat(result.name).isEqualTo("New Name")
        assertThat(result.domain).isEqualTo("new.no")

        val eventCaptor = argumentCaptor<WebsiteChangeEvent>()
        verify(kafkaTemplate).send(eq(websiteTopic), any<String>(), eventCaptor.capture())
        assertThat(eventCaptor.firstValue.status).isEqualTo(WebsiteEventStatus.UPDATE)
    }

    @Test
    fun `update throws WebsiteNotFoundException when website does not exist`() {
        val websiteId = UUID.randomUUID()
        whenever(websiteRepository.findById(websiteId)).thenReturn(Optional.empty())

        assertThrows<WebsiteNotFoundException> {
            service.update(websiteId, "Name", "domain.no")
        }
    }

    @Test
    fun `delete removes entity and publishes DELETE event`() {
        val websiteId = UUID.randomUUID()
        val existing = WebsiteEntity(websiteId, "Test", "test.no", Instant.now())

        whenever(websiteRepository.findById(websiteId)).thenReturn(Optional.of(existing))

        service.delete(websiteId)

        verify(websiteRepository).deleteById(websiteId)

        val eventCaptor = argumentCaptor<WebsiteChangeEvent>()
        verify(kafkaTemplate).send(eq(websiteTopic), any<String>(), eventCaptor.capture())
        assertThat(eventCaptor.firstValue.status).isEqualTo(WebsiteEventStatus.DELETE)
    }

    @Test
    fun `delete throws WebsiteNotFoundException when website does not exist`() {
        val websiteId = UUID.randomUUID()
        whenever(websiteRepository.findById(websiteId)).thenReturn(Optional.empty())

        assertThrows<WebsiteNotFoundException> {
            service.delete(websiteId)
        }
    }

    @Test
    fun `findAll returns all websites from repository`() {
        val entities = listOf(
            WebsiteEntity(UUID.randomUUID(), "Site 1", "site1.no", Instant.now()),
            WebsiteEntity(UUID.randomUUID(), "Site 2", "site2.no", Instant.now())
        )
        whenever(websiteRepository.findAll()).thenReturn(entities)

        val result = service.findAll()
        assertThat(result).hasSize(2)
    }

    @Test
    fun `findById returns website when found`() {
        val websiteId = UUID.randomUUID()
        val entity = WebsiteEntity(websiteId, "Test", "test.no", Instant.now())
        whenever(websiteRepository.findById(websiteId)).thenReturn(Optional.of(entity))

        val result = service.findById(websiteId)
        assertThat(result).isNotNull
        assertThat(result!!.websiteId).isEqualTo(websiteId)
    }

    @Test
    fun `findById returns null when not found`() {
        val websiteId = UUID.randomUUID()
        whenever(websiteRepository.findById(websiteId)).thenReturn(Optional.empty())

        val result = service.findById(websiteId)
        assertThat(result).isNull()
    }

    @Test
    fun `findAllFresh returns all websites directly from repository`() {
        val entities = listOf(
            WebsiteEntity(UUID.randomUUID(), "Site 1", "site1.no", Instant.now()),
            WebsiteEntity(UUID.randomUUID(), "Site 2", "site2.no", Instant.now())
        )
        whenever(websiteRepository.findAll()).thenReturn(entities)

        val result = service.findAllFresh()
        assertThat(result).hasSize(2)
        assertThat(result.map { it.name }).containsExactly("Site 1", "Site 2")
    }

    @Test
    fun `findByIdFresh returns website directly from repository`() {
        val websiteId = UUID.randomUUID()
        val entity = WebsiteEntity(websiteId, "Test", "test.no", Instant.now())
        whenever(websiteRepository.findById(websiteId)).thenReturn(Optional.of(entity))

        val result = service.findByIdFresh(websiteId)
        assertThat(result).isNotNull
        assertThat(result!!.websiteId).isEqualTo(websiteId)
    }

    @Test
    fun `findByIdFresh returns null when not found`() {
        val websiteId = UUID.randomUUID()
        whenever(websiteRepository.findById(websiteId)).thenReturn(Optional.empty())

        val result = service.findByIdFresh(websiteId)
        assertThat(result).isNull()
    }

    @Test
    fun `isAllowed returns true when website exists`() {
        val websiteId = UUID.randomUUID()
        whenever(websiteRepository.existsById(websiteId)).thenReturn(true)

        assertThat(service.isAllowed(websiteId)).isTrue()
    }

    @Test
    fun `isAllowed returns false when website does not exist`() {
        val websiteId = UUID.randomUUID()
        whenever(websiteRepository.existsById(websiteId)).thenReturn(false)

        assertThat(service.isAllowed(websiteId)).isFalse()
    }
}
