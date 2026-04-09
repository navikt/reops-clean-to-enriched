package no.nav.reops

import io.micrometer.core.instrument.MeterRegistry
import no.nav.reops.event.EnrichedEvent
import no.nav.reops.event.Event
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.config.KafkaListenerEndpointRegistry
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.kafka.test.utils.ContainerTestUtils
import org.springframework.messaging.support.MessageBuilder
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.Duration
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext
@EmbeddedKafka(
    partitions = 1,
    topics = ["test-topic", "test-topic-retry", "test-topic-dlt", "test-output-topic", "test-website-topic"],
    bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
class DltHandlerTest {

    @MockitoBean
    lateinit var enrichedEventKafkaTemplate: KafkaTemplate<String, EnrichedEvent>

    @Autowired
    lateinit var kafkaTemplate: KafkaTemplate<String, Event>

    @Autowired
    lateinit var registry: KafkaListenerEndpointRegistry

    @Autowired
    lateinit var meterRegistry: MeterRegistry

    @Autowired
    lateinit var websiteRepository: no.nav.reops.website.WebsiteRepository

    @Autowired
    lateinit var cacheManager: org.springframework.cache.CacheManager

    @BeforeEach
    fun waitForKafkaListener() {
        registry.listenerContainers.forEach { container ->
            ContainerTestUtils.waitForAssignment(container, 1)
        }
    }

    @Test
    fun `DLT handler is invoked after retries are exhausted`() {
        // Make the producer always fail so eventListen throws -> retries exhausted -> DLT
        whenever(enrichedEventKafkaTemplate.send(eq("test-output-topic"), any<String>(), any<EnrichedEvent>()))
            .thenThrow(RuntimeException("Simulated producer failure"))

        val websiteId = UUID.randomUUID()
        val entity = no.nav.reops.website.WebsiteEntity(
            websiteId = websiteId,
            name = "DLT Test",
            domain = "dlt.test.no",
            updatedAt = java.time.Instant.now()
        )
        websiteRepository.save(entity)
        cacheManager.cacheNames.forEach { cacheManager.getCache(it)?.clear() }

        val event = Event(
            type = "visit", payload = Event.Payload(
                website = websiteId,
                hostname = "localhost",
                screen = "1920x1080",
                language = "nb",
                title = "Test",
                url = "/test",
                referrer = ""
            )
        )

        val message = MessageBuilder.withPayload(event)
            .setHeader(KafkaHeaders.TOPIC, "test-topic")
            .setHeader(KafkaHeaders.KEY, "dlt-test-key")
            .build()

        kafkaTemplate.send(message)

        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(500)).untilAsserted {
            val dltCount = meterRegistry.find("kafka_events_dlt_total").counter()
            assertThat(dltCount).isNotNull
            assertThat(dltCount!!.count()).isGreaterThanOrEqualTo(1.0)
        }
    }
}
