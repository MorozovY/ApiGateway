package com.company.gateway.admin

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class AdminApplication

fun main(args: Array<String>) {
    runApplication<AdminApplication>(*args)
}
