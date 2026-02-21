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
        private val EXCLUDE_KEYWORDS =
            listOf(
                "원복",
                "교복",
                "구합니다",
                "팝니다",
                "삽니다",
                "월급",
                "연봉",
                "급여",
                "채용",
                "구인",
                "교사모집",
                "경매",
                "매매",
                "분양",
                "부동산",
                "전세",
                "월세",
                "학원",
                "과외",
                "수학",
                "영어",
                "태권도",
                "청소",
                "방역",
                "소독",
                "인테리어",
                "야근",
            )
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

        val baseQuery = buildBaseQuery(center)
        logger.info("Syncing reviews for center: {} (query: {})", center.name, baseQuery)

        val reviews = runBlocking { fetchReviews(baseQuery, center) }

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

    fun syncReviewsByRegion(sidoName: String) {
        val centers = centerRepository.findAllByAddressStartingWith(sidoName)
        logger.info("Starting review sync for {} centers in {}", centers.size, sidoName)
        syncCenters(centers, sidoName)
    }

    fun syncAllReviews() {
        val centers = centerRepository.findAll()
        logger.info("Starting review sync for {} centers", centers.size)
        syncCenters(centers, "all")
    }

    private fun syncCenters(
        centers: List<Center>,
        label: String,
    ) {
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
                logger.info("[{}/{}] Review sync progress ({})", index + 1, centers.size, label)
            }
        }

        logger.info(
            "Review sync completed ({}). success={}, failed={}, total={}",
            label,
            successCount,
            failCount,
            centers.size,
        )
    }

    private suspend fun fetchReviews(
        baseQuery: String,
        center: Center,
    ): List<CenterReview> {
        val reviewQuery = "$baseQuery$SEARCH_KEYWORD_SUFFIX"
        val seenLinks = mutableSetOf<String>()
        val results = mutableListOf<CenterReview>()

        // 1차: "유치원명" 구 후기 → 정확한 리뷰 우선
        results += searchBoth(reviewQuery, center, seenLinks)
        delay(SYNC_DELAY_MS)

        // 2차: "유치원명" 구 → 후기 키워드 없는 글 보충
        results += searchBoth(baseQuery, center, seenLinks)

        return results
    }

    private suspend fun searchBoth(
        query: String,
        center: Center,
        seenLinks: MutableSet<String>,
    ): List<CenterReview> {
        val centerName = center.name.stripHtmlTags()

        val blogItems =
            naverSearchClient
                .searchBlog(query, SEARCH_DISPLAY_COUNT)
                .items
                .filter { it.isRelevant(centerName) }
                .filter { seenLinks.add(it.link) }
                .map { it.toEntity(center, "blog") }

        delay(SYNC_DELAY_MS)

        val cafeItems =
            naverSearchClient
                .searchCafe(query, SEARCH_DISPLAY_COUNT, sort = "date")
                .items
                .filter { it.isRelevant(centerName) }
                .filter { seenLinks.add(it.link) }
                .map { it.toEntity(center, "cafe") }

        return blogItems + cafeItems
    }

    private fun NaverSearchItem.isRelevant(centerName: String): Boolean {
        val cleanTitle = title.stripHtmlTags()
        if (!cleanTitle.contains(centerName)) return false
        val text = "$cleanTitle ${description.stripHtmlTags()}"
        if (EXCLUDE_KEYWORDS.any { text.contains(it) }) return false
        val date = postdate.toLocalDate()
        if (date != null && date.isBefore(LocalDate.now().minusYears(1))) return false
        return true
    }

    private fun buildBaseQuery(center: Center): String {
        val district =
            center.address
                ?.trim()
                ?.split(WHITESPACE_REGEX)
                ?.getOrNull(1)
                ?.takeIf { it.isNotBlank() }
                ?.let { " $it" }
                .orEmpty()

        return "\"${center.name}\"$district"
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
