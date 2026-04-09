package no.nav.reops

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ScrubsApplication

fun main(args: Array<String>) {
    runApplication<ScrubsApplication>(*args)
}