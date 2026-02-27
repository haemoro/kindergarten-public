package com.sotti.kindergarten.repository

import com.sotti.kindergarten.entity.CenterReview
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
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

    fun findByCenterIdInAndPostDateIsNotNullOrderByPostDateDesc(
        centerIds: List<UUID>,
        pageable: Pageable,
    ): Page<CenterReview>

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
                SELECT c.id AS center_id, c.name, c.address, c.establish_type,
                       cr.snippet,
                       COUNT(*) OVER (PARTITION BY c.id) AS review_count,
                       ROW_NUMBER() OVER (PARTITION BY c.id ORDER BY cr.post_date DESC NULLS LAST) AS rn
                FROM center_review cr
                JOIN center c ON c.id = cr.center_id
                WHERE cr.snippet IS NOT NULL AND cr.snippet != ''
                GROUP BY c.id, c.name, c.address, c.establish_type, cr.id, cr.snippet, cr.post_date
            )
            SELECT center_id, name, address, establish_type, review_count, snippet
            FROM ranked
            WHERE review_count >= :minCount AND rn <= 5
            ORDER BY review_count DESC, center_id, rn
            """,
        nativeQuery = true,
    )
    fun findRichTargetsRaw(minCount: Long): List<Array<Any>>
}
