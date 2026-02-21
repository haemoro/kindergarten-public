package com.sotti.kindergarten.dto.app

import java.time.LocalDate

data class CenterReviewResponse(
    val title: String,
    val link: String,
    val snippet: String,
    val source: String,
    val postDate: LocalDate?,
)
