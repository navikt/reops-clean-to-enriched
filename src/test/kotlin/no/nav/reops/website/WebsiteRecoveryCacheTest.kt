package no.nav.reops.website

import no.nav.reops.event.WebsiteChangeEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.kafka.core.KafkaTemplate
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.function.Supplier

class WebsiteRecoveryCacheTest {

    @Test
    fun `recreating an ID evicts cached ingestion rejection`() {
        val repository = mock<WebsiteRepository>()
        val kafka = mock<KafkaTemplate<String, WebsiteChangeEvent>>()
        val websiteId = UUID.randomUUID()
        whenever(repository.existsByWebsiteIdAndDeletedAtIsNull(websiteId)).thenReturn(false, true)
        whenever(repository.save(any<WebsiteEntity>())).thenAnswer { it.arguments[0] }
        whenever(kafka.send(any<String>(), any<String>(), any())).thenReturn(CompletableFuture.completedFuture(null))

        AnnotationConfigApplicationContext().use { context ->
            context.register(CacheConfiguration::class.java)
            context.registerBean("websiteService", WebsiteService::class.java, Supplier {
                WebsiteService(repository, kafka, "websites")
            })
            context.refresh()
            val service = context.getBean(WebsiteService::class.java)

            assertThat(service.isAllowed(websiteId)).isFalse()
            assertThat(service.isAllowed(websiteId)).isFalse() // cached
            service.create("Recovered", "recovered.nav.no", null, "Z123456", websiteId)
            assertThat(service.isAllowed(websiteId)).isTrue()
        }
    }
}
