package no.nav.reops

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import no.nav.reops.event.EnrichedEvent
import no.nav.reops.event.EventDataEntry
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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
    partitions = 1, topics = ["test-topic", "test-topic-dlt"], bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
class EndeTilEndeTest {

    @InjectWireMock("opensearch")
    lateinit var wiremock: WireMockServer

    private lateinit var grpcService: WireMockGrpcService

    @Autowired
    lateinit var kafkaTemplate: KafkaTemplate<String, EnrichedEvent>

    @Autowired
    lateinit var registry: KafkaListenerEndpointRegistry

    @BeforeEach
    fun setUp() {
        grpcService = WireMockGrpcService(WireMock(wiremock.port()), DocumentServiceGrpc.SERVICE_NAME)
        grpcService.stubFor(
            method("Bulk").willReturn(json("""{"errors": false, "took": 1}"""))
        )
        registry.listenerContainers.forEach { container ->
            ContainerTestUtils.waitForAssignment(container, 1)
        }
    }

    @Test
    fun `skal kunne konsumere hendelse på kafka uten data`() {
        val event = EnrichedEvent(
            websiteId = UUID.randomUUID(),
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

        kafkaTemplate.send(
            MessageBuilder.withPayload(event)
                .setHeader(KafkaHeaders.TOPIC, "test-topic")
                .setHeader(KafkaHeaders.KEY, "event-key")
                .build()
        )

        val expectedIndex = "events-${LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))}"

        await().atMost(Duration.ofSeconds(10)).untilAsserted {
            grpcService.verify(1, "Bulk")
                .withRequestMessage(
                    WireMock.matchingJsonPath("$.index", WireMock.equalTo(expectedIndex))
                )
        }
    }

    @Test
    fun `skal kunne konsumere hendelse på kafka med data`() {
        val event = EnrichedEvent(
            websiteId = UUID.randomUUID(),
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

        kafkaTemplate.send(
            MessageBuilder.withPayload(event)
                .setHeader(KafkaHeaders.TOPIC, "test-topic")
                .setHeader(KafkaHeaders.KEY, "event-key")
                .build()
        )

        await().atMost(Duration.ofSeconds(10)).untilAsserted {
            grpcService.verify(1, "Bulk")
                .withRequestMessage(
                    WireMock.matchingJsonPath("$.bulkRequestBody[0].operationContainer.index")
                )
        }
    }
}