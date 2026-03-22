package com.sotti.kindergarten.controller.app

import com.sotti.kindergarten.dto.PageResponse
import com.sotti.kindergarten.dto.app.RecentUserReviewResponse
import com.sotti.kindergarten.dto.app.UserReviewCreateRequest
import com.sotti.kindergarten.dto.app.UserReviewResponse
import com.sotti.kindergarten.dto.app.UserReviewUpdateRequest
import com.sotti.kindergarten.service.UserReviewService
import com.sotti.kindergarten.util.parseUUIDs
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/app/user-reviews")
@Validated
class AppUserReviewController(
    private val userReviewService: UserReviewService,
) {
    @GetMapping("/recent")
    fun getRecentReviews(
        @RequestParam(required = false) centerIds: String?,
        @RequestParam(required = false, defaultValue = "3") size: Int,
    ): PageResponse<RecentUserReviewResponse> {
        val ids = parseUUIDs(centerIds)
        return userReviewService.getRecentReviews(ids, size)
    }

    @GetMapping("/centers/{centerId}")
    fun getReviews(
        @PathVariable centerId: UUID,
        @RequestParam(required = false) deviceId: String?,
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "20") size: Int,
    ): PageResponse<UserReviewResponse> =
        userReviewService.getReviews(
            centerId = centerId,
            deviceId = deviceId,
            page = page,
            size = size,
        )

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createReview(
        @RequestBody @Valid request: UserReviewCreateRequest,
    ): UserReviewResponse = userReviewService.createReview(request)

    @PutMapping("/{reviewId}")
    fun updateReview(
        @PathVariable reviewId: UUID,
        @RequestBody @Valid request: UserReviewUpdateRequest,
    ): UserReviewResponse = userReviewService.updateReview(reviewId, request)

    @DeleteMapping("/{reviewId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteReview(
        @PathVariable reviewId: UUID,
        @RequestParam deviceId: String,
    ) {
        userReviewService.deleteReview(reviewId, deviceId)
    }
}
