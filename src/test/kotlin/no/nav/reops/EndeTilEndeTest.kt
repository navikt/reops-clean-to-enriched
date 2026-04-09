package no.nav.reops

import no.nav.reops.event.EnrichedEvent
import no.nav.reops.event.Event
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.config.KafkaListenerEndpointRegistry
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.listener.KafkaMessageListenerContainer
import org.springframework.kafka.listener.MessageListener
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.kafka.test.utils.ContainerTestUtils
import org.springframework.messaging.support.MessageBuilder
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import tools.jackson.databind.node.JsonNodeFactory
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext
@EmbeddedKafka(
    partitions = 1,
    topics = ["test-topic", "test-topic-retry", "test-topic-dlt", "test-output-topic", "test-website-topic"],
    bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
class EndeTilEndeTest {

    @Autowired
    lateinit var kafkaTemplate: KafkaTemplate<String, Event>

    @Autowired
    lateinit var registry: KafkaListenerEndpointRegistry

    @Autowired
    lateinit var embeddedKafka: EmbeddedKafkaBroker

    @Autowired
    lateinit var websiteRepository: no.nav.reops.website.WebsiteRepository

    @Autowired
    lateinit var cacheManager: org.springframework.cache.CacheManager

    private lateinit var outputContainer: KafkaMessageListenerContainer<String, EnrichedEvent>
    private val receivedMessages = CopyOnWriteArrayList<EnrichedEvent>()

    @BeforeEach
    fun setUp() {
        // Wait until the @KafkaListener container has partitions assigned
        registry.listenerContainers.forEach { container ->
            ContainerTestUtils.waitForAssignment(container, 1)
        }

        // Set up a consumer to read from the output topic
        val deserializer = JacksonJsonDeserializer(EnrichedEvent::class.java)
        deserializer.addTrustedPackages("*")

        val consumerProps = mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to embeddedKafka.brokersAsString,
            ConsumerConfig.GROUP_ID_CONFIG to "test-output-consumer",
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to JacksonJsonDeserializer::class.java,
        )
        val consumerFactory = DefaultKafkaConsumerFactory(
            consumerProps, StringDeserializer(), deserializer
        )

        val containerProps = ContainerProperties("test-output-topic")
        containerProps.setMessageListener(MessageListener<String, EnrichedEvent> { record ->
            receivedMessages.add(record.value())
        })

        outputContainer = KafkaMessageListenerContainer(consumerFactory, containerProps)
        outputContainer.start()
        ContainerTestUtils.waitForAssignment(outputContainer, 1)
    }

    @AfterEach
    fun tearDown() {
        outputContainer.stop()
        receivedMessages.clear()
    }

    @Test
    fun `page view produces enriched event with eventType 1 and correct session`() {
        val websiteId = UUID.randomUUID()
        registerWebsite(websiteId)

        val event = Event(
            type = "visit", payload = Event.Payload(
                website = websiteId,
                hostname = "localhost",
                screen = "1920x1080",
                language = "nb",
                title = "Testside",
                url = "https://example.no/test",
                referrer = "https://example.no/"
            )
        )

        val message = MessageBuilder.withPayload(event)
            .setHeader(KafkaHeaders.TOPIC, "test-topic")
            .setHeader(KafkaHeaders.KEY, "event-key")
            .build()

        kafkaTemplate.send(message)

        await().atMost(Duration.ofSeconds(10)).untilAsserted {
            assertThat(receivedMessages).anyMatch { enriched ->
                enriched.websiteId == websiteId
                        && enriched.createdAt != null
                        && enriched.websiteEvent.eventType == 1
                        && enriched.websiteEvent.eventName == null
                        && enriched.websiteEvent.urlPath == "/test"
                        && enriched.websiteEvent.referrerPath == "/"
                        && enriched.websiteEvent.referrerDomain == "example.no"
                        && enriched.sessionId != null
                        && enriched.websiteEvent.pageTitle == "Testside"
                        && enriched.websiteEvent.eventId != null
                        && enriched.websiteEvent.visitId != null
                        && enriched.websiteEvent.hostname == "localhost"
                        && enriched.session.screen == "1920x1080"
                        && enriched.session.language == "nb"
                        && enriched.sessionId != null
                        && enriched.websiteEvent.eventData == null
            }
        }
    }

    @Test
    fun `custom event with data produces enriched event with eventType 2 and eventData`() {
        val websiteId = UUID.randomUUID()
        registerWebsite(websiteId)

        val data =
            JsonNodeFactory.instance.objectNode().put("hest", "er best").put("antall", 42).put("liker-hest", true)

        val event = Event(
            type = "event", payload = Event.Payload(
                website = websiteId,
                hostname = "localhost",
                screen = "1920x1080",
                language = "nb",
                title = "Testside",
                url = "https://example.no/test",
                referrer = "https://example.no/",
                name = "my-event",
                data = data
            )
        )

        val message = MessageBuilder.withPayload(event)
            .setHeader(KafkaHeaders.TOPIC, "test-topic")
            .setHeader(KafkaHeaders.KEY, "event-key")
            .build()

        kafkaTemplate.send(message)

        await().atMost(Duration.ofSeconds(10)).untilAsserted {
            assertThat(receivedMessages).anyMatch { enriched ->
                enriched.websiteId == websiteId
                        && enriched.websiteEvent.eventType == 2
                        && enriched.websiteEvent.eventName == "my-event"
                        && enriched.websiteEvent.eventData != null
                        && enriched.websiteEvent.eventData!!.any { it.dataKey == "hest" && it.dataType == 1 && it.stringValue == "er best" }
                        && enriched.websiteEvent.eventData!!.any { it.dataKey == "antall" && it.dataType == 2 && it.numberValue == 42.0 }
                        && enriched.websiteEvent.eventData!!.any { it.dataKey == "liker-hest" && it.dataType == 2 && it.numberValue == 1.0 }
            }
        }
    }

    @Test
    fun `events from unregistered websiteId are filtered out`() {
        val unregisteredWebsiteId = UUID.randomUUID()
        val event = Event(
            type = "visit", payload = Event.Payload(
                website = unregisteredWebsiteId,
                hostname = "localhost",
                screen = "1920x1080",
                language = "nb",
                title = "Should be filtered",
                url = "https://example.no/filtered"
            )
        )

        val message = MessageBuilder.withPayload(event)
            .setHeader(KafkaHeaders.TOPIC, "test-topic")
            .setHeader(KafkaHeaders.KEY, "filtered-key")
            .build()

        kafkaTemplate.send(message)

        // Wait a bit and verify no enriched event was produced for the unregistered websiteId
        Thread.sleep(3000)
        assertThat(receivedMessages).noneMatch { it.websiteId == unregisteredWebsiteId }
    }

    private fun registerWebsite(websiteId: UUID) {
        val entity = no.nav.reops.website.WebsiteEntity(
            websiteId = websiteId,
            name = "Test Website",
            domain = "test.example.no",
            updatedAt = java.time.Instant.now()
        )
        websiteRepository.save(entity)
        cacheManager.cacheNames.forEach { cacheManager.getCache(it)?.clear() }
    }
}