package com.sotti.kindergarten.dto.app

import java.time.LocalDateTime
import java.util.UUID

data class UserReviewResponse(
    val id: UUID,
    val centerId: UUID,
    val nickname: String,
    val content: String,
    val isMine: Boolean,
    val createdAt: LocalDateTime,
)
