package com.sotti.kindergarten.service

import com.sotti.kindergarten.dto.PageResponse
import com.sotti.kindergarten.dto.app.RecentUserReviewResponse
import com.sotti.kindergarten.dto.app.UserReviewCreateRequest
import com.sotti.kindergarten.dto.app.UserReviewResponse
import com.sotti.kindergarten.dto.app.UserReviewUpdateRequest
import com.sotti.kindergarten.entity.UserReview
import com.sotti.kindergarten.exception.BusinessException
import com.sotti.kindergarten.exception.ErrorCode
import com.sotti.kindergarten.repository.CenterRepository
import com.sotti.kindergarten.repository.UserReviewRepository
import com.sotti.kindergarten.util.ProfanityFilter
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class UserReviewService(
    private val userReviewRepository: UserReviewRepository,
    private val centerRepository: CenterRepository,
    private val profanityFilter: ProfanityFilter,
) {
    fun getReviews(
        centerId: UUID,
        deviceId: String?,
        page: Int,
        size: Int,
    ): PageResponse<UserReviewResponse> {
        val pageable = PageRequest.of(page, size)
        val reviewPage = userReviewRepository.findAllByCenterIdWithCenter(centerId, pageable)

        val content =
            reviewPage.content.map { review ->
                UserReviewResponse(
                    id = review.id!!,
                    centerId = review.center.id!!,
                    nickname = review.nickname,
                    content = review.content,
                    isMine = deviceId != null && review.deviceId == deviceId,
                    createdAt = review.createdAt,
                )
            }

        return PageResponse(
            content = content,
            page = reviewPage.number,
            size = reviewPage.size,
            totalElements = reviewPage.totalElements,
            totalPages = reviewPage.totalPages,
        )
    }

    fun getRecentReviews(
        centerIds: List<UUID>?,
        size: Int,
    ): PageResponse<RecentUserReviewResponse> {
        if (centerIds.isNullOrEmpty()) {
            return PageResponse(content = emptyList(), page = 0, size = size, totalElements = 0, totalPages = 0)
        }

        val reviews =
            userReviewRepository.findRecentByCenterIds(
                centerIds,
                PageRequest.of(0, size),
            )
        val content =
            reviews.map { review ->
                RecentUserReviewResponse(
                    id = review.id!!,
                    centerId = review.center.id!!,
                    centerName = review.center.name,
                    nickname = review.nickname,
                    content = review.content,
                    createdAt = review.createdAt,
                )
            }
        return PageResponse(
            content = content,
            page = 0,
            size = size,
            totalElements = content.size.toLong(),
            totalPages = 1,
        )
    }

    @Transactional
    fun createReview(request: UserReviewCreateRequest): UserReviewResponse {
        if (userReviewRepository.existsByDeviceIdAndCenterId(request.deviceId, request.centerId)) {
            throw BusinessException(ErrorCode.DUPLICATE_USER_REVIEW)
        }

        val center =
            centerRepository
                .findById(request.centerId)
                .orElseThrow { BusinessException(ErrorCode.KINDERGARTEN_NOT_FOUND) }

        profanityFilter.validate(request.nickname)
        profanityFilter.validate(request.content)

        val review =
            UserReview(
                deviceId = request.deviceId,
                center = center,
                nickname = request.nickname,
                content = request.content,
            )

        val saved = userReviewRepository.save(review)

        return UserReviewResponse(
            id = saved.id!!,
            centerId = center.id!!,
            nickname = saved.nickname,
            content = saved.content,
            isMine = true,
            createdAt = saved.createdAt,
        )
    }

    @Transactional
    fun updateReview(
        reviewId: UUID,
        request: UserReviewUpdateRequest,
    ): UserReviewResponse {
        val review =
            userReviewRepository.findByIdAndDeviceId(reviewId, request.deviceId)
                ?: throw BusinessException(ErrorCode.USER_REVIEW_NOT_FOUND)

        profanityFilter.validate(request.nickname)
        profanityFilter.validate(request.content)

        review.nickname = request.nickname
        review.content = request.content

        return UserReviewResponse(
            id = review.id!!,
            centerId = review.center.id!!,
            nickname = review.nickname,
            content = review.content,
            isMine = true,
            createdAt = review.createdAt,
        )
    }

    @Transactional
    fun deleteReview(
        reviewId: UUID,
        deviceId: String,
    ) {
        val deletedCount = userReviewRepository.deleteByIdAndDeviceId(reviewId, deviceId)
        if (deletedCount == 0) {
            throw BusinessException(ErrorCode.USER_REVIEW_NOT_FOUND)
        }
    }
}
