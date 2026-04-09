package no.nav.reops.opensearch

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.reops.event.EnrichedEvent
import no.nav.reops.event.Session
import no.nav.reops.event.WebsiteEvent
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.opensearch.protobufs.services.DocumentServiceGrpc
import org.wiremock.grpc.GrpcExtensionFactory
import org.wiremock.grpc.dsl.WireMockGrpc.json
import org.wiremock.grpc.dsl.WireMockGrpc.method
import org.wiremock.grpc.dsl.WireMockGrpcService
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.Instant
import java.util.UUID

class OpenSearchServiceTest {

    private lateinit var wm: WireMockServer
    private lateinit var grpcService: WireMockGrpcService
    private lateinit var channel: ManagedChannel

    @BeforeEach
    fun setUp() {
        val wiremockRoot = javaClass.classLoader.getResource("wiremock")!!.toURI().path
        wm = WireMockServer(
            WireMockConfiguration.options()
                .dynamicPort()
                .withRootDirectory(wiremockRoot)
                .extensions(GrpcExtensionFactory())
        )
        wm.start()
        grpcService = WireMockGrpcService(WireMock(wm.port()), DocumentServiceGrpc.SERVICE_NAME)
        channel = ManagedChannelBuilder.forAddress("localhost", wm.port())
            .usePlaintext()
            .build()
    }

    @AfterEach
    fun tearDown() {
        channel.shutdownNow()
        wm.stop()
    }

    @ParameterizedTest(name = "errors={0} => success={1}, failure={2}")
    @CsvSource(
        "false, 1.0, 0.0",
        "true, 0.0, 1.0",
    )
    fun `opensearch_requests_total counts success and failure correctly`(
        errors: Boolean,
        expectedSuccess: Double,
        expectedFailure: Double,
    ) {
        grpcService.stubFor(
            method("Bulk").willReturn(json("""{"errors": $errors, "took": 1}"""))
        )
        val registry = SimpleMeterRegistry()
        val stub = DocumentServiceGrpc.newBlockingStub(channel)
        val service = OpenSearchService(stub, "events", jacksonObjectMapper(), registry)
        val events = listOf(sampleEvent())

        if (!errors) {
            service.sendEvents(events)
        } else {
            assertThrows<Exception> {
                service.sendEvents(events)
            }
        }

        assertEquals(expectedSuccess, successCounter(registry))
        assertEquals(expectedFailure, failureCounter(registry))
    }

    @Test
    fun `sendEvents batches multiple events into single bulk request`() {
        grpcService.stubFor(
            method("Bulk").willReturn(json("""{"errors": false, "took": 1}"""))
        )
        val registry = SimpleMeterRegistry()
        val stub = DocumentServiceGrpc.newBlockingStub(channel)
        val service = OpenSearchService(stub, "events", jacksonObjectMapper(), registry)
        val events = listOf(sampleEvent(), sampleEvent(), sampleEvent())

        service.sendEvents(events)

        grpcService.verify(1, "Bulk")
            .withRequestMessage(matchingJsonPath("$.bulkRequestBody[2]"))
        assertEquals(3.0, successCounter(registry))
    }

    @Test
    fun `sendEvents with empty list does nothing`() {
        val registry = SimpleMeterRegistry()
        val stub = DocumentServiceGrpc.newBlockingStub(channel)
        val service = OpenSearchService(stub, "events", jacksonObjectMapper(), registry)

        service.sendEvents(emptyList())

        grpcService.verify(0, "Bulk")
        assertEquals(0.0, successCounter(registry))
    }

    private fun sampleEvent() = EnrichedEvent(
        websiteId = UUID.randomUUID(),
        sessionId = "abc123",
        createdAt = Instant.now(),
        websiteEvent = WebsiteEvent(
            eventId = UUID.randomUUID(),
            eventName = null,
            eventType = 1,
            urlPath = "/test",
            pageTitle = "Test Page",
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

    private fun successCounter(registry: SimpleMeterRegistry): Double =
        registry.find("opensearch_requests_total").tag("result", "success").counter()?.count() ?: 0.0

    private fun failureCounter(registry: SimpleMeterRegistry): Double =
        registry.find("opensearch_requests_total").tag("result", "failure").counter()?.count() ?: 0.0
}

