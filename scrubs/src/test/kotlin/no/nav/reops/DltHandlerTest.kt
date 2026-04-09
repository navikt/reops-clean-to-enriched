package no.nav.reops

import io.micrometer.core.instrument.MeterRegistry
import no.nav.reops.event.Event
import no.nav.reops.event.FORWARDED_FOR
import no.nav.reops.event.USER_AGENT
import no.nav.reops.filter.FilterService
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
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
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(
    partitions = 1,
    topics = ["test-topic", "test-topic-retry", "test-topic-dlt", "test-output-topic"],
    bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
class DltHandlerTest {

    @MockitoBean
    lateinit var filterService: FilterService

    @Autowired
    lateinit var kafkaTemplate: KafkaTemplate<String, Event>

    @Autowired
    lateinit var registry: KafkaListenerEndpointRegistry

    @Autowired
    lateinit var meterRegistry: MeterRegistry

    @BeforeEach
    fun setUp() {
        registry.listenerContainers.forEach { container ->
            ContainerTestUtils.waitForAssignment(container, 1)
        }
        // Make filterService always throw so eventListen fails and retries are exhausted
        whenever(filterService.filterEvent(any(), anyOrNull())).thenThrow(RuntimeException("boom"))
    }

    @Test
    fun `DLT handler is invoked after retries are exhausted`() {
        val event = Event(
            type = "visit", payload = Event.Payload(
                website = UUID.randomUUID(),
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
            .setHeader(USER_AGENT, "test-agent")
            .setHeader(FORWARDED_FOR, "127.0.0.1")
            .build()

        kafkaTemplate.send(message)

        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(500)).untilAsserted {
            val dltCount = meterRegistry.find("kafka_events_dlt_total").counter()
            assertThat(dltCount).isNotNull
            assertThat(dltCount!!.count()).isGreaterThanOrEqualTo(1.0)
        }
    }
}
