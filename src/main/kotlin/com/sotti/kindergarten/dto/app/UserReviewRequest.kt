package com.sotti.kindergarten.dto.app

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.UUID

data class UserReviewCreateRequest(
    @field:NotBlank
    val deviceId: String,
    val centerId: UUID,
    @field:NotBlank
    @field:Size(max = 20)
    val nickname: String,
    @field:NotBlank
    @field:Size(max = 100)
    val content: String,
)

data class UserReviewUpdateRequest(
    @field:NotBlank
    val deviceId: String,
    @field:NotBlank
    @field:Size(max = 20)
    val nickname: String,
    @field:NotBlank
    @field:Size(max = 100)
    val content: String,
)
