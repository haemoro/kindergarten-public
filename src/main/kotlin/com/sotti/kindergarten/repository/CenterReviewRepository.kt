package com.sotti.kindergarten.repository

import com.sotti.kindergarten.entity.CenterReview
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate
import java.util.UUID

interface CenterReviewRepository : JpaRepository<CenterReview, UUID> {
    fun findByCenterIdOrderByPostDateDesc(
        centerId: UUID,
        pageable: Pageable,
    ): Page<CenterReview>

    fun findByCenterIdOrderByPostDateDesc(centerId: UUID): List<CenterReview>

    fun deleteAllByCenterId(centerId: UUID)

    fun existsByLink(link: String): Boolean

    fun findByPostDateIsNotNullOrderByPostDateDesc(pageable: Pageable): Page<CenterReview>

    @Query(
        value = """
            SELECT cr.id, cr.center_id AS centerId, c.name AS centerName,
                   cr.title, cr.link, cr.snippet, cr.source, cr.post_date AS postDate
            FROM center_review cr
            JOIN center c ON c.id = cr.center_id
            WHERE cr.center_id IN (:centerIds) AND cr.post_date IS NOT NULL
            ORDER BY cr.post_date DESC
            LIMIT :size
        """,
        nativeQuery = true,
    )
    fun findRecentByCenterIds(
        @Param("centerIds") centerIds: List<UUID>,
        @Param("size") size: Int,
    ): List<RecentCenterReviewProjection>

    @Query(
        """
        SELECT cr.center.id, COUNT(cr)
        FROM CenterReview cr
        GROUP BY cr.center.id
        HAVING COUNT(cr) >= :minCount
        ORDER BY COUNT(cr) DESC
        """,
    )
    fun findCenterIdsWithMinReviewCount(
        minCount: Long,
        pageable: Pageable,
    ): List<Array<Any>>

    @Query(
        value = """
            WITH ranked AS (
                SELECT c.id AS center_id, c.name, c.address,
                       c.establish_type,
                       cr.snippet,
                       COUNT(*) OVER (PARTITION BY c.id)
                           AS review_count,
                       ROW_NUMBER() OVER (
                           PARTITION BY c.id
                           ORDER BY cr.post_date DESC NULLS LAST
                       ) AS rn
                FROM center_review cr
                JOIN center c ON c.id = cr.center_id
                WHERE cr.snippet IS NOT NULL AND cr.snippet != ''
                GROUP BY c.id, c.name, c.address,
                         c.establish_type, cr.id,
                         cr.snippet, cr.post_date
            )
            SELECT center_id, name, address, establish_type,
                   review_count, snippet
            FROM ranked
            WHERE review_count >= :minCount AND rn <= 5
            ORDER BY review_count DESC, center_id, rn
            """,
        nativeQuery = true,
    )
    fun findRichTargetsRaw(minCount: Long): List<Array<Any?>>
}

interface RecentCenterReviewProjection {
    fun getId(): UUID

    fun getCenterId(): UUID

    fun getCenterName(): String

    fun getTitle(): String

    fun getLink(): String

    fun getSnippet(): String

    fun getSource(): String

    fun getPostDate(): LocalDate
}
