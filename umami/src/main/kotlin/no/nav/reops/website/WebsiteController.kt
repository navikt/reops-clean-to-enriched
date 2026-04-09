package no.nav.reops.website

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import no.nav.reops.event.Website
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

data class WebsiteRequest(
    @field:NotBlank val name: String,
    @field:NotBlank val domain: String,
    val websiteId: UUID? = null
)

@RestController
@RequestMapping("/api/websites")
class WebsiteController(
    private val websiteService: WebsiteService
) {

    @GetMapping
    fun listAll(): List<Website> = websiteService.findAll()

    @GetMapping("/{websiteId}")
    fun getById(@PathVariable websiteId: UUID): Website =
        websiteService.findById(websiteId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Website not found")

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: WebsiteRequest): Website =
        websiteService.create(websiteId = request.websiteId, name = request.name, domain = request.domain)

    @PutMapping("/{websiteId}")
    fun update(@PathVariable websiteId: UUID, @Valid @RequestBody request: WebsiteRequest): Website =
        try {
            websiteService.update(websiteId = websiteId, name = request.name, domain = request.domain)
        } catch (e: WebsiteNotFoundException) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
        }

    @DeleteMapping("/{websiteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable websiteId: UUID) =
        try {
            websiteService.delete(websiteId)
        } catch (e: WebsiteNotFoundException) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
        }
}
