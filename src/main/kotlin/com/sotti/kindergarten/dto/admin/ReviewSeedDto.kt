package com.sotti.kindergarten.dto.admin

import java.util.UUID

data class ReviewSeedTargetResponse(
    val centerId: UUID,
    val name: String,
    val establishType: String?,
    val address: String?,
    val mealType: String?,
    val busOperating: String?,
    val teacherCount: Int?,
    val cctvTotal: Int?,
)

data class ReviewSeedTargetsResponse(
    val targets: List<ReviewSeedTargetResponse>,
    val currentSido: String,
    val nextSido: String,
)

data class ReviewSeedSaveRequest(
    val centerId: UUID,
    val nickname: String,
    val content: String,
)

data class ReviewSeedSaveResponse(
    val reviewId: UUID,
    val centerId: UUID,
    val nickname: String,
    val content: String,
)

data class ReviewSeedStatusResponse(
    val currentSidoIndex: Int,
    val currentSidoName: String,
    val currentOffset: Int,
    val totalSidoCount: Int,
    val sidoList: List<String>,
)

data class BatchSeedRequest(
    val count: Int = 100,
)

data class BatchSeedResponse(
    val seededCount: Int,
    val samples: List<ReviewSeedSaveResponse>,
)

data class RichTargetResponse(
    val centerId: UUID,
    val name: String,
    val address: String?,
    val establishType: String?,
    val externalReviewCount: Int,
    val snippets: List<String>,
)

data class RichTargetsResponse(
    val targets: List<RichTargetResponse>,
    val totalFound: Int,
)
