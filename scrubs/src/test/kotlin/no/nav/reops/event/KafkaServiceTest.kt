package no.nav.reops.event

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.reops.filter.FilterService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeaders
import org.apache.kafka.common.record.TimestampType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CompletableFuture

class KafkaServiceTest {
    private lateinit var filterService: FilterService
    private lateinit var kafkaTemplate: KafkaTemplate<String, Event>
    private lateinit var meterRegistry: SimpleMeterRegistry
    private lateinit var kafkaService: KafkaService

    @BeforeEach
    fun setUp() {
        filterService = mock()
        kafkaTemplate = mock()
        meterRegistry = SimpleMeterRegistry()
        kafkaService = KafkaService(filterService, kafkaTemplate, "test-output-topic", meterRegistry)
    }

    @Test
    fun `eventListen - success increments success counter and calls services`() {
        val event = sampleEvent(name = null)
        val filteredEvent = sampleEvent(name = "besøk")

        val key = "key"
        val userAgent = "Mozilla/5.0 (Linux; Android 10; K)"
        val forwardedFor = "127.0.0.1"

        whenever(filterService.filterEvent(eq(event), isNull())).thenReturn(filteredEvent)
        whenever(kafkaTemplate.send(any<ProducerRecord<String, Event>>()))
            .thenReturn(CompletableFuture.completedFuture(mock<SendResult<String, Event>>()))

        val record = consumerRecord(key = key, value = event)

        kafkaService.eventListen(
            event = event,
            key = key,
            userAgent = userAgent,
            optOutFilters = null,
            forwardedFor = forwardedFor,
            record = record
        )

        verify(filterService).filterEvent(eq(event), isNull())

        verify(kafkaTemplate).send(
            check<ProducerRecord<String, Event>> { sent ->
                assertThat(sent.topic()).isEqualTo("test-output-topic")
                assertThat(sent.key()).isEqualTo(key)
                assertThat(sent.value().type).isEqualTo("event")
                assertThat(sent.value().payload.website).isEqualTo(filteredEvent.payload.website)
                assertThat(sent.value().payload.name).isEqualTo("besøk")
                assertThat(String(sent.headers().lastHeader(USER_AGENT).value())).isEqualTo(userAgent)
                assertThat(String(sent.headers().lastHeader(FORWARDED_FOR).value())).isEqualTo(forwardedFor)
            }
        )

        val success = meterRegistry.find("kafka_events_processed_total").tag("result", "success").counter()
        val failure = meterRegistry.find("kafka_events_processed_total").tag("result", "failure").counter()

        assertThat(success?.count()).isEqualTo(1.0)
        assertThat(failure?.count()).isEqualTo(0.0)
    }

    @Test
    fun `eventListen - success with defaults`() {
        val event = sampleEvent(name = null)
        val filteredEvent = sampleEvent(name = null)

        val key = "key"
        val userAgent = "Mozilla/5.0 (Linux; Android 10; K)"
        val forwardedFor = "127.0.0.1"

        whenever(filterService.filterEvent(eq(event), isNull())).thenReturn(filteredEvent)
        whenever(kafkaTemplate.send(any<ProducerRecord<String, Event>>()))
            .thenReturn(CompletableFuture.completedFuture(mock<SendResult<String, Event>>()))

        val record = consumerRecord(key = key, value = event)

        kafkaService.eventListen(
            event = event,
            key = key,
            userAgent = userAgent,
            optOutFilters = null,
            forwardedFor = forwardedFor,
            record = record
        )

        verify(filterService).filterEvent(eq(event), isNull())

        verify(kafkaTemplate).send(
            check<ProducerRecord<String, Event>> { sent ->
                assertThat(sent.topic()).isEqualTo("test-output-topic")
                assertThat(sent.key()).isEqualTo(key)
                assertThat(sent.value().type).isEqualTo("event")
                assertThat(sent.value().payload.website).isEqualTo(filteredEvent.payload.website)
                assertThat(sent.value().payload.name).isNull()
            }
        )

        val success = meterRegistry.find("kafka_events_processed_total").tag("result", "success").counter()
        val failure = meterRegistry.find("kafka_events_processed_total").tag("result", "failure").counter()

        assertThat(success?.count()).isEqualTo(1.0)
        assertThat(failure?.count()).isEqualTo(0.0)
    }

