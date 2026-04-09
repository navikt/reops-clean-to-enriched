package no.nav.reops

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class OpenSearchApplication

fun main(args: Array<String>) {
    runApplication<OpenSearchApplication>(*args)
}