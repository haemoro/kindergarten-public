package com.sotti.kindergarten.client

import com.sotti.kindergarten.client.dto.NaverSearchResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class NaverSearchClient(
    private val httpClient: HttpClient,
    private val properties: NaverSearchProperties,
) {
    private val logger = LoggerFactory.getLogger(NaverSearchClient::class.java)

    companion object {
        private const val BLOG_SEARCH_URL = "https://openapi.naver.com/v1/search/blog.json"
        private const val CAFE_SEARCH_URL = "https://openapi.naver.com/v1/search/cafearticle.json"
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 1000L
        private const val DEFAULT_DISPLAY = 10
    }

    suspend fun searchBlog(
        query: String,
        display: Int = DEFAULT_DISPLAY,
        sort: String = "sim",
    ): NaverSearchResponse = search(BLOG_SEARCH_URL, query, display, sort)

    suspend fun searchCafe(
        query: String,
        display: Int = DEFAULT_DISPLAY,
        sort: String = "sim",
    ): NaverSearchResponse = search(CAFE_SEARCH_URL, query, display, sort)

    private suspend fun search(
        url: String,
        query: String,
        display: Int,
        sort: String,
    ): NaverSearchResponse =
        retryOnFailure {
            httpClient
                .get(url) {
                    header("X-Naver-Client-Id", properties.clientId)
                    header("X-Naver-Client-Secret", properties.clientSecret)
                    parameter("query", query)
                    parameter("display", display)
                    parameter("sort", sort)
                }.body()
        }

    private suspend fun <T> retryOnFailure(block: suspend () -> T): T {
        var lastException: Exception? = null
        repeat(MAX_RETRY_ATTEMPTS) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                logger.warn(
                    "Naver API call failed (attempt ${attempt + 1}/$MAX_RETRY_ATTEMPTS): ${e.message}",
                )
                if (attempt < MAX_RETRY_ATTEMPTS - 1) {
                    delay(RETRY_DELAY_MS * (attempt + 1))
                }
            }
        }
        throw lastException ?: RuntimeException("Unknown error during Naver API call")
    }
}
