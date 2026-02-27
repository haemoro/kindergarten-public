package com.sotti.kindergarten.dto.app

import java.time.LocalDate
import java.util.UUID

data class RecentReviewResponse(
    val centerId: UUID,
    val centerName: String,
    val title: String,
    val link: String,
    val snippet: String,
    val source: String,
    val postDate: LocalDate,
)
