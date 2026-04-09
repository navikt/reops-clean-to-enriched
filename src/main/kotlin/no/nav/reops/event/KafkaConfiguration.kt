package no.nav.reops.event

import org.apache.kafka.clients.admin.NewTopic
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.kafka.autoconfigure.KafkaProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate

@EnableKafka
@Configuration
class KafkaConfiguration(
    private val kafkaProperties: KafkaProperties
) {

    @Bean
    fun enrichedEventKafkaTemplate(): KafkaTemplate<String, EnrichedEvent> {
        return KafkaTemplate(DefaultKafkaProducerFactory(kafkaProperties.buildProducerProperties()))
    }

    @Bean
    fun websiteEventKafkaTemplate(): KafkaTemplate<String, WebsiteChangeEvent> {
        return KafkaTemplate(DefaultKafkaProducerFactory(kafkaProperties.buildProducerProperties()))
    }

    @Bean("defaultRetryTopicKafkaTemplate")
    fun kafkaTemplate(): KafkaTemplate<String, Event> {
        return KafkaTemplate(DefaultKafkaProducerFactory(kafkaProperties.buildProducerProperties()))
    }

    @Bean
    fun retryTopic(@Value("\${spring.kafka.topic}") mainTopic: String): NewTopic {
        return TopicBuilder.name("$mainTopic-retry")
            .partitions(1)
            .build()
    }

    @Bean
    fun dltTopic(@Value("\${spring.kafka.topic}") mainTopic: String): NewTopic {
        return TopicBuilder.name("$mainTopic-dlt")
            .partitions(1)
            .build()
    }

    @Bean
    fun websiteTopic(@Value("\${spring.kafka.website-topic}") websiteTopic: String): NewTopic {
        return TopicBuilder.name(websiteTopic)
            .partitions(1)
            .build()
    }
}