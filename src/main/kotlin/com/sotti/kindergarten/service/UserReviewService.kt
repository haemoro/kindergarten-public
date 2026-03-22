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
import com.sotti.kindergarten.util.toPageResponse
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
        val reviewPage = userReviewRepository.findAllByCenterId(centerId, PageRequest.of(page, size))

        return reviewPage.toPageResponse { row ->
            UserReviewResponse(
                id = row.getId(),
                centerId = row.getCenterId(),
                nickname = row.getNickname(),
                content = row.getContent(),
                isMine = deviceId != null && row.getDeviceId() == deviceId,
                createdAt = row.getCreatedAt(),
            )
        }
    }

    fun getRecentReviews(
        centerIds: List<UUID>?,
        size: Int,
    ): PageResponse<RecentUserReviewResponse> {
        if (centerIds.isNullOrEmpty()) {
            return PageResponse(content = emptyList(), page = 0, size = size, totalElements = 0, totalPages = 0)
        }

        val reviews = userReviewRepository.findRecentByCenterIds(centerIds, size)
        val content =
            reviews.map { row ->
                RecentUserReviewResponse(
                    id = row.getId(),
                    centerId = row.getCenterId(),
                    centerName = row.getCenterName(),
                    nickname = row.getNickname(),
                    content = row.getContent(),
                    createdAt = row.getCreatedAt(),
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
