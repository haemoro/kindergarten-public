package com.sotti.kindergarten.controller.admin

import com.sotti.kindergarten.dto.admin.BatchSeedRequest
import com.sotti.kindergarten.dto.admin.BatchSeedResponse
import com.sotti.kindergarten.dto.admin.ReviewSeedSaveRequest
import com.sotti.kindergarten.dto.admin.ReviewSeedSaveResponse
import com.sotti.kindergarten.dto.admin.ReviewSeedStatusResponse
import com.sotti.kindergarten.dto.admin.ReviewSeedTargetsResponse
import com.sotti.kindergarten.dto.admin.RichTargetsResponse
import com.sotti.kindergarten.service.ReviewSeedService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/review-seed")
class AdminReviewSeedController(
    private val reviewSeedService: ReviewSeedService,
) {
    @PostMapping("/batch")
    fun batchSeed(
        @Valid @RequestBody request: BatchSeedRequest,
    ): ResponseEntity<BatchSeedResponse> =
        ResponseEntity
            .status(HttpStatus.CREATED)
            .body(reviewSeedService.batchSeed(request.count))

    @GetMapping("/targets")
    fun getTargets(
        @RequestParam(defaultValue = "10") count: Int,
    ): ResponseEntity<ReviewSeedTargetsResponse> = ResponseEntity.ok(reviewSeedService.getNextTargets(count))

    @PostMapping("/save")
    fun saveSeedReview(
        @RequestBody request: ReviewSeedSaveRequest,
    ): ResponseEntity<ReviewSeedSaveResponse> =
        ResponseEntity
            .status(HttpStatus.CREATED)
            .body(reviewSeedService.saveSeedReview(request))

    @GetMapping("/status")
    fun getStatus(): ResponseEntity<ReviewSeedStatusResponse> = ResponseEntity.ok(reviewSeedService.getStatus())

    @GetMapping("/rich-targets")
    fun getRichTargets(
        @RequestParam(defaultValue = "20") count: Int,
    ): ResponseEntity<RichTargetsResponse> = ResponseEntity.ok(reviewSeedService.getRichTargets(count))
}
