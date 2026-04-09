package no.nav.reops.event

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.reops.bigquery.BigQueryService
import no.nav.reops.event.Website
import no.nav.reops.event.WebsiteChangeEvent
import no.nav.reops.event.WebsiteEventStatus
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
    private lateinit var bigQueryService: BigQueryService
    private lateinit var meterRegistry: SimpleMeterRegistry
    private lateinit var kafkaService: KafkaService

    @BeforeEach
    fun setUp() {
        bigQueryService = mock()
        meterRegistry = SimpleMeterRegistry()
        kafkaService = KafkaService(bigQueryService, meterRegistry)
    }

    @Test
    fun `batchListen - success increments success counter and calls BigQueryService`() {
        val event = sampleEnrichedEvent(eventName = "besøk")
        val records = listOf(consumerRecord(key = "key", value = event))

        kafkaService.batchListen(records)

        verify(bigQueryService).sendEvents(
            check { sent ->
                assertThat(sent).hasSize(1)
                assertThat(sent[0].websiteId).isEqualTo(event.websiteId)
                assertThat(sent[0].websiteEvent.eventName).isEqualTo("besøk")
            }
        )

        val success = meterRegistry.find("kafka_events_processed_total").tag("result", "success").counter()
        val failure = meterRegistry.find("kafka_events_processed_total").tag("result", "failure").counter()

        assertThat(success?.count()).isEqualTo(1.0)
        assertThat(failure?.count()).isEqualTo(0.0)
    }

    @Test
    fun `batchListen - multiple events are batched together`() {
        val event1 = sampleEnrichedEvent(eventName = "besøk")
        val event2 = sampleEnrichedEvent(eventName = null)
        val records = listOf(
            consumerRecord(key = "key1", value = event1),
            consumerRecord(key = "key2", value = event2)
        )

        kafkaService.batchListen(records)

        verify(bigQueryService).sendEvents(
            check { sent ->
                assertThat(sent).hasSize(2)
                assertThat(sent[0].websiteEvent.eventName).isEqualTo("besøk")
                assertThat(sent[1].websiteEvent.eventName).isNull()
            }
        )

        val success = meterRegistry.find("kafka_events_processed_total").tag("result", "success").counter()
        assertThat(success?.count()).isEqualTo(2.0)
    }

    @Test
    fun `batchListen - exception increments failure counter and rethrows`() {
        val event = sampleEnrichedEvent(eventName = null)
        doThrow(RuntimeException("boom")).whenever(bigQueryService).sendEvents(any())

        val records = listOf(consumerRecord(key = "key", value = event))

        assertThatThrownBy {
            kafkaService.batchListen(records)
        }.isInstanceOf(RuntimeException::class.java)

        val success = meterRegistry.find("kafka_events_processed_total").tag("result", "success").counter()
        val failure = meterRegistry.find("kafka_events_processed_total").tag("result", "failure").counter()

        assertThat(success?.count()).isEqualTo(0.0)
        assertThat(failure?.count()).isEqualTo(1.0)
    }

    @Test
    fun `websiteEventListen - success increments counter and calls BigQueryService`() {
        val websiteEvent = WebsiteChangeEvent(
            website = Website(
                websiteId = UUID.randomUUID(),
                name = "Test",
                domain = "test.nav.no",
                updatedAt = Instant.now()
            ),
            status = WebsiteEventStatus.CREATE
        )
        val records = listOf(websiteConsumerRecord(key = "key", value = websiteEvent))

        kafkaService.websiteEventListen(records)

        verify(bigQueryService).sendWebsiteEvents(
            check { sent ->
                assertThat(sent).hasSize(1)
                assertThat(sent[0].website.name).isEqualTo("Test")
                assertThat(sent[0].status).isEqualTo(WebsiteEventStatus.CREATE)
            }
        )

        val success = meterRegistry.find("kafka_website_events_processed_total").tag("result", "success").counter()
        assertThat(success?.count()).isEqualTo(1.0)
    }

    @Test
    fun `websiteEventListen - exception increments failure counter and rethrows`() {
        doThrow(RuntimeException("BQ down")).whenever(bigQueryService).sendWebsiteEvents(any())

        val websiteEvent = WebsiteChangeEvent(
            website = Website(
                websiteId = UUID.randomUUID(),
                name = "Test",
                domain = "test.nav.no",
                updatedAt = Instant.now()
            ),
            status = WebsiteEventStatus.DELETE
        )
        val records = listOf(websiteConsumerRecord(key = "key", value = websiteEvent))

        assertThatThrownBy {
            kafkaService.websiteEventListen(records)
        }.isInstanceOf(RuntimeException::class.java)

        val failure = meterRegistry.find("kafka_website_events_processed_total").tag("result", "failure").counter()
        assertThat(failure?.count()).isEqualTo(1.0)
    }

    private fun consumerRecord(key: String, value: EnrichedEvent): ConsumerRecord<String, EnrichedEvent> = ConsumerRecord(
        "topic", 0, 0L, 0L, TimestampType.CREATE_TIME, 0, 0, key, value, RecordHeaders(), Optional.empty()
    )

    private fun websiteConsumerRecord(key: String, value: WebsiteChangeEvent): ConsumerRecord<String, WebsiteChangeEvent> = ConsumerRecord(
        "website-topic", 0, 0L, 0L, TimestampType.CREATE_TIME, 0, 0, key, value, RecordHeaders(), Optional.empty()
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