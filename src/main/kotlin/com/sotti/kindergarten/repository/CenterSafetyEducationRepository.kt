package com.sotti.kindergarten.repository

import com.sotti.kindergarten.entity.CenterSafetyEducation
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Repository
interface CenterSafetyEducationRepository : JpaRepository<CenterSafetyEducation, UUID> {
    fun findAllByCenterId(centerId: UUID): List<CenterSafetyEducation>

    fun findAllByCenterIdIn(centerIds: List<UUID>): List<CenterSafetyEducation>

    @Modifying
    @Transactional
    @Query("DELETE FROM CenterSafetyEducation e WHERE e.center.id = :centerId")
    fun deleteAllByCenterId(centerId: UUID)
}
