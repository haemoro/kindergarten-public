package com.sotti.kindergarten.controller.app

import com.sotti.kindergarten.dto.PageResponse
import com.sotti.kindergarten.dto.app.RecentReviewResponse
import com.sotti.kindergarten.service.CenterReviewService
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/app/reviews")
@Validated
class AppReviewController(
    private val centerReviewService: CenterReviewService,
) {
    @GetMapping("/recent")
    fun getRecentReviews(
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "5") size: Int,
    ): PageResponse<RecentReviewResponse> = centerReviewService.getRecentReviews(page, size)
}
