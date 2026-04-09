package no.nav.reops

import no.nav.reops.bigquery.BigQueryService
import com.google.cloud.bigquery.storage.v1.BigQueryWriteClient
import io.micrometer.core.instrument.MeterRegistry
import no.nav.reops.event.EnrichedEvent
import no.nav.reops.event.Session
import no.nav.reops.event.WebsiteEvent
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.config.KafkaListenerEndpointRegistry
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.kafka.test.utils.ContainerTestUtils
import org.springframework.messaging.support.MessageBuilder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.Duration
import java.time.Instant
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(
    partitions = 1,
    topics = ["test-topic", "test-topic-dlt", "test-website-topic"],
    bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
class DltHandlerTest {

    @MockitoBean
    lateinit var bigQueryWriteClient: BigQueryWriteClient

    @MockitoBean
    lateinit var bigQueryService: BigQueryService

    @Autowired
    lateinit var kafkaTemplate: KafkaTemplate<String, EnrichedEvent>

    @Autowired
    lateinit var registry: KafkaListenerEndpointRegistry

    @Autowired
    lateinit var meterRegistry: MeterRegistry

    @BeforeEach
    fun waitForKafkaListener() {
        registry.listenerContainers.forEach { container ->
            ContainerTestUtils.waitForAssignment(container, 1)
        }
    }

    @Test
    fun `DLT handler is invoked after retries are exhausted`() {
        // BigQuery always throws -> batchListen throws -> retries exhausted -> DLT
        doThrow(RuntimeException("BigQuery unavailable")).whenever(bigQueryService).sendEvents(any())

        val websiteId = UUID.randomUUID()
        val event = EnrichedEvent(
            websiteId = websiteId,
            sessionId = "abc123",
            createdAt = Instant.now(),
            websiteEvent = WebsiteEvent(
                eventId = UUID.randomUUID(),
                eventName = null,
                eventType = 1,
                urlPath = "/test",
                pageTitle = "Test",
                referrerPath = null,
                referrerDomain = null,
                visitId = UUID.randomUUID(),
                hostname = "localhost"
            ),
            session = Session(
                browser = "Chrome",
                os = "macOS",
                device = "desktop",
                screen = "1920x1080",
                language = "nb",
                country = "NO"
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
