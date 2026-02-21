package com.sotti.kindergarten.repository

import com.sotti.kindergarten.entity.Center
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Repository
interface CenterRepository :
    JpaRepository<Center, UUID>,
    CenterRepositoryCustom {
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

    fun countByEstablishType(establishType: String): Long

    @Modifying
    @Transactional
    @Query("UPDATE Center c SET c.sourceUpdatedAt = :now WHERE c.id IN :ids")
    fun updateSourceUpdatedAt(
        ids: List<UUID>,
        now: LocalDateTime,
    ): Int
}
