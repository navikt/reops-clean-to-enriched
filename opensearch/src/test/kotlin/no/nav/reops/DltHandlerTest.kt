package no.nav.reops

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import io.micrometer.core.instrument.MeterRegistry
import no.nav.reops.event.EnrichedEvent
import no.nav.reops.event.Session
import no.nav.reops.event.WebsiteEvent
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opensearch.protobufs.services.DocumentServiceGrpc
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.config.KafkaListenerEndpointRegistry
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.kafka.test.utils.ContainerTestUtils
import org.springframework.messaging.support.MessageBuilder
import org.springframework.test.context.ActiveProfiles
import org.wiremock.grpc.GrpcExtensionFactory
import org.wiremock.grpc.dsl.WireMockGrpc.json
import org.wiremock.grpc.dsl.WireMockGrpc.method
import org.wiremock.grpc.dsl.WireMockGrpcService
import org.wiremock.spring.ConfigureWireMock
import org.wiremock.spring.EnableWireMock
import org.wiremock.spring.InjectWireMock
import java.time.Duration
import java.time.Instant
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
@EnableWireMock(
    ConfigureWireMock(
        name = "opensearch",
        portProperties = ["reops.opensearch.port"],
        filesUnderClasspath = "wiremock",
        extensionFactories = [GrpcExtensionFactory::class],
    )
)
@EmbeddedKafka(
    partitions = 1,
    topics = ["test-topic", "test-topic-dlt"],
    bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
class DltHandlerTest {

    @InjectWireMock("opensearch")
    lateinit var wiremock: WireMockServer

    @Autowired
    lateinit var kafkaTemplate: KafkaTemplate<String, EnrichedEvent>

    @Autowired
    lateinit var registry: KafkaListenerEndpointRegistry

    @Autowired
    lateinit var meterRegistry: MeterRegistry

    @BeforeEach
    fun setUp() {
        val grpcService = WireMockGrpcService(WireMock(wiremock.port()), DocumentServiceGrpc.SERVICE_NAME)
        // Stub Bulk to return errors so OpenSearchService throws
        grpcService.stubFor(
            method("Bulk").willReturn(json("""{"errors": true, "took": 1}"""))
        )
        registry.listenerContainers.forEach { container ->
            ContainerTestUtils.waitForAssignment(container, 1)
        }
    }

    @Test
    fun `DLT handler is invoked after retries are exhausted`() {
        val event = EnrichedEvent(
            websiteId = UUID.randomUUID(),
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

        kafkaTemplate.send(
            MessageBuilder.withPayload(event)
                .setHeader(KafkaHeaders.TOPIC, "test-topic")
                .setHeader(KafkaHeaders.KEY, "dlt-test-key")
                .build()
        )

        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(500)).untilAsserted {
            val dltCount = meterRegistry.find("kafka_events_dlt_total").counter()
            assertThat(dltCount).isNotNull
            assertThat(dltCount!!.count()).isGreaterThanOrEqualTo(1.0)
        }
    }
}
