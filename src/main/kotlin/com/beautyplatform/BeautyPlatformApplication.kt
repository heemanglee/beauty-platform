package com.beautyplatform

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class BeautyPlatformApplication

fun main(args: Array<String>) {
    runApplication<BeautyPlatformApplication>(*args)
}
