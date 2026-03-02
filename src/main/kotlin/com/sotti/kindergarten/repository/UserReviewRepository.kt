package com.sotti.kindergarten.repository

import com.sotti.kindergarten.entity.UserReview
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.UUID

@Repository
interface UserReviewRepository : JpaRepository<UserReview, UUID> {
    @Query(
        value = """
            SELECT r.id, r.center_id AS centerId, r.device_id AS deviceId,
                   r.nickname, r.content, r.created_at AS createdAt
            FROM user_review r
            WHERE r.center_id = :centerId
            ORDER BY r.created_at DESC
        """,
        countQuery = "SELECT COUNT(*) FROM user_review WHERE center_id = :centerId",
        nativeQuery = true,
    )
    fun findAllByCenterId(
        @Param("centerId") centerId: UUID,
        pageable: Pageable,
    ): Page<UserReviewProjection>

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

    @Query(
        value = """
            SELECT r.id, r.center_id AS centerId, c.name AS centerName,
                   r.nickname, r.content, r.created_at AS createdAt
            FROM user_review r
            JOIN center c ON c.id = r.center_id
            WHERE r.center_id IN (:centerIds)
            ORDER BY r.created_at DESC
            LIMIT :size
        """,
        nativeQuery = true,
    )
    fun findRecentByCenterIds(
        @Param("centerIds") centerIds: List<UUID>,
        @Param("size") size: Int,
    ): List<RecentUserReviewProjection>

    @Modifying
    @Query(
        value = "UPDATE user_review SET created_at = :createdAt, updated_at = :updatedAt WHERE id = :id",
        nativeQuery = true,
    )
    fun updateTimestamps(
        @Param("id") id: UUID,
        @Param("createdAt") createdAt: LocalDateTime,
        @Param("updatedAt") updatedAt: LocalDateTime,
    ): Int
}

interface UserReviewProjection {
    fun getId(): UUID

    fun getCenterId(): UUID

    fun getDeviceId(): String

    fun getNickname(): String

    fun getContent(): String

    fun getCreatedAt(): LocalDateTime
}

interface RecentUserReviewProjection {
    fun getId(): UUID

    fun getCenterId(): UUID

    fun getCenterName(): String

    fun getNickname(): String

    fun getContent(): String

    fun getCreatedAt(): LocalDateTime
}
