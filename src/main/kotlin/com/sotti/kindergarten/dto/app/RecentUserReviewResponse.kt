package com.sotti.kindergarten.dto.app

import java.time.LocalDateTime
import java.util.UUID

data class RecentUserReviewResponse(
    val id: UUID,
    val centerId: UUID,
    val centerName: String,
    val nickname: String,
    val content: String,
    val createdAt: LocalDateTime,
)
