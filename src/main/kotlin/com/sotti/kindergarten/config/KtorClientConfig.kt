package com.sotti.kindergarten.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.sotti.kindergarten.client.KindergartenApiProperties
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.ContentType
import io.ktor.serialization.jackson.jackson
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(KindergartenApiProperties::class)
class KtorClientConfig {
    @Bean
    fun httpClient(): HttpClient =
        HttpClient(CIO) {
            install(ContentNegotiation) {
                jackson(contentType = ContentType.Application.Json) {
                    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                }
                jackson(contentType = ContentType.Text.Html) {
                    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                }
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 10_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 10_000
            }
            install(Logging) {
                level = LogLevel.INFO
            }
        }
}
