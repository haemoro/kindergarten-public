package com.sotti.kindergarten.repository

import com.sotti.kindergarten.entity.CenterInsurance
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Repository
interface CenterInsuranceRepository : JpaRepository<CenterInsurance, UUID> {
    fun findAllByCenterId(centerId: UUID): List<CenterInsurance>

    fun findAllByCenterIdIn(centerIds: List<UUID>): List<CenterInsurance>

    @Modifying
    @Transactional
    @Query("DELETE FROM CenterInsurance e WHERE e.center.id = :centerId")
    fun deleteAllByCenterId(centerId: UUID)
}
