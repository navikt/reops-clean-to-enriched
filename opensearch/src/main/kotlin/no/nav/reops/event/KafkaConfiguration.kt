package no.nav.reops.event

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.common.TopicPartition
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.kafka.autoconfigure.KafkaProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.CommonErrorHandler
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.util.backoff.FixedBackOff

@EnableKafka
@Configuration
class KafkaConfiguration(
    private val kafkaProperties: KafkaProperties
) {

    @Bean
    fun kafkaTemplate(): KafkaTemplate<String, EnrichedEvent> {
        return KafkaTemplate(DefaultKafkaProducerFactory(kafkaProperties.buildProducerProperties()))
    }

    @Bean
    fun dltTopic(@Value("\${spring.kafka.dlt-topic}") dltTopicName: String): NewTopic {
        return TopicBuilder.name(dltTopicName)
            .partitions(1)
            .build()
    }

    @Bean
    fun commonErrorHandler(
        kafkaTemplate: KafkaTemplate<String, EnrichedEvent>,
        meterRegistry: MeterRegistry,
        @Value("\${spring.kafka.retry.backoff-delay:300000}") backoffDelay: Long,
        @Value("\${spring.kafka.dlt-topic}") dltTopicName: String
    ): CommonErrorHandler {
        val dltCounter: Counter = Counter.builder("kafka_events_dlt_total").register(meterRegistry)
        val dltRecoverer = DeadLetterPublishingRecoverer(kafkaTemplate) { _, _ ->
            TopicPartition(dltTopicName, -1)
        }

        return DefaultErrorHandler({ record, exception ->
            dltCounter.increment()
            LOG.error(
                "Message exhausted all retries and sent to DLT: key={} offset={} partition={} topic={}",
                record.key(),
                record.offset(),
                record.partition(),
                record.topic(),
                exception
            )
            dltRecoverer.accept(record, exception)
        }, FixedBackOff(backoffDelay, 1)) // 1 retry = 2 total attempts
    }

    private companion object {
        private val LOG = LoggerFactory.getLogger(KafkaConfiguration::class.java)
    }
}