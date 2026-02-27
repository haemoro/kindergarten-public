package com.sotti.kindergarten.repository

import com.sotti.kindergarten.entity.Center
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Repository
interface CenterRepository :
    JpaRepository<Center, UUID>,
    CenterRepositoryCustom {
    /**
     * Fetches a Center with all 11 OneToOne relationships in a single query.
     * OneToMany collections (safetyEducations, insurances) are loaded lazily
     * via @BatchSize(50) within the @Transactional service to avoid Cartesian product.
     */
    @Query(
        """
        SELECT DISTINCT c FROM Center c
        LEFT JOIN FETCH c.building
        LEFT JOIN FETCH c.classroom
        LEFT JOIN FETCH c.teacher
        LEFT JOIN FETCH c.lessonDay
        LEFT JOIN FETCH c.meal
        LEFT JOIN FETCH c.bus
        LEFT JOIN FETCH c.yearOfWork
        LEFT JOIN FETCH c.environment
        LEFT JOIN FETCH c.safetyCheck
        LEFT JOIN FETCH c.mutualAid
        LEFT JOIN FETCH c.afterSchool
        LEFT JOIN FETCH c.safetyEducations
        LEFT JOIN FETCH c.insurances
        WHERE c.id = :id
        """,
    )
    fun findByIdWithDetails(
        @Param("id") id: UUID,
    ): Center?

    /**
     * Fetches multiple Centers with all OneToOne relationships in a single query.
     * Used by compare endpoints to avoid N+1 on multiple center lookups.
     */
    @Query(
        """
        SELECT DISTINCT c FROM Center c
        LEFT JOIN FETCH c.building
        LEFT JOIN FETCH c.classroom
        LEFT JOIN FETCH c.teacher
        LEFT JOIN FETCH c.lessonDay
        LEFT JOIN FETCH c.meal
        LEFT JOIN FETCH c.bus
        LEFT JOIN FETCH c.yearOfWork
        LEFT JOIN FETCH c.environment
        LEFT JOIN FETCH c.safetyCheck
        LEFT JOIN FETCH c.mutualAid
        LEFT JOIN FETCH c.afterSchool
        WHERE c.id IN :ids
        """,
    )
    fun findAllByIdsWithDetails(
        @Param("ids") ids: List<UUID>,
    ): List<Center>

    fun findByKinderCode(kinderCode: String): Center?

    fun findAllByKinderCodeIn(kinderCodes: List<String>): List<Center>

    @Modifying
    @Query("UPDATE Center c SET c.isActive = :isActive WHERE c.id IN :ids")
    fun batchUpdateIsActive(
        ids: List<UUID>,
        isActive: Boolean,
    ): Int

    @Modifying
    @Query("UPDATE Center c SET c.isVerified = :isVerified WHERE c.id IN :ids")
    fun batchUpdateIsVerified(
        ids: List<UUID>,
        isVerified: Boolean,
    ): Int

    fun countByIsVerifiedTrue(): Long

    fun countByIsVerifiedFalse(): Long

    fun countByIsActiveTrue(): Long

    fun findAllByAddressStartingWith(addressPrefix: String): List<Center>

    @Query("SELECT c.id AS id, c.name AS name, c.address AS address FROM Center c WHERE c.address LIKE :prefix%")
    fun findIdNameAddressByAddressPrefix(
        @Param("prefix") prefix: String,
    ): List<CenterIdNameProjection>

    fun countByEstablishType(establishType: String): Long

    @Modifying
    @Transactional
    @Query("UPDATE Center c SET c.sourceUpdatedAt = :now WHERE c.id IN :ids")
    fun updateSourceUpdatedAt(
        ids: List<UUID>,
        now: LocalDateTime,
    ): Int
}
