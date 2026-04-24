package no.nav.reops.website

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
@EnableCaching
class CacheConfiguration {

    @Bean
    fun cacheManager(): CacheManager =
        CaffeineCacheManager("websites", "website", "websiteAllowed", "sessions").apply {
            setCaffeine(Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(5)))
        }
}
