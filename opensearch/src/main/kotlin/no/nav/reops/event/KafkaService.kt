package no.nav.reops.event

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import no.nav.reops.opensearch.OpenSearchService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service

@Service
class KafkaService(
    private val openSearchService: OpenSearchService, meterRegistry: MeterRegistry
) {
    private val kafkaEventsSuccess: Counter =
        Counter.builder("kafka_events_processed_total").tag("result", "success").register(meterRegistry)

    private val kafkaEventsFailure: Counter =
        Counter.builder("kafka_events_processed_total").tag("result", "failure").register(meterRegistry)

    @KafkaListener(
        topics = ["\${spring.kafka.topic}"],
        groupId = "\${spring.kafka.consumer.group-id}",
        concurrency = "\${spring.kafka.listener.concurrency}",
    )
    fun batchListen(records: List<ConsumerRecord<String, EnrichedEvent>>) {
        try {
            LOG.info("Received batch of {} events", records.size)
            val events = records.map { it.value() }
            openSearchService.sendEvents(events)
            kafkaEventsSuccess.increment(records.size.toDouble())
        } catch (ex: Exception) {
            kafkaEventsFailure.increment(records.size.toDouble())
            LOG.error("Failed processing batch of {} events", records.size, ex)
            throw ex
        }
    }

    private companion object {
        private val LOG = LoggerFactory.getLogger(KafkaService::class.java)
    }
}
