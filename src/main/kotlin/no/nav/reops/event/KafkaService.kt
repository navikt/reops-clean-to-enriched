package no.nav.reops.event

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import no.nav.reops.geo.GeoIpService
import no.nav.reops.session.SessionService
import no.nav.reops.useragent.UserAgentService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.annotation.DltHandler
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.annotation.RetryableTopic
import org.springframework.kafka.annotation.BackOff
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.retrytopic.SameIntervalTopicReuseStrategy
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.stereotype.Service

const val FORWARDED_FOR = "x-forwarded-for"
const val USER_AGENT = "user-agent"
const val SESSION_ID_HEADER = "x-reops-session-id"
const val VISIT_ID_HEADER = "x-reops-visit-id"
const val CREATED_AT_HEADER = "x-reops-created-at"

@Service
class KafkaService(
    private val enrichedEventKafkaTemplate: KafkaTemplate<String, EnrichedEvent>,
    private val geoIpService: GeoIpService,
    private val userAgentService: UserAgentService,
    private val websiteService: no.nav.reops.website.WebsiteService,
    private val sessionService: SessionService,
    @Value("\${spring.kafka.output-topic}") private val outputTopic: String,
    @Value("\${app.secret:}") private val appSecret: String,
    meterRegistry: MeterRegistry
) {
    private val kafkaEventsSuccess: Counter =
        Counter.builder("kafka_events_processed_total").tag("result", "success").register(meterRegistry)

    private val kafkaEventsFailure: Counter =
        Counter.builder("kafka_events_processed_total").tag("result", "failure").register(meterRegistry)

    private val kafkaEventsDlt: Counter =
        Counter.builder("kafka_events_dlt_total").register(meterRegistry)

    private val kafkaEventsBot: Counter =
        Counter.builder("kafka_events_bot_filtered_total").register(meterRegistry)

    private val kafkaEventsUnregistered: Counter =
        Counter.builder("kafka_events_unregistered_website_total").register(meterRegistry)

    @RetryableTopic(
        attempts = "2", // Dont change! - creates more topics
        backOff = BackOff(delayString = "\${spring.kafka.retry.backoff-delay:300000}"), // Dont add more backoff! - creates more topics
        sameIntervalTopicReuseStrategy = SameIntervalTopicReuseStrategy.SINGLE_TOPIC,
        autoCreateTopics = "false"
    )
    @KafkaListener(
        topics = ["\${spring.kafka.topic}"],
        groupId = "\${spring.kafka.consumer.group-id}"
    )
    fun eventListen(
        event: Event,
        @Header(KafkaHeaders.RECEIVED_KEY) key: String,
        record: ConsumerRecord<String, Event>
    ) {
        try {
            if (!websiteService.isAllowed(event.payload.website)) {
                kafkaEventsUnregistered.increment()
                LOG.info(
                    "Filtered event from unregistered websiteId={} key={}",
                    event.payload.website, key
                )
                return
            }

            val forwardedFor = record.headers().lastHeader(FORWARDED_FOR)?.value()?.let { String(it) }
            val userAgent = record.headers().lastHeader(USER_AGENT)?.value()?.let { String(it) }
            val sessionIdHeader = record.headers().lastHeader(SESSION_ID_HEADER)?.value()
                ?.let { runCatching { java.util.UUID.fromString(String(it)) }.getOrNull() }
            val visitIdHeader = record.headers().lastHeader(VISIT_ID_HEADER)?.value()
                ?.let { runCatching { java.util.UUID.fromString(String(it)) }.getOrNull() }
            val createdAtHeader = record.headers().lastHeader(CREATED_AT_HEADER)?.value()
                ?.let { runCatching { java.time.Instant.parse(String(it)) }.getOrNull() }

            LOG.info(
                "Received event with key={} website={} offset={} partition={}",
                key, event.payload.website, record.offset(), record.partition()
            )

            val country = geoIpService.lookupCountry(forwardedFor)
            LOG.debug("userAgent: {} screen: {}", userAgent, event.payload.screen)
            val userAgentInfo = userAgentService.parse(userAgent, event.payload.screen)
            LOG.debug(userAgentInfo.toString())

            if (userAgentInfo.isBot) {
                kafkaEventsBot.increment()
                LOG.debug(
                    "Filtered bot event key={} website={} ua={}",
                    key, event.payload.website, userAgent
                )
                return
            }

            val enrichedEvent = event.toEnrichedEvent(
                country = country,
                userAgentInfo = userAgentInfo,
                ip = forwardedFor?.split(",")?.firstOrNull()?.trim(),
                userAgent = userAgent,
                salt = appSecret,
                preComputedSessionId = sessionIdHeader,
                preComputedVisitId = visitIdHeader,
                preComputedCreatedAt = createdAtHeader
            )

            val isNewSession = enrichedEvent.sessionId?.let { sid ->
                sessionService.checkAndRegister(sid, enrichedEvent.websiteId, enrichedEvent.createdAt)
            } ?: true

            val finalEvent = enrichedEvent.copy(isNewSession = isNewSession)

            enrichedEventKafkaTemplate.send(outputTopic, key, finalEvent)
                .whenComplete { _, ex ->
                    if (ex != null) {
                        kafkaEventsFailure.increment()
                        LOG.error(
                            "Async produce failed topic={} key={} website={}",
                            outputTopic, key, event.payload.website, ex
                        )
                    } else {
                        kafkaEventsSuccess.increment()
                        LOG.debug(
                            "Forwarded enriched event to topic={} key={} website={}",
                            outputTopic, key, event.payload.website
                        )
                    }
                }
        } catch (ex: Exception) {
            kafkaEventsFailure.increment()
            LOG.error(
                "Failed processing kafka event key={} offset={} partition={}",
                key,
                record.offset(),
                record.partition(),
                ex
            )
            throw ex
        }
    }

    @DltHandler
    fun handleDlt(record: ConsumerRecord<String, Event>) {
        kafkaEventsDlt.increment()
        val key = record.key()
        val website = runCatching { record.value()?.payload?.website }.getOrNull()
        LOG.error(
            "Message exhausted all retries and sent to DLT: key={} website={} offset={} partition={} topic={}",
            key, website, record.offset(), record.partition(), record.topic()
        )
    }

    private companion object {
        private val LOG = LoggerFactory.getLogger(KafkaService::class.java)
    }
}