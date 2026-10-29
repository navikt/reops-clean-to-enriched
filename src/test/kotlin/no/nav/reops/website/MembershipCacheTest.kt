package no.nav.reops.website

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.cache.annotation.Cacheable
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import reactor.core.publisher.Mono
import java.util.concurrent.atomic.AtomicInteger

class MembershipCacheTest {

    @Test
    fun `successful reactive membership results are cached`() {
        AnnotationConfigApplicationContext(CacheConfiguration::class.java, MembershipProbe::class.java).use { context ->
            val probe = context.getBean(MembershipProbe::class.java)
            assertThat(probe.lookup("member").block()).containsExactly("team")
            assertThat(probe.lookup("member").block()).containsExactly("team")
            assertThat(probe.callCount()).isEqualTo(1)
        }
    }

    @Test
    fun `failed reactive membership lookups do not poison later requests`() {
        AnnotationConfigApplicationContext(CacheConfiguration::class.java, MembershipProbe::class.java).use { context ->
            val probe = context.getBean(MembershipProbe::class.java)
            assertThat(probe.lookup("retry").onErrorReturn(emptyList()).block()).isEmpty()
            assertThat(probe.lookup("retry").block()).containsExactly("team")
            assertThat(probe.lookup("retry").block()).containsExactly("team")
            assertThat(probe.callCount()).isEqualTo(2)
        }
    }

    open class MembershipProbe {
        private val calls = AtomicInteger()

        @Cacheable("teamMemberships")
        open fun lookup(key: String): Mono<List<String>> = Mono.defer {
            val attempt = calls.incrementAndGet()
            if (key == "retry" && attempt == 1) Mono.error(IllegalStateException("Temporary outage"))
            else Mono.just(listOf("team"))
        }

        open fun callCount(): Int = calls.get()
    }
}
