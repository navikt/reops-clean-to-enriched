package no.nav.reops.event

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.reops.opensearch.OpenSearchService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.internals.RecordHeaders
import org.apache.kafka.common.record.TimestampType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.time.Instant
import java.util.Optional
import java.util.UUID

class KafkaServiceTest {
    private lateinit var openSearchService: OpenSearchService
    private lateinit var meterRegistry: SimpleMeterRegistry
    private lateinit var kafkaService: KafkaService

    @BeforeEach
    fun setUp() {
        openSearchService = mock()
        meterRegistry = SimpleMeterRegistry()
        kafkaService = KafkaService(openSearchService, meterRegistry)
    }

    @Test
    fun `batchListen - success increments success counter by batch size`() {
        val events = listOf(
            sampleEnrichedEvent(eventName = "besøk"),
            sampleEnrichedEvent(eventName = "klikk"),
        )
        val records = events.mapIndexed { i, e -> consumerRecord(key = "key-$i", value = e, offset = i.toLong()) }

        kafkaService.batchListen(records)

        verify(openSearchService).sendEvents(
            check { sent ->
                assertThat(sent).hasSize(2)
                assertThat(sent[0].websiteEvent.eventName).isEqualTo("besøk")
                assertThat(sent[1].websiteEvent.eventName).isEqualTo("klikk")
            }
        )

        val success = meterRegistry.find("kafka_events_processed_total").tag("result", "success").counter()
        val failure = meterRegistry.find("kafka_events_processed_total").tag("result", "failure").counter()

        assertThat(success?.count()).isEqualTo(2.0)
        assertThat(failure?.count()).isEqualTo(0.0)
    }

    @Test
    fun `batchListen - single event batch`() {
        val event = sampleEnrichedEvent(eventName = null)
        val records = listOf(consumerRecord(key = "key", value = event))

        kafkaService.batchListen(records)

        verify(openSearchService).sendEvents(
            check { sent ->
                assertThat(sent).hasSize(1)
                assertThat(sent[0].websiteEvent.eventName).isNull()
            }
        )

        val success = meterRegistry.find("kafka_events_processed_total").tag("result", "success").counter()
        assertThat(success?.count()).isEqualTo(1.0)
    }

    @Test
    fun `batchListen - exception increments failure counter by batch size and rethrows`() {
        doThrow(RuntimeException("boom")).whenever(openSearchService).sendEvents(any())

        val events = listOf(
            sampleEnrichedEvent(eventName = null),
            sampleEnrichedEvent(eventName = null),
            sampleEnrichedEvent(eventName = null),
        )
        val records = events.mapIndexed { i, e -> consumerRecord(key = "key-$i", value = e, offset = i.toLong()) }

        assertThatThrownBy {
            kafkaService.batchListen(records)
        }.isInstanceOf(RuntimeException::class.java)

        val success = meterRegistry.find("kafka_events_processed_total").tag("result", "success").counter()
        val failure = meterRegistry.find("kafka_events_processed_total").tag("result", "failure").counter()

        assertThat(success?.count()).isEqualTo(0.0)
        assertThat(failure?.count()).isEqualTo(3.0)
    }

    private fun consumerRecord(key: String, value: EnrichedEvent, offset: Long = 0L): ConsumerRecord<String, EnrichedEvent> = ConsumerRecord(
        "topic", 0, offset, 0L, TimestampType.CREATE_TIME, 0, 0, key, value, RecordHeaders(), Optional.empty()
    )

    private fun sampleEnrichedEvent(eventName: String?): EnrichedEvent {
        val websiteId = UUID.fromString("1dbfe4e9-bf8b-45d9-9305-928f22200bc0")
        return EnrichedEvent(
            websiteId = websiteId,
            sessionId = "abc123",
            createdAt = Instant.now(),
            websiteEvent = WebsiteEvent(
                eventId = UUID.randomUUID(),
                eventName = eventName,
                eventType = if (eventName != null) 2 else 1,
                urlPath = "/path/to/thing",
                pageTitle = "Nav Webapps | Snarveier",
                referrerPath = null,
                referrerDomain = null,
                visitId = UUID.randomUUID(),
                hostname = "felgen.intern.nav.no"
            ),
            session = Session(
                browser = "Chrome",
                os = "macOS",
                device = "desktop",
                screen = "172x111",
                language = "en",
                country = "NO"
            )
        )
    }
}