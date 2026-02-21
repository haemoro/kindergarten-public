package com.sotti.kindergarten.service

import com.sotti.kindergarten.client.NaverSearchClient
import com.sotti.kindergarten.client.dto.NaverSearchItem
import com.sotti.kindergarten.dto.PageResponse
import com.sotti.kindergarten.dto.app.CenterReviewResponse
import com.sotti.kindergarten.entity.Center
import com.sotti.kindergarten.entity.CenterReview
import com.sotti.kindergarten.repository.CenterRepository
import com.sotti.kindergarten.repository.CenterReviewRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

@Service
class CenterReviewService(
    private val centerReviewRepository: CenterReviewRepository,
    private val centerRepository: CenterRepository,
    private val naverSearchClient: NaverSearchClient,
) {
    private val logger = LoggerFactory.getLogger(CenterReviewService::class.java)

    companion object {
        private const val SEARCH_KEYWORD_SUFFIX = " 후기"
        private const val SEARCH_DISPLAY_COUNT = 10
        private const val SYNC_DELAY_MS = 200L
        private val POST_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd")
        private val HTML_TAG_REGEX = Regex("<[^>]*>")
        private val WHITESPACE_REGEX = "\\s+".toRegex()
    }

    @Transactional(readOnly = true)
    fun getReviews(
        centerId: UUID,
        page: Int,
        size: Int,
    ): PageResponse<CenterReviewResponse> {
        val reviewPage =
            centerReviewRepository
                .findByCenterIdOrderByPostDateDesc(centerId, PageRequest.of(page, size))

        return PageResponse(
            content = reviewPage.content.map { it.toResponse() },
            page = reviewPage.number,
            size = reviewPage.size,
            totalElements = reviewPage.totalElements,
            totalPages = reviewPage.totalPages,
        )
    }

    @Transactional
    fun syncReviews(centerId: UUID) {
        val center =
            centerRepository.findById(centerId).orElseThrow {
                IllegalArgumentException("Center not found: $centerId")
            }

        val query = buildSearchQuery(center)
        logger.info("Syncing reviews for center: {} (query: {})", center.name, query)

        val reviews = runBlocking { fetchReviews(query, center) }

        centerReviewRepository.deleteAllByCenterId(centerId)
        centerReviewRepository.flush()

        val savedCount =
            reviews.count { review ->
                runCatching { centerReviewRepository.save(review) }
                    .onFailure { logger.warn("Failed to save review (link={}): {}", review.link, it.message) }
                    .isSuccess
            }

        logger.info("Synced {}/{} reviews for center: {}", savedCount, reviews.size, center.name)
    }

    fun syncAllReviews() {
        val centers = centerRepository.findAll()
        logger.info("Starting review sync for {} centers", centers.size)

        var successCount = 0
        var failCount = 0

        centers.forEachIndexed { index, center ->
            runCatching { syncReviews(center.id!!) }
                .onSuccess { successCount++ }
                .onFailure { e ->
                    failCount++
                    logger.error("Failed to sync reviews for center {} ({}): {}", center.name, center.id, e.message)
                }

            if ((index + 1) % 10 == 0) {
                logger.info("[{}/{}] Review sync progress...", index + 1, centers.size)
            }
        }

        logger.info("Review sync completed. success={}, failed={}, total={}", successCount, failCount, centers.size)
    }

    private suspend fun fetchReviews(
        query: String,
        center: Center,
    ): List<CenterReview> {
        val blogReviews =
            naverSearchClient
                .searchBlog(query, SEARCH_DISPLAY_COUNT)
                .items
                .map { it.toEntity(center, "blog") }

        delay(SYNC_DELAY_MS)

        val cafeReviews =
            naverSearchClient
                .searchCafe(query, SEARCH_DISPLAY_COUNT)
                .items
                .map { it.toEntity(center, "cafe") }

        return blogReviews + cafeReviews
    }

    private fun buildSearchQuery(center: Center): String {
        val region =
            center.address
                ?.trim()
                ?.split(WHITESPACE_REGEX)
                ?.take(2)
                ?.joinToString(" ")
                ?.takeIf { it.isNotBlank() }
                ?.let { " $it" }
                .orEmpty()

        return "${center.name}$region$SEARCH_KEYWORD_SUFFIX"
    }

    private fun NaverSearchItem.toEntity(
        center: Center,
        source: String,
    ): CenterReview =
        CenterReview(
            center = center,
            title = title.stripHtmlTags(),
            link = link,
            snippet = description.stripHtmlTags(),
            source = source,
            postDate = postdate.toLocalDate(),
        )

    private fun CenterReview.toResponse(): CenterReviewResponse =
        CenterReviewResponse(
            title = title,
            link = link,
            snippet = snippet,
            source = source,
            postDate = postDate,
        )

    private fun String.stripHtmlTags(): String =
        replace(HTML_TAG_REGEX, "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")

    private fun String?.toLocalDate(): LocalDate? = runCatching { this?.let { LocalDate.parse(it, POST_DATE_FORMAT) } }.getOrNull()
}
