package com.sotti.kindergarten.controller.app

import com.sotti.kindergarten.dto.PageResponse
import com.sotti.kindergarten.dto.app.RecentReviewResponse
import com.sotti.kindergarten.service.CenterReviewService
import com.sotti.kindergarten.util.parseUUIDs
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
        @RequestParam(required = false) centerIds: String?,
        @RequestParam(required = false, defaultValue = "3") size: Int,
    ): PageResponse<RecentReviewResponse> {
        val ids = parseUUIDs(centerIds)
        return centerReviewService.getRecentReviews(ids, size)
    }
}
