package no.nav.reops.event

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.reops.geo.GeoIpService
import no.nav.reops.session.SessionService
import no.nav.reops.useragent.UserAgentInfo
import no.nav.reops.useragent.UserAgentService
import no.nav.reops.website.WebsiteService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.internals.RecordHeaders
import org.apache.kafka.common.record.TimestampType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.kafka.core.KafkaTemplate
import tools.jackson.databind.node.JsonNodeFactory
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CompletableFuture

class KafkaServiceTest {
    private lateinit var enrichedEventKafkaTemplate: KafkaTemplate<String, EnrichedEvent>
    private lateinit var geoIpService: GeoIpService
    private lateinit var userAgentService: UserAgentService
    private lateinit var websiteService: WebsiteService
    private lateinit var sessionService: SessionService
    private lateinit var meterRegistry: SimpleMeterRegistry
    private lateinit var kafkaService: KafkaService

    private val websiteId = UUID.fromString("1dbfe4e9-bf8b-45d9-9305-928f22200bc0")

    @BeforeEach
    fun setUp() {
        enrichedEventKafkaTemplate = mock()
        geoIpService = mock()
        userAgentService = mock()
        websiteService = mock()
        sessionService = mock()
        meterRegistry = SimpleMeterRegistry()
        kafkaService = KafkaService(enrichedEventKafkaTemplate, geoIpService, userAgentService, websiteService, sessionService, "output-topic", "test-secret", meterRegistry)

        whenever(userAgentService.parse(anyOrNull(), anyOrNull())).thenReturn(UserAgentInfo())
        whenever(websiteService.isAllowed(any())).thenReturn(true)
        whenever(sessionService.checkAndRegister(any(), any(), any())).thenReturn(true)
    }

    @Test
    fun `page view without event name produces eventType 1`() {
        val event = sampleEvent(name = null)
        val key = "key"
        val record = consumerRecord(key = key, value = event)

        whenever(geoIpService.lookupCountry(any())).thenReturn(null)
        whenever(enrichedEventKafkaTemplate.send(eq("output-topic"), eq(key), any())).thenReturn(
            CompletableFuture.completedFuture(null)
        )

        kafkaService.eventListen(event = event, key = key, record = record)

        verify(enrichedEventKafkaTemplate).send(eq("output-topic"), eq(key), check { enriched ->
            // website_event
            assertThat(enriched.websiteEvent.eventId).isNotNull()
            assertThat(enriched.websiteId).isEqualTo(websiteId)
            assertThat(enriched.websiteEvent.eventType).isEqualTo(1)
            assertThat(enriched.websiteEvent.eventName).isNull()
            assertThat(enriched.websiteEvent.urlPath).isEqualTo("/path/to/thing")
            assertThat(enriched.websiteEvent.pageTitle).isEqualTo("Nav Webapps | Snarveier")
            assertThat(enriched.websiteEvent.referrerPath).isEqualTo("")
            assertThat(enriched.createdAt).isNotNull()
            assertThat(enriched.sessionId).isNotNull()
            assertThat(enriched.websiteEvent.visitId).isNotNull()
            assertThat(enriched.websiteEvent.hostname).isEqualTo("felgen.intern.nav.no")

            // session
            assertThat(enriched.websiteId).isEqualTo(websiteId)
            assertThat(enriched.session.screen).isEqualTo("172x111")
            assertThat(enriched.session.language).isEqualTo("en")
            assertThat(enriched.session.country).isNull()
            assertThat(enriched.sessionId).isNotNull()
            assertThat(enriched.createdAt).isNotNull()

            // event_data
            assertThat(enriched.websiteEvent.eventData).isNull()
        })

        assertThat(meterRegistry.find("kafka_events_processed_total").tag("result", "success").counter()?.count()).isEqualTo(1.0)
        assertThat(meterRegistry.find("kafka_events_processed_total").tag("result", "failure").counter()?.count()).isEqualTo(0.0)
    }

    @Test
    fun `country is resolved from forwarded-for header`() {
        val event = sampleEvent(name = null)
        val key = "key"
        val record = consumerRecord(key = key, value = event)

        whenever(geoIpService.lookupCountry("8.8.8.8")).thenReturn("US")
        whenever(enrichedEventKafkaTemplate.send(eq("output-topic"), eq(key), any())).thenReturn(
            CompletableFuture.completedFuture(null)
        )

        kafkaService.eventListen(event = event, key = key, record = consumerRecord(key = key, value = event, forwardedFor = "8.8.8.8"))

        verify(enrichedEventKafkaTemplate).send(eq("output-topic"), eq(key), check { enriched ->
            assertThat(enriched.session.country).isEqualTo("US")
        })
    }

    @Test
    fun `custom event with name produces eventType 2 with trimmed name`() {
        val event = sampleEvent(name = "  besøk  ")
        val key = "key"
        val record = consumerRecord(key = key, value = event)

        whenever(geoIpService.lookupCountry(any())).thenReturn(null)
        whenever(enrichedEventKafkaTemplate.send(eq("output-topic"), eq(key), any())).thenReturn(
            CompletableFuture.completedFuture(null)
        )

        kafkaService.eventListen(event = event, key = key, record = record)

        verify(enrichedEventKafkaTemplate).send(eq("output-topic"), eq(key), check { enriched ->
            assertThat(enriched.websiteEvent.eventType).isEqualTo(2)
            assertThat(enriched.websiteEvent.eventName).isEqualTo("besøk")
        })
    }

