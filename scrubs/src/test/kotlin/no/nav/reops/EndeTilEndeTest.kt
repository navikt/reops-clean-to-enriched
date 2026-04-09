package no.nav.reops

import no.nav.reops.event.Event
import no.nav.reops.event.FORWARDED_FOR
import no.nav.reops.event.USER_AGENT
import org.apache.kafka.clients.consumer.Consumer
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
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.kafka.test.utils.ContainerTestUtils
import org.springframework.kafka.test.utils.KafkaTestUtils
import org.springframework.messaging.support.MessageBuilder
import org.springframework.test.context.ActiveProfiles
import tools.jackson.databind.node.JsonNodeFactory
import java.time.Duration
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(
    partitions = 1,
    topics = ["test-topic", "test-topic-retry", "test-topic-dlt", "test-output-topic"],
    bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
class EndeTilEndeTest {

    @Autowired
    lateinit var kafkaTemplate: KafkaTemplate<String, Event>

    @Autowired
    lateinit var registry: KafkaListenerEndpointRegistry

    @Autowired
    lateinit var embeddedKafkaBroker: EmbeddedKafkaBroker

    private lateinit var outputConsumer: Consumer<String, String>

    @BeforeEach
    fun setUp() {
        registry.listenerContainers.forEach { container ->
            ContainerTestUtils.waitForAssignment(container, 1)
        }

        val consumerProps = mapOf<String, Any>(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to embeddedKafkaBroker.brokersAsString,
            ConsumerConfig.GROUP_ID_CONFIG to "test-output-group",
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to true,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java
        )
        outputConsumer = DefaultKafkaConsumerFactory<String, String>(consumerProps).createConsumer()
        outputConsumer.subscribe(listOf("test-output-topic"))
    }

    @AfterEach
    fun tearDown() {
        outputConsumer.close()
    }

    @Test
    fun `skal kunne konsumere hendelse på kafka uten data`() {
        val event = Event(
            type = "visit", payload = Event.Payload(
                website = UUID.randomUUID(),
                hostname = "localhost",
                screen = "12345678910",
                language = "nb",
                title = "john.doe@kake.no",
                url = "https://kake.no/12345678910",
                referrer = "https://kake.no/"
            )
        )

        val message = MessageBuilder.withPayload(event).setHeader(KafkaHeaders.TOPIC, "test-topic")
            .setHeader(KafkaHeaders.KEY, "event-key").setHeader(USER_AGENT, "test-agent")
            .setHeader(FORWARDED_FOR, "127.0.0.1").build()

        kafkaTemplate.send(message)

        await().atMost(Duration.ofSeconds(10)).untilAsserted {
            val records = KafkaTestUtils.getRecords(outputConsumer, Duration.ofSeconds(1))
            assertThat(records.count()).isGreaterThanOrEqualTo(1)
            val record = records.last()
            val body = record.value()
            assertThat(body).doesNotContain("12345678910")
            assertThat(body).doesNotContain("john.doe@kake.no")
            assertThat(body).contains("[PROXY-EMAIL]")
            assertThat(body).contains("https://kake.no/[PROXY-FNR]")
            assertThat(body).contains("https://kake.no/")
        }
    }

    @Test
    fun `skal kunne konsumere hendelse på kafka med data`() {
        val data =
            JsonNodeFactory.instance.objectNode().put("hest", "er best").put("antall", 42).put("liker-hest", true)

        val event = Event(
            type = "visit", payload = Event.Payload(
                website = UUID.randomUUID(),
                hostname = "localhost",
                screen = "12345678910",
                language = "nb",
                title = "john.doe@kake.no",
                url = "https://kake.no/12345678910",
                referrer = "https://kake.no/",
                data = data
            )
        )

        val message = MessageBuilder.withPayload(event).setHeader(KafkaHeaders.TOPIC, "test-topic")
            .setHeader(KafkaHeaders.KEY, "event-key").setHeader(USER_AGENT, "test-agent")
            .setHeader(FORWARDED_FOR, "127.0.0.1").build()

        kafkaTemplate.send(message)

        await().atMost(Duration.ofSeconds(10)).untilAsserted {
            val records = KafkaTestUtils.getRecords(outputConsumer, Duration.ofSeconds(1))
            assertThat(records.count()).isGreaterThanOrEqualTo(1)
            val record = records.last()
            val body = record.value()
            assertThat(body).doesNotContain("12345678910")
            assertThat(body).doesNotContain("john.doe@kake.no")
            assertThat(body).contains("[PROXY-EMAIL]")
            assertThat(body).contains("https://kake.no/[PROXY-FNR]")
            assertThat(body).contains("https://kake.no/")
            assertThat(body).contains("\"hest\"")
            assertThat(body).contains("\"antall\"")
            assertThat(body).contains("\"liker-hest\"")
        }
    }
}