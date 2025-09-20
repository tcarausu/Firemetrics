package fhir

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

//Making sure that we can setup and tear down a database connection.
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy

@SpringBootApplication
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}

@Component
class DbSanity(private val jdbc: JdbcTemplate) {

    // Runs after the Spring context is initialized. Throwing a sanity check here
    @PostConstruct
    fun probeOnStartup() {
        val one = jdbc.queryForObject("SELECT 1", Int::class.java)
            ?: error("DB probe returned null")
        println("DB probe on startup = $one")
    }

    // Added the destroy to make sure I can visualise the disconnect, or error; will add logging later on.
    @PreDestroy
    fun probeOnShutdown() {
        try {
            val one = jdbc.queryForObject("SELECT 1", Int::class.java)
            println("DB probe on shutdown = $one")
        } catch (ignored: Exception) {
            println("DB probe failed on shutdown: ${ignored.message}")
        }
    }
}
