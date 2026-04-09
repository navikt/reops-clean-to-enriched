package no.nav.reops

import no.nav.reops.bigquery.BigQueryService
import com.google.cloud.bigquery.storage.v1.BigQueryWriteClient
import no.nav.reops.event.EnrichedEvent
import no.nav.reops.event.EventDataEntry
import no.nav.reops.event.Session
import no.nav.reops.event.WebsiteEvent
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.check
import org.mockito.kotlin.verify
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
    partitions = 1, topics = ["test-topic", "test-topic-dlt", "test-website-topic"], bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
class EndeTilEndeTest {

    @MockitoBean
    lateinit var bigQueryWriteClient: BigQueryWriteClient

    @MockitoBean
    lateinit var bigQueryService: BigQueryService

    @Autowired
    lateinit var kafkaTemplate: KafkaTemplate<String, EnrichedEvent>

    @Autowired
    lateinit var registry: KafkaListenerEndpointRegistry

    @BeforeEach
    fun waitForKafkaListener() {
        // Wait until the @KafkaListener container has partitions assigned
        registry.listenerContainers.forEach { container ->
            ContainerTestUtils.waitForAssignment(container, 1)
        }
    }

    @Test
    fun `skal kunne konsumere hendelse på kafka uten data`() {
        val websiteId = UUID.randomUUID()
        val event = EnrichedEvent(
            websiteId = websiteId,
            sessionId = "abc123",
            createdAt = Instant.now(),
            websiteEvent = WebsiteEvent(
                eventId = UUID.randomUUID(),
                eventName = null,
                eventType = 1,
                urlPath = "/path/to/page",
                pageTitle = "Test Page",
                referrerPath = "/",
                referrerDomain = "example.com",
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

        val message = MessageBuilder.withPayload(event).setHeader(KafkaHeaders.TOPIC, "test-topic")
            .setHeader(KafkaHeaders.KEY, "event-key").build()

        kafkaTemplate.send(message)

        await().atMost(Duration.ofSeconds(10)).untilAsserted {
            verify(bigQueryService).sendEvents(
                check { events ->
                    assertThat(events).isNotEmpty
                    assertThat(events.any { it.websiteId == websiteId }).isTrue()
                }
            )
        }
    }

    @Test
    fun `skal kunne konsumere hendelse på kafka med data`() {
        val websiteId = UUID.randomUUID()
        val event = EnrichedEvent(
            websiteId = websiteId,
            sessionId = "abc123",
            createdAt = Instant.now(),
            websiteEvent = WebsiteEvent(
                eventId = UUID.randomUUID(),
                eventName = "click",
                eventType = 2,
                urlPath = "/path/to/page",
                pageTitle = "Test Page",
                referrerPath = "/",
                referrerDomain = "example.com",
                visitId = UUID.randomUUID(),
                hostname = "localhost",
                eventData = listOf(
                    EventDataEntry(eventDataId = UUID.randomUUID(), dataKey = "hest", dataType = 1, stringValue = "er best"),
                    EventDataEntry(eventDataId = UUID.randomUUID(), dataKey = "antall", dataType = 2, numberValue = 42.0),
                    EventDataEntry(eventDataId = UUID.randomUUID(), dataKey = "liker-hest", dataType = 2, numberValue = 1.0)
                )
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

        val message = MessageBuilder.withPayload(event).setHeader(KafkaHeaders.TOPIC, "test-topic")
            .setHeader(KafkaHeaders.KEY, "event-key").build()

        kafkaTemplate.send(message)

        await().atMost(Duration.ofSeconds(10)).untilAsserted {
            verify(bigQueryService).sendEvents(
                check { events ->
                    assertThat(events).isNotEmpty
                    val sent = events.first { it.websiteId == websiteId }
                    assertThat(sent.websiteEvent.eventData).isNotNull
                    assertThat(sent.websiteEvent.eventData!!.map { it.dataKey }).contains("hest", "antall", "liker-hest")
                }
            )
        }
    }
}