    @Test
    fun `blank name becomes null and produces eventType 1`() {
        val event = sampleEvent(name = "   ")
        val key = "key"
        val record = consumerRecord(key = key, value = event)

        whenever(geoIpService.lookupCountry(any())).thenReturn(null)
        whenever(enrichedEventKafkaTemplate.send(eq("output-topic"), eq(key), any())).thenReturn(
            CompletableFuture.completedFuture(null)
        )

        kafkaService.eventListen(event = event, key = key, record = record)

        verify(enrichedEventKafkaTemplate).send(eq("output-topic"), eq(key), check { enriched ->
            assertThat(enriched.websiteEvent.eventType).isEqualTo(1)
            assertThat(enriched.websiteEvent.eventName).isNull()
        })
    }

    @Test
    fun `custom data is mapped to typed EventDataValue entries`() {
        val data = JsonNodeFactory.instance.objectNode()
            .put("action", "click")
            .put("count", 42)
            .put("enabled", true)
        val event = sampleEvent(name = "action", data = data)
        val key = "key"
        val record = consumerRecord(key = key, value = event)

        whenever(geoIpService.lookupCountry(any())).thenReturn(null)
        whenever(enrichedEventKafkaTemplate.send(eq("output-topic"), eq(key), any())).thenReturn(
            CompletableFuture.completedFuture(null)
        )

        kafkaService.eventListen(event = event, key = key, record = record)

        verify(enrichedEventKafkaTemplate).send(eq("output-topic"), eq(key), check { enriched ->
            assertThat(enriched.websiteEvent.eventData).isNotNull
            assertThat(enriched.websiteEvent.eventData).hasSize(3)

            val action = enriched.websiteEvent.eventData!!.find { it.dataKey == "action" }!!
            assertThat(action.dataType).isEqualTo(1) // string
            assertThat(action.stringValue).isEqualTo("click")
            assertThat(action.eventDataId).isNotNull()

            val count = enriched.websiteEvent.eventData!!.find { it.dataKey == "count" }!!
            assertThat(count.dataType).isEqualTo(2) // number
            assertThat(count.numberValue).isEqualTo(42.0)

            val enabled = enriched.websiteEvent.eventData!!.find { it.dataKey == "enabled" }!!
            assertThat(enabled.dataType).isEqualTo(2) // boolean -> number
            assertThat(enabled.numberValue).isEqualTo(1.0)
        })
    }

    @Test
    fun `exception increments failure counter and rethrows`() {
        val event = sampleEvent(name = null)
        val key = "key"
        val record = consumerRecord(key = key, value = event)

        whenever(geoIpService.lookupCountry(any())).thenReturn(null)
        whenever(enrichedEventKafkaTemplate.send(eq("output-topic"), eq(key), any())).thenThrow(RuntimeException("boom"))

        assertThatThrownBy {
            kafkaService.eventListen(event = event, key = key, record = record)
        }.isInstanceOf(RuntimeException::class.java)

        assertThat(meterRegistry.find("kafka_events_processed_total").tag("result", "success").counter()?.count()).isEqualTo(0.0)
        assertThat(meterRegistry.find("kafka_events_processed_total").tag("result", "failure").counter()?.count()).isEqualTo(1.0)
    }

    @Test
    fun `bot events are silently dropped and counted`() {
        val event = sampleEvent(name = null)
        val key = "key"
        val record = consumerRecord(key = key, value = event)

        whenever(geoIpService.lookupCountry(anyOrNull())).thenReturn(null)
        whenever(userAgentService.parse(anyOrNull(), anyOrNull())).thenReturn(UserAgentInfo(isBot = true))

        kafkaService.eventListen(event = event, key = key, record = consumerRecord(key = key, value = event, userAgent = "Googlebot/2.1"))

        verify(enrichedEventKafkaTemplate, never()).send(any<String>(), any<String>(), any())
        assertThat(meterRegistry.find("kafka_events_bot_filtered_total").counter()?.count()).isEqualTo(1.0)
        assertThat(meterRegistry.find("kafka_events_processed_total").tag("result", "success").counter()?.count()).isEqualTo(0.0)
    }

    @Test
    fun `events from unregistered websiteId are filtered`() {
        val event = sampleEvent(name = null)
        val key = "key"
        val record = consumerRecord(key = key, value = event)

        whenever(websiteService.isAllowed(any())).thenReturn(false)

        kafkaService.eventListen(event = event, key = key, record = record)

        verify(enrichedEventKafkaTemplate, never()).send(any<String>(), any<String>(), any())
        assertThat(meterRegistry.find("kafka_events_unregistered_website_total").counter()?.count()).isEqualTo(1.0)
    }

    private fun consumerRecord(
        key: String,
        value: Event,
        forwardedFor: String? = null,
        userAgent: String? = null
    ): ConsumerRecord<String, Event> {
        val headers = RecordHeaders()
        forwardedFor?.let { headers.add(FORWARDED_FOR, it.toByteArray()) }
        userAgent?.let { headers.add(USER_AGENT, it.toByteArray()) }
        return ConsumerRecord("topic", 0, 0L, 0L, TimestampType.CREATE_TIME, 0, 0, key, value, headers, Optional.empty())
    }

    private fun sampleEvent(name: String?, data: tools.jackson.databind.JsonNode? = null): Event = Event(
        type = "event", payload = Event.Payload(
            website = websiteId,
            hostname = "felgen.intern.nav.no",
            screen = "172x111",
            language = "en-US",
            title = "Nav Webapps | Snarveier",
            url = "/path/to/thing",
            referrer = "",
            name = name,
            data = data
        )
    )
}