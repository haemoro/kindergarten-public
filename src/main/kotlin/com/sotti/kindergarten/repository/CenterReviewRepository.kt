package com.sotti.kindergarten.repository

import com.sotti.kindergarten.entity.CenterReview
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CenterReviewRepository : JpaRepository<CenterReview, UUID> {
    fun findByCenterIdOrderByPostDateDesc(
        centerId: UUID,
        pageable: Pageable,
    ): Page<CenterReview>

    fun deleteAllByCenterId(centerId: UUID)

    fun existsByLink(link: String): Boolean
}