    @Test
    fun `eventListen - exception increments failure counter and rethrows`() {
        val event = sampleEvent(name = null)

        val key = "key"
        val userAgent = "Mozilla/5.0 (Linux; Android 10; K)"
        val forwardedFor = "127.0.0.1"

        whenever(filterService.filterEvent(eq(event), isNull())).thenThrow(RuntimeException("boom"))

        val record = consumerRecord(key = key, value = event)

        assertThatThrownBy {
            kafkaService.eventListen(
                event = event,
                key = key,
                userAgent = userAgent,
                optOutFilters = null,
                forwardedFor = forwardedFor,
                record = record
            )
        }.isInstanceOf(RuntimeException::class.java)

        verify(filterService).filterEvent(eq(event), isNull())
        verify(kafkaTemplate, never()).send(any<ProducerRecord<String, Event>>())

        val success = meterRegistry.find("kafka_events_processed_total").tag("result", "success").counter()
        val failure = meterRegistry.find("kafka_events_processed_total").tag("result", "failure").counter()

        assertThat(success?.count()).isEqualTo(0.0)
        assertThat(failure?.count()).isEqualTo(1.0)
    }

    @Test
    fun `eventListen - identify event preserves type through pipeline`() {
        val event = identifyEvent()
        val filteredEvent = identifyEvent()

        val key = "key"
        val userAgent = "Mozilla/5.0"
        val forwardedFor = "127.0.0.1"

        whenever(filterService.filterEvent(eq(event), isNull())).thenReturn(filteredEvent)
        whenever(kafkaTemplate.send(any<ProducerRecord<String, Event>>()))
            .thenReturn(CompletableFuture.completedFuture(mock<SendResult<String, Event>>()))

        val record = consumerRecord(key = key, value = event)

        kafkaService.eventListen(
            event = event,
            key = key,
            userAgent = userAgent,
            optOutFilters = null,
            forwardedFor = forwardedFor,
            record = record
        )

        verify(kafkaTemplate).send(
            check<ProducerRecord<String, Event>> { sent ->
                assertThat(sent.value().type).isEqualTo("identify")
                assertThat(sent.value().payload.id).isEqualTo("1ba86cead5f21fbfa7d355fa482f5808bfd5460a")
            }
        )
    }

    private fun consumerRecord(key: String, value: Event): ConsumerRecord<String, Event> = ConsumerRecord(
        "topic", 0, 0L, 0L, TimestampType.CREATE_TIME, 0, 0, key, value, RecordHeaders(), Optional.empty()
    )

    private fun sampleEvent(name: String?): Event = Event(
        type = "event", payload = Event.Payload(
            website = UUID.fromString("1dbfe4e9-bf8b-45d9-9305-928f22200bc0"),
            hostname = "felgen.intern.nav.no",
            screen = "172x111",
            language = "en-US",
            title = "Nav Webapps | Snarveier",
            url = "/path/to/thing",
            referrer = "",
            name = name
        )
    )

    private fun identifyEvent(): Event = Event(
        type = "identify", payload = Event.Payload(
            website = UUID.fromString("1dbfe4e9-bf8b-45d9-9305-928f22200bc0"),
            id = "1ba86cead5f21fbfa7d355fa482f5808bfd5460a",
            hostname = "felgen.ansatt.nav.no",
            screen = "1280x800",
            language = "nb",
            title = "kake",
            url = "felgen.ansatt.nav.no/greier"
        )
    )
}