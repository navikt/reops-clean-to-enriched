package no.nav.reops

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class UmamiApplication

fun main(args: Array<String>) {
    runApplication<UmamiApplication>(*args)
}