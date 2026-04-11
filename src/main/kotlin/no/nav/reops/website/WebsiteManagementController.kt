package no.nav.reops.website

import no.nav.reops.event.Website
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@RestController
@RequestMapping("/api/manage/websites")
class WebsiteManagementController(
    private val websiteService: WebsiteService
) {

    @GetMapping
    fun listAll(): List<Website> = websiteService.findAllFresh()

    @GetMapping("/{websiteId}")
    fun getById(@PathVariable websiteId: UUID): Website =
        websiteService.findByIdFresh(websiteId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Website not found")
}
