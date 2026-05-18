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
    fun cacheManager(): CacheManager {
        // Default spec used by website-related caches: short TTL because
        // admins can mutate websites and we want changes to propagate quickly.
        val websiteCacheManager = CaffeineCacheManager("websites", "website", "websiteAllowed").apply {
            setCaffeine(Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(5)))
        }

        // Sessions cache: sessionIds are deterministic for a full calendar month,
        // so a long TTL is safe and dramatically reduces DB load for `existsById`
        // lookups. Bounded by maximumSize to keep memory predictable.
        websiteCacheManager.registerCustomCache(
            "sessions",
            Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(24))
                .maximumSize(200_000)
                .build()
        )

        return websiteCacheManager
    }
}
