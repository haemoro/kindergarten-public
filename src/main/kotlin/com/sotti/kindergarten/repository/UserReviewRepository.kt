package com.sotti.kindergarten.repository

import com.sotti.kindergarten.entity.UserReview
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface UserReviewRepository : JpaRepository<UserReview, UUID> {
    @Query(
        value = "SELECT r FROM UserReview r JOIN FETCH r.center WHERE r.center.id = :centerId ORDER BY r.createdAt DESC",
        countQuery = "SELECT COUNT(r) FROM UserReview r WHERE r.center.id = :centerId",
    )
    fun findAllByCenterIdWithCenter(
        @Param("centerId") centerId: UUID,
        pageable: Pageable,
    ): Page<UserReview>

    fun existsByDeviceIdAndCenterId(
        deviceId: String,
        centerId: UUID,
    ): Boolean

    fun findByIdAndDeviceId(
        id: UUID,
        deviceId: String,
    ): UserReview?

    fun deleteByIdAndDeviceId(
        id: UUID,
        deviceId: String,
    ): Int
}
