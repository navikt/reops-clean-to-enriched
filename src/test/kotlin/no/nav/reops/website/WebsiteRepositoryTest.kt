package no.nav.reops.website

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class WebsiteRepositoryTest {

    @Autowired
    lateinit var repository: WebsiteRepository

    @Test
    fun `new entity is inserted with a version assigned by Hibernate`() {
        val saved = repository.save(WebsiteEntity(websiteId = UUID.randomUUID(), name = "a", domain = "a.no"))
        assertThat(saved.version).isNotNull()
        repository.deleteById(saved.websiteId)
    }

    @Test
    fun `hard-deleted ID can be inserted again as an active registration`() {
        val id = UUID.randomUUID()
        repository.save(WebsiteEntity(websiteId = id, name = "original", domain = "a.no"))
        repository.deleteById(id)

        val restored = repository.save(WebsiteEntity(websiteId = id, name = "restored", domain = "a.no"))
        try {
            assertThat(restored.websiteId).isEqualTo(id)
            assertThat(repository.existsByWebsiteIdAndDeletedAtIsNull(id)).isTrue()
        } finally {
            repository.deleteById(id)
        }
    }

    @Test
    fun `insert with an existing ID cannot overwrite an active or soft-deleted registration`() {
        for (deletedAt in listOf(null, Instant.now())) {
            val id = UUID.randomUUID()
            val existing = repository.save(WebsiteEntity(websiteId = id, name = "original", domain = "a.no", deletedAt = deletedAt))
            try {
                assertThatThrownBy {
                    repository.save(WebsiteEntity(websiteId = id, name = "replacement", domain = "b.no"))
                }.isInstanceOf(DataIntegrityViolationException::class.java)
                assertThat(repository.findById(id).orElseThrow()).isEqualTo(existing)
            } finally {
                repository.deleteById(id)
            }
        }
    }

    @Test
    fun `save of stale detached entity after hard delete does not resurrect the row`() {
        val id = UUID.randomUUID()
        repository.save(WebsiteEntity(websiteId = id, name = "a", domain = "a.no"))

        val stale = repository.findById(id).orElseThrow()
        repository.deleteById(id)

        assertThatThrownBy { repository.save(stale.copy(name = "b", updatedAt = Instant.now())) }
            .isInstanceOf(ObjectOptimisticLockingFailureException::class.java)
        assertThat(repository.findById(id)).isEmpty
    }
}